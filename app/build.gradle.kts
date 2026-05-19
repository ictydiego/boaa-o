import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")

    // ATENÇÃO: Se isso der erro de 'Unresolved reference: libs',
    // troque por: id("com.google.firebase.appdistribution")
    alias(libs.plugins.google.firebase.appdistribution)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingProperty(name: String): String =
    keystoreProperties.getProperty(name)
        ?: error("Missing '$name' in ${keystorePropertiesFile.path}")

android {
    namespace = "br.unasp.boacao"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.unasp.boacao"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(signingProperty("storeFile"))
                storePassword = signingProperty("storePassword")
                keyAlias = signingProperty("keyAlias")
                keyPassword = signingProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false // Habilite para produção (true) e configure o ProGuard/R8
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            firebaseAppDistribution {
                // CORREÇÃO: Usar releaseNotes direto em texto, ou apontar para um arquivo na raiz do app (ex: "notas_versao.txt")
                releaseNotes = "Nova versão de produção disponível para testes."
                testers = "matheuscruz03242000@gmail.com, natanrochadealmeida@gmail.com, leandrosgama@gmail.com,icty.diego@gmail.com"
            }
        }
        debug {
            // Configurações específicas para debug
        }
    }

    flavorDimensions += listOf("version")

    productFlavors {
        create("demo") {
            dimension = "version"
            firebaseAppDistribution {
                releaseNotes = "Release notes for demo version"
                testers = "demo@testers.com"
            }
        }
        create("full") {
            dimension = "version"
            firebaseAppDistribution {
                releaseNotes = "Release notes for full version"
                testers = "full@testers.com"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Animação e Imagens
    implementation("com.airbnb.android:lottie-compose:6.4.0")
    implementation("com.vanniktech:android-image-cropper:4.6.0")
    implementation("org.mindrot:jbcrypt:0.4")

    // Firebase (BOM gerencia as versões)
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-firestore")

    // PDF e OCR
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Core AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.compose.foundation:foundation:1.6.8")

    // Jetpack Compose (BOM - Ele quem dita a versão dos pacotes abaixo)
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose UI (Sem versão explícita por causa do BOM)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0") // Activity não faz parte do BOM do compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime") // CORREÇÃO: Removido o :1.6.8
    implementation("androidx.compose.animation:animation") // CORREÇÃO: Removido o :1.6.8

    // Gráficos e UI Adicional (Requer repositório JitPack no settings.gradle.kts)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("com.google.accompanist:accompanist-swiperefresh:0.36.0")
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Navegação e ZXing
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Localização e Mapas
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.itextpdf:itextg:5.5.10")
    implementation("androidx.bluetooth:bluetooth:1.0.0-alpha02")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // CameraX (para leitor de QR Code)
    val cameraVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")
    implementation("com.google.guava:guava:32.0.1-android")

    // Testes
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
