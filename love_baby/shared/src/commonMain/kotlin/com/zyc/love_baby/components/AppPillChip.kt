package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.TextUnit
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppPillChip — 轻量文字小钮(单行文字胶囊,实底色或渐变底)
 * 用法:AppPillChip("重试", bg = ThemeStore.current.brandStart.copy(alpha = 0.12f),
 *      textColor = ThemeStore.current.brandStart, fontWeight = FontWeight.Medium) { onRetry() };
 *      渐变底形态传 bgBrush(如 brandBrush),bg/bgBrush 同传时 bgBrush 优先
 * 依赖:AppTheme(TextTertiary 等);无动画(纯条件渲染)
 * 事件:onClick(收尾参数,支持尾随 lambda)
 * 功能:
 *  - background(常驻)与 clickable 同节点(铁律⑤);Text 作 Box 子节点——背景直挂
 *    Text 会落原生 TextView 失控(铁律 14/15,本件两节点形态即合规:背景挂 Box、Text 是子节点)
 *  - 视觉常数取仓内同型手写件(发布钮/重试胶囊/「换一换」等 5 处)归中:圆角 14/横 12 竖 6
 *    内距/字号 12;具体页面已有更强语义常数(重试胶囊 16dp 圆角 18/8 内距 13sp、「换一换」
 *    12dp 圆角 10/4 内距 11sp)由参数覆写,不强行统一已验收视觉
 *  - color/brush 重载禁混用(铁律⑦):bg/bgBrush 双参仿 AppIconTile——内部 remember 归一
 *    成单个 Brush(纯色转同色 linearGradient),节点恒走 brush 重载,调用点两种底色类型互切
 *    也不会触发重载翻转冻结渐变层;2026-10-26 收编 ChatPage 重试胶囊/MinePage「知道了」
 *    (原手写 Box+brandBrush 形态)
 */
@Composable
internal fun AppPillChip(
    text: String,
    bg: Color = Color.Transparent, // 纯色底;渐变底走 bgBrush(全缺省=透明胶囊,调用方自查)
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    fontSize: TextUnit = 12.sp,
    textColor: Color = Color.White,
    fontWeight: FontWeight? = null,
    bgBrush: Brush? = null,
    onClick: () -> Unit,
) {
    // 铁律⑦/铁律⑤:背景归一单 Brush(remember 缓存禁每次重建),bgBrush 传值优先
    val brush = remember(bg, bgBrush) {
        bgBrush ?: Brush.linearGradient(listOf(bg, bg))
    }
    Box(
        modifier = modifier
            .background(brush, RoundedCornerShape(corner))
            .clickable(onClick = onClick)
            .padding(contentPadding),
    ) {
        Text(text, color = textColor, fontSize = fontSize, fontWeight = fontWeight)
    }
}
