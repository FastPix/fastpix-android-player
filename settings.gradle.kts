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
        maven {
            url = uri("https://maven.pkg.github.com/FastPix/android-data-androidXmedia3")
        }
        maven {
            url = uri("https://maven.pkg.github.com/FastPix/android-data-media3-player-sdk")
            credentials {
                val props = java.util.Properties()
                val localPropsFile = file("${rootDir}/local.properties")

                if (localPropsFile.exists()) {
                    props.load(localPropsFile.inputStream())
                }
                username = props.getProperty("gpr.user")
                password = props.getProperty("gpr.key")
            }
        }
    }
}

rootProject.name = "AndroidFastpixPlayer"
include(":library")
include(":app")
