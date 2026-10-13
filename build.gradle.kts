// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.androidx.room) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.spotless)
}

// Kotlin style is ktlint's, configured in .editorconfig. `spotlessApply` formats; CI runs `spotlessCheck`.
spotless {
  kotlin {
    target("app/src/**/*.kt")
    ktlint(libs.versions.ktlint.get())
  }
  kotlinGradle {
    target("*.gradle.kts", "app/*.gradle.kts")
    ktlint(libs.versions.ktlint.get())
  }
}
