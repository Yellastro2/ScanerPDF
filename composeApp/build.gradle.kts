import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

fun configuredValue(
    localKey: String,
    gradleKey: String,
    defaultValue: String = "",
): String =
    localProperties.getProperty(localKey)
        ?: (project.findProperty(gradleKey) as String?)
        ?: System.getenv(localKey)
        ?: defaultValue

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val debugBackendUrl = configuredValue("DEBUG_BACKEND", "debugBackend")
val releaseBackendUrl = configuredValue("RELEASE_BACKEND", "releaseBackend")
val rustoreConsoleAppId = configuredValue("RUSTORE_CONSOLE_APP_ID", "rustoreConsoleAppId")
val rustoreMonthlyId = configuredValue(
    "RUSTORE_MONTHLY_ID",
    "rustoreMonthlyId",
    "premium_monthly",
)
val rustoreYearlyId = configuredValue(
    "RUSTORE_YEARLY_ID",
    "rustoreYearlyId",
    "premium_yearly",
)

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

android {
    namespace = "com.nla.AIscanerPDF"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nla.AIscanerPDF"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // RuStore Pay: ID приложения в консоли RuStore и ID продуктов (см. README)
        buildConfigField(
            "String",
            "RUSTORE_CONSOLE_APP_ID",
            rustoreConsoleAppId.asBuildConfigString(),
        )
        resValue("string", "rustore_console_application_id", rustoreConsoleAppId)
        buildConfigField(
            "String",
            "RUSTORE_MONTHLY_ID",
            rustoreMonthlyId.asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "RUSTORE_YEARLY_ID",
            rustoreYearlyId.asBuildConfigString(),
        )
    }

    signingConfigs {
        // Keystore и пароли поступают ТОЛЬКО из окружения (GitHub Secrets);
        // в репозитории секретов нет. Без KEYSTORE_FILE собирается unsigned release.
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_FILE")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "AI_BASE_URL", debugBackendUrl.asBuildConfigString())
            val useMockRuStore = configuredValue(
                localKey = "USE_MOCK_RUSTORE",
                gradleKey = "useMockRuStore",
                defaultValue = rustoreConsoleAppId.isBlank().toString(),
            )
            buildConfigField("boolean", "USE_MOCK_RUSTORE", useMockRuStore)
            // Без RUSTORE_CONSOLE_APP_ID debug остаётся на mock RuStore.
            // Локальный MockAiDocumentService можно включить вручную.
            val useMockAi = (project.findProperty("useMockAi") as String?) ?: "false"
            buildConfigField("boolean", "USE_MOCK_AI", useMockAi)
            if (!System.getenv("KEYSTORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            buildConfigField("String", "AI_BASE_URL", releaseBackendUrl.asBuildConfigString())
            buildConfigField("boolean", "USE_MOCK_AI", "false")
            buildConfigField("boolean", "USE_MOCK_RUSTORE", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (System.getenv("KEYSTORE_FILE").isNullOrBlank()) {
                null
            } else {
                signingConfigs.getByName("release")
            }
            // Оптимизация размера: в release только реальные ABI устройств RuStore.
            // AAB дополнительно раздаёт per-device (abi/density/language splits).
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.opencv)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.splashscreen)
    implementation(libs.tesseract4android)
    implementation(platform(libs.rustore.bom))
    implementation(libs.rustore.pay)

    implementation(libs.coil.compose)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
}
