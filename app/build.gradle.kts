plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
    id("com.google.devtools.ksp")
}

android {
    namespace = "fan.superai"
    compileSdk = 34

    defaultConfig {
        applicationId = "fan.superai"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.4"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Code on the Go keeps its host-Python installation inside the IDE sandbox.
// Chaquopy normally searches PATH, but CoGo's embedded Python is not always on
// Gradle's PATH. Resolve it explicitly when available, while still allowing
// normal desktop/CI builds to use the standard Python commands.
fun resolveChaquopyBuildPython(): String? {
    val explicit = providers.gradleProperty("chaquopyBuildPython").orNull
        ?: System.getenv("CHAQUOPY_BUILD_PYTHON")
    if (!explicit.isNullOrBlank()) return explicit

    val termuxPrefix = System.getenv("TERMUX_PREFIX")
    val candidates = buildList {
        if (!termuxPrefix.isNullOrBlank()) {
            add("$termuxPrefix/bin/python3.11")
            add("$termuxPrefix/bin/python3")
        }
        add("/data/data/com.itsaky.androidide/files/usr/bin/python3.11")
        add("/data/data/com.itsaky.androidide/files/usr/bin/python3")
        add("/data/data/com.appdevforall.codeonthego/files/usr/bin/python3.11")
        add("/data/data/com.appdevforall.codeonthego/files/usr/bin/python3")
    }
    candidates.firstOrNull { File(it).canExecute() }?.let { return it }

    // Desktop/CI fallback: only select a command which is actually executable.
    listOf("python3.11", "python3", "python").firstOrNull { command ->
        try {
            ProcessBuilder(command, "--version")
                .redirectErrorStream(true)
                .start()
                .waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }?.let { return it }

    return null
}

chaquopy {
    defaultConfig {
        version = "3.11"
        resolveChaquopyBuildPython()?.let { buildPython(it) }
        pip {
            install("numpy")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
}
