# Mongosky language section

Supported languages: English (`en`) and বাংলা (`bn`). English is the initial default.

- `language/LanguageSettingsHost.kt` owns Settings -> Language navigation and saved list state.
- `LanguageScreen.kt` provides two mutually exclusive native Material switches with accessible 48dp+ touch targets, scrolling and a 600dp content limit.
- `AppLanguage.kt` reads one local SharedPreferences choice, writes asynchronously and updates Compose snapshot state. `LanguageApplication` restores the choice before activity/service UI starts.
- `LanguageProvider.kt` supplies configured Android resources and Compose configuration without recreating the Activity. It retains the Activity in its ContextWrapper chain.
- `AppText.kt` bridges existing section labels and app-owned errors to native resources at presentation sites. It never translates user names, biographies, post/comment text, chat content or arbitrary remote titles. Unknown server wording retains the server's text.
- `catalog.json` is the translation source. Generated Android resources and `LanguageCatalog.kt` are checked in. The English path bypasses the index; Bangla resources are cached, and the index initializes once on demand. No network translation, new runtime dependency, fonts or image assets are included.
- App Bundles retain both languages for offline changes; unrelated dependency locales are filtered out. Translation text has a small nonzero package cost; APK/AAB download size must be measured from a real build.

## Maintaining copy

Add/update `{ "en": "English source", "bn": "বাংলা অনুবাদ" }` in catalog.json. Preserve `Mongosky` and numbered `%1$s` arguments. Existing `resource` names remain stable. Generate and verify with Python 3.10+:

```bash
python3 tools/language/generate_catalog.py
python3 tools/language/verify_language.py
```

Existing app-owned labels use `appText(label)` at display sites. New sections can use standard `stringResource(R.string.<generated_name>)` under LanguageProvider. Keep route names, JSON fields, MIME types and wire enum values unchanged. Localize only app-owned parts of annotated strings and sentences; keep names/content as opaque arguments.

Product wording: the Love reaction is **লাভ** (possessive: **লাভের**); the short Share action is **শেয়ার**. The catalog verifier checks these choices. `ProfileErrorText.kt` translates the editor's known error sentences and photo labels only when displayed, so switching language does not change drafts or save/upload state. The editor's existing native resource files stay in their own section.

## Android checks

Use the repository's existing Android Studio/SDK/JDK configuration (compile SDK 37; daemon toolchain 25). This update does not change Gradle, plugin or dependency versions.

```bash
bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
bash gradlew :app:connectedDebugAndroidTest
```

The second command needs a connected emulator/device. LanguagePolicyTest covers saved-locale fallback, switching policy and template argument preservation. LanguageSettingsFlowTest covers Settings navigation, global updates, exactly one active switch, preference writes, no Activity recreation and a user name identical to an English UI label.

Manual device checks: switch both ways; reopen after force-stop; check portrait/landscape, 320dp width, tablet, large font and dark theme; verify a drafted post/comment retains its text and the current route/feed remain in place. Backend-authored arbitrary messages/content are not machine-translated.
