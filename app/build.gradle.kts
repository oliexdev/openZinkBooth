import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.photo.openzinkbooth"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.photo.openzinkbooth"
        minSdk = 31
        targetSdk = 36
        versionCode = 6
        versionName = "0.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("../openZinkBooth.keystore")
            val keystoreProperties = Properties()

            if (keystorePropertiesFile.exists()) {
                keystorePropertiesFile.inputStream().use { input ->
                    keystoreProperties.load(input)
                }
            }

            if (keystoreProperties.isNotEmpty()) {
                storeFile = file(rootDir.canonicalPath + "/" + keystoreProperties.getProperty("releaseKeyStore"))
                keyAlias = keystoreProperties.getProperty("releaseKeyAlias")
                keyPassword = keystoreProperties.getProperty("releaseKeyPassword")
                storePassword = keystoreProperties.getProperty("releaseStorePassword")
            }
        }

        create("oss") {
            val keystorePropertiesFile = rootProject.file("../openZinkBooth_oss.keystore")
            val keystoreProperties = Properties()

            if (keystorePropertiesFile.exists()) {
                keystorePropertiesFile.inputStream().use { input ->
                    keystoreProperties.load(input)
                }
            }

            if (keystoreProperties.isNotEmpty()) {
                storeFile = file(rootDir.canonicalPath + "/" + keystoreProperties.getProperty("releaseKeyStore"))
                keyAlias = keystoreProperties.getProperty("releaseKeyAlias")
                keyPassword = keystoreProperties.getProperty("releaseKeyPassword")
                storePassword = keystoreProperties.getProperty("releaseStorePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }

        create("oss") {
            applicationIdSuffix = ".oss"
            versionNameSuffix = "-oss"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("oss")
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
}

val appVersionName = android.defaultConfig.versionName

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName.set(
                    if (variant.name == "debug")
                        "openZinkBooth-debug.apk"
                    else
                        "openZinkBooth-$appVersionName-${variant.name}.apk"
                )
            }
        }
    }
}

listOf("release", "oss").forEach { variantName ->
    val bundleTask = "bundle${variantName.replaceFirstChar { it.uppercase() }}"
    tasks.matching { it.name == bundleTask }.configureEach {
        doLast {
            val bundleDir = layout.buildDirectory.dir("outputs/bundle/$variantName").get().asFile
            val target = File(bundleDir, "openZinkBooth-$appVersionName-$variantName.aab")
            bundleDir.listFiles { f -> f.extension == "aab" && f.name != target.name }
                ?.forEach { aab ->
                    aab.copyTo(target, overwrite = true)
                    aab.delete()
                }
        }
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
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.window.size.class1)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // BLE
    implementation(libs.blessed.android.coroutines)

    // Persistent settings
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}