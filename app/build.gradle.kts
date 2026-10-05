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

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.aistudio.mayraai.kzyq"

        minSdk = 24
        targetSdk = 36

        // Mayra AI v1.0.1
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath =
                System.getenv("KEYSTORE_PATH")
                    ?: "${rootDir}/my-upload-key.jks"

            storeFile = file(keystorePath)
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("KEY_PASSWORD")
        }

        val localDebugKeystore =
            file("${rootDir}/debug.keystore")

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

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("release")
        }

        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

/*
 * Gemini API key resolution
 *
 * Priority:
 * 1. Environment variable
 * 2. Gradle property
 * 3. .env
 * 4. local.properties
 *
 * The API key is NEVER hardcoded in the source code.
 */

val envFile = rootProject.file(".env")
val appEnvFile = project.file(".env")
val localPropsFile = rootProject.file("local.properties")

val envApiKey =
    System.getenv("GEMINI_API_KEY")
        ?: (project.findProperty("GEMINI_API_KEY") as? String)
        ?: System.getenv("GOOGLE_API_KEY")
        ?: (project.findProperty("GOOGLE_API_KEY") as? String)

val resolvedApiKey =
    envApiKey
        ?.trim()
        ?.removeSurrounding("\"")
        ?.takeIf { it.isNotBlank() }
        ?: run {
            if (envFile.exists()) {
                envFile.readLines()
                    .firstOrNull {
                        it.trim().startsWith("GEMINI_API_KEY=")
                    }
                    ?.substringAfter("GEMINI_API_KEY=")
                    ?.trim()
                    ?.removeSurrounding("\"")
            } else {
                null
            }
        }
        ?.takeIf {
            it.isNotBlank() &&
                it != "MY_GEMINI_API_KEY"
        }
        ?: run {
            if (localPropsFile.exists()) {
                localPropsFile.readLines()
                    .firstOrNull {
                        it.trim().startsWith("GEMINI_API_KEY=")
                    }
                    ?.substringAfter("GEMINI_API_KEY=")
                    ?.trim()
                    ?.removeSurrounding("\"")
            } else {
                null
            }
        }
        ?.takeIf {
            it.isNotBlank() &&
                it != "MY_GEMINI_API_KEY"
        }
        ?: run {
            if (appEnvFile.exists()) {
                appEnvFile.readLines()
                    .firstOrNull {
                        it.trim().startsWith("GEMINI_API_KEY=")
                    }
                    ?.substringAfter("GEMINI_API_KEY=")
                    ?.trim()
                    ?.removeSurrounding("\"")
            } else {
                null
            }
        }
        ?.takeIf {
            it.isNotBlank() &&
                it != "MY_GEMINI_API_KEY"
        }

if (
    !resolvedApiKey.isNullOrBlank() &&
    resolvedApiKey != "MY_GEMINI_API_KEY"
) {
    val existingLines =
        if (envFile.exists()) {
            envFile.readLines()
                .filter {
                    !it.trim()
                        .startsWith("GEMINI_API_KEY=")
                }
        } else {
            emptyList()
        }

    envFile.writeText(
        (
            existingLines +
                "GEMINI_API_KEY=$resolvedApiKey"
            ).joinToString("\n") + "\n"
    )

    val appLines =
        if (appEnvFile.exists()) {
            appEnvFile.readLines()
                .filter {
                    !it.trim()
                        .startsWith("GEMINI_API_KEY=")
                }
        } else {
            emptyList()
        }

    appEnvFile.writeText(
        (
            appLines +
                "GEMINI_API_KEY=$resolvedApiKey"
            ).joinToString("\n") + "\n"
    )

    val localLines =
        if (localPropsFile.exists()) {
            localPropsFile.readLines()
                .filter {
                    !it.trim()
                        .startsWith("GEMINI_API_KEY=")
                }
        } else {
            emptyList()
        }

    localPropsFile.writeText(
        (
            localLines +
                "GEMINI_API_KEY=$resolvedApiKey"
            ).joinToString("\n") + "\n"
    )
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"

    ignoreList.add(
        "FIREBASE_APPCHECK_DEBUG_TOKEN"
    )
}

googleServices {
    missingGoogleServicesStrategy =
        MissingGoogleServicesStrategy.WARN
}

dependencies {

    implementation(
        platform(libs.androidx.compose.bom)
    )

    implementation(
        platform(libs.firebase.bom)
    )

    implementation(
        libs.androidx.activity.compose
    )

    implementation(
        libs.androidx.compose.material.icons.core
    )

    implementation(
        libs.androidx.compose.material.icons.extended
    )

    implementation(
        libs.androidx.compose.material3
    )

    implementation(
        libs.androidx.compose.ui
    )

    implementation(
        libs.androidx.compose.ui.graphics
    )

    implementation(
        libs.androidx.compose.ui.tooling.preview
    )

    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.androidx.lifecycle.runtime.compose
    )

    implementation(
        libs.androidx.lifecycle.runtime.ktx
    )

    implementation(
        libs.androidx.lifecycle.viewmodel.compose
    )

    implementation(
        libs.androidx.room.ktx
    )

    implementation(
        libs.androidx.room.runtime
    )

    implementation(
        libs.commonmark
    )

    implementation(
        libs.commonmark.ext.gfm.tables
    )

    implementation(
        libs.commonmark.ext.gfm.strikethrough
    )

    implementation(
        libs.commonmark.ext.autolink
    )

    implementation(
        libs.coil.compose
    )

    implementation(
        libs.converter.moshi
    )

    implementation(
        libs.firebase.ai
    )

    implementation(
        libs.firebase.appcheck.recaptcha
    )

    implementation(
        libs.firebase.appcheck.debug
    )

    implementation(
        libs.kotlinx.coroutines.android
    )

    implementation(
        libs.kotlinx.coroutines.core
    )

    implementation(
        libs.logging.interceptor
    )

    implementation(
        libs.moshi.kotlin
    )

    implementation(
        libs.okhttp
    )

    implementation(
        libs.retrofit
    )

    testImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    testImplementation(
        libs.androidx.core
    )

    testImplementation(
        libs.androidx.junit
    )

    testImplementation(
        libs.junit
    )

    testImplementation(
        libs.kotlinx.coroutines.test
    )

    testImplementation(
        libs.robolectric
    )

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    androidTestImplementation(
        libs.androidx.runner
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    "ksp"(
        libs.androidx.room.compiler
    )

    "ksp"(
        libs.moshi.kotlin.codegen
    )
}
