import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.androidx.baselineprofile)
}

// Release signing lives outside version control. On a machine (or CI) that has no
// keystore.properties, release signingConfig is simply never created -- assembleRelease then
// produces an unsigned build instead of failing the whole configuration.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystorePropertiesFile.exists()

// UMP consent debug overrides (see AdConsentManager) -- read from the uncommitted
// local.properties and compiled into debug builds only, so each developer can force a region
// without touching code:
//   ump.debugGeography=EEA          # EEA | REGULATED_US_STATE | OTHER | blank = no override
//   ump.testDeviceHashedId=XXXXXXXX # UMP logs this device's hashed ID to Logcat on first run
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
fun localProperty(key: String): String = localProperties.getProperty(key)?.trim().orEmpty()

android {
    namespace = "text.message.sms.messaging"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "text.message.sms.messaging"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Overridden in debug only (see buildTypes.debug); release/benchmark never apply any
        // UMP debug settings.
        buildConfigField("String", "UMP_DEBUG_GEOGRAPHY", "\"\"")
        buildConfigField("String", "UMP_TEST_DEVICE_HASHED_ID", "\"\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "UMP_DEBUG_GEOGRAPHY", "\"${localProperty("ump.debugGeography")}\"")
            buildConfigField("String", "UMP_TEST_DEVICE_HASHED_ID", "\"${localProperty("ump.testDeviceHashedId")}\"")
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }

        // Release-shaped but locally installable, for on-device performance measurement only (see
        // the Phase 2 cold-start investigation) -- initWith(release) so it inherits release's
        // settings, including optimization.enable=false above, unchanged: R8 is deliberately NOT
        // enabled here, that is a separate, riskier change tracked on its own. Only debuggable and
        // signing differ from release, so this measures the same dex/resource shape a release
        // build would actually ship without needing a real release signing key on this machine.
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // MigrationTestHelper (see MessagingDatabaseMigrationTest) reads each version's exported
    // schema JSON as a test asset to build the "before" database it migrates from.
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room exports the generated schema JSON so migrations can be diffed in review.
room {
    schemaDirectory("$projectDir/schemas")
}

// Off, not automatic: generating a profile needs a connected device/emulator, and this repo's
// release build shouldn't silently depend on one being attached (CI included). Run
// `./gradlew :baselineprofile:generateBaselineProfile` explicitly when the profile needs updating
// -- see baselineprofile/src/main/java/.../BaselineProfileGenerator.kt for the journey it records.
baselineProfile {
    automaticGenerationDuringBuild = false
}

dependencies {
    // AndroidX foundation
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // ProcessLifecycleOwner -- app-wide foreground/background for AppOpenAdManager
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.lottie.compose)
    implementation(libs.coil.compose)

    // Ads -- call-end screen's native/banner ad slot, no mediation
    implementation(libs.google.play.services.ads)
    // Ad consent (GDPR/US-state messages) -- gates every ad request, see AdConsentManager
    implementation(libs.google.ump)

    // Dependency injection
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Per-app language
    implementation(libs.androidx.appcompat)

    // Background work -- scheduled sends
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Kotlin libraries
    implementation(libs.kotlinx.coroutines.android)

    // Installs the profile :baselineprofile generates into ART's own profile store on-device --
    // without this, a generated/shipped baseline-prof.txt in the release APK/AAB is inert on API
    // < 34 (API 34+'s installer is built into the platform, but this app's minSdk is 26).
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)

    // Instrumented tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
