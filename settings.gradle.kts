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
    }
}

rootProject.name = "SystemApps"
include(":core-ui")
include(":app-launcher")
include(":app-settings")
include(":app-files")
include(":app-phone")
include(":app-contacts")
include(":app-messages")
include(":app-camera")
include(":app-gallery")
include(":app-browser")
include(":app-clock")
include(":app-calculator")
include(":app-calendar")
include(":app-notes")
