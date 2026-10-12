# Version 1.0.1 validation

The original scroll crash was caused by duplicate lazy-list keys: the FAQ article with ID `feedback` and the fixed feedback action both used `feedback`.

The original key sequence reproduces **`IllegalArgumentException: Key feedback was used multiple times`** in the official Compose saveable-state runtime used by lazy item reuse. The corrected sequence composes successfully. Three runtime regression tests passed, including filtering, reordering, and an empty result list. These are JVM tests of the actual Compose state implementation, using host-only logging/tracing shims; they are not a device scroll test.

- Full-source Kotlin/Compose compilation: **161 files** on the clean GitHub integration; **176 files** with existing Settings/information pages. Both passed.
- Seven support-policy tests passed in each compiled project.
- Ten PowerShell installer checks passed, including upgrading the previous 1.0.0 ZIP and repeated installation.
- Upgrading 1.0.0 modifies only the existing **HelpSupportScreen.kt** and adds **HelpSupportListKeys.kt**. The existing Settings host and other sections remain byte-for-byte unchanged.
- Locally edited or damaged files still fail preflight before project writes.

The compiler is Kotlin 2.2.10 plus its official Compose plugin, checked against the Android API 36 platform jar. Project SDK 37 and build settings are unchanged. A full Gradle APK build and physical-device/emulator test were not performed here; `Install.ps1 -Build` performs the debug APK build in the user's existing project.

Help & Support uses no backend. FAQ content is bundled locally. The form opens an email draft for **mongosky0@gmail.com**; it does not create a database ticket or confirm email delivery.
