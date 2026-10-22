package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.Module

/**
 * iOS 平台扩展模块(actual):无原生长连/文件桥实现(未做),不注册 WebSocketModule/FileModule
 * → getModule=null → 调用方判空降级(WS 纯轮询/头像 toast 不支持),渐进降级设计
 */
internal actual fun platformExternalModules(): Map<String, Module> = mapOf(
    BridgeModule.MODULE_NAME to BridgeModule(),
)
