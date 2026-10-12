param([string]$ProjectRoot = (Get-Location).Path, [switch]$Build)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$mainRelative = 'app/src/main/java/com/mongosky/app/navigation/MainScreen.kt'
$settingsRelative = 'app/src/main/java/com/mongosky/app/settings/SettingsRoute.kt'
$modelsRelative = 'app/src/main/java/com/mongosky/app/settings/SettingsModels.kt'
if (!(Test-Path -LiteralPath (Join-Path $root 'app/build.gradle.kts')) -or !(Test-Path -LiteralPath (Join-Path $root $mainRelative))) {
    throw 'Open the Android Studio PowerShell terminal at the Mongosky project root and run this command again.'
}
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Raw | ConvertFrom-Json
$utf8 = New-Object Text.UTF8Encoding($false)
$operations = New-Object 'System.Collections.Generic.List[object]'
function FileHash([string]$path) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([IO.File]::ReadAllBytes($path)))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function TextHash([string]$value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($value)))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function ReadNormalized([string]$path) { return [IO.File]::ReadAllText($path).Replace("`r`n", "`n") }
function RequireRegularPath([string]$path) {
    $cursor = $path
    while ($cursor -and $cursor.Length -ge $root.Length) {
        if (Test-Path -LiteralPath $cursor) {
            $item = Get-Item -LiteralPath $cursor -Force
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Cannot install through a linked path: $cursor" }
        }
        $cursor = Split-Path -Parent $cursor
    }
}
function StageText([string]$relative, [string]$value) {
    $target = Join-Path $root $relative
    RequireRegularPath $target
    $value = $value.Replace("`r`n", "`n")
    if (Test-Path -LiteralPath $target) {
        $original = [IO.File]::ReadAllText($target)
        if ($original.Replace("`r`n", "`n") -eq $value) { return }
        if ($original.Contains("`r`n")) { $value = $value.Replace("`n", "`r`n") }
    }
    $operations.Add([pscustomobject]@{relative=$relative; bytes=$utf8.GetBytes($value)})
}
function KotlinMask([string]$text) {
    $pattern = '(?s)""".*?"""|"(?:\\.|[^"\\])*"|''(?:\\.|[^''\\])*''|//[^\r\n]*|/\*.*?\*/'
    return [regex]::Replace($text, $pattern, [Text.RegularExpressions.MatchEvaluator]{ param($m) return (' ' * $m.Length) })
}
function AddHelpToSettings([string]$text) {
    $text = $text.Replace("`r`n", "`n")
    $marker = '// Mongosky Help & Support integration v1'
    if ($text.Contains($marker)) {
        if (!$text.Contains('HelpSupportSettingsHost { openHelpAndSupport ->') -or !$text.Contains('SettingsMenuId.HELP_AND_SUPPORT -> openHelpAndSupport()')) {
            throw 'The existing Help & Support integration was changed. No project files changed. Send SettingsRoute.kt for a compatible update.'
        }
        return $text
    }
    $mask = KotlinMask $text
    $signatures = [regex]::Matches($mask, '\bfun\s+SettingsRoute\s*\(')
    if ($signatures.Count -ne 1) { throw 'Cannot locate one SettingsRoute function. No project files changed.' }
    $paren = $mask.IndexOf('(', $signatures[0].Index)
    $depth = 0; $close = -1
    for ($i=$paren; $i -lt $mask.Length; $i++) {
        if ($mask[$i] -eq '(') { $depth++ }
        elseif ($mask[$i] -eq ')') { $depth--; if ($depth -eq 0) { $close=$i; break } }
    }
    if ($close -lt 0) { throw 'Unbalanced SettingsRoute signature. No project files changed.' }
    $open = $close + 1
    while ($open -lt $mask.Length -and [char]::IsWhiteSpace($mask[$open])) { $open++ }
    if ($open -ge $mask.Length -or $mask[$open] -ne '{') { throw 'Unsupported SettingsRoute body. No project files changed.' }
    $depth = 0; $end = -1
    for ($i=$open; $i -lt $mask.Length; $i++) {
        if ($mask[$i] -eq '{') { $depth++ }
        elseif ($mask[$i] -eq '}') { $depth--; if ($depth -eq 0) { $end=$i; break } }
    }
    if ($end -lt 0) { throw 'Unbalanced SettingsRoute body. No project files changed.' }
    $body = $text.Substring($open+1, $end-$open-1)
    $legacy = [regex]::Matches($body, 'SettingsScreen\(onBack\s*=\s*onBack\s*\)')
    if ($legacy.Count -eq 1) {
        $replacement = @'
SettingsScreen(onBack = onBack,
                enabledMenus = setOf(SettingsMenuId.HELP_AND_SUPPORT),
                onMenuSelected = { menu ->
                    when (menu) {
                        SettingsMenuId.HELP_AND_SUPPORT -> openHelpAndSupport()
                        else -> Unit
                    }
                })
'@
        $body = $body.Replace($legacy[0].Value, $replacement)
        $body = $body.Replace('            // This section is deliberately menu-only. No submenu callback is supplied.', '')
    } else {
        $enabled = [regex]::Matches($body, 'enabledMenus\s*=\s*buildSet\s*\{')
        $dispatch = [regex]::Matches($body, 'onMenuSelected\s*=\s*\{\s*(?<menu>[A-Za-z_]\w*)\s*->')
        if ($enabled.Count -ne 1 -or $dispatch.Count -ne 1) {
            throw 'Unsupported Settings menu structure. No project files changed. Send your current SettingsRoute.kt for a compatible update.'
        }
        $body = $body.Insert($enabled[0].Index + $enabled[0].Length, "`n                    add(SettingsMenuId.HELP_AND_SUPPORT)")
        $menu = $dispatch[0].Groups['menu'].Value
        $when = [regex]::Matches($body, ('when\s*\(\s*' + [regex]::Escape($menu) + '\s*\)\s*\{'))
        if ($when.Count -ne 1 -or $body.Contains('SettingsMenuId.HELP_AND_SUPPORT ->')) {
            throw 'Cannot safely connect the Help & Support action. No project files changed. Send SettingsRoute.kt for a compatible update.'
        }
        $body = $body.Insert($when[0].Index + $when[0].Length, "`n                        SettingsMenuId.HELP_AND_SUPPORT -> openHelpAndSupport()")
    }
    $indented = (($body.Trim("`r", "`n") -split "`n" | ForEach-Object { if ($_ -eq '') { '' } else { '    ' + $_ } }) -join "`n")
    $wrapped = "`n    $marker`n    HelpSupportSettingsHost { openHelpAndSupport ->`n$indented`n    }`n"
    $text = $text.Substring(0,$open+1) + $wrapped + $text.Substring($end)
    $package = 'package com.mongosky.app.settings'
    $import = 'import com.mongosky.app.helpsupport.HelpSupportSettingsHost'
    if (!$text.Contains($import)) {
        if ([regex]::Matches($text, [regex]::Escape($package)).Count -ne 1) { throw 'Unexpected Settings package. No project files changed.' }
        $text = $text.Replace($package, "$package`n`n$import")
    }
    return $text
}

