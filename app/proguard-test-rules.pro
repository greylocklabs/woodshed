# Shrinking rules for the end-to-end test APK when it runs against the release build (-Pe2eRelease).
# Error Prone's annotations are compile-only: androidx.test references them without shipping them, and they
# name a compiler-only class that never exists on a device.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.lang.model.element.Modifier
