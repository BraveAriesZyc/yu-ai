package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * AppGhostIconButton — 透明/浅底小图标钮(月导航箭头/头部小操作钮通用件)
 * 用法:AppGhostIconButton(AppIconType.ChevronLeft, onClick = { closePage() })
 * 依赖:AppIcons/AppTheme(TextSecondary 默认着色)
 * 事件:onClick
 * 功能:
 *  - background(含透明)与 clickable 同节点(铁律⑤,命中测试以背景视图为准——透明底也要挂,
 *    无背景的 clickable 节点会被表面背景子视图吞掉点击,AppCheckbox 真机实测同知识)
 *  - 圆钮直径 size(默认 30dp),icon 着色 tint(默认 TextSecondary)、边长 iconSize(默认 18dp,
 *    CheckinPage.MonthNavArrow 30/18 生产常数;26/14 与 30/16 漂移参数可覆写)
 *  - 浅底变体传 bg(如 Color.White.copy(alpha = 0.25f)),透明底默认
 */
@Composable
internal fun AppGhostIconButton(
    icon: AppIconType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    bg: Color = Color.Transparent,
    tint: Color = AppTheme.TextSecondary,
    iconSize: Dp = 18.dp,
) {
    Box(
        modifier = modifier.size(size)
            .background(bg, RoundedCornerShape((size.value / 2f).dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, tint, Modifier.size(iconSize))
    }
}
