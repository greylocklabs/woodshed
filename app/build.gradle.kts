plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.androidx.room)
  alias(libs.plugins.roborazzi)
}

// The version lives in VERSION at the repo root; `make release` bumps it. Play's version code is derived from it,
// so it always rises with the version: 1.2.3 becomes 10203.
val appVersion = rootProject.file("VERSION").readText().trim()

fun versionCodeOf(version: String): Int {
  val (major, minor, patch) = version.split(".").map { it.toInt() }
  require(minor < 100 && patch < 100) { "version code needs minor and patch below 100: $version" }
  return major * 10_000 + minor * 100 + patch
}

android {
  namespace = "com.larakelley.woodshed"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.larakelley.woodshed"
    minSdk = 26
    targetSdk = 36
    versionName = appVersion
    versionCode = versionCodeOf(appVersion)
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  // The seed JSON lives in the repo's data/ directory and ships as assets unchanged.
  sourceSets {
    getByName("main") { assets.srcDir(rootProject.file("data")) }
    // Migration tests read the exported schemas; Robolectric only sees app assets, so debug builds carry them.
    getByName("debug") { assets.srcDir("$projectDir/schemas") }
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
    // Robolectric's SDK 36 environment setup uses JDK internals.
    unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
    // End-to-end tests run on an emulator Gradle downloads and manages: `./gradlew pixel6api34DebugAndroidTest`.
    // Never on a phone with real practice data, since a connected test run reinstalls the app.
    managedDevices {
      localDevices {
        create("pixel6api34") {
          device = "Pixel 6"
          apiLevel = 34
          systemImageSource = "aosp-atd"
        }
      }
    }
  }

  lint {
    // Any warning fails the build, so new ones get fixed instead of piling up. Newer versions are Dependabot's job.
    warningsAsErrors = true
    abortOnError = true
    disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    // A newer Android SDK than targetSdk is reported, not fatal: raising it is a deliberate upgrade with its own testing.
    informational += "OldTargetApi"
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  // Robolectric needs Java 21 to run SDK 36 tests.
  jvmToolchain(21)
}

// Screenshot references live beside the tests. `recordRoborazziDebug` rewrites them; CI runs `verifyRoborazziDebug`.
roborazzi {
  outputDir.set(file("src/test/screenshots"))
}

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.test.rules)

  // Data
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.kotlinx.serialization.json)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.test.core)
  testImplementation(composeBom)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
}
