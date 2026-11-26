plugins {
    id("violinsheet.kmp-library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
