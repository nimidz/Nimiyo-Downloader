import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProps = Properties().apply {
    listOf(
        rootProject.file("../android/local.properties"),
        rootProject.file("local.properties"),
    ).forEach { f ->
        if (f.exists()) f.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.nimiyo.nativeapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nimiyo.dl"
        minSdk = 24
        targetSdk = 34
        versionCode = 12
        versionName = "3.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val arm64Only = project.findProperty("arm64Only")?.toString()?.toBoolean() == true
    splits {
        abi {
            isEnable = true
            reset()
            if (arm64Only) {
                include("arm64-v8a")
                isUniversalApk = false
            } else {
                include("arm64-v8a", "armeabi-v7a", "x86_64")
                isUniversalApk = false
            }
        }
    }

    signingConfigs {
        create("release") {
            val sf = signingProps.getProperty("signing.storeFile")
            if (!sf.isNullOrBlank()) {
                val resolved = listOf(
                    file(sf),
                    rootProject.file(sf),
                    rootProject.file("../android/app/$sf"),
                    rootProject.file("../android/$sf"),
                ).firstOrNull { it.exists() }
                if (resolved != null) {
                    storeFile = resolved
                    storePassword = signingProps.getProperty("signing.storePassword")
                    keyAlias = signingProps.getProperty("signing.keyAlias")
                    keyPassword = signingProps.getProperty("signing.keyPassword")
                }
            }
        }
    }

    buildTypes {
        debug {
            val relSigning = signingConfigs.findByName("release")
            if (relSigning?.storeFile?.exists() == true) {
                signingConfig = relSigning
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val relSigning = signingConfigs.findByName("release")
            signingConfig = if (relSigning?.storeFile?.exists() == true) {
                relSigning
            } else {
                signingConfigs.getByName("debug")
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("io.coil-kt:coil-compose:2.6.0")

    val youtubedlAndroidVersion = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroidVersion")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroidVersion")
    implementation("io.github.junkfood02.youtubedl-android:aria2c:$youtubedlAndroidVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}

