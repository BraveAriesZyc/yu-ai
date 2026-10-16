package com.zyc.love_baby.features.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
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
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.base.Utils
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppTag
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.data.model.MoodRecordInfo
import com.zyc.love_baby.features.chat.chatHHmm
import com.zyc.love_baby.features.chat.civilDateKeyGmt8
import com.zyc.love_baby.features.chat.daypartLabel
import com.zyc.love_baby.features.chat.todayDayIndexGmt8
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.MoodApi
import com.zyc.love_baby.state.SessionStore
import com.zyc.love_baby.state.currentDate
import com.zyc.love_baby.state.moodRecordColor
import com.zyc.love_baby.state.monthParts
import com.zyc.love_baby.state.monthShift
import kotlinx.coroutines.launch

/**
 * MoodTimelinePage — 心情时间线页(@Page 注册名 "moodTimeline"):按日期分组+时段回看双方心情
 * 用法:心情页月历图例「心情时间线 ›」RouterModule.openPage 打开;返回键/closePage 返回。
 *      独立二级页(GalleryPager 同款壳,不走 MainPager 五 Tab)
 * 依赖:MoodApi.list(按月拉 couple 双方记录);MoodRecordInfo(快照三列+createTime);
 *      ChatTime(chatHHmm/daypartLabel);AppState(currentDate/moodRecordColor/monthParts/monthShift/
 *      civilDateKeyGmt8/todayDayIndexGmt8);Utils(toast);RouterModule(返回);ThemeStore
 * 事件:返回钮 closePage;月份钮切月重拉;失败态「重试」重拉当月
 * 功能:
 *  - 页内自持月份与记录(remember),不碰 MoodStore.moodRecords/displayMonth——心情页月历缓存
 *    零耦合,本页翻月不影响月历展示月(两套状态各管各的视图)
 *  - 按 moodDate 字符串分组:日期倒序(最新一天在上)、组内 createTime/id 升序(记录顺序,
 *    同日两条 = 我+TA,uk_user_date 约束一天每人最多一条);组头居中胶囊 = ChatPage 日期条同款
 *    (今天/昨天/M月d日,跨年补年份)
 *  - 组内行:40dp emoji 方块(记录色 12% 洗底)+ moodLabel + 我/TA AppTag + 时段徽标
 *    (清晨/上午/中午/下午/傍晚/晚上/深夜 + HH:mm);createTime<=0 徽标整层隐藏(不造假数据)。
 *    时段取当日首次记录时刻:服务端 uk_user_date 覆盖写不更新 create_time(§4,抽查实证)
 *  - 取色 moodRecordColor(color 快照 → 存量 moodType 旧映射 → 灰,D-14 铁律不变)
 *  - 切月失败保留现月缓存 + toast(§7.4 只读降级);首拉失败进失败重试态(四态分家,不造假空态)
 */
@Page("moodTimeline", supportInLocal = true)
internal class MoodTimelinePager : BasePager() {

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            MoodTimelinePage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }
}

