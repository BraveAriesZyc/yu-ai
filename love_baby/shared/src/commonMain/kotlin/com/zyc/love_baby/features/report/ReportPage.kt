package com.zyc.love_baby.features.report

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.base.closeLovePage
import com.zyc.love_baby.base.openLovePage
import com.zyc.love_baby.components.AppEmptyHint
import com.zyc.love_baby.components.AppFeedback
import com.zyc.love_baby.components.AppGhostIconButton
import com.zyc.love_baby.components.AppIconTile
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppLoadFailed
import com.zyc.love_baby.components.AppLoading
import com.zyc.love_baby.components.AppSectionTitle
import com.zyc.love_baby.components.AppStatBar
import com.zyc.love_baby.components.AppStatItem
import com.zyc.love_baby.components.AppSubPageHeader
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.data.model.ReportMonthlyInfo
import com.zyc.love_baby.data.model.TopMoodInfo
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.ReportApi
import com.zyc.love_baby.state.colorFromHex
import com.zyc.love_baby.state.currentDate
import com.zyc.love_baby.state.monthParts
import com.zyc.love_baby.state.monthShift
import kotlinx.coroutines.launch

/** 页面路由名(入口 openMonthlyReport 与 @Page 名同源,勿散落字面量——BatteryPage 同款约定) */
internal const val MONTHLY_REPORT_PAGE_NAME = "monthlyReport"

/** 打开恋爱月报页(路由助手随页走:features 内顶层函数 + openLovePage,BatteryPage 同款) */
internal fun openMonthlyReport(pager: Pager) {
    pager.openLovePage(MONTHLY_REPORT_PAGE_NAME)
}

/**
 * ReportPage — 恋爱月报页(@Page "monthlyReport",M30;同款壳参照 MoodTimelinePage/BatteryPage)
 * 用法:首页快捷入口「恋爱月报」卡 openMonthlyReport 打开;返回键/closePage 返回。
 *      独立二级页(不走 MainPager 五 Tab)
 * 依赖:ReportApi.monthly(单月五域聚合,服务端聚合——相册游标分页/悄悄话 50 截尾,端上拼不出
 *      全月);ReportMonthlyInfo/TopMoodInfo;components(AppSubPageHeader/AppGhostIconButton/
 *      AppStatBar/AppSectionTitle/AppIconTile/AppEmptyHint/四态件);AppState(currentDate/
 *      monthParts/monthShift/colorFromHex);closeLovePage(返回);ThemeStore
 * 事件:返回钮 closePage;月份钮切月重拉(未来月禁切);失败态「重试」重拉
 * 功能:
 *  - 页内自持月份与月报(remember),不落全局 Store——数据页域内、无跨页消费
 *    (MoodTimelinePage 先例),免 MainPager reset 接线
 *  - 未绑定/空月全 0 也是合法月报:不设空态分支,零计数照常渲染(服务端静默全 0 口径)
 *  - 切月失败保留现月缓存 + toast(§7.4 只读降级);首拉失败进失败重试态(四态分家,不造假空态)
 *  - 次月越界守卫:delta>0 且 next 超当月直接忽略(yyyy-MM 字典序即时间序);Next 钮到当月变灰
 */
@Page("monthlyReport", supportInLocal = true)
internal class ReportPager : BasePager() {

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            ReportPage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }
}

