package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.RowScope
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.TextUnit
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppSectionTitle — 区块标题(品牌渐变竖条 + 加粗标题 + 可选尾部说明)
 * 用法:AppSectionTitle("快捷入口");带尾部提示:AppSectionTitle("心愿墙", trailing = { Text("点卡片许愿", ...) })
 * 依赖:ThemeStore(brandBrush)/AppTheme
 * 事件:无
 * 功能:leading 可换成图标(如心情页的心形);trailing 一般放弱化说明文字
 */
@Composable
fun AppSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 16.sp,
    leading: @Composable RowScope.() -> Unit = {
        Box(
            modifier = Modifier.size(width = 4.dp, height = 14.dp)
                .background(ThemeStore.current.brandBrush, RoundedCornerShape(2.dp)),
        )
    },
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(modifier = Modifier.width(6.dp))
        Text(title, color = AppTheme.TextPrimary, fontSize = titleSize, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        trailing()
    }
}