@Composable
internal fun MoodTimelinePage(pager: Pager) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    val records = remember { mutableStateListOf<MoodRecordInfo>() }
    var month by remember { mutableStateOf("") } // yyyy-MM;空串 = 未初始化,首帧取当月
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }

    fun load(target: String) {
        scope.launch {
            loading = true
            val result = MoodApi.list(pager, target)
            if (result is ApiResult.Ok) {
                records.clear()
                records.addAll(MoodRecordInfo.listFrom(result.dataArray))
                month = target
                loadFailed = false
            } else if (month.isEmpty()) {
                // 首拉失败且无内容:失败重试态(四态分家,不造假空态)
                loadFailed = true
            } else {
                // 浏览中切月失败:保留现月缓存,toast 提示(§7.4 只读降级)
                Utils.bridgeModule(pager.pagerId).toast("切换失败,请重试")
            }
            loading = false
        }
    }

    fun shiftMonth(delta: Int) {
        val next = monthShift(month, delta)
        if (next.isNotEmpty() && !loading) {
            load(next)
        }
    }

    LaunchedEffect(Unit) {
        load(currentDate(pager).take(7)) // yyyy-MM-dd → yyyy-MM
    }

    // 按 moodDate 字符串分组(月度数据量 ≤ 62 行,每次重组重算零负担,不做 remember 缓存):
    // 日期倒序 = 最新一天在上;组内 createTime/id 升序(记录顺序,同 id 稳定)
    val groups = records
        .groupBy { it.moodDate }
        .map { (date, dayRecords) ->
            date to dayRecords.sortedWith(compareBy({ it.createTime }, { it.id }))
        }
        .sortedByDescending { it.first }

    Column(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxSize()) {
            item {
                AppGradientHeader {
                    Box(
                        modifier = Modifier.size(38.dp)
                            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(19.dp))
                            .clickable {
                                pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIconType.ChevronLeft, Color.White, Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    AppHeaderTitle("心情日签", "每一天的心情都值得收藏")
                }
            }
            item {
                // 月份切换条:双钮等宽 + SpaceBetween,标题自然居中(月历卡月份行同构)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = AppTheme.PageHPadding, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TimelineMonthNavButton(AppIconType.ChevronLeft) { shiftMonth(-1) }
                    val (year, monthNum) = monthParts(month)
                    Text(
                        if (year == 0) "—" else "${year}年${monthNum}月",
                        color = AppTheme.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    TimelineMonthNavButton(AppIconType.ChevronRight) { shiftMonth(1) }
                }
            }
            when {
                loading -> item { TimelineLoading() }
                loadFailed -> item {
                    TimelineLoadFailed { load(month.ifEmpty { currentDate(pager).take(7) }) }
                }
                records.isEmpty() -> item { MoodMonthEmpty() }
                else -> items(
                    groups,
                    key = { it.first }, // moodDate 全月唯一,稳定 key
                    contentType = { "mood-day" },
                ) { (date, dayRecords) ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = AppTheme.PageHPadding)
                            .padding(top = 6.dp),
                    ) {
                        // 组头日期胶囊:单节点(背景+文字同节点,无 clickable),ChatPage 日期条同款
                        Text(
                            moodGroupLabel(date),
                            fontSize = 10.sp,
                            color = AppTheme.TextSecondary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .background(AppTheme.TextTertiary.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        dayRecords.forEachIndexed { index, record ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            MoodRecordRow(record)
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp + navBarSafePaddingDp())) }
        }
    }
}

/**
 * MoodRecordRow — 单条心情记录行:白卡 + 40dp emoji 方块(记录色 12% 洗底)+ 标签行
 * (我/TA AppTag + 时段徽标「晚上 21:30」);emoji 快照缺省不造假,方块只着色不画符
 */
@Composable
private fun MoodRecordRow(record: MoodRecordInfo) {
    val isMine = record.userId == SessionStore.userId
    val rowColor = moodRecordColor(record, AppTheme.TextTertiary)
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp)
                .background(rowColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (record.emoji.isNotEmpty()) {
                Text(record.emoji, fontSize = 18.sp)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                record.moodLabel.ifEmpty { "心情" },
                color = AppTheme.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTag(
                    if (isMine) "我" else "TA",
                    bgColor = rowColor.copy(alpha = 0.12f),
                    textColor = rowColor,
                )
                if (record.createTime > 0L) {
                    Spacer(modifier = Modifier.width(6.dp))
                    // 时段徽标:同日重复记为覆盖写,create_time = 当日首次记录时刻(§4)
                    AppTag(
                        "${daypartLabel(record.createTime)} ${chatHHmm(record.createTime)}",
                        bgColor = AppTheme.TextTertiary.copy(alpha = 0.08f),
                        textColor = AppTheme.TextSecondary,
                    )
                }
            }
        }
    }
}

/** 月份切换圆钮:白底胶囊,clickable 与 background 同节点(铁律⑤) */
@Composable
private fun TimelineMonthNavButton(icon: AppIconType, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(30.dp)
            .background(AppTheme.CardBg, RoundedCornerShape(15.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, AppTheme.TextSecondary, Modifier.size(16.dp))
    }
}

/** 空月空态:两行文案,语气对齐既有页面空态 */
@Composable
private fun MoodMonthEmpty() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🫧", fontSize = 34.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text("这个月还没有心情记录", color = AppTheme.TextSecondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("去心情页,记下今天的心情吧", color = AppTheme.TextTertiary, fontSize = 11.sp)
    }
}

/** 日期键(yyyy-MM-dd)→ 组头文案:今天/昨天/M月d日(跨年补 yyyy年);空串 = 未知日期 */
private fun moodGroupLabel(date: String): String {
    if (date.isEmpty()) return "未知日期"
    val todayKey = civilDateKeyGmt8(todayDayIndexGmt8())
    if (date == todayKey) return "今天"
    if (date == civilDateKeyGmt8(todayDayIndexGmt8() - 1)) return "昨天"
    val parts = date.split("-")
    val y = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val d = parts.getOrNull(2)?.toIntOrNull() ?: 0
    val todayYear = todayKey.split("-").getOrNull(0)?.toIntOrNull() ?: 0
    return if (y == todayYear) "${m}月${d}日" else "${y}年${m}月${d}日"
}