@Composable
internal fun ReportPage(pager: Pager) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<ReportMonthlyInfo?>(null) }
    var month by remember { mutableStateOf("") } // yyyy-MM;空串 = 未初始化,首帧取当月
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    // 当月上限(remember 一次:currentDate 走桥接 dateFormatter,不进重组热路径)
    val currentMonth = remember { currentDate(pager).take(7) } // yyyy-MM-dd → yyyy-MM

    fun load(target: String) {
        scope.launch {
            loading = true
            val result = ReportApi.monthly(pager, target)
            if (result is ApiResult.Ok) {
                val parsed = ReportMonthlyInfo.from(result.data)
                if (parsed != null) {
                    report = parsed
                    month = parsed.month // 服务端回填月为准(与入参一致)
                    loadFailed = false
                } else if (month.isEmpty()) {
                    // 首拉失败且无内容:失败重试态(四态分家,不造假空态)
                    loadFailed = true
                } else {
                    AppFeedback.toast(pager, "切换失败,请重试")
                }
            } else if (month.isEmpty()) {
                loadFailed = true
            } else {
                // 浏览中切月失败:保留现月缓存,toast 提示(§7.4 只读降级)
                AppFeedback.toast(pager, "切换失败,请重试")
            }
            loading = false
        }
    }

    fun shiftMonth(delta: Int) {
        if (loading) return
        val next = monthShift(month, delta)
        if (next.isEmpty()) return
        // 不看未来:次月不超当月(yyyy-MM 字典序即时间序)
        if (delta > 0 && next > currentMonth) return
        load(next)
    }

    LaunchedEffect(Unit) {
        load(currentMonth)
    }

    val info = report
    val canGoNext = month.isNotEmpty() && month < currentMonth

    Column(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxSize()) {
            item {
                AppSubPageHeader(
                    "恋爱月报",
                    "这个月，我们的小结",
                    onBack = {
                        pager.closeLovePage()
                    },
                )
            }
            item {
                // 月份切换条:MoodTimelinePage 同构(双钮等宽 + SpaceBetween,标题自然居中)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = AppTheme.PageHPadding, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AppGhostIconButton(
                        AppIconType.ChevronLeft,
                        { shiftMonth(-1) },
                        size = 30.dp,
                        bg = AppTheme.CardBg,
                        iconSize = 16.dp,
                    )
                    val (year, monthNum) = monthParts(month)
                    Text(
                        if (year == 0) "—" else "${year}年${monthNum}月",
                        color = AppTheme.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    AppGhostIconButton(
                        AppIconType.ChevronRight,
                        { shiftMonth(1) },
                        size = 30.dp,
                        bg = AppTheme.CardBg,
                        tint = if (canGoNext) AppTheme.TextSecondary else AppTheme.TextTertiary,
                        iconSize = 16.dp,
                    )
                }
            }
            when {
                loading -> item { AppLoading() }
                loadFailed -> item {
                    AppLoadFailed { load(month.ifEmpty { currentMonth }) }
                }
                info == null -> item { AppLoading() } // Ok 回包残包且无缓存:理论不可达,兜底转圈
                else -> {
                    item {
                        // 头条统计:四列白卡(统计条与首页悬浮卡/MinePage 统计卡同件)
                        AppStatBar(
                            items = listOf(
                                AppStatItem("${info.bindDays}", "在一起(天)"),
                                AppStatItem("${info.togetherCheckinDays}", "共同打卡(天)"),
                                AppStatItem("${info.messageCount}", "悄悄话(条)"),
                                AppStatItem("${info.wishDoneCount}", "完成心愿(个)"),
                            ),
                            modifier = Modifier.padding(horizontal = AppTheme.PageHPadding),
                        )
                    }
                    item { MoodDistributionSection(info) }
                    item { HighlightsSection(info) }
                    item {
                        Spacer(modifier = Modifier.height(navBarSafePaddingDp() + 24.dp))
                    }
                }
            }
        }
    }
}

/** 心情分布:标题行带总数 + Top3 行(40dp emoji 方块记录色洗底);空分布出空态提示 */
@Composable
private fun MoodDistributionSection(info: ReportMonthlyInfo) {
    Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppSectionTitle("心情分布")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "共 ${info.moodCount} 条",
                color = AppTheme.TextTertiary,
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (info.topMoods.isEmpty()) {
            AppEmptyHint(
                icon = AppIconType.Emoji,
                title = "这个月还没有心情记录",
                hint = "去心情页记下今天的心情吧",
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                info.topMoods.forEach { mood ->
                    MoodCountRow(mood)
                }
            }
        }
    }
}

@Composable
private fun MoodCountRow(mood: TopMoodInfo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // emoji 方块:记录色 12% 洗底(MoodTimelinePage 组内行同款);空色走 colorFromHex 兜底,
        // 空 emoji 显 label 首字(存量旧行快照列可能为空,不造假数据)
        val tileBg = colorFromHex(mood.color).copy(alpha = 0.12f)
        Box(
            modifier = Modifier.size(40.dp).background(tileBg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (mood.emoji.isEmpty()) mood.label.take(1) else mood.emoji,
                fontSize = 20.sp,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                mood.label,
                color = AppTheme.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "${mood.count} 次",
                color = AppTheme.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

/** 本月点滴:三行小结卡(打卡明细/相册/清单;图标配色与首页快捷入口同源) */
@Composable
private fun HighlightsSection(info: ReportMonthlyInfo) {
    Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
        Spacer(modifier = Modifier.height(24.dp))
        AppSectionTitle("本月点滴")
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportNoteCard(
                icon = AppIconType.Check,
                iconTint = Color(0xFF6C7BFF),
                iconBg = Color(0xFFEDEFFF),
                title = "每日打卡",
                desc = "我 ${info.myCheckinDays} 天 · TA ${info.partnerCheckinDays} 天 · " +
                    "共同点亮 ${info.togetherCheckinDays} 天",
            )
            ReportNoteCard(
                icon = AppIconType.Heart,
                iconTint = Color(0xFFFF5A8C),
                iconBg = Color(0xFFFFEAF1),
                title = "我们的相册",
                desc = "一起存下 ${info.momentCount} 张瞬间",
            )
            ReportNoteCard(
                icon = AppIconType.Flag,
                iconTint = Color(0xFFF7A44A),
                iconBg = Color(0xFFFFF3E6),
                title = "恋爱清单",
                desc = "一起完成 ${info.wishDoneCount} 个心愿",
            )
        }
    }
}

@Composable
private fun ReportNoteCard(
    icon: AppIconType,
    iconTint: Color,
    iconBg: Color,
    title: String,
    desc: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconTile(
            icon = icon,
            size = 38.dp,
            bgColor = iconBg,
            iconTint = iconTint,
            iconSize = 18.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = AppTheme.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                desc,
                color = AppTheme.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}
