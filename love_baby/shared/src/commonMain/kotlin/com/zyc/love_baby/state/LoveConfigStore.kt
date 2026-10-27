package com.zyc.love_baby.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.LoveConfigApi
import kotlin.concurrent.Volatile

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
 *      quickStickers ← quick_stickers(聊天快捷贴纸条,M25)
 *      moodEmojiPresets ← mood_emoji_presets(心情新增浮层 emoji 预设,M25)
 *      moodColorPresets ← mood_color_presets(心情新增浮层色板预设,M25)
 *    新配置项 = 后台加行 + 此处加一个取值器,端上其余零改动;JSON 字符串数组走 stringListFrom
 */
internal object LoveConfigStore {

    /** 客服微信号内置兜底值(⚠️ 与后台种子同值;后台缺行/停用/拉取失败时兜底,永不空白) */
    private const val DEFAULT_CONTACT_WECHAT = "lovebaby-helper"

    /** 快捷贴纸条内置兜底(⚠️ 与后台种子同值;M25 前为 ChatPage 内置死数据,值原样迁入) */
    private val DEFAULT_QUICK_STICKERS = listOf("贴贴", "mua~", "想你了", "举高高", "晚安鸭")

    /**
     * 心情 emoji 预设内置兜底(⚠️ 与后台种子同值;M25 前为 MoodOptionManageOverlay 内置死数据)。
     * 维护约束随值迁移:全部 Unicode 11 及以前——老系统字体可渲染,勿随手加新版字形
     * (H5/鸿蒙字体覆盖 M4 多端测试再核);后台改值同理,只影响上送 emoji 字符串
     */
    private val DEFAULT_MOOD_EMOJI_PRESETS =
        listOf("😄", "😊", "🥰", "😌", "😎", "🥺", "😭", "😤", "😠", "😴", "🤒", "🥳")

    /** 心情色板预设内置兜底(⚠️ 与后台种子同值;8 色,同上迁入) */
    private val DEFAULT_MOOD_COLOR_PRESETS =
        listOf("#FFB93C", "#FF8FA3", "#FF5A5A", "#FF8A5C", "#4EB087", "#6C8BFF", "#9B6BFF", "#8FA3BF")

    /** 后台维护的配置键(App 契约,与 love_config 表 config_key 同串双源,勿单改一边) */
    private const val KEY_CONTACT_WECHAT = "customer_service_wechat"
    private const val KEY_QUICK_STICKERS = "quick_stickers"
    private const val KEY_MOOD_EMOJI_PRESETS = "mood_emoji_presets"
    private const val KEY_MOOD_COLOR_PRESETS = "mood_color_presets"

    /** 配置快照(configKey→configValue;空 Map = 全走内置兜底,UI 语义不变) */
    private var configs by mutableStateOf<Map<String, String>>(emptyMap())

    /** 在飞守卫(M19 Store 层惯例):首拉/回前台两个 effect 可能同拍并发,双发无收益 */
    @Volatile
    private var refreshing = false

    /** 客服微信号(我的页「帮助与客服」行复制引导);缺键/空值回内置兜底 */
    val contactWechat: String
        get() = configs[KEY_CONTACT_WECHAT]?.takeIf { it.isNotBlank() } ?: DEFAULT_CONTACT_WECHAT

    /** 聊天快捷贴纸条(输入行上方 5 胶囊,M25);坏 JSON/空数组回内置兜底 */
    val quickStickers: List<String>
        get() = stringListFrom(KEY_QUICK_STICKERS, DEFAULT_QUICK_STICKERS)

    /** 心情新增浮层 emoji 预设(M25);坏 JSON/空数组回内置兜底 */
    val moodEmojiPresets: List<String>
        get() = stringListFrom(KEY_MOOD_EMOJI_PRESETS, DEFAULT_MOOD_EMOJI_PRESETS)

    /** 心情新增浮层色板预设(M25);坏 JSON/空数组回内置兜底 */
    val moodColorPresets: List<String>
        get() = stringListFrom(KEY_MOOD_COLOR_PRESETS, DEFAULT_MOOD_COLOR_PRESETS)

    /**
     * configValue(JSON 字符串数组,如 ["a","b"])→ List<String>;坏 JSON/含空串条目静默剔除,
     * 结果空回 fallback(取值器语义:兜底值永远非空,消费方不做判空)
     */
    private fun stringListFrom(key: String, fallback: List<String>): List<String> {
        val raw = configs[key]?.takeIf { it.isNotBlank() } ?: return fallback
        val parsed = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { s -> arr.optString(s)?.takeIf { it.isNotEmpty() } }
        }.getOrDefault(emptyList())
        return parsed.ifEmpty { fallback }
    }

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
