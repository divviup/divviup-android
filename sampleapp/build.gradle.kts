plugins {
    id("com.android.application")
}

android {
    namespace = "org.divviup.sampleapp"
    compileSdk = 37

    ndkVersion = findProperty("ndkVersion") as String

    defaultConfig {
        applicationId = "org.divviup.sampleapp"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_9
        targetCompatibility = JavaVersion.VERSION_1_9
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":divviup"))

    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

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
