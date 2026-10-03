import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "cn.jlu.schedule"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.jlu.schedule"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "2.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val localSigningFile = rootProject.file("local-signing.properties")
    val localSigningProperties = Properties()
    if (localSigningFile.isFile) {
        localSigningFile.inputStream().use(localSigningProperties::load)
    }
    val hasLocalSigning = localSigningProperties.getProperty("storePassword")?.isNotBlank() == true &&
        localSigningProperties.getProperty("keyPassword")?.isNotBlank() == true
    signingConfigs {
        if (hasLocalSigning) {
            create("official") {
                storeFile = rootProject.file("../apk-key/key.jks")
                storePassword = localSigningProperties.getProperty("storePassword")
                keyAlias = "JFyuhong"
                keyPassword = localSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasLocalSigning) signingConfig = signingConfigs.getByName("official")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.yalantis:ucrop:2.2.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
