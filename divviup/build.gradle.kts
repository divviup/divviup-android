plugins {
    id("com.android.library")
    id("org.mozilla.rust-android-gradle.rust-android")
}

android {
    namespace = "org.divviup.android"
    compileSdk = 34

    ndkVersion = "26.1.10909125"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 21

        version = "0.1.0-SNAPSHOT"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "VERSION", "\"" + version.toString() + "\"")
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
    testImplementation(project(":divviup:commontest"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    testImplementation("ch.qos.logback:logback-core:1.4.11")
    testImplementation("ch.qos.logback:logback-classic:1.4.11")
    androidTestImplementation(project(":divviup:commontest"))
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

val rustTargets: List<String> by rootProject.extra
val hostRustTarget: String by rootProject.extra

cargo {
    module = "./rust"
    libname = "divviup_android"
    targets = rustTargets
    profile = "release"
    pythonCommand = "python3"
}

tasks.matching { it.name.matches(Regex("merge.*JniLibFolders")) }.configureEach {
    inputs.dir(File(buildDir, "rustJniLibs/android"))
    dependsOn(tasks.named("cargoBuild"))
}

tasks.withType<Test>().matching { it.name.matches(Regex("test.*UnitTest"))}.configureEach {
    systemProperty("java.library.path", "${buildDir}/rustJniLibs/desktop/${hostRustTarget}")
    val capitalizedHostRustTarget = hostRustTarget.replaceFirstChar { it.uppercase() }
    dependsOn(tasks.named("cargoBuild${capitalizedHostRustTarget}"))
}

afterEvaluate {
    android.libraryVariants.forEach { variant ->
        val capitalizedVariantName = variant.name.replaceFirstChar { it.uppercase() }
        tasks.register<Javadoc>("generate${capitalizedVariantName}Javadoc") {
            source = android.sourceSets["main"].java.getSourceFiles()
            classpath += files(android.bootClasspath)
            classpath += files(variant.compileConfiguration)
        }
    }
}
