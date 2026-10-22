package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.tencent.kuikly.compose.animation.animateColorAsState
import com.tencent.kuikly.compose.animation.core.FastOutSlowInEasing
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.BoxWithConstraints
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxHeight
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppBottomBar — 浮岛式底部导航栏(圆角悬浮 + 滑动渐变胶囊 + 弹性图标动画)
 * 用法:AppBottomBar(selectedIndex, pillPosition = { ... }, badges, onSelect = { ... }),外层 Box 对齐 BottomCenter
 * 依赖:AppIcons/AppTheme;入参:selectedIndex 当前选中下标(驱动图标高亮阈值翻转),
 *      pillPosition 胶囊页位置 lambda(= pagerState.targetPage,MainPager 提供),
 *      badges 下标→角标数
 * 事件:onSelect(index) 切换回调
 * 功能:选中胶囊阈值翻转/点切时同帧瞬移(与图标高亮同步,零动画帧)、图标弹性缩放、文字颜色过渡、数字角标
 * 性能:胶囊位置只在 SlidingPill 的 graphicsLayer 内读 pillPosition()——Kuikly 对 layerBlock 做
 *       快照观察,翻转帧仅重算胶囊层参数写原生 transform(零重组/零重测/零动画帧),胶囊瞬移
 *       不重组整条底栏;拖拽中胶囊不逐帧追页面(连续跟随真机实测掉帧,见 SlidingPill 注);
 *       图标 scale/tint 动画状态只在叶子 Composable 内读取;
 *       位移走 graphicsLayer 纯变换(不走布局 offset,避免每帧原生 re-layout)
 */
data class TabItem(val icon: AppIconType, val label: String)

val MainTabs = listOf(
    TabItem(AppIconType.Home, "首页"),
    TabItem(AppIconType.Todo, "待办"),
    TabItem(AppIconType.Heart, "心情"),
    TabItem(AppIconType.Chat, "聊天"),
    TabItem(AppIconType.Mine, "我的"),
)

@Composable
fun AppBottomBar(
    selectedIndex: Int,
    pillPosition: () -> Float,
    badges: Map<Int, Int> = emptyMap(),
    onSelect: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(64.dp)
            // 1dp 发丝描边略去:环需要两个独立原生视图(同链双 background 互相覆盖),
            // 7% 透明度黑描边视觉增益≈0,不值得加嵌套结构
            .background(Color(0xF7FFFFFF), RoundedCornerShape(24.dp))
    ) {
        val itemW = maxWidth / MainTabs.size
        val pillW = itemW - 20.dp

        // 滑动渐变胶囊:先组合 → 垫在图标/文字下层(z-order 关键,放后面会盖住文字)
        SlidingPill(
            modifier = Modifier.align(Alignment.TopStart),
            itemW = itemW,
            pillW = pillW,
            pillPosition = pillPosition,
        )

        // 图标行:后组合,绘制在胶囊之上;不读任何动画状态 → 动画帧不会重组到这里
        Row(modifier = Modifier.fillMaxSize()) {
            MainTabs.forEachIndexed { index, tab ->
                NavItem(
                    selected = index == selectedIndex,
                    tab = tab,
                    badge = badges[index] ?: 0,
                    modifier = Modifier.weight(1f),
                    onClick = { if (index != selectedIndex) onSelect(index) },
                )
            }
        }
    }
}

/**
 * SlidingPill — 滑动渐变胶囊(targetPage 瞬移驱动,零动画帧)
 * 性能关键:graphicsLayer lambda 内读取 pillPosition()(= pagerState.targetPage)——Kuikly 对
 * layerBlock 做快照观察(NodeCoordinator.updateLayerParameters 的 observeReads),翻转帧仅重算
 * 胶囊层参数写原生 transform:零重组/零重测/零布局/零动画帧。
 * ⚠ 胶囊只在 targetPage 阈值翻转/点切时瞬移(与图标高亮同帧同步),勿改回连续跟随
 * (currentPage+currentPageOffsetFraction 逐帧跟手,2026-10-16 真机实测拖拽掉帧:拖拽中每帧
 * 层参数重算都要过与五页重摆/边界全量重测共享的 Kotlin 队列,页面位移在原生层不受影响、
 * 胶囊帧被推迟即肉眼可见);也勿改回 animateFloatAsState(tween)——重新引入插值窗口,
 * 与切 Tab 爆发帧争抢(底栏掉帧根因)。
 * 注意:preferNative 原生插值路径在本项目真机上实测有异常(动画丢失/位置错乱),
 * 暂不使用 —— 见 CLAUDE.md 第 10 条
 */
@Composable
private fun SlidingPill(modifier: Modifier, itemW: Dp, pillW: Dp, pillPosition: () -> Float) {
    val theme = ThemeStore.current
    Box(
        modifier = modifier
            .offset(y = 6.dp)
            .width(pillW)
            .height(48.dp)
            .graphicsLayer {
                val pos = pillPosition().coerceIn(0f, MainTabs.lastIndex.toFloat())
                // itemW * pos 走 Dp.times(Float) 类成员(顶层 Float.times(Dp) 扩展需额外 import,勿用)
                translationX = (itemW * pos + (itemW - pillW) / 2).toPx()
            }
            .background(theme.brandBrush, RoundedCornerShape(18.dp)),
    )
}

@Composable
private fun NavItem(
    selected: Boolean,
    tab: TabItem,
    badge: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 图标缩放:短 tween,Compose 插帧(preferNative 实测异常,暂不启用)
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "iconScale",
    )
    // 颜色过渡:Canvas tint/文字颜色不在 Native 支持列表,走 Compose 插帧,
    // 用短 tween(150ms)把帧数压到最低,避免与切 Tab 的页面组合抢 Kuikly 线程
    val tint by animateColorAsState(
        targetValue = if (selected) Color.White else AppTheme.TextSecondary,
        animationSpec = tween<Color>(durationMillis = 150),
        label = "tint",
    )

    Column(
        modifier = modifier.fillMaxHeight().clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            AppIcon(
                type = tab.icon,
                tint = tint,
                modifier = Modifier.size(25.dp).graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            )
            if (badge > 0) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd)
                        .offset(x = 7.dp, y = (-3).dp)
                        .size(14.dp)
                        .background(AppTheme.Danger, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badge > 99) "99+" else "$badge",
                        color = Color.White,
                        fontSize = 8.sp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.size(3.dp))
        Text(
            text = tab.label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
