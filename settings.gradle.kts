pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") // MPAndroidChart için
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
rootProject.name = "SpyScan"
include(":app")
