plugins {
    id("com.android.library")
    id("org.mozilla.rust-android-gradle.rust-android")
}

android {
    namespace = "org.divviup.android"
    compileSdk = 33

    ndkVersion = "26.1.10909125"

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("commons-io:commons-io:2.15.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

cargo {
    module = "./rust"
    libname = "divviup_android"
    targets = listOf("arm", "arm64", "x86", "x86_64")

    profile = "release"

    pythonCommand = "python3"
}

tasks.matching { it.name.matches(Regex("merge.*JniLibFolders")) }.configureEach {
    this.inputs.dir(File(buildDir, "rustJniLibs/android"))
    this.dependsOn(tasks.named("cargoBuild"))
}
