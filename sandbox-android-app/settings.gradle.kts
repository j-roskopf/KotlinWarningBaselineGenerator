pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    includeBuild("../kotlin-warning-baseline-generator")
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KotlinWarningBaselineGeneratorAndroidApplication"
include(":app")
include(":feature")
include(":empty-source-set-feature")


