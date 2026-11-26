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
