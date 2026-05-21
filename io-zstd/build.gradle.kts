plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "nota.android.io.zstd"
    ndkVersion = libs.versions.ndkVersion.get()
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        all {
            val zstdVersion = File("${projectDir}/src/main/jni/zstd-jni/version").readText().trim()
            buildConfigField("String", "ZSTD_VERSION", "\"${zstdVersion}\"")
        }
    }

    sourceSets {
        getByName("main") {
            java.directories += "src/main/jni/zstd-jni/src/main/java"
        }
    }

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments.addAll(listOf("-DANDROID_PLATFORM=28"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/jni/CMakeLists.txt")
            version = libs.versions.cmakeVersion.get()
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
        }
    }
}

dependencies {
    // Compression
    //noinspection UseTomlInstead
//    api("com.github.luben:zstd-jni:1.5.7-7@aar") caused error
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}