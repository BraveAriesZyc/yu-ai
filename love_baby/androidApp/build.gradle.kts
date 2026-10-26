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
        // M22:targetSdk 33 起 POST_NOTIFICATIONS 为运行时权限,应用可主动弹授权框(打开时
        // 请求,KuiklyRenderActivity 主页 onCreate;30→31/32/33 全部门槛已审计合规,见提交说明)
        targetSdk = 33
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
        getByName("debug") {
            // app-api 基地址,经 KuiklyRenderActivity.createPageData 第四路注入 shared ApiConfig
            // (§7.5 pageData 覆盖通道);真机联调改此处,模拟器直跑走 shared 默认 10.0.2.2
//            buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.4:48080\"")
            buildConfigField("String", "API_BASE_URL", "\"http://172.16.31.44:48080\"")
//            buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.3:48080\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            // ⚠️ 占位域名,上线前替换为真实 HTTPS 地址(D-4 上线清单项)
            buildConfigField("String", "API_BASE_URL", "\"https://love.shattered-dream.top\"")
        }
    }
    // AGP 8 默认关闭 BuildConfig;KRUncaughtExceptionHandlerAdapter 引用了 BuildConfig.DEBUG
    buildFeatures {
        buildConfig = true
    }
    lint {
        // 个人应用不上架 Google Play,无 Play 政策的 targetSdk 时限约束;lintVital 关闭防
        // release 构建被非阻断 lint 拦截(M20 模板遗留开关,维持不变)
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

    // 位置页原生动态地图(M27):高德 3D 地图 SDK(Maven Central 最新稳定版);头像 Marker 经
    // BitmapDescriptorFactory.fromBitmap 渲染。Manifest 需配 Android 平台 key(与 Web 服务
    // key 两码事),未配 key 瓦片空白但不崩,其余功能不受影响
    implementation("com.amap.api:3dmap:10.0.600")
    // KRAMapView 生命周期桥:DefaultLifecycleObserver(2.4 起才在 lifecycle-common,
    // appcompat 1.3.1 传递的 2.3.1 没有它)
    implementation("androidx.lifecycle:lifecycle-common:2.6.2")

    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")
}