# Validate the entire package and integration before touching any project files.
foreach ($file in $manifest.files) {
    if ($file.path -notmatch '^app/src/main/java/com/mongosky/app/helpsupport/[A-Za-z0-9]+\.kt$') { throw 'Unexpected Help & Support package path.' }
    $source = Join-Path (Join-Path $PSScriptRoot 'payload') $file.path
    if ((FileHash $source) -ne $file.sha256) { throw "Package checksum failed: $($file.path). No project files changed." }
    $target = Join-Path $root $file.path
    RequireRegularPath $target
    if ((Test-Path -LiteralPath $target) -and (ReadNormalized $target) -ne (ReadNormalized $source)) {
        $previousHash = TextHash (ReadNormalized $target)
        if (@($file.acceptedPreviousNormalizedSha256) -notcontains $previousHash) {
            throw "Your Help & Support source has local changes: $($file.path). No project files changed."
        }
    }
    StageText $file.path (ReadNormalized $source)
}
foreach ($file in $manifest.compatibilityFiles) {
    if ($file.name -notmatch '^Settings[A-Za-z]+\.kt$') { throw 'Unexpected compatibility package path.' }
    if ((FileHash (Join-Path (Join-Path $PSScriptRoot 'compatibility') $file.name)) -ne $file.sha256) {
        throw 'Compatibility package checksum failed. No project files changed.'
    }
}
$settingsPath = Join-Path $root $settingsRelative
if (Test-Path -LiteralPath $settingsPath) {
    foreach ($required in @('SettingsModels.kt','SettingsScreen.kt','SettingsIcons.kt')) {
        if (!(Test-Path -LiteralPath (Join-Path (Split-Path $settingsPath) $required))) { throw 'The Settings package is incomplete. No project files changed.' }
    }
    if (!(ReadNormalized (Join-Path $root $modelsRelative)).Contains('HELP_AND_SUPPORT')) { throw 'No Help & Support menu ID found. No project files changed.' }
    StageText $settingsRelative (AddHelpToSettings (ReadNormalized $settingsPath))
} else {
    $settingsDirectory = Split-Path $settingsPath
    if (Test-Path -LiteralPath $settingsDirectory) { throw 'An unrecognized Settings package already exists. No project files changed.' }
    foreach ($file in $manifest.compatibilityFiles) {
        $source = Join-Path (Join-Path $PSScriptRoot 'compatibility') $file.name
        $text = ReadNormalized $source
        if ($file.name -eq 'SettingsRoute.kt') { $text = AddHelpToSettings $text }
        StageText ('app/src/main/java/com/mongosky/app/settings/' + $file.name) $text
    }
    $mainPath = Join-Path $root $mainRelative
    $main = ReadNormalized $mainPath
    foreach ($patch in $manifest.baselineIntegrationPatches) {
        if ($main.Contains($patch.after)) { continue }
        $anchor = $null
        foreach ($candidate in $patch.before) {
            if ([regex]::Matches($main,[regex]::Escape($candidate)).Count -eq 1) { $anchor=$candidate; break }
        }
        if (!$anchor) { throw "Cannot safely integrate $($patch.name) into MainScreen.kt. No project files changed." }
        $replacement = $patch.after
        if ($patch.mode -eq 'prefix-first') { $replacement += $anchor.TrimStart() }
        elseif ($patch.mode -eq 'drawer-prefix') { $replacement += $anchor.Substring($anchor.IndexOf(' = ')+3) }
        $main = $main.Replace($anchor, $replacement)
    }
    StageText $mainRelative $main
}

