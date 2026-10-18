package com.zyc.love_baby.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.LoveConfigApi

/**
 * LoveConfigStore — 全局业务配置快照单例(StatsStore 单例范式,M23)
 * 用法:读 LoveConfigStore.contactWechat 等具名取值(缺键/停用/拉取失败恒回内置兜底值);
 *      刷新由 MainPager 接线:登录后首拉 + 回前台 effect(与 StatsStore.refresh 同点)
 * 依赖:androidx.compose.runtime 快照状态;LoveConfigApi.list(HttpClient 401 单飞刷新兜底)
 * 事件:refresh 成功才落值,失败静默保留旧值(只读降级,§7.4 失败不拦 UI);会话无关的运营
 *      配置,登出/解绑不 reset(下一个会话照常复用,配置项全部可后台管理端热更)
 * 功能:
 *  - 服务端 GET /love/config/list 拆包:回包单对象 = configKey→configValue 映射,
 *    keySet() 遍历转 Map(核心 JSONObject.optString 非空返回,免判空)
 *  - 具名取值器逐个声明(configKey 即 App 读取契约,后台「业务配置」页维护):
 *      contactWechat ← customer_service_wechat(我的页「帮助与客服」行复制引导)
 *    新配置项 = 后台加行 + 此处加一个取值器,端上其余零改动
 */
internal object LoveConfigStore {

    /** 客服微信号内置兜底值(⚠️ 与后台种子同值;后台缺行/停用/拉取失败时兜底,永不空白) */
    private const val DEFAULT_CONTACT_WECHAT = "lovebaby-helper"

    /** 后台维护的配置键(App 契约,与 love_config 表 config_key 同串双源,勿单改一边) */
    private const val KEY_CONTACT_WECHAT = "customer_service_wechat"

    /** 配置快照(configKey→configValue;空 Map = 全走内置兜底,UI 语义不变) */
    private var configs by mutableStateOf<Map<String, String>>(emptyMap())

    /** 在飞守卫(M19 Store 层惯例):首拉/回前台两个 effect 可能同拍并发,双发无收益 */
    @Volatile
    private var refreshing = false

    /** 客服微信号(我的页「帮助与客服」行复制引导);缺键/空值回内置兜底 */
    val contactWechat: String
        get() = configs[KEY_CONTACT_WECHAT]?.takeIf { it.isNotBlank() } ?: DEFAULT_CONTACT_WECHAT

    /**
     * 拉取业务配置(登录后首拉/回前台,MainPager 接线),失败静默保留旧值。
     * 全量替换(Map 整体赋值,相等跳重组);会话无关,不做 loaded 门——每轮前台都拉,
     * 后台改配置下一个前台周期即可见(与 inspiration「即时生效」语义对齐)
     */
    suspend fun refresh(pager: Pager) {
        if (refreshing) return
        refreshing = true
        try {
            val result = LoveConfigApi.list(pager)
            if (result is ApiResult.Ok) {
                val fresh = HashMap<String, String>()
                for (key in result.data.keySet()) {
                    fresh[key] = result.data.optString(key)
                }
                configs = fresh
            }
        } finally {
            refreshing = false
        }
    }
}
