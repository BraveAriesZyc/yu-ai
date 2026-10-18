package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.TextUnit
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppAvatar — 文字头像(圆底 + 居中单字/短词);avatarUrl 非空时升级为远程图形态
 * 用法:AppAvatar("宝");渐变底传 bgBrush,纯色底传 bgColor;远程头像传 avatarUrl(文件服务 URL)
 * 依赖:kuikly compose coil3 rememberAsyncImagePainter(KRImageAdapter 承载 http 加载)
 * 事件:无(需要点击时外层包 clickable Box)
 * 功能:
 *  - 颜色/渐变统一归一成一个 Brush,背景节点常驻(纯色 = 同色渐变,视觉一致),
 *    避免 bgColor/bgBrush 分支增删背景节点造成原生残留属性
 *  - avatarUrl 非空:背景圆上叠远程图(圆角裁切,Crop 中心裁满——上传链路 centerInside
 *    不裁方,非方图 Fit 会 letterbox 露底色带,2026-10-18 改 Crop;全仓调用点均圆形头像展示);
 *    加载中/失败时 painter 空绘,背景圆透出充当占位——背景节点不分支,只切内层内容
 *  - 5 套主题下白色半透明底头像均可用;聊天气泡小头像传 size = 34.dp
 */
@Composable
fun AppAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    bgColor: Color = Color.White.copy(alpha = 0.35f),
    bgBrush: Brush? = null, // 传了优先于 bgColor(渐变头像)
    textColor: Color = Color.White,
    fontSize: TextUnit = 15.sp,
    avatarUrl: String = "", // 非空走远程图形态(空串 = 首字头像,兼容存量调用点)
) {
    val brush = remember(bgColor, bgBrush) {
        bgBrush ?: Brush.linearGradient(listOf(bgColor, bgColor))
    }
    val shape = RoundedCornerShape((size.value / 2f).dp)
    Box(
        modifier = modifier.size(size)
            .background(brush, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl.isNotEmpty()) {
            Image(
                modifier = Modifier.fillMaxSize().clip(shape),
                painter = rememberAsyncImagePainter(avatarUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(text, color = textColor, fontSize = fontSize, fontWeight = FontWeight.Bold)
        }
    }
}
