pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "Eruditus"

// L'app Android (SPEC § 1.3).
include(":app")

// Tests serveur (spec/serveur.md § 10) : module JVM rangé dans server/tests/, lancé par
// `./gradlew :server-tests:test`, par server/deploy.sh puis par la CI.
include(":server-tests")
project(":server-tests").projectDir = file("../server/tests")
