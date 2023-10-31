plugins {
    id("com.android.library")
    id("org.mozilla.rust-android-gradle.rust-android")
}

android {
    namespace = "org.divviup.android"
    compileSdk = 33

    ndkVersion = "25.1.8937393"

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

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
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
