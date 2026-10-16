plugins {
    //trick: for the same plugin versions in all sub-modules
    // ⚠️ AGP 7.4.2 内置 dexer(R8 4.0.52)读 compileSdk 34+ 的 android.jar 会直接中止打包;
    // 且 compose 1.7.6 AAR 要求 compileSdk ≥ 34 → 7.4.2 根本打不出包,必须 8.1+(8.5.2 适配 Gradle 8.14)
    id("com.android.application").version("8.5.2").apply(false)
    id("com.android.library").version("8.5.2").apply(false)
    kotlin("android").version("2.1.21").apply(false)
    kotlin("multiplatform").version("2.1.21").apply(false)
    id("com.google.devtools.ksp").version("2.1.21-2.0.1").apply(false)
    id("org.jetbrains.compose").version("1.7.3").apply(false)
    kotlin("plugin.compose").version("2.1.21").apply(false)
}

buildscript {
    dependencies {
        classpath(BuildPlugin.kuikly)
    }
}