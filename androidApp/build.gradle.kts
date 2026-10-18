import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

// 正式签名:密钥与密码不进 git(.gitignore 的 keystore.properties / *.jks)
// 密钥生成命令见 CLAUDE.md;丢失 jks 无法再以同一身份更新应用,务必备份
val keystoreProps = Properties().apply {
    val f = rootProject.file("androidApp/keystore.properties")
    if (f.exists()) load(FileInputStream(f))
}

android {
    namespace = "com.zyc.love_baby"
    // ⚠️ 必须 34+:compose 1.7.6 AAR 要求 compileSdk ≥ 34;AGP 8.5.2 的 dexer 才读得动 android-34(7.4.2 的读不动)
    compileSdk = 34
    defaultConfig {
        applicationId = "com.zyc.love_baby"
        minSdk = 23
        targetSdk = 30
        versionCode = 1
        // 与 MinePage.kt 的 APP_VERSION_NAME 手工双源同步(关于我们卡展示;ohosApp app.json5 同 1.0.0)
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("androidApp/${keystoreProps.getProperty("storeFile")}")
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    // AGP 8 默认关闭 BuildConfig;KRUncaughtExceptionHandlerAdapter 引用了 BuildConfig.DEBUG
    buildFeatures {
        buildConfig = true
    }
    lint {
        // 个人应用不上架 Google Play,targetSdk 30 为模板默认值;lintVital 会因 ExpiredTargetSdkVersion 拦截 release 构建
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":shared"))

    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.appcompat:appcompat:1.3.1")
    // WS 触发器(D-16):KRWebSocketModule 用 okhttp WebSocket(core-render-android 的 NetworkModule 是
    // HttpURLConnection 实现,不带 okhttp 传递依赖,须显式引)
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    implementation("com.squareup.picasso:picasso:2.71828")

    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")
}