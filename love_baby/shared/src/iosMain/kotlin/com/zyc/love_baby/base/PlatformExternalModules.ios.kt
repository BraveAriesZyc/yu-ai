package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.Module
import com.tencent.kuiklybase.websocket.WebSocketModule

/**
 * iOS 平台扩展模块(actual):注册 BridgeModule(宿主 KuiklyExpand/Modules/HRBridgeModule)+
 * WebSocketModule(M35:官方组件 KuiklyWebsocket vendored common 侧;iOS 原生件 KRWebSocketModule.h/.m
 *  已入宿主同目录并进 pbxproj,OpenKuiklyIOSRender 按 NSClassFromString 自动发现,无需手动注册)。
 * FileModule/通知/定位/地图等仍未实现 → getModule=null → 调用方判空降级(渐进降级设计)
 */
internal actual fun platformExternalModules(): Map<String, Module> = mapOf(
    BridgeModule.MODULE_NAME to BridgeModule(),
    WebSocketModule.MODULE_NAME to WebSocketModule(),
)
