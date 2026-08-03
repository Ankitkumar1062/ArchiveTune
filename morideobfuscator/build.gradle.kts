plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

// Stub module providing compile-time stubs for the proprietary morideobfuscator.
// All runtime calls return failure / no-op so the app falls back to open-source paths.
dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
