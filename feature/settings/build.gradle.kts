plugins {
    id("violinsheet.kmp-library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:data"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.material.icons.core)
        }
    }
}
