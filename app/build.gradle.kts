plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    sourceSets {
        getByName("main") {
            java.srcDir(rootProject.file("browser-core"))
            res.srcDir(rootProject.file("app-res"))
        }
    }

    namespace = "com.mainbrowser.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mainbrowser.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 9
        versionName = "2.0"
        ndk { abiFilters += listOf("armeabi-v7a") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
