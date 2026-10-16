package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.Module

/**
 * Android 平台扩展模块(actual):Android 壳已注册同名原生模块(KRWebSocketModule/KRFileModule,
 * KuiklyRenderActivity.registerExternalModule),core 侧注册后 getModule 即可取用发调用
 */
internal actual fun platformExternalModules(): Map<String, Module> = mapOf(
    BridgeModule.MODULE_NAME to BridgeModule(),
    WebSocketModule.MODULE_NAME to WebSocketModule(),
    FileModule.MODULE_NAME to FileModule(),
)
