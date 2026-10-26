package com.zyc.love_baby.features.checkin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
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
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.base.closeLovePage
import com.zyc.love_baby.components.AppLoadFailed
import com.zyc.love_baby.components.AppLoading
import com.zyc.love_baby.components.AppMonthNav
import com.zyc.love_baby.components.AppRingDot
import com.zyc.love_baby.components.AppSubPageHeader
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.data.model.CheckinInfo
import com.zyc.love_baby.data.model.CheckinSummaryInfo
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.state.SessionStore
import com.zyc.love_baby.state.CheckinStore
import com.zyc.love_baby.state.currentDate
import com.zyc.love_baby.state.daysInMonthYear
import com.zyc.love_baby.state.firstWeekdayOfMondayFirst
import com.zyc.love_baby.state.monthShift
import kotlinx.coroutines.launch

/**
 * CheckinPage — 每日打卡页(@Page 注册名 "checkinPage"):纯日期点 + 月历主视图 + 共同连续(M8)
 * 用法:首页「今日打卡」条 / 我的页「每日打卡」菜单 RouterModule.openPage 打开;返回键 closePage。
 *      独立二级页(AnniversaryPager 同款壳,不走 MainPager 五 Tab)
 * 依赖:CheckinStore(当月缓存 + summary + 今日双布尔);AppState 月算术纯函数(monthShift/
 *      daysInMonthYear/firstWeekdayOfMondayFirst/currentDate);couple 组 toast;closeLovePage(返回)
 * 事件:返回钮 closePage;失败态「重试」重拉;打卡按钮发 CheckinStore.check;‹› 翻月 loadMonth
 * 功能:
 *  - 页头卡:今日打卡大按钮三态(未打=品牌渐变「今日打卡」/我打了 TA 没打=灰底「等 TA 打卡」
 *    不可点/共同完成=浅品牌底「今天已共同打卡 ✓」)+ 共同连续 streak + 双方累计
 *  - 月历卡:7 列 chunked(7) Row 自排(MoodCalendarCard 结构,M3 心情月历同款;不抽公共组件
 *    ——心情版与心情字典耦合,本页复制改语义);我=7dp 实心点,TA=9dp 空心环(嵌套双圆画法,
 *    不依赖 border);今天浅品牌底高亮;格子不可点(无补打,月历纯展示)
 *  - 翻月:monthShift ±1 调 loadMonth,失败静默保留现月(只读降级,MoodStore 同款)
 *  - check 成功:Store 落态(记录并入当月缓存 + checkedToday + summary 重拉),按钮三态即时翻转
 */
@Page("checkinPage", supportInLocal = true)
internal class CheckinPager : BasePager() {

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            CheckinPage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }
}

@Composable
internal fun CheckinPage(pager: Pager) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    // 展示月(yyyy-MM):初始当月;页内自持,翻月成功才推进(失败保留现月,与缓存一致)
    var curMonth by remember { mutableStateOf("") }
    // 今天(yyyy-MM-dd)remember 一次:本页短驻留内跨天不重算(重进页面校准,§7.4)
    val todayKey = remember { currentDate(pager) }
    val summary = CheckinStore.summary
    val checkins = CheckinStore.checkins

    fun load() {
        scope.launch {
            loading = true
            if (curMonth.isEmpty()) curMonth = todayKey.take(7)
            val r1 = CheckinStore.loadSummary(pager)
            val r2 = CheckinStore.loadMonth(pager, curMonth)
            loading = false
            loadFailed = r1 !is ApiResult.Ok || r2 !is ApiResult.Ok
        }
    }

    fun shiftMonth(delta: Int) {
        val target = monthShift(curMonth, delta)
        if (target.isEmpty()) return
        scope.launch {
            val result = CheckinStore.loadMonth(pager, target)
            if (result is ApiResult.Ok) curMonth = target // 失败静默保留现月(只读降级)
        }
    }

    fun checkin() {
        if (checking) return
        checking = true
        scope.launch {
            val result = CheckinStore.check(pager)
            checking = false
            // 失败 toast(未绑定 1_003_004_003 等业务错误 toast 服务端 msg,§5.1 对照表);
            // 成功落态全在 Store(记录/今日布尔/summary),按钮三态即时翻转,无需额外提示
            if (result !is ApiResult.Ok) {
                toastCoupleResult(pager, result)
            }
        }
    }

    // 独立页每次打开都是新 Pager 组合,LaunchedEffect(Unit) 即每次进入都重拉
    LaunchedEffect(Unit) { load() }

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        Column(
            modifier = Modifier.fillMaxSize().background(theme.bg),
        ) {
            AppSubPageHeader(
                "每日打卡",
                "一起坚持的小浪漫",
                onBack = {
                    pager.closeLovePage()
                },
            )
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) { AppLoading() }
                loadFailed -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) { AppLoadFailed { load() } }
                else -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Spacer(modifier = Modifier.height(28.dp)) // 给渐变头呼吸位
                    CheckinHeaderCard(
                        summary = summary,
                        checkedToday = CheckinStore.checkedToday,
                        partnerCheckedToday = CheckinStore.partnerCheckedToday,
                        checking = checking,
                        onCheck = { checkin() },
                    )
                    CheckinCalendarCard(
                        curMonth = curMonth,
                        todayKey = todayKey,
                        onPrev = { shiftMonth(-1) },
                        onNext = { shiftMonth(1) },
                        checkins = checkins,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp + navBarSafePaddingDp()))
        }
    }
}

