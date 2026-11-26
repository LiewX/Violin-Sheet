plugins {
    id("violinsheet.kmp-library")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.room.runtime)
            api(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(project(":core:pdf"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    if (configurations.findByName("kspIosArm64") != null) {
        add("kspIosArm64", libs.room.compiler)
        add("kspIosSimulatorArm64", libs.room.compiler)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}
