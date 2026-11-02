import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.dummy.app"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "app"
    productFlavors {
        val apps = listOf(
            "Netflix Fake", "YouTube Pro", "Prime Video Clone", "Disney+ Test",
            "HBO Max Fake", "Spotify TV", "Twitch Stream", "Plex Media",
            "VLC Player", "RetroArch TV", "Kodi Media Center", "IPTV Smart Player",
            "File Explorer", "MX Player Pro", "Stremio TV", "Crunchyroll Anime",
            "Pluto TV Free", "Steam Link", "Moonlight Game", "GeForce Now"
        )
        apps.forEachIndexed { index, appName ->
            create("app${index + 1}") {
                dimension = "app"
                applicationId = "com.testapp.fake${index + 1}"
                manifestPlaceholders["appLabel"] = appName
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
