pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 🟢 JitPack — Kadb کی spake2-java dependency کے لیے
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "RealEarnPayPal"
include(":app")
