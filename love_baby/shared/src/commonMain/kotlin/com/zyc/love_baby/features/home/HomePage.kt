package com.zyc.love_baby.features.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.openLovePage
import com.zyc.love_baby.components.AppAvatar
import com.zyc.love_baby.components.AppFeatureCard
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconTile
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppQuickAction
import com.zyc.love_baby.components.AppSectionTitle
import com.zyc.love_baby.components.AppStatBar
import com.zyc.love_baby.components.AppStatItem
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.state.todayDayIndexGmt8
import com.zyc.love_baby.features.profile.PROFILE_WHO_MINE
import com.zyc.love_baby.features.profile.openProfilePage
import com.zyc.love_baby.state.AnniversaryStore
import com.zyc.love_baby.state.ChatStore
import com.zyc.love_baby.state.CheckinStore
import com.zyc.love_baby.state.CoupleBindState
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.StatsStore
import com.zyc.love_baby.state.TodoStore
import com.zyc.love_baby.state.nextAnniversaryCountdownDays

/**
 * HomePage — 首页:主题渐变问候头部 + 悬浮待办统计(含纪念日倒数第 4 列)+ 功能快捷入口 + 恋爱灵感卡片
 * 用法:HomePage(pager)(MainPager 传 pager 供拉取/路由)
 * 依赖:ThemeStore(主题)/TodoStore/StatsStore/AnniversaryStore/CheckinStore(统计与打卡条);
 *      components 通用组件(AppGradientHeader/AppStatBar/AppQuickAction 等);RouterModule(开独立页)
 * 事件:四快捷入口(纪念日提醒/每日打卡/我们的相册/恋爱清单,2026-10-18 自 MinePage 迁入)
 *      与纪念日统计列 onClick 均经 RouterModule 打开对应独立页;铃铛 → onGoChat()(M18 死入口
 *      接轻功能:跳聊天 Tab,红点=ChatStore.unreadCount)
 * 功能:纯展示型首页;统计实时来自 TodoStore;bindState 驱动纪念日整表首拉(§7.4)
 */
@Composable
fun HomePage(
    pager: Pager,
    myNickname: String = "",
    myAvatar: String = "",
    onGoChat: () -> Unit = {},
) {
    val theme = ThemeStore.current
    // 纪念日首拉(bindState 驱动,TodoPage LaunchedEffect 同款):首页第 4 统计列消费
    // AnniversaryStore,未绑定/登出静默跳过;独立页增删写穿透即时反映,跨端靠回前台/
    // 进独立页拉取收敛(§7.4 拉取触发点)
    LaunchedEffect(CoupleStore.bindState) {
        if (CoupleStore.bindState != CoupleBindState.BOUND) return@LaunchedEffect
        AnniversaryStore.loadList(pager)
        // 今日打卡条(M8):summary 供条上「连续 N 天」展示;今日双布尔此前仅打卡页 loadMonth
        // 校准,冷启动长时陈旧(M18 修复)——syncToday 拉当月只校准 flags,不动打卡页月缓存
        CheckinStore.loadSummary(pager)
        CheckinStore.syncToday(pager)
    }
    LazyColumn(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        item {
            HeaderWithStats(pager, myNickname, myAvatar, onGoChat)
        }
        item {
            // 今日打卡条(M8):HeaderWithStats 悬浮统计卡之下、快捷入口之上
            Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
                Spacer(modifier = Modifier.height(36.dp)) // 避让悬浮统计卡(offset y=20dp 伸出)
                TodayCheckinEntry(
                    pager = pager,
                    bound = CoupleStore.bindState == CoupleBindState.BOUND,
                )
            }
        }
        item {
            // 功能入口(2026-10-18 真机反馈:原「去待办/送心意/去聊天/我的」与底栏五 Tab 纯重复,
            // 删掉换上原「我的」页四功能——图标/配色/命名与 MinePage 菜单行同源,路由开独立页;
            // 未绑定不设守卫:进页拉取 401 静默落空态,写操作经 toastCoupleResult 提示登录失效,
            // 与 MinePage 菜单行为一致)
            Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
                Spacer(modifier = Modifier.height(24.dp))
                AppSectionTitle("快捷入口")
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppQuickAction(
                        label = "纪念日提醒",
                        iconTint = Color(0xFFFF9A56),
                        iconBg = Color(0xFFFFF1E8),
                        icon = AppIconType.Star,
                        modifier = Modifier.weight(1f),
                    ) { pager.openLovePage("anniversaryList") }
                    AppQuickAction(
                        label = "每日打卡",
                        iconTint = Color(0xFF6C7BFF),
                        iconBg = Color(0xFFEDEFFF),
                        icon = AppIconType.Todo,
                        modifier = Modifier.weight(1f),
                    ) { pager.openLovePage("checkinPage") }
                    AppQuickAction(
                        label = "我们的相册",
                        iconTint = Color(0xFFFF5A8C),
                        iconBg = Color(0xFFFFEAF1),
                        icon = AppIconType.Heart,
                        modifier = Modifier.weight(1f),
                    ) { pager.openLovePage("momentPage") }
                    AppQuickAction(
                        label = "恋爱清单",
                        iconTint = Color(0xFFF7A44A),
                        iconBg = Color(0xFFFFF3E6),
                        icon = AppIconType.Flag,
                        modifier = Modifier.weight(1f),
                    ) { pager.openLovePage("wishListPage") }
                }
            }
        }
        item {
            // 恋爱灵感
            Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
                Spacer(modifier = Modifier.height(24.dp))
                AppSectionTitle("恋爱灵感")
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppFeatureCard(
                        title = "今日情话",
                        subtitle = "每天一句小甜蜜",
                        icon = AppIconType.Heart,
                        tileColors = listOf(Color(0xFFFFB27A), Color(0xFFFF7A8C)),
                        modifier = Modifier.weight(1f),
                        onClick = { pager.openLovePage("quotePage") },
                    )
                    AppFeatureCard(
                        title = "约会灵感",
                        subtitle = "周末去哪儿玩",
                        icon = AppIconType.Star,
                        tileColors = listOf(theme.brandStart, theme.brandEnd),
                        modifier = Modifier.weight(1f),
                        onClick = { pager.openLovePage("inspirationPage") },
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(AppTheme.BottomBarAvoidExtra + navBarSafePaddingDp())) } // 底部导航避让(M18 统一常数)
    }
}

