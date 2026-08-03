plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

// Stub module — the proprietary morideobfuscator is not available on Mhsm.
// Provides MoriCipherRuntime with no-op implementations so callers fall back
// to the NewPipe / JavaScript-player path already present in core.
