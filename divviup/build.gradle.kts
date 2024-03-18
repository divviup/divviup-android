plugins {
    id("com.android.library")
    id("org.mozilla.rust-android-gradle.rust-android")
    id("maven-publish")
    signing
}

android {
    namespace = "org.divviup.android"
    compileSdk = 34

    ndkVersion = "26.2.11394342"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 21

        version = "0.2.0-SNAPSHOT"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "VERSION", "\"" + version.toString() + "\"")
    }

    testOptions {
        targetSdk = 23
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(project(":divviup:commontest"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    testImplementation("ch.qos.logback:logback-core:1.5.3")
    testImplementation("ch.qos.logback:logback-classic:1.5.3")
    testImplementation("commons-io:commons-io:2.15.1")
    testImplementation("org.mockito:mockito-core:5.11.0")
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

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "org.divviup.android"
            artifactId = "divviup-android"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("divviup-android")
                description.set("An Android client library for the Distributed Aggregation Protocol.")
                url.set("https://divviup.org/")
                licenses {
                    license {
                        name.set("Mozilla Public License 2.0")
                        url.set("https://www.mozilla.org/MPL/2.0/")
                    }
                }
                developers {
                    developer {
                        email.set("sre@divviup.org")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/divviup/divviup-android.git")
                    developerConnection.set("scm:git:https://github.com/divviup/divviup-android.git")
                    url.set("https://github.com/divviup/divviup-android/tree/main")
                }
            }
        }
    }

    repositories {
        maven {
            name = "OSSRH"

            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

            credentials {
                val ossrhUsername: String? by project
                val ossrhPassword: String? by project
                username = ossrhUsername
                password = ossrhPassword
            }
        }
    }
}

signing {
    sign(publishing.publications["release"])

    val signingKey: String? by project
    val signingPassword: String? by project
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    } else {
        useGpgCmd()
    }
}
