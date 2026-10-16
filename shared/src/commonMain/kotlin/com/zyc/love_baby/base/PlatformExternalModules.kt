package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.Module

/**
 * PlatformExternalModules — 平台侧扩展模块注册表(expect/actual;core getModule 注册链缺口收口)
 * 背景:core Pager.getModule 只创建 moduleCreatorMap 里登记过的模块(Pager.kt createModuleIfNeed
 *  源码实证),自定义模块必须随 createExternalModules 注册,否则恒 null——与原生侧 moduleExport
 *  注册无关(core 侧发不出调用,原生注册了也收不到)。此前 WebSocketModule/FileModule 从未注册:
 *  WS 触发器(D-16)实际从未连上、「收到很快」一直靠 5s 轮询兜底;头像上传报「当前端暂不支持」
 *  同根因(2026-10-17 真机暴露后修)
 * 平台差异:Android 有原生实现(KRWebSocketModule/KRFileModule,okhttp)注册之;js(H5)/ios
 *  无原生实现不注册 → getModule=null → 调用方判空降级(头像 toast 不支持/WS 纯轮询渐进降级),
 *  base/FileModule 与 base/WebSocketModule 头注释的「未桥接端 getModule 返回 null」契约由此落地
 */
internal expect fun platformExternalModules(): Map<String, Module>
