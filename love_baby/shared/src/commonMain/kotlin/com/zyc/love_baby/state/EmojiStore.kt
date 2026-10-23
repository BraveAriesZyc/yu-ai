package com.zyc.love_baby.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.EmojiInfo
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.EmojiApi

/**
 * EmojiStore — 表情面板字典快照单例(LoveConfigStore 单例范式,M25)
 * 用法:读 [categories]/[effectiveCategory]/[currentEmojis] 渲染分类 tab + 网格;
 *      刷新由 MainPager 接线:登录后首拉 + 回前台 effect(与 LoveConfigStore.refresh 同点)
 * 依赖:androidx.compose.runtime 快照状态;EmojiApi.list(HttpClient 401 单飞刷新兜底);ChatStickers
 * 事件:refresh 成功才落值,失败静默保留旧值(只读降级,§7.4 失败不拦 UI);会话无关的运营字典,
 *      登出/解绑不 reset(下一个会话照常复用,后台改表回前台即见)
 * 功能:
 *  - 服务端 GET /love/emoji/list 启用行全量落 templates;分类动态聚合(category 空串归「常用」)
 *  - 兜底哲学:池空/停用全删/拉取失败 → templates 空,当前表情回退 ChatStickers.ORDER 包装的
 *    单分类「常用」(kind=6 渲染兜底字典原样复用,面板永不空白;老版兼容语义与 M23 一致)
 *  - selectedCategory 跨面板开合保留;refresh 后失效即回落第一个分类(clamp)
 */
internal object EmojiStore {

    /** 空分类兜底桶名(category 空串与全池兜底共用) */
    private const val FALLBACK_CATEGORY = "常用"

    /** 全池兜底字典(templates 空时唯一分类);ChatStickers 是 append-only 铁则字典,恒可用 */
    private val FALLBACK_EMOJIS: List<EmojiInfo> =
        ChatStickers.ORDER.map { EmojiInfo(id = it, category = FALLBACK_CATEGORY, emoji = ChatStickers.emojiOf(it)) }

    /** 服务端表情字典快照(启用行全量;空表 = 走 [FALLBACK_EMOJIS] 兜底) */
    var templates by mutableStateOf(emptyList<EmojiInfo>())
        private set

    /** 当前选中分类(空串 = 自动取第一个;refresh 后失效回落,clamp 语义见 refresh) */
    var selectedCategory by mutableStateOf("")

    /** 在飞守卫(M19 Store 层惯例):首拉/回前台两个 effect 可能同拍并发,双发无收益 */
    @Volatile
    private var refreshing = false

    /** 分类 tab 数据源(服务端序聚合去重;兜底恒单分类「常用」) */
    val categories: List<String>
        get() = if (templates.isEmpty()) {
            listOf(FALLBACK_CATEGORY)
        } else {
            templates.map { it.category.ifBlank { FALLBACK_CATEGORY } }.distinct()
        }

    /** 生效分类(selectedCategory 失效/未选时取第一个;tab 高亮与网格过滤共用同值) */
    val effectiveCategory: String
        get() = selectedCategory.takeIf { it in categories }
            ?: categories.firstOrNull() ?: FALLBACK_CATEGORY

    /** 当前分类下的表情(网格数据源;兜底/服务端两源统一为 EmojiInfo 流,渲染零分支) */
    val currentEmojis: List<EmojiInfo>
        get() {
            val list = if (templates.isEmpty()) FALLBACK_EMOJIS else templates
            return list.filter { it.category.ifBlank { FALLBACK_CATEGORY } == effectiveCategory }
        }

    /**
     * 拉取表情字典(登录后首拉/回前台,MainPager 接线),失败静默保留旧值。
     * 全量替换;成功后 selectedCategory 失效回落第一个分类(后台删分类防 tab 落空)
     */
    suspend fun refresh(pager: Pager) {
        if (refreshing) return
        refreshing = true
        try {
            val result = EmojiApi.list(pager)
            if (result is ApiResult.Ok) {
                templates = EmojiInfo.listFrom(result.dataArray)
                val cats = categories
                if (selectedCategory !in cats) selectedCategory = cats.first()
            }
        } finally {
            refreshing = false
        }
    }
}
