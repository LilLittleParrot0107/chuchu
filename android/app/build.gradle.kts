plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-kapt")
}

android {
    namespace = "com.jossephus.chuchu"
    compileSdk {
        version = release(36)
    }

    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }

    defaultConfig {
        applicationId = "com.jossephus.chuchu"
        minSdk = 24
        targetSdk = 36

        // kohi versioning: keep in step with the delivered APK name
        // (kohi-v<major>.<minor>.<patch>.apk). CI truyền VERSION_* qua input
        // của build-debug-apk.yml; default dưới đây chỉ cho build tay, phải
        // được nâng cùng phiên bản phát hành (23/8: từng quên -> versionCode
        // tụt về 1.23.13 và máy từ chối cài vì downgrade).
        val major = (System.getenv("VERSION_MAJOR")?.toIntOrNull() ?: 1)
        val minor = (System.getenv("VERSION_MINOR")?.toIntOrNull() ?: 50)
        val patch = (System.getenv("VERSION_PATCH")?.toIntOrNull() ?: 0)
        // versionCode must never go backwards: Android refuses to install a
        // lower code over an existing build.
        val releaseBase = major * 10_000 + minor * 100 + patch
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: (releaseBase * 1_000)
        versionName = System.getenv("VERSION_NAME") ?: "$major.$minor.$patch"

        System.getenv("ANDROID_ABI_FILTERS")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { filters ->
                ndk {
                    abiFilters += filters
                }
            }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("key.jks")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("KEY_ALIAS")
            val keyPasswordEnv = System.getenv("KEY_PASSWORD")

            if (keystorePassword != null && keyAliasEnv != null && keyPasswordEnv != null && keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            } else {
                // Personal-fork fallback: no release secrets on CI, so sign
                // release builds with the committed fixed debug keystore —
                // same signature as the debug builds, so either installs
                // over the other.
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            // Fixed debug keystore committed to the repo: CI runners are
            // ephemeral and would otherwise mint a fresh auto-generated
            // debug key per run, making every sideloaded build a
            // signature-mismatch reinstall. Standard Android debug
            // credentials — this key signs nothing distributable.
            signingConfig = signingConfigs.create("fixedDebug") {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    testImplementation(libs.junit)
    // org.json cua Android chi la stub trong unit test (moi ham nem
    // "not mocked"). Them ban that de test parser hang doi chay duoc tren JVM.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