@Composable
private fun HeaderWithStats(pager: Pager, myNickname: String, myAvatar: String, onGoChat: () -> Unit) {
    val theme = ThemeStore.current
    Box(modifier = Modifier.fillMaxWidth()) {
        // 渐变头部
        AppGradientHeader(height = 122.dp, topSpacing = 16.dp) {
            AppHeaderTitle("Hi,宝贝", "今天也要甜甜的哦", titleSize = 22.sp, subtitleSize = 12.sp)
            // 铃铛(M18 死入口接轻功能):点击跳聊天 Tab;红点=ChatStore.unreadCount(M11 同一
            // 事实源),样式照抄 AppQuickAction badge——红点盖在带底可点件上必须挂同一回调(铁律 7)
            Box {
                AppIconTile(
                    AppIconType.Bell,
                    size = 38.dp,
                    bgColor = Color.White.copy(alpha = 0.25f),
                    corner = 19.dp,
                    onClick = onGoChat,
                )
                if (ChatStore.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(14.dp)
                            .background(AppTheme.Danger, RoundedCornerShape(7.dp))
                            .clickable { onGoChat() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (ChatStore.unreadCount > 99) "99+" else "${ChatStore.unreadCount}",
                            color = Color.White,
                            fontSize = 8.sp,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            // 头像(2026-10-18 资料详情页入口):接真实昵称/头像(MainPager 传参),点击进自己资料页;
            // 空值回退「宝」字——原写死「宝」是 M12 遗留,今随 profilePage 一并解决
            Box(modifier = Modifier.clickable { openProfilePage(pager, PROFILE_WHO_MINE) }) {
                AppAvatar(
                    myNickname.trim().take(1).ifEmpty { "宝" },
                    size = 38.dp,
                    bgColor = Color.White.copy(alpha = 0.35f),
                    fontSize = 15.sp,
                    avatarUrl = myAvatar,
                )
            }
        }
        // 悬浮统计卡(实时来自待办 + StatsStore 真数据,「统计真数据化」里程碑;
        // 第 4 列纪念日倒数 = M5,值来自 AnniversaryStore 端上纯函数,D-17)
        AppStatBar(
            items = listOf(
                // 「待办」非「今日待办」(M18 收口):TodoStore.todos 是全量列表(含更早),不限今日
                AppStatItem("${TodoStore.todos.size}", "待办"),
                AppStatItem("${TodoStore.doneCount}", "已完成"),
                AppStatItem(
                    if (CoupleStore.bindState == CoupleBindState.BOUND) "${StatsStore.bindDays}" else "--",
                    "在一起(天)",
                ),
                AppStatItem(
                    // 最近一个将来的纪念日倒数天数;无数据(空表/只剩已过一次性)显 "--"
                    if (CoupleStore.bindState == CoupleBindState.BOUND) {
                        nextAnniversaryCountdownDays(AnniversaryStore.anniversaries, todayDayIndexGmt8())
                            ?.toString() ?: "--"
                    } else {
                        "--"
                    },
                    "纪念日(天)",
                    onClick = { if (CoupleStore.bindState == CoupleBindState.BOUND) pager.openLovePage("anniversaryList") },
                ),
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
                .offset(y = 20.dp)
                .padding(horizontal = AppTheme.PageHPadding),
        )
    }
}

/**
 * 今日打卡条(M8):共同 streak + 今日状态,onClick 开打卡独立页(AnniversaryRow 同款横条视觉)。
 * 今日状态文案吃 CheckinStore.checkedToday/partnerCheckedToday(独立页打卡后回首页即时翻转);
 * summary 只供「连续 N 天」徽标,未拉到/streak=0 隐藏徽标不挡入口
 */
@Composable
private fun TodayCheckinEntry(pager: Pager, bound: Boolean) {
    val theme = ThemeStore.current
    val summary = CheckinStore.summary
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(16.dp))
            .clickable { if (bound) pager.openLovePage("checkinPage") }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 🔥 方块(AnniversaryRow emoji 方块同视觉)
        Box(
            modifier = Modifier.size(38.dp)
                .background(theme.brandStart.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("🔥", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "今日打卡",
                color = AppTheme.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                when {
                    !bound -> "绑定后和 TA 一起打卡"
                    CheckinStore.checkedToday && CheckinStore.partnerCheckedToday -> "今天已共同打卡 ✓"
                    CheckinStore.checkedToday -> "我已打卡,等 TA 中"
                    else -> "还没有打卡,点一下"
                },
                color = AppTheme.TextTertiary,
                fontSize = 11.sp,
            )
        }
        val streak = summary?.commonStreak ?: 0
        if (bound && streak > 0) {
            Text(
                "连续 $streak 天",
                color = theme.brandStart,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        if (bound) {
            AppIcon(AppIconType.ChevronRight, Color(0xFFC8CCD8), Modifier.size(16.dp))
        }
    }
}
