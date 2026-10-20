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
        // 🟢 JitPack — Kadb کی ممکنہ dependencies کے لیے محفوظ رکھیں
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "RealEarnPayPal"
include(":app")
