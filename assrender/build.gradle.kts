plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.LumeraD3v"
                artifactId = "assrender"
                version = "1.0.0"
            }
        }
    }
}

android {
    namespace = "io.github.assrender"
    compileSdk = 35

    defaultConfig {
        minSdk = 21

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += ""
                arguments += listOf(
                    "-DANDROID_STL=c++_shared"
                )
            }
        }

        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
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
    compileOnly("androidx.media3:media3-exoplayer:1.5.1")
    compileOnly("androidx.media3:media3-common:1.5.1")
    compileOnly("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.annotation:annotation:1.9.1")
}