if ($operations.Count -gt 0) {
    $backup = Join-Path $root ('.help-support-backups/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $backup -Force | Out-Null
    $records = @()
    foreach ($item in $operations) {
        $target = Join-Path $root $item.relative
        $exists = Test-Path -LiteralPath $target
        if ($exists) {
            $saved = Join-Path $backup $item.relative
            New-Item -ItemType Directory -Path (Split-Path $saved) -Force | Out-Null
            Copy-Item -LiteralPath $target -Destination $saved
        }
        $records += [pscustomobject]@{path=$item.relative; existed=$exists}
    }
    $records | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $backup 'files.json') -Encoding UTF8
    $written = @()
    try {
        foreach ($item in $operations) {
            $target = Join-Path $root $item.relative
            New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
            $written += $item
            [IO.File]::WriteAllBytes($target, $item.bytes)
        }
    } catch {
        foreach ($item in $written) {
            $target = Join-Path $root $item.relative
            $saved = Join-Path $backup $item.relative
            if (Test-Path -LiteralPath $saved) { Copy-Item -LiteralPath $saved -Destination $target -Force }
            elseif (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target }
        }
        throw
    }
    Write-Host "Installed Help & Support. Contact: mongosky0@gmail.com. Backup: $backup"
} else { Write-Host 'Help & Support is already installed.' }
if ($Build) {
    Push-Location -LiteralPath $root
    try {
        if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { & (Join-Path $root 'gradlew.bat') ':app:assembleDebug' }
        else { & bash (Join-Path $root 'gradlew') ':app:assembleDebug' }
        if ($LASTEXITCODE -ne 0) { throw 'Help & Support is installed, but the Android build failed. Check the Gradle error above.' }
    } finally { Pop-Location }
} else { Write-Host 'Android Studio: Run the app, then open Settings > Help and Support.' }
