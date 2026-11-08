plugins {
    id("com.android.application")
}

import java.util.Properties

// 签名材料不进仓库:口令与 keystore 路径放在仓库根目录的 signing/keystore.properties(已 gitignore)。
// 没有这个文件时(比如别人 clone 下来)release 包不签名,assembleRelease 仍能跑,用来验证能编过。
val signingPropertiesFile = rootProject.file("signing/keystore.properties")
val signingProperties = Properties()
if (signingPropertiesFile.isFile) {
    signingPropertiesFile.inputStream().use { signingProperties.load(it) }
}
val hasSigningMaterial = signingProperties.getProperty("storeFile") != null

android {
    namespace = "com.ptfast.scanqr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ptfast.scanqr"
        minSdk = 23
        targetSdk = 33
        versionCode = 2
        versionName = "1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            if (hasSigningMaterial) {
                // storeFile 相对 signing/keystore.properties 所在目录解析
                val keystorePath = signingProperties.getProperty("storeFile")
                storeFile = File(signingPropertiesFile.parentFile, keystorePath)
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
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
            if (hasSigningMaterial) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/**", "**/*.proto", "**/*.txt", "**/*.md")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
