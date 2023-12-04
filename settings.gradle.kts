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
        repositories {
            maven {
                name = "OSSRH Snapshots"
                url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            }
        }
    }
}

rootProject.name = "Divvi Up"
include(":divviup")
include(":divviup:commontest")
include(":sampleapp")
