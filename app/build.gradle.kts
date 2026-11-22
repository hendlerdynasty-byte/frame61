import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

android {
    namespace = "de.hendler.frame61"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "de.hendler.frame61"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Version comes from version.properties so an old signed build can be
        // produced for a real update run without editing this file.
        val versionProps = Properties().apply {
            rootProject.file("version.properties").inputStream().use { load(it) }
        }
        versionCode = versionProps.getProperty("versionCode").toInt()
        versionName = versionProps.getProperty("versionName")

        // M6 (TMDB de-DE, Verfügbarkeits-Badges, Trakt) ist optional und
        // standardmäßig aus. Die Pfade sind Stubs; kein Key, kein Live-Call.
        val m6 = (project.findProperty("frame61.m6") as? String)?.toBoolean() ?: false
        buildConfigField("boolean", "M6_ENABLED", m6.toString())

        vectorDrawables {
            useSupportLibrary = true
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../frame61.jks")
            // Nur aus der Umgebung; kein Passwort-Fallback im Repository.
            storePassword = System.getenv("FRAME61_STORE_PASS")
            keyAlias = System.getenv("FRAME61_KEY_ALIAS")
            keyPassword = System.getenv("FRAME61_KEY_PASS")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Remote builds without the signing env produce an unsigned artifact;
            // it is signed only on the Mac with the out-of-repo keystore.
            signingConfig = if (System.getenv("FRAME61_STORE_PASS") != null) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        // Messvariante: R8 wie Release, aber mit Debug-Key und eigener ID.
        // Nicht fuer Store/Release; nur fuer lokale Performance-Messungen.
        create("perf") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".perf"
            versionNameSuffix = "-perf"
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isProfileable = true
        }
        // Separate CI installation beside release/perf; no signature downgrade or data wipe.
        create("ci") {
            initWith(getByName("perf"))
            applicationIdSuffix = ".ci"
            versionNameSuffix = "-ci"
        }
        // CI builds this unsigned; local acceptance signing uses the existing release key.
        create("acceptance") {
            initWith(getByName("perf"))
            applicationIdSuffix = ".acceptance"
            versionNameSuffix = "-acceptance"
            signingConfig = null
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

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.kotlin.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main").jniLibs.srcDirs("src/main/jniLibs")
        getByName("ci") {
            java.srcDir("src/perf/java")
            assets.srcDir("src/perf/assets")
            manifest.srcFile("src/perf/AndroidManifest.xml")
        }
        getByName("acceptance") {
            java.srcDir("src/perf/java")
            assets.srcDir("src/perf/assets")
            manifest.srcFile("src/perf/AndroidManifest.xml")
        }
    }
}

dependencies {
    // Modules
    implementation(project(":core:design"))

    implementation(project(":core:data"))
    implementation(project(":core:meta"))
    implementation(project(":core:net"))
    implementation(project(":scraper:engine"))
    implementation(project(":scraper:resolver"))
    implementation(project(":player"))
    implementation(project(":recs"))
    implementation(project(":sources:free"))

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.wsc)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.tv.provider)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.okio)
    implementation(libs.kotlinx.serialization)
    implementation(libs.androidx.datastore)

    // Media3 for the player screen (PlayerView lives in media3-ui)
    implementation(libs.androidx.media3.exoplayer)
    // HLS lives in its own artifact. Without it ExoPlayer throws
    // ClassNotFoundException: HlsMediaSource$Factory at setMediaItem time
    // (measured on the A52, crash buffer 15:14:32).
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Material Components
    implementation(libs.androidx.material)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
