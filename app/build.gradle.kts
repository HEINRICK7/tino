plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.tino.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tino.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.0-pilot.1"
        buildConfigField("String", "TINO_BUILD_CHANNEL", "\"pilot\"")
        buildConfigField("String", "TINO_BUILD_ID", "\"0.1.0-pilot.1\"")
        buildConfigField("String", "TINO_SYNC_BASE_URL", "\"\"")
        val backendBaseUrl = providers.gradleProperty("tinoApiBaseUrl").orNull
            ?: providers.gradleProperty("tinoBackendBaseUrl").orNull
            ?: "https://api.tino.otimizanegocio.com/"
        val oidcIssuer = providers.gradleProperty("tinoOidcIssuer").orNull
            ?: "https://auth.tino.otimizanegocio.com/realms/tino"
        val oidcClientId = providers.gradleProperty("tinoOidcClientId").orNull ?: "tino-android"
        val oidcRedirectUri = providers.gradleProperty("tinoOidcRedirectUri").orNull ?: "tino://oauth/callback"
        buildConfigField("String", "TINO_BACKEND_BASE_URL", "\"${backendBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "TINO_API_BASE_URL", "\"${backendBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "TINO_OIDC_ISSUER", "\"${oidcIssuer.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "TINO_OIDC_CLIENT_ID", "\"${oidcClientId.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "TINO_OIDC_REDIRECT_URI", "\"${oidcRedirectUri.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        manifestPlaceholders["appAuthRedirectScheme"] = "tino"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        resources.merges += "**/META-INF/INDEX.LIST"
        resources.merges += "**/META-INF/DEPENDENCIES"
    }
}

dependencies {
    implementation(project(":tino-agent-contracts"))
    implementation(project(":tino-fiscal-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.dagger.hilt.android)
    kapt(libs.dagger.hilt.compiler)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.gson)
    implementation(libs.androidx.appauth)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}
