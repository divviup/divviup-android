plugins {
    id("com.android.library")
}

android {
    namespace = "org.divviup.commontest"
    compileSdk = 37

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_9
        targetCompatibility = JavaVersion.VERSION_1_9
    }
}

dependencies {
    implementation("com.squareup.okhttp3:mockwebserver3:5.5.0")
    implementation("com.squareup.okhttp3:mockwebserver3-junit4:5.5.0")

    constraints {
        // Avoid https://github.com/advisories/GHSA-j288-q9x7-2f5v
        api("org.apache.commons:commons-lang3:3.20.0")
        // Avoid https://github.com/advisories/GHSA-wg6q-6289-32hp
        api("org.bouncycastle:bcpkix-jdk18on:1.84")
        api("org.bouncycastle:bcprov-jdk18on:1.84")
        api("org.bouncycastle:bcutil-jdk18on:1.84")
        androidLintTool("org.bouncycastle:bcpkix-jdk18on:1.84")
        androidLintTool("org.bouncycastle:bcprov-jdk18on:1.84")
        androidLintTool("org.bouncycastle:bcutil-jdk18on:1.84")
    }
}
