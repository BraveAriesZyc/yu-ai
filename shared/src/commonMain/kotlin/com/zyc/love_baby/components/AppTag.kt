package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.TextUnit
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppTag — 轻量文字标签(圆角色块 + 单行文字)
 * 用法:AppTag("日常", bgColor = tagColor.copy(alpha = 0.12f), textColor = tagColor)
 * 依赖:无
 * 事件:无(需要点击时外层包 clickable Box)
 * 功能:待办分类标签/头部计数胶囊/状态小标签通用;字号与内边距可调;fontWeight 透传
 *      (默认 null 不加粗,存量调用零影响)
 */
@Composable
fun AppTag(
    text: String,
    modifier: Modifier = Modifier,
    bgColor: Color,
    textColor: Color,
    fontSize: TextUnit = 10.sp,
    corner: Dp = 5.dp,
    hPadding: Dp = 6.dp,
    vPadding: Dp = 2.dp,
    fontWeight: FontWeight? = null,
) {
    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(corner))
            .padding(horizontal = hPadding, vertical = vPadding),
    ) {
        Text(text, color = textColor, fontSize = fontSize, fontWeight = fontWeight)
    }
}
