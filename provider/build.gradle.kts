plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "snapshot.provider.appmanager"
    compileSdk = 36

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        consumerProguardFiles("consumer-rules.pro")
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

    buildFeatures {
        aidl = true
    }
    sourceSets {
        getByName("main") {
            aidl {
                directories += "src/main/aidl"
            }
        }
    }
}

dependencies {
    implementation(project(":api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Root service dependencies
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.libsu.nio)

    // Hidden API
    implementation(project(":hiddenapi"))
    implementation(project(":systemapi"))


    // Native library
    implementation(project(":io-nativefs"))
    implementation(project(":io-tar"))
    implementation(project(":io-zstd"))

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)


}
