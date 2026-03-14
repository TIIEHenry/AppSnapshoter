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
            java.srcDir("src/main/jni/zstd-jni/src/main/java")
        }
    }

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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