# Mongosky Help & Support

Native Android / Jetpack Compose section. Support email: **mongosky0@gmail.com**.

**Version 1.0.1** fixes the scroll crash in version 1.0.0. The final FAQ and the feedback action previously shared the `feedback` lazy-list key. FAQ and fixed rows now have separate stable key namespaces. The installer upgrades the unmodified 1.0.0 release automatically.

Download `Mongosky-Help-Support.zip` to your Windows Downloads folder. Open the Android Studio **PowerShell** terminal at your Mongosky project root, then paste:

```powershell
$helpZip = Join-Path $env:USERPROFILE 'Downloads\Mongosky-Help-Support.zip'
Expand-Archive -LiteralPath $helpZip -DestinationPath '.\mongosky-help-support' -Force
powershell -NoProfile -ExecutionPolicy Bypass -File '.\mongosky-help-support\Mongosky-Help-Support\Install.ps1' -ProjectRoot (Get-Location).Path -Build
```

Replace the older ZIP in Downloads with this version before running the command. The command installs or upgrades the section and runs `:app:assembleDebug`. Run the app from Android Studio and open **Settings > Help and Support**. The debug APK is normally at `app/build/outputs/apk/debug/app-debug.apk`.

If you save the download somewhere else, change the first line to its full path. To install without building, omit `-Build`. The installer works on Windows PowerShell 5.1 and PowerShell 7; on Linux/macOS invoke it with `pwsh` instead of `powershell`.

## Features

- Twelve offline FAQ articles with search, topic filters, expandable answers, and an empty-results state.
- Contact support, report a problem, account help, privacy and safety questions, and feedback.
- A validated email draft addressed to `mongosky0@gmail.com`.
- Optional app/Android versions, included only when the user selects them.
- Native email handoff, copy-email action, and a fallback when no email app is available.
- Saved navigation, FAQ scroll, search, selected topic, and support draft during navigation and normal Android state restoration.
- Responsive capped content width, scrollable screens and category chips, keyboard handling, and accessible touch targets.

The form **opens an email draft**. The user reviews and sends it in their email app. Opening a draft does not confirm delivery or create a server ticket. A screenshot can be attached in the email app. FAQ content is bundled locally for immediate loading; this section needs no network request to display help.

## Integration

The GitHub repository snapshot used is `030f42e7e5302e252e83ac73442b7e560689a20c` from `Everest5204/Mongosky-app`.

The existing Settings implementation was checked against the earlier Mongosky Settings and About/Privacy/GetVerified packages. With an existing compatible Settings package, the installer adds a nested destination in **SettingsRoute.kt** and the new **helpsupport** package. It preserves the existing password, account deletion, and information callbacks. It also leaves the existing app navigation host in place.

A clean GitHub checkout has a Settings placeholder. For that checkout only, the installer adds the existing Settings menu scaffold from the prior Settings package and connects the native Settings host. Other menu entries retain their existing availability.

All checks run before any source file is written. Original files are copied to `.help-support-backups/<timestamp>/`, and interrupted writes are rolled back. Re-running the same package is idempotent. Locally modified Help & Support files or an unknown Settings structure cause the installer to stop before writing; keep the error and provide the indicated current source file for a compatible update.

## Maintenance

| File | Responsibility |
| --- | --- |
| HelpSupportContent.kt | FAQ text and the support email |
| HelpSupportModels.kt | Article, draft, reason, and email models |
| HelpSupportPolicy.kt | Search, input limits, validation, and email encoding |
| HelpSupportActions.kt | Android email, clipboard, and optional app information |
| HelpSupportSettingsHost.kt | Nested navigation and Settings/support state buckets |
| HelpSupportRoute.kt | Form and search state, Back handling, dialogs, and email handoff |
| HelpSupportScreen.kt | Help landing screen, search, topics, and FAQ list |
| HelpSupportContactScreen.kt | Contact form, validation focus, and keyboard layout |
| HelpSupportComponents.kt | Header, FAQ, contact card, and action rows |
| HelpSupportIcons.kt | Cached native vectors |
| HelpSupportTheme.kt | Section colors |
| HelpSupportListKeys.kt | Separate stable FAQ and fixed-row keys to prevent scroll collisions |

No new dependency, permission, database schema, or server endpoint is required. If a real support-ticket service is introduced later, add its repository/API and connect it explicitly rather than treating an email handoff as a submitted ticket.

## Validation

See `validation/validation-report.md` for compiler, policy, and installer results and the limits of verification. A full Gradle APK build and physical-device UI test were not run in the creation environment; the included `-Build` option runs the debug build with your existing Android toolchain.
