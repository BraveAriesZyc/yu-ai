package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.Module

/**
 * Android 平台扩展模块(actual):Android 壳已注册同名原生模块(KRWebSocketModule/KRFileModule/
 * KRNotificationModule,KuiklyRenderActivity.registerExternalModule),core 侧注册后 getModule
 * 即可取用发调用。⚠ 自定义 Module 必进本表——漏配则 getModule 恒 null 全端死路(D-16 期 WS、
 * M21 期通知两度踩坑,编译全绿被 T? 返回值掩盖)
 */
internal actual fun platformExternalModules(): Map<String, Module> = mapOf(
    BridgeModule.MODULE_NAME to BridgeModule(),
    WebSocketModule.MODULE_NAME to WebSocketModule(),
    FileModule.MODULE_NAME to FileModule(),
    NotificationModule.MODULE_NAME to NotificationModule(),
    AudioModule.MODULE_NAME to AudioModule(), // M23 语音播放(跨气泡生命周期,原生单飞收口)
    BatteryModule.MODULE_NAME to BatteryModule(), // M26 情侣电量互看(原生 sticky broadcast 读值)
    LocationModule.MODULE_NAME to LocationModule(), // M26 位置共享/轨迹(原生单次定位+权限)
)
