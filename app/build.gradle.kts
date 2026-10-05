import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.mayraai.kzyq"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    val localDebugKeystore = file("${rootDir}/debug.keystore")
    if (localDebugKeystore.exists()) {
      getByName("debug") {
        storeFile = localDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debug") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// Resolves GEMINI_API_KEY from environment variables (GitHub Actions secrets / CI),
// Gradle properties, .env files, or local.properties before the plugin generates BuildConfig.
val envFile = rootProject.file(".env")
val appEnvFile = project.file(".env")
val localPropsFile = rootProject.file("local.properties")

val envApiKey = System.getenv("GEMINI_API_KEY")
  ?: (project.findProperty("GEMINI_API_KEY") as? String)
  ?: System.getenv("GOOGLE_API_KEY")
  ?: (project.findProperty("GOOGLE_API_KEY") as? String)

val resolvedApiKey = envApiKey?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }
  ?: run {
    if (envFile.exists()) {
      envFile.readLines()
        .firstOrNull { it.trim().startsWith("GEMINI_API_KEY=") }
        ?.substringAfter("GEMINI_API_KEY=")
        ?.trim()
        ?.removeSurrounding("\"")
    } else null
  }?.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
  ?: run {
    if (localPropsFile.exists()) {
      localPropsFile.readLines()
        .firstOrNull { it.trim().startsWith("GEMINI_API_KEY=") }
        ?.substringAfter("GEMINI_API_KEY=")
        ?.trim()
        ?.removeSurrounding("\"")
    } else null
  }?.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
  ?: run {
    if (appEnvFile.exists()) {
      appEnvFile.readLines()
        .firstOrNull { it.trim().startsWith("GEMINI_API_KEY=") }
        ?.substringAfter("GEMINI_API_KEY=")
        ?.trim()
        ?.removeSurrounding("\"")
    } else null
  }?.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }

if (!resolvedApiKey.isNullOrBlank() && resolvedApiKey != "MY_GEMINI_API_KEY") {
  val existingLines = if (envFile.exists()) envFile.readLines().filter { !it.trim().startsWith("GEMINI_API_KEY=") } else emptyList()
  envFile.writeText((existingLines + "GEMINI_API_KEY=$resolvedApiKey").joinToString("\n") + "\n")

  val appLines = if (appEnvFile.exists()) appEnvFile.readLines().filter { !it.trim().startsWith("GEMINI_API_KEY=") } else emptyList()
  appEnvFile.writeText((appLines + "GEMINI_API_KEY=$resolvedApiKey").joinToString("\n") + "\n")

  val localLines = if (localPropsFile.exists()) localPropsFile.readLines().filter { !it.trim().startsWith("GEMINI_API_KEY=") } else emptyList()
  localPropsFile.writeText((localLines + "GEMINI_API_KEY=$resolvedApiKey").joinToString("\n") + "\n")
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.commonmark)
  implementation(libs.commonmark.ext.gfm.tables)
  implementation(libs.commonmark.ext.gfm.strikethrough)
  implementation(libs.commonmark.ext.autolink)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
