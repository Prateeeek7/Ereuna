pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Ereuna"

include(":app")
include(":core:design")
include(":core:model")
include(":core:network")
include(":core:data")
include(":feature:search")
include(":feature:map")
include(":feature:paper")
include(":feature:compare")
include(":feature:graph")
include(":feature:library")
include(":feature:settings")
include(":feature:auth")
