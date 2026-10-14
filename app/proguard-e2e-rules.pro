# Added only when the end-to-end tests run against the release build (-Pe2eRelease). The test APK shares the
# app's copies of Kotlin, kotlinx and AndroidX, so they are kept whole. The app's own code is shrunk and renamed
# as in a shipped release, which is where R8 breakage shows up; the libraries ship their own rules.
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class androidx.** { *; }
