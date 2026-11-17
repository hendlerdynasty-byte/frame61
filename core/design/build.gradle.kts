plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

android {
    namespace = "de.hendler.frame61.design"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.kotlin.get()
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    // Kotlin 2.x: resolves multi-module virtual classes in tv-foundation kotlin_module
    implementation(libs.jetbrains.kotlinx.metadata.jvm)
    // Compiler-classpath: needed for virtual package resolution
    implementation(libs.kotlin.reflect)
    // Central PosterCard owns image loading and the clip stack
    implementation(libs.coil.compose)
    // Skin files are JSON — one parser, no second token path
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}

// Canonical skin files live in :app; the test needs them without copying them.
val canonicalSkinsDir = rootProject.layout.projectDirectory
    .dir("app/src/main/assets/skins")

tasks.withType<Test>().configureEach {
    systemProperty("frame61.skinsDir", canonicalSkinsDir.asFile.absolutePath)
}
