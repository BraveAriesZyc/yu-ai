package com.zyc.love_baby.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.WishTemplateInfo
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.WishTemplateApi
import kotlin.concurrent.Volatile

/**
 * WishStore — 心愿墙挑选模板快照单例(LoveConfigStore 单例范式,M25)
 * 用法:读 [wishes]/[categories] 渲染心愿墙分类 chips + 卡片网格;刷新由 MainPager 接线:
 *      登录后首拉 + 回前台 effect(与 LoveConfigStore.refresh 同点)
 * 依赖:androidx.compose.runtime 快照状态;WishTemplateApi.list;AppState 的 Wish/colorFromHex/
 *      appIconFromName/allWishes 兜底;MoodStore(选中集剪枝)
 * 事件:refresh 成功才落值,失败静默保留旧值(只读降级,§7.4 失败不拦 UI);会话无关的运营字典,
 *      登出/解绑不 reset(选中态随 MoodStore.reset 清,字典本体跨会话复用)
 * 功能:
 *  - 服务端 GET /love/wish-template/list 启用行全量落 templates,经 toWish() 转既有 Wish 类型
 *    ——MoodPage 网格/WishCard/送出条下游零改;模板 id 即 wishKey(sendWishes 快照式上送)
 *  - 兜底哲学:池空/停用全删/拉取失败 → 走 allWishes 内置 24 条兜底(与 M3.5 起端上字典同源,
 *    心愿墙永不空白);转码失败兜底:hex 不合法回主题粉系、icon 未知名回 Heart
 *  - refresh 成功后 MoodStore.retainSelected 剪枝选中集(停用/删除的模板从已选中剔除,
 *    防「已选 N 个」悬空计数;全停用则选中集清空,显示回落兜底列表)
 */
internal object WishStore {

    /** 空分类兜底桶名(category 空串归此;与 emoji「常用」异名——心愿语境「其他」更自然) */
    private const val FALLBACK_CATEGORY = "其他"

    /** colorEnd hex 解析失败兜底(与种子默认渐变止色同值;colorStart 走 colorFromHex 默认主题粉) */
    private val FALLBACK_COLOR_END = Color(0xFFFF5A8C)

    /** 服务端模板快照(启用行全量;空表 = 走 allWishes 兜底) */
    var templates by mutableStateOf(emptyList<WishTemplateInfo>())
        private set

    /** 在飞守卫(M19 Store 层惯例):首拉/回前台两个 effect 可能同拍并发,双发无收益 */
    @Volatile
    private var refreshing = false

    /**
     * 心愿墙数据源(网格/送出条共用):模板空走 allWishes 兜底,非空整表转 Wish
     * (服务端 sort asc, id asc 序原样保留;转 Wish 是纯映射,id 恒定不抖 key 槽位)
     */
    val wishes: List<Wish>
        get() = if (templates.isEmpty()) allWishes else templates.map { it.toWish() }

    /** 分类 chips 数据源(数据序聚合去重;含兜底列表时与原 moodCategories 序一致) */
    val categories: List<String>
        get() = wishes.map { it.category }.distinct()

    /** 模板 → 既有 Wish(M14 类型)纯映射:下游 MoodPage/WishCard/sendWishes 零改 */
    private fun WishTemplateInfo.toWish(): Wish = Wish(
        id = id,
        category = category.ifBlank { FALLBACK_CATEGORY },
        title = title,
        subtitle = subtitle,
        colors = listOf(colorFromHex(colorStart), colorFromHex(colorEnd, FALLBACK_COLOR_END)),
        icon = appIconFromName(icon),
    )

    /**
     * 拉取心愿模板(登录后首拉/回前台,MainPager 接线),失败静默保留旧值。
     * 全量替换;成功后按新模板集剪枝选中集(仅 Ok 才剪——拉取失败不动用户当前选中)
     */
    suspend fun refresh(pager: Pager) {
        if (refreshing) return
        refreshing = true
        try {
            val result = WishTemplateApi.list(pager)
            if (result is ApiResult.Ok) {
                templates = WishTemplateInfo.listFrom(result.dataArray)
                MoodStore.retainSelected(templates.map { it.id }.toSet())
            }
        } finally {
            refreshing = false
        }
    }
}
