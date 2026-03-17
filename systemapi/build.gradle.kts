plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "tiiehenry.android.app.snapshot.systemapi"
    compileSdk = 36

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf(
            "-Xlint:unchecked",
            "-Xlint:deprecation"
        ))
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
}