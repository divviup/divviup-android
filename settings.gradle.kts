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
        maven {
            name = "Staging"
            url = uri("https://s01.oss.sonatype.org/content/repositories/orgdivviup-1000")
        }
    }
}

rootProject.name = "Divvi Up"
include(":divviup")
include(":divviup:commontest")
include(":sampleapp")
