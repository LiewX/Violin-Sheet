// Shared setup for every KMP module: Android target for V1.0, iOS targets declared for the later port.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    jvmToolchain(17)
    androidTarget()
    // shortcut: iOS targets only on macOS, since KSP (Room) skips them elsewhere and the klib then fails to compile.
    // Revisit at the iPad port if Windows CI should check iOS compilation.
    if (System.getProperty("os.name").startsWith("Mac")) {
        iosArm64()
        iosSimulatorArm64()
    }
    compilerOptions {
        // Room's database constructor is an expect object.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.violinsheet" + path.replace(':', '.')
    compileSdk = 36
    defaultConfig {
        minSdk = 33
    }
}
