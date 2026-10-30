import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.hubiplan.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.hubiplan.app"
        // Android 8.0: Benachrichtigungskanäle, einheitliche Hintergrundregeln (Konzept, Frage 12)
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.10.0"
    }

    // Signatur der Veröffentlichung aus keystore.properties (nur auf dem Rechner des Herausgebers):
    // storeFile, storePassword, keyAlias, keyPassword. Fehlt die Datei, bleibt release unsigniert
    // (F-Droid signiert selbst).
    val keystore = rootProject.file("keystore.properties")
    if (keystore.exists()) {
        val p = Properties().apply { keystore.inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    // foss: F-Droid und APK, ohne Google-Bibliotheken · play: Google Play, zusätzlich Push über Google
    flavorDimensions += "vertrieb"
    productFlavors {
        create("foss") { dimension = "vertrieb" }
        create("play") {
            dimension = "vertrieb"
            // Firebase-Projekt des Herausgebers aus firebase.properties (nicht im Repo); leer: kein Google-Push
            val firebase = Properties().apply {
                rootProject.file("firebase.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
            }
            listOf("app_id", "project_id", "api_key", "sender_id").forEach { key ->
                val value = firebase.getProperty(key, "").replace("\"", "")
                buildConfigField("String", "FIREBASE_" + key.uppercase(), "\"$value\"")
            }
        }
    }

    buildTypes {
        release {
            if (keystore.exists()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // F-Droid: keine Abhängigkeits-Metadaten von Google im APK
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.compose.material.icons)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.embedded)
    implementation(libs.androidx.work)
    implementation(libs.androidx.biometric)
    debugImplementation(libs.compose.ui.tooling)
    "playImplementation"(libs.firebase.messaging)
    testImplementation(libs.junit)
}
