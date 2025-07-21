
plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "io.fastpix.player"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    api(libs.media3)
}

afterEvaluate {
    extensions.configure<PublishingExtension>("publishing") {
        publications {
            create<MavenPublication>("bar") {
                groupId = "io.fastpix.player"
                artifactId = "android"
                version = "1.0.0"

                artifact("$buildDir/outputs/aar/library-release.aar")
                pom.withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    configurations.getByName("api").dependencies.forEach { dependency ->
                        if (dependency.group != null && dependency.version != null) {
                            val dependencyNode = dependenciesNode.appendNode("dependency")
                            dependencyNode.appendNode("groupId", dependency.group)
                            dependencyNode.appendNode("artifactId", dependency.name)
                            dependencyNode.appendNode("version", dependency.version)
                            dependencyNode.appendNode("scope", "compile")
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                name = "GithubPackages"
                url = uri("https://maven.pkg.github.com/FastPix/fastpix-android-player")
                credentials {
                    username = project.findProperty("gpr.user").toString()
                    password = project.findProperty("gpr.key").toString()
                }
            }
        }
    }
}