plugins {
    id("com.android.application")
}

android {
    namespace = "org.dusk"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.dusk"
        minSdk = 26
        targetSdk = 37
        // CI passes -PversionCode / -PversionName; local builds use 1 / "1.0".
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0"
    }

    // CI points DUSK_KEYSTORE at the fixed release key (from the DUSK_KEYSTORE_BASE64 secret)
    // so every release installs over the last. Local builds fall back to the machine's debug key.
    val duskKeystore = System.getenv("DUSK_KEYSTORE")?.let { file(it) }
    signingConfigs {
        if (duskKeystore != null) {
            create("dusk") {
                storeFile = duskKeystore
                storeType = "pkcs12"
                storePassword = "android"
                keyAlias = "dusk"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("dusk") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
