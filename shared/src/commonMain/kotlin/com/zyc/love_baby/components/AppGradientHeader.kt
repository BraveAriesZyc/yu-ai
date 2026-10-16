package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.RowScope
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.TextUnit
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppGradientHeader — 页面通用渐变头部(品牌渐变 + 底部 28dp 圆角 + 状态栏避让 + 水平 16dp 内边距)
 * 用法:AppGradientHeader { AppHeaderTitle("标题", "副标题"); ...尾部控件 }
 *       height 传固定值时头部高度 = statusBar + height(Home/Mine 式,下方悬浮统计卡);
 *       默认 null 为包裹内容式(Todo/Mood/Chat 式),上下各留 topSpacing/bottomSpacing
 * 依赖:ThemeStore(brandBrush)/statusBarPaddingDp
 * 事件:无(点击行为由 content 内部控件自理)
 * 功能:统一各页头部骨架;content 为一行 Row 内容,标题列用 AppHeaderTitle 占 weight(1f)
 */
@Composable
fun AppGradientHeader(
    modifier: Modifier = Modifier,
    height: Dp? = null,
    topSpacing: Dp = 14.dp,
    bottomSpacing: Dp = 14.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.height(statusBarPaddingDp() + height) else Modifier)
            .background(
                brush = ThemeStore.current.brandBrush,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            )
            .padding(horizontal = AppTheme.PageHPadding),
    ) {
        Spacer(modifier = Modifier.height(statusBarPaddingDp()))
        Spacer(modifier = Modifier.height(topSpacing))
        Row(verticalAlignment = Alignment.CenterVertically) {
            content()
        }
        if (height == null) {
            Spacer(modifier = Modifier.height(bottomSpacing))
        }
    }
}

/**
 * AppHeaderTitle — 头部标题列(白字标题 + 可选副标题),放进 AppGradientHeader 的 content
 * 用法:AppGradientHeader { AppHeaderTitle("心情", "把想要的,说给TA听"); ... }
 * 依赖:无
 * 事件:无
 * 功能:占满 Row 剩余宽度(weight 1f),标题加粗白字 + 90% 透明度副标题
 */
@Composable
fun RowScope.AppHeaderTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 20.sp,
    subtitleSize: TextUnit = 11.sp,
) {
    Column(modifier = modifier.weight(1f)) {
        Text(title, color = Color.White, fontSize = titleSize, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.9f), fontSize = subtitleSize)
        }
    }
}
