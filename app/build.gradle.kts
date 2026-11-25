import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun calculateVersionCode(vName: String): Int {
    val parts = vName.replace(Regex("[^0-9.]"), "").split('.').filter { it.isNotEmpty() }
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return (major * 1000000) + (minor * 1000) + patch
}

val resolvedVersionName = providers.gradleProperty("appVersionName").orElse("1.0.0").get()
val resolvedVersionCode = providers.gradleProperty("appVersionCode").orNull
    ?.toIntOrNull()
    ?: calculateVersionCode(resolvedVersionName)

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "cz.helicode.hondaescanner"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "cz.helicode.hondaescanner"
        minSdk = 26
        targetSdk = 36
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = providers.environmentVariable("KEYSTORE_FILE").orNull
                ?: keystoreProperties.getProperty("storeFile")
            if (!storePath.isNullOrBlank()) {
                storeFile = rootProject.file(storePath)
            }
            storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
                ?: keystoreProperties.getProperty("storePassword")
            keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
                ?: keystoreProperties.getProperty("keyAlias")
            keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
                ?: keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt")
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
