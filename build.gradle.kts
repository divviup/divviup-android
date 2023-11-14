// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.1.3" apply false
    id("com.android.library") version "8.1.2" apply false
    id("org.mozilla.rust-android-gradle.rust-android") version "0.9.3" apply false
}

val osName: String? = System.getProperty("os.name")
val osArch: String? = System.getProperty("os.arch")
when {
    osName == "Linux" && osArch == "amd64" -> extra["hostRustTarget"] = "linux-x86-64"
    // see https://github.com/openjdk/jdk/blob/master/src/java.base/macosx/native/libjava/java_props_macosx.c
    osName == "Mac OS X" && osArch == "amd64" -> extra["hostRustTarget"] = "darwin-x86-64"
    osName == "Mac OS X" && osArch == "aarch64" -> extra["hostRustTarget"] = "darwin-aarch64"
    // see https://github.com/openjdk/jdk/blob/master/src/java.base/windows/native/libjava/java_props_md.c
    osName != null && osName.startsWith("Windows") && osArch == "amd64" -> extra["hostRustTarget"] =
        "win32-x86-64-msvc"

    else -> throw GradleException("Unsupported host operating system and architecture")
}
extra["rustTargets"] = listOf(
    "arm",
    "arm64",
    "x86",
    "x86_64",
    extra["hostRustTarget"]
)
