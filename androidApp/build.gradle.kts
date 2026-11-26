plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.violinsheet.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.violinsheet"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":feature:viewer"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:annotation"))
    implementation(compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.uiautomator)
    implementation(libs.lifecycle.viewmodel.compose)
}
