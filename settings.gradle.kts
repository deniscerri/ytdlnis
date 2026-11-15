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
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "Kinetic Media Engine"

include(":app")
include(":core:common")
include(":core:database")
include(":core:datastore")
include(":feature:browser")
include(":feature:downloads")
include(":feature:library")
include(":feature:settings")
