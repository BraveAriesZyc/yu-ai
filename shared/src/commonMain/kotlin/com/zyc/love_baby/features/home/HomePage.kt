package com.zyc.love_baby.features.home

import androidx.compose.runtime.Composable
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
import com.zyc.love_baby.state.CoupleBindState
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.StatsStore
import com.zyc.love_baby.state.TodoStore

/**
 * HomePage — 首页:主题渐变问候头部 + 悬浮待办统计 + 快捷入口 + 恋爱灵感卡片
 * 用法:HomePage(onNavigate = { tabIndex -> ... })
 * 依赖:ThemeStore(主题)/TodoStore(统计)/components 通用组件(AppGradientHeader/AppStatBar/AppQuickAction 等)
 * 事件:onNavigate(0..4) 跳转底部导航对应 Tab
 * 功能:纯展示型首页;统计实时来自 TodoStore
 */
@Composable
fun HomePage(onNavigate: (Int) -> Unit) {
    val theme = ThemeStore.current
    LazyColumn(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        item {
            HeaderWithStats()
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
private fun HeaderWithStats() {
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
        // 悬浮统计卡(实时来自待办 + StatsStore 真数据,「统计真数据化」里程碑)
        AppStatBar(
            items = listOf(
                AppStatItem("${TodoStore.todos.size}", "今日待办"),
                AppStatItem("${TodoStore.doneCount}", "已完成"),
                AppStatItem(
                    if (CoupleStore.bindState == CoupleBindState.BOUND) "${StatsStore.bindDays}" else "--",
                    "在一起(天)",
                ),
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
                .offset(y = 20.dp)
                .padding(horizontal = AppTheme.PageHPadding),
        )
    }
}
