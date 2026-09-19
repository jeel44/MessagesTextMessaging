// Root build file. Plugins are declared here so the version catalog resolves them once,
// then applied in the modules that actually need them.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
}
