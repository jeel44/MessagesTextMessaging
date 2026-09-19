plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "text.message.sms.messaging.baselineprofile"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 37

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Profiles are generated against a build that behaves like release (so the recorded classes
    // and methods match what will actually ship) but stays debuggable/unsigned-for-local-use --
    // the standard shape of a baseline-profile-generation build type.
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

// Points the plugin at the module whose profile is being generated -- `generateBaselineProfile`
// writes the result straight into app/src/main/baselineProfiles/, and applying the same plugin in
// app/build.gradle.kts is what makes AGP compile that file into the release build automatically.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
