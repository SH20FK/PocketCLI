plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pocketcli"
    compileSdk = 35

    val vCode = (project.findProperty("versionCode") as? String)?.toIntOrNull()
        ?: System.getenv("VERSION_CODE")?.toIntOrNull()
        ?: 1000011
    val vName = (project.findProperty("versionName") as? String)
        ?: System.getenv("VERSION_NAME")
        ?: "1.0.9-beta.4"

    defaultConfig {
        applicationId = "com.pocketcli"
        minSdk = 28
        targetSdk = 35
        versionCode = vCode
        versionName = vName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("pocketcli-release.jks")
            storePassword = project.findProperty("POCKETCLI_KEYSTORE_PASSWORD") as? String
                ?: System.getenv("POCKETCLI_KEYSTORE_PASSWORD")
                ?: "pocketcli_release_2026"
            keyAlias = project.findProperty("POCKETCLI_KEY_ALIAS") as? String
                ?: System.getenv("POCKETCLI_KEY_ALIAS")
                ?: "pocketcli"
            keyPassword = project.findProperty("POCKETCLI_KEY_PASSWORD") as? String
                ?: System.getenv("POCKETCLI_KEY_PASSWORD")
                ?: "pocketcli_release_2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("release")
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:security"))
    implementation(project(":core:update"))
    implementation(project(":data:local"))
    implementation(project(":data:opencode"))
    implementation(project(":runtime:remote"))
    implementation(project(":runtime:local"))
    implementation(project(":feature:sessions"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:projects"))

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
