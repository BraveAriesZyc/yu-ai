package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.Module
import com.tencent.kuiklybase.websocket.WebSocketModule

/**
 * 鸿蒙 ohosArm64 平台扩展模块(actual;M35 新建——此前 ohos 管线缺本 actual,shared 的 ohos
 *  构建本就编不过):注册 BridgeModule(宿主 kuikly/modules/KRBridgeModule.ets,同名 HRBridgeModule)+
 *  WebSocketModule(官方组件 KuiklyWebsocket vendored common 侧;ohos 原生件 KRWebSocketModule.ets
 *  已入宿主同目录,KuiklyViewDelegate.getCustomRenderModuleCreatorRegisterMap 显式注册)。
 * FileModule/通知/定位/地图等仍未实现 → getModule=null → 调用方判空降级(渐进降级设计)
 */
internal actual fun platformExternalModules(): Map<String, Module> = mapOf(
    BridgeModule.MODULE_NAME to BridgeModule(),
    WebSocketModule.MODULE_NAME to WebSocketModule(),
)
