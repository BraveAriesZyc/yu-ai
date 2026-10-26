package com.zyc.love_baby.base

import com.tencent.kuikly.core.base.BaseObject
import com.tencent.kuikly.core.manager.BridgeManager
import com.tencent.kuikly.core.manager.PagerManager
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager

internal object Utils : BaseObject() {

    fun bridgeModule(pager: String): BridgeModule {
        return PagerManager.getPager(pager).acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
    }

    fun logToNative(pagerId: String, content: String) {
        // logToNaive
        bridgeModule(pagerId).log(content)
    }

    fun currentBridgeModule(): BridgeModule {
        return PagerManager.getPager(BridgeManager.currentPageId).acquireModule<BridgeModule>(
            BridgeModule.MODULE_NAME
        )
    }

    fun logToNative(content: String) {
        bridgeModule(BridgeManager.currentPageId).log(content)
    }

    fun convertToPriceStr(price: Long): String {
        return (price / 100f).toString()
    }

}

/**
 * 路由打开站内二级页(新页压栈):try/catch 吞未桥接端异常(getModule null/宿主未实现,
 * 渐进降级语义同 bridgeModule 系);HomePage/MinePage/PermissionPage 原各持一份 openXxx
 * 私有包裹(同体复制 9 份),2026-10-22 收口单源
 */
internal fun Pager.openLovePage(name: String, params: JSONObject = JSONObject()) {
    try {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(name, params)
    } catch (_: Exception) {
    }
}

/**
 * 路由关闭当前二级页(退栈回上级):与 openLovePage 同款 try/catch(铁律 9,acquireModule 失败
 * 双抛接不住)。原各二级页「返回键 pager.acquireModule<RouterModule>(...).closePage()」15 处同体
 * 2026-10-26 收口单源;模板基准件 ImageAdapterBenchmarks 按保留政策不直替
 */
internal fun Pager.closeLovePage() {
    try {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    } catch (_: Exception) {
    }
}
