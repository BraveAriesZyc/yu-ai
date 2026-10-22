package com.zyc.love_baby.base

import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.*

internal abstract class BasePager : ComposeContainer() {
    private var nightModel: Boolean? by observable(null)

    // 扩展模块注册收口到平台 actual(platformExternalModules):core getModule 只认此处登记
    // 的模块(Pager.createModuleIfNeed 源码实证),此前 WebSocketModule/FileModule 漏注册导致
    // WS 从未连上、头像上传恒「当前端暂不支持」——详见 PlatformExternalModules 头注释
    override fun createExternalModules(): Map<String, Module>? {
        return platformExternalModules()
    }

    override fun created() {
        super.created()
        isNightMode()
    }

    override fun themeDidChanged(data: JSONObject) {
        super.themeDidChanged(data)
        nightModel = data.optBoolean(IS_NIGHT_MODE_KEY)
    }

    // 是否为夜间模式
    override fun isNightMode(): Boolean {
        if (nightModel == null) {
            nightModel = pageData.params.optBoolean(IS_NIGHT_MODE_KEY)
        }
        return nightModel!!
    }

    // 不开启调试UI模式
    override fun debugUIInspector(): Boolean {
        return false
    }

    companion object {
        const val IS_NIGHT_MODE_KEY = "isNightMode"
    }

}
