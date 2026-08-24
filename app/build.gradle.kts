import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// API key for the Claude API is read from local.properties (never committed, see .gitignore)
// and injected into BuildConfig at compile time. This is acceptable ONLY for this local
// prototype phase — see README.md "Säkerhet" section. In production the app must call a
// backend proxy instead of holding the key on-device.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { load(it) }
    }
}
val claudeApiKey: String = (localProperties.getProperty("CLAUDE_API_KEY") ?: "").also {
    if (it.isBlank()) {
        logger.warn(
            "CLAUDE_API_KEY saknas i local.properties. AI-sammanfattning och tolkning " +
                "av otydliga kommandon kommer inte att fungera förrän en nyckel läggs till."
        )
    }
}
val claudeModel: String = localProperties.getProperty("CLAUDE_MODEL") ?: "claude-opus-5"

android {
    namespace = "com.klarl.accessibility"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.klarl.accessibility"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-prototype"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CLAUDE_API_KEY", "\"$claudeApiKey\"")
        buildConfigField("String", "CLAUDE_MODEL", "\"$claudeModel\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Raw HTTPS client for the Claude API. See README.md "AI-backend" for why this project
    // uses OkHttp + org.json instead of the server-oriented anthropic-java SDK on-device.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
