plugins {
    id("com.android.application")
}

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
            storeFile = file("signing/ptfastscan.jks")
            storePassword = "ptfastscan"
            keyAlias = "ptfastscan"
            keyPassword = "ptfastscan"
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
