package com.zyc.love_baby.features.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.tencent.kuikly.compose.foundation.background
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
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
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
import com.zyc.love_baby.features.chat.todayDayIndexGmt8
import com.zyc.love_baby.state.AnniversaryStore
import com.zyc.love_baby.state.CoupleBindState
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.StatsStore
import com.zyc.love_baby.state.TodoStore
import com.zyc.love_baby.state.nextAnniversaryCountdownDays

/**
 * HomePage — 首页:主题渐变问候头部 + 悬浮待办统计(含纪念日倒数第 4 列)+ 快捷入口 + 恋爱灵感卡片
 * 用法:HomePage(pager, onNavigate = { tabIndex -> ... })(MainPager 传 pager 供拉取/路由)
 * 依赖:ThemeStore(主题)/TodoStore/StatsStore/AnniversaryStore(统计,第 4 列倒数纯函数算);
 *      components 通用组件(AppGradientHeader/AppStatBar/AppQuickAction 等);RouterModule(开独立页)
 * 事件:onNavigate(0..4) 跳转底部导航对应 Tab;纪念日列 onClick 打开独立页
 * 功能:纯展示型首页;统计实时来自 TodoStore;bindState 驱动纪念日整表首拉(§7.4)
 */
@Composable
fun HomePage(pager: Pager, onNavigate: (Int) -> Unit) {
    val theme = ThemeStore.current
    // 纪念日首拉(bindState 驱动,TodoPage LaunchedEffect 同款):首页第 4 统计列消费
    // AnniversaryStore,未绑定/登出静默跳过;独立页增删写穿透即时反映,跨端靠回前台/
    // 进独立页拉取收敛(§7.4 拉取触发点)
    LaunchedEffect(CoupleStore.bindState) {
        if (CoupleStore.bindState != CoupleBindState.BOUND) return@LaunchedEffect
        AnniversaryStore.loadList(pager)
    }
    LazyColumn(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        item {
            HeaderWithStats(pager)
        }
        item {
            // 快捷入口
            Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
                Spacer(modifier = Modifier.height(36.dp))
                AppSectionTitle("快捷入口")
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppQuickAction(
                        label = "去待办",
                        iconTint = Color(0xFF6C7BFF),
                        iconBg = Color(0xFFEDEFFF),
                        icon = AppIconType.Todo,
                        modifier = Modifier.weight(1f),
                    ) { onNavigate(1) }
                    AppQuickAction(
                        label = "送心意",
                        iconTint = Color(0xFFFF5A8C),
                        iconBg = Color(0xFFFFEAF1),
                        icon = AppIconType.Heart,
                        modifier = Modifier.weight(1f),
                    ) { onNavigate(2) }
                    AppQuickAction(
                        label = "去聊天",
                        iconTint = Color(0xFF34C77B),
                        iconBg = Color(0xFFE6F8EF),
                        icon = AppIconType.Chat,
                        modifier = Modifier.weight(1f),
                    ) { onNavigate(3) }
                    AppQuickAction(
                        label = "我的",
                        iconTint = Color(0xFFFF9A56),
                        iconBg = Color(0xFFFFF1E8),
                        icon = AppIconType.Mine,
                        modifier = Modifier.weight(1f),
                    ) { onNavigate(4) }
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
                    )
                    AppFeatureCard(
                        title = "约会灵感",
                        subtitle = "周末去哪儿玩",
                        icon = AppIconType.Star,
                        tileColors = listOf(theme.brandStart, theme.brandEnd),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(96.dp + navBarSafePaddingDp())) } // 底部导航避让
    }
}

@Composable
private fun HeaderWithStats(pager: Pager) {
    val theme = ThemeStore.current
    Box(modifier = Modifier.fillMaxWidth()) {
        // 渐变头部
        AppGradientHeader(height = 122.dp, topSpacing = 16.dp) {
            AppHeaderTitle("Hi,宝贝", "今天也要甜甜的哦", titleSize = 22.sp, subtitleSize = 12.sp)
            // 铃铛通知入口
            AppIconTile(
                AppIconType.Bell,
                size = 38.dp,
                bgColor = Color.White.copy(alpha = 0.25f),
                corner = 19.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            // 头像
            AppAvatar("宝", size = 38.dp, bgColor = Color.White.copy(alpha = 0.35f), fontSize = 15.sp)
        }
        // 悬浮统计卡(实时来自待办 + StatsStore 真数据,「统计真数据化」里程碑;
        // 第 4 列纪念日倒数 = M5,值来自 AnniversaryStore 端上纯函数,D-17)
        AppStatBar(
            items = listOf(
                AppStatItem("${TodoStore.todos.size}", "今日待办"),
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
                    onClick = { if (CoupleStore.bindState == CoupleBindState.BOUND) openAnniversaryList(pager) },
                ),
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
                .offset(y = 20.dp)
                .padding(horizontal = AppTheme.PageHPadding),
        )
    }
}

/** 路由打开纪念日独立页(新页压栈;MinePage.openWishTimeline 同款 try/catch 包 acquireModule) */
private fun openAnniversaryList(pager: Pager) {
    try {
        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
            .openPage("anniversaryList", JSONObject())
    } catch (_: Exception) {
    }
}
