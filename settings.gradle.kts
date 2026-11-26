rootProject.name = "ViolinSheet"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(
    ":androidApp",
    ":core:pdf",
    ":core:ink",
    ":core:data",
    ":feature:viewer",
    ":feature:annotation",
    ":feature:metronome",
)
