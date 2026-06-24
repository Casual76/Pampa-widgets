import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.hilt)
}

val releaseSigningProperties = Properties().apply {
  listOf(
    rootProject.file("keystore.properties"),
    rootProject.file("local.properties"),
    file("C:/VibeCoded Projects/Pampa-store-src/local.properties"),
  ).filter { it.isFile }
    .forEach { propertiesFile ->
      propertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningProperty(vararg keys: String): String? {
  return keys.firstNotNullOfOrNull { key ->
    providers.gradleProperty(key).orNull
      ?: releaseSigningProperties.getProperty(key)
      ?: providers.environmentVariable(key).orNull
  }?.takeIf { it.isNotBlank() }
}

android {
  namespace = "com.pampa.widgets"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.pampa.widgets"
    minSdk = 31
    targetSdk = 36
    versionCode = 7
    versionName = "0.2.5"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      storeFile = releaseSigningProperty(
        "pampa.release.storeFile",
        "PAMPA_RELEASE_STORE_FILE",
        "KEYSTORE_PATH",
      )?.let { file(it) }
      storePassword = releaseSigningProperty(
        "pampa.release.storePassword",
        "PAMPA_RELEASE_STORE_PASSWORD",
        "KEYSTORE_PASSWORD",
      )
      keyAlias = releaseSigningProperty(
        "pampa.release.keyAlias",
        "PAMPA_RELEASE_KEY_ALIAS",
        "KEY_ALIAS",
      )
      keyPassword = releaseSigningProperty(
        "pampa.release.keyPassword",
        "PAMPA_RELEASE_KEY_PASSWORD",
        "KEY_PASSWORD",
      )
      enableV1Signing = true
      enableV2Signing = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
      freeCompilerArgs.add(
        "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
      )
    }
  }

  packaging {
    resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
  }
}

kapt {
  correctErrorTypes = true
}

dependencies {
  implementation(platform(libs.compose.bom))
  androidTestImplementation(platform(libs.compose.bom))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.hilt.navigation.compose)

  implementation(libs.compose.ui)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.material3.window)
  implementation(libs.compose.material.icons.extended)
  debugImplementation(libs.compose.ui.tooling)
  implementation(libs.compose.ui.tooling.preview)

  implementation(libs.glance)
  implementation(libs.glance.appwidget)
  implementation(libs.glance.material3)

  implementation(libs.hilt.android)
  kapt(libs.hilt.compiler)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.espresso.core)
  androidTestImplementation(libs.compose.ui.test.junit4)
  debugImplementation(libs.compose.ui.test.manifest)
}
