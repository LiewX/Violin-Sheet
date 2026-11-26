plugins {
    id("violinsheet.kmp-library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.ui)
            implementation(compose.foundation)
        }
        androidMain.dependencies {
            implementation(libs.ink.authoring)
            implementation(libs.ink.brush)
            implementation(libs.ink.strokes)
            implementation(libs.ink.rendering)
        }
    }
}
