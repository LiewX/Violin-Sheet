plugins {
    id("violinsheet.kmp-library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:data"))
            api(project(":feature:settings"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.lifecycle.viewmodel.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// The Bravura font in src/commonMain/composeResources/font, its OFL.txt licence in composeResources/files.
compose.resources {
    packageOfResClass = "com.violinsheet.feature.annotation.resources"
}
