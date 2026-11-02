import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firma de release: se lee de keystore.properties (NUNCA se versiona).
// Ver docs/PLAY_COMPLIANCE.md. Recomendado: Play App Signing + clave de subida.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.tvlauncher.app"
    compileSdk = 36

    defaultConfig {
        // TODO(Play): CAMBIAR antes de la primera subida; el applicationId es permanente en Google Play
        // (ej. com.tu_nombre.tvlauncher). El namespace puede quedarse como está.
        applicationId = "com.tvlauncher.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    androidResources {
        localeFilters += listOf("en", "es")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sin keystore.properties el AAB sale SIN firmar (Play lo rechaza de forma explícita);
            // nunca se cae a la clave de debug, que Play también rechaza pero sin avisar en el build.
            // Para instalar en el TV box sin keystore: ./gradlew assembleRelease -PsignWithDebug
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug").takeIf { project.hasProperty("signWithDebug") }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    // Instala los Baseline Profiles (propios y de Compose) también fuera de Play (sideload / tvbox).
    implementation(libs.androidx.profileinstaller)
}
