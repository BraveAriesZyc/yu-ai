package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.ColumnScope
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.CircularProgressIndicator
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppIconTile — 图标底座(圆角方块,纯色/渐变底 + 居中图标)
 * 用法:AppIconTile(AppIconType.Heart, size = 46.dp, bgBrush = 渐变, iconSize = 22.dp)
 *       ⚠️ 放在可点击容器(卡片/行)内时必须传 onClick 透传父级回调 —— Kuikly 命中测试以
 *       背景视图为准,本组件自带背景会吞掉父级 clickable 的点击(见 AppCheckbox 注释)
 * 依赖:AppIcons
 * 事件:onClick 可空,为 null 时点击被本组件吞掉(不会传给父级)
 * 功能:
 *  - bgColor/bgBrush 二选一,内部统一归一成一个 remember 缓存的 Brush(纯色 = 同色渐变,视觉一致),
 *    背景节点常驻,颜色/渐变切换只重写属性,无节点增删残留
 *  - 首页快捷入口 40dp、灵感卡 46dp、头部铃铛 38dp(圆角传 size/2 即圆形)、心愿卡磨砂座 46dp
 */
@Composable
fun AppIconTile(
    icon: AppIconType,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    bgColor: Color? = null,
    bgBrush: Brush? = null,
    corner: Dp = 14.dp,
    iconTint: Color = Color.White,
    iconSize: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
) {
    val brush = remember(bgColor, bgBrush) {
        bgBrush ?: Brush.linearGradient(listOf(bgColor ?: Color.Transparent, bgColor ?: Color.Transparent))
    }
    Box(
        modifier = modifier.size(size)
            .background(brush, RoundedCornerShape(corner))
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick.invoke() }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, iconTint, Modifier.size(iconSize))
    }
}

/**
 * AppQuickAction — 快捷入口卡(白卡 + 圆角图标底座 + 标签,竖排居中)
 * 用法:AppQuickAction("去待办", AppIconType.Todo, tint, bg, Modifier.weight(1f)) { ... }
 * 依赖:AppIconTile/AppTheme
 * 事件:onClick
 * 功能:首页快捷入口;几枚横排时调用方用 Row + weight 均分;
 *       badge>0 时图标右上角红点数字(未读,>99 显 99+;M11 聊天入口用)
 */
@Composable
fun AppQuickAction(
    label: String,
    icon: AppIconType,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .background(AppTheme.CardBg, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIconTile(icon, size = 40.dp, bgColor = iconBg, corner = 14.dp, iconTint = iconTint, iconSize = 20.dp, onClick = onClick)
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(14.dp)
                        .background(AppTheme.Danger, RoundedCornerShape(7.dp))
                        // 铁律7:红点是盖在 AppIconTile(带底可点)之上的带背景子视图,不透传点击
                        // 会吞掉 14dp 区域的点击(AppCheckbox 事故同型),必须挂同一回调
                        .clickable { onClick() },
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = AppTheme.TextPrimary, fontSize = 12.sp)
    }
}

/**
 * AppFeatureCard — 灵感/推荐卡(白卡横排:渐变图底座 + 标题/副标题)
 * 用法:AppFeatureCard("今日情话", "每天一句小甜蜜", AppIconType.Heart, Modifier.weight(1f), tileColors = 渐变)
 * 依赖:AppIconTile/AppTheme
 * 事件:onClick
 * 功能:tileColors(渐变)与 tileColor(纯色)二选一;底座 46dp 圆角 14,图标 22dp
 */
@Composable
fun AppFeatureCard(
    title: String,
    subtitle: String,
    icon: AppIconType,
    modifier: Modifier = Modifier,
    tileColors: List<Color>? = null,
    tileColor: Color? = null,
    onClick: () -> Unit = {},
) {
    val tileBrush = remember(tileColors) { tileColors?.let { Brush.linearGradient(it) } }
    Row(
        modifier = modifier
            .background(AppTheme.CardBg, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconTile(icon, size = 46.dp, bgColor = tileColor, bgBrush = tileBrush, corner = 14.dp, iconSize = 22.dp, onClick = onClick)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, color = AppTheme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(3.dp))
            Text(subtitle, color = AppTheme.TextSecondary, fontSize = 11.sp)
        }
    }
}

/**
 * AppSelectableCard — 可选中卡片(选中时品牌色描边环)
 * 用法:AppSelectableCard(selected, corner = 20.dp, onClick = { ... }) { 卡片内容 }
 *       ⚠️ content 里的背景子视图(图标底座/图位等)会吞点击,须传 onClick 透传(见 AppIconTile)
 * 依赖:ThemeStore(brandStart)/AppTheme
 * 事件:onClick(整卡点击,一般切 selected)
 * 功能:
 *  - 描边环 = 外层 Box 当环 + 内层白卡,分属两个原生视图(同链双 background 互相覆盖;
 *    原生 border 描边不跟随圆角)
 *  - 环背景常驻未选中切透明(条件增删背景节点会残留上一次的方色块)
 *  - ⚠️ 环宽常驻占位(padding(ringWidth) 恒在):选中/未选中外层尺寸完全一致,
 *    切换选中不会引起列表/页面位移(条件 padding 会让内容缩放 4dp,LazyColumn 整体抖动)
 *  - 内层圆角恒为 corner - ringWidth,选中环出现时视觉圆角一致
 */
@Composable
fun AppSelectableCard(
    selected: Boolean,
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    ringWidth: Dp = 2.dp,
    onClick: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                if (selected) ThemeStore.current.brandStart else Color.Transparent,
                RoundedCornerShape(corner),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ringWidth)
                .background(AppTheme.CardBg, RoundedCornerShape((corner.value - ringWidth.value).dp))
                .clickable { onClick() },
        ) {
            content()
        }
    }
}

/**
 * AppIconButton — 品牌渐变圆形按钮(添加/发送等悬浮操作钮)
 * 用法:AppIconButton(AppIconType.Plus, onClick = { ... });
 *      提交中反馈:enabled = !sending, loading = sending(禁用时刷 40% 透明渐变且不挂 clickable)
 * 依赖:ThemeStore(brandBrush)/AppIcons/kuikly material3 CircularProgressIndicator
 * 事件:onClick(enabled=false 时不响应)
 * 功能:默认 44dp 圆形(圆角 = size/2);Todo 添加钮 44dp/图标 22,Chat 发送钮同款;
 *      enabled=false 渐变整体降透明(背景常驻只重写属性,铁律⑦)+ 卸掉 clickable 静默吞点;
 *      loading=true 图标位换官方加载圈(动画状态只在叶内读,铁律10②)
 */
@Composable
fun AppIconButton(
    icon: AppIconType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    iconTint: Color = Color.White,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val theme = ThemeStore.current
    // 铁律⑤:Brush remember 缓存,禁用态 40% 透明渐变仅在 enabled/主题变化时重建
    val buttonBrush = remember(theme.brandBrush, enabled) {
        if (enabled) {
            theme.brandBrush
        } else {
            Brush.linearGradient(
                listOf(theme.brandStart.copy(alpha = 0.4f), theme.brandEnd.copy(alpha = 0.4f)),
            )
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .background(buttonBrush, RoundedCornerShape((size.value / 2f).dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                color = iconTint,
                strokeWidth = 2.dp,
            )
        } else {
            AppIcon(icon, iconTint, Modifier.size(iconSize))
        }
    }
}
