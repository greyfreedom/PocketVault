import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.protobuf)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseSigningConfig = keystorePropertiesFile.isFile
if (hasReleaseSigningConfig) {
    FileInputStream(keystorePropertiesFile).use(keystoreProperties::load)
}

android {
    namespace = "com.turisla.hellopocket"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.turisla.hellopocket"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "2.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("hellopocket") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        debug {
            // 调试包与 Google Play 正式包共存，并明确区分非官方签名构建。
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("hellopocket")
            }

            ndk {
                // 生成完整的调试符号表
                debugSymbolLevel = "FULL"
            }
        }
    }

    flavorDimensions += "market"

    productFlavors {
        create("googlePlay") {
            dimension = "market"
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

java {
    // Gradle 由 Android Studio 的 JDK 运行，源码编译固定使用稳定的 JDK 17 工具链。
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.biometric.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.koin.android.core)
    implementation(libs.androidx.koin.android.compat)
    implementation(libs.androidx.koin.android.compose)
    implementation(libs.androidx.koin.android.composeNavigation)
    // serializable
    implementation(libs.jetbrains.kotlinx.serialization.json)
    implementation(libs.tink.android)

    // Protobuf
    implementation(libs.protobuf.kotlin.lite)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // TOTP
    implementation(libs.zxing.android.embedded)

    // one time password
    implementation(libs.kotlin.onetimepassword)

    // accompanist
    implementation(libs.accompanist.permissions)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach {
            it.builtins {
                create("java") {
                    option("lite")
                }
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}