/** toast 收口复用 couple 域(getModule 可空降级,AnniversaryPage 同款;不另起 checkin 本地版本) */

/**
 * 页头卡:今日打卡大按钮(三态)+ 共同连续 streak + 双方累计。
 * 三态只看今日双布尔(summary 缺失不挡打卡——按钮是主操作,streak 显 "--" 即可)
 */
@Composable
private fun CheckinHeaderCard(
    summary: CheckinSummaryInfo?,
    checkedToday: Boolean,
    partnerCheckedToday: Boolean,
    checking: Boolean,
    onCheck: () -> Unit,
) {
    val theme = ThemeStore.current
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = AppTheme.PageHPadding)
            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 今日打卡大按钮(三态;选中态换底色不改结构,铁律③:底色常驻只换值)
        val both = checkedToday && partnerCheckedToday
        val waiting = checkedToday && !partnerCheckedToday
        // 三态底统一走 brush 重载(single-brush 2026-10-16 Q2:同节点 color/brush 不混用;
        // 纯色用同色 linearGradient 模拟,AppCards.buttonBrush/ChatPage.taBubbleBrush 先例)
        val buttonBg = remember(both, waiting, theme.brandStart, theme.brandEnd) {
            when {
                both -> Brush.linearGradient(
                    listOf(theme.brandStart.copy(alpha = 0.12f), theme.brandEnd.copy(alpha = 0.12f)),
                )
                waiting -> Brush.linearGradient(listOf(AppTheme.SecondaryBg, AppTheme.SecondaryBg))
                else -> theme.brandBrush // 未打 = 品牌渐变可点
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth()
                .height(48.dp)
                .background(buttonBg, RoundedCornerShape(24.dp))
                .clickable {
                    // 三态守卫:未打且非请求中才放行(clickable enabled 参数仓内无先例,不用)
                    if (!checkedToday && !checking) onCheck()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    both -> "今天已共同打卡 ✓"
                    waiting -> "等 TA 打卡"
                    checking -> "打卡中…"
                    else -> "今日打卡"
                },
                color = when {
                    both -> theme.brandStart
                    waiting -> AppTheme.TextSecondary
                    else -> Color.White
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // 共同连续(streak 0 = 还没有共同连续;summary 未拉到显 "--")
        Text("共同连续打卡", color = AppTheme.TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            when {
                summary == null -> "--"
                summary.commonStreak > 0 -> "${summary.commonStreak} 天"
                else -> "还没有共同连续"
            },
            color = if ((summary?.commonStreak ?: 0) > 0) theme.brandStart else AppTheme.TextSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        // 双方累计(全量跨月)
        Text(
            "我 ${summary?.myTotal ?: "--"} 次 · TA ${summary?.partnerTotal ?: "--"} 次",
            color = AppTheme.TextTertiary,
            fontSize = 12.sp,
        )
    }
}

/**
 * 月历卡:年月导航(‹ 2026年10月 › + 图例)+ 星期表头(周一首)+ 7 列日历格 + 本月注记。
 * 结构照 MoodPage.MoodCalendarCard(chunked(7) Row 自排,非 LazyVerticalGrid);
 * 我=7dp 实心点 / TA=9dp 空心环(MoodDayCell 语义,环用嵌套双圆画法不依赖 border);
 * 今天浅品牌底高亮;格子不可点(无补打,纯展示)
 */
@Composable
private fun CheckinCalendarCard(
    curMonth: String,
    todayKey: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    checkins: List<CheckinInfo>,
) {
    val theme = ThemeStore.current
    val (year, month) = monthPartsOf(curMonth)
    val days = daysInMonthYear(year, month)
    val leading = firstWeekdayOfMondayFirst(year, month) // 周一=0..周日=6
    // 头尾补 0 占位补齐整周(chunked(7) 行等宽)
    val cells: List<Int> =
        List(leading) { 0 } + (1..days).toList() + List((7 - (leading + days) % 7) % 7) { 0 }
    val myCount = checkins.count { it.userId == SessionStore.userId }
    val partnerCount = checkins.count { it.userId != SessionStore.userId }

    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = AppTheme.PageHPadding)
            .padding(top = 12.dp)
            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        // 年月导航(AppMonthNav 单源,原 15sp Bold/30dp 透明底 18dp 箭头 = 组件默认+iconSize 覆写)+ 图例
        AppMonthNav(
            label = "${year}年${month}月",
            onPrev = onPrev,
            onNext = onNext,
            iconSize = 18.dp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(7.dp)
                    .background(theme.brandStart, RoundedCornerShape(4.dp)),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("我", color = AppTheme.TextTertiary, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(12.dp))
            AppRingDot(Color(0xFFFF9A56))
            Spacer(modifier = Modifier.width(4.dp))
            Text("TA", color = AppTheme.TextTertiary, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 星期表头(周一首)
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    label,
                    color = AppTheme.TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        // 日历格(7 列 chunked Row 自排;同月缓存按日查双标记)
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day > 0) {
                            val dateKey = "%04d-%02d-%02d".format(year, month, day)
                            CheckinDayCell(
                                day = day,
                                isToday = dateKey == todayKey,
                                mine = checkins.any {
                                    it.userId == SessionStore.userId &&
                                        it.checkinDate == dateKey
                                },
                                partner = checkins.any {
                                    it.userId != SessionStore.userId &&
                                        it.checkinDate == dateKey
                                },
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 本月注记
        Text(
            "本月我 $myCount 次 · TA $partnerCount 次",
            color = AppTheme.TextTertiary,
            fontSize = 11.sp,
        )
    }
}

/** 日历格:日数字(今天品牌色加粗 + 浅品牌底)+ 标记(我=实心点,TA=空心环,双打=点+环) */
@Composable
private fun CheckinDayCell(day: Int, isToday: Boolean, mine: Boolean, partner: Boolean) {
    val theme = ThemeStore.current
    Column(
        modifier = Modifier.fillMaxSize()
            .padding(1.dp)
            .background(
                if (isToday) theme.brandStart.copy(alpha = 0.08f) else Color.Transparent,
                RoundedCornerShape(10.dp),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$day",
            color = if (isToday) theme.brandStart else AppTheme.TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        if (mine || partner) {
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mine) {
                    Box(
                        modifier = Modifier.size(7.dp)
                            .background(theme.brandStart, RoundedCornerShape(4.dp)),
                    )
                }
                if (mine && partner) {
                    Spacer(modifier = Modifier.width(3.dp))
                }
                if (partner) {
                    AppRingDot(Color(0xFFFF9A56))
                }
            }
        }
    }
}

/** "yyyy-MM" → (年, 月);解析失败回 (1970, 1) 兜底(调用方 monthShift 空串已挡,理论不可达) */
private fun monthPartsOf(ym: String): Pair<Int, Int> {
    val parts = ym.split("-")
    val y = parts.getOrNull(0)?.toIntOrNull() ?: return 1970 to 1
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return 1970 to 1
    if (y !in 1..9999 || m !in 1..12) return 1970 to 1
    return y to m
}
