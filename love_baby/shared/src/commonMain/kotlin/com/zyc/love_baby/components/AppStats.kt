package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
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
 * AppStatItem — 统计项(数值 + 标签)
 * onClick 非空 = 整列可点(跳详情页用);默认 null 不可点,零调用点破坏
 */
data class AppStatItem(
    val value: String,
    val label: String,
    val onClick: (() -> Unit)? = null,
)

/**
 * AppStatBar — 数据统计条(白底 20dp 圆角卡,均分多列,列间细分隔线)
 * 用法:AppStatBar(listOf(AppStatItem("36", "心愿完成"), ...))
 *       头部悬浮统计用法:外层 Box align(BottomCenter).offset(y = 22.dp) + modifier 传水平边距
 * 依赖:AppTheme
 * 事件:AppStatItem.onClick(item 级可点,热区=整列;默认 null 不可点)
 * 功能:valueSize/dividerHeight 可调(首页 20sp/28dp,我的页 18sp/26dp)
 */
@Composable
fun AppStatBar(
    items: List<AppStatItem>,
    modifier: Modifier = Modifier,
    valueSize: TextUnit = 20.sp,
    labelSize: TextUnit = 11.sp,
    dividerHeight: Dp = 28.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                AppStatDivider(dividerHeight)
            }
            Column(
                // clickable 与 background 同节点(铁律⑤):透明 background 常驻不分支,可点性由 enabled 表达
                modifier = Modifier.weight(1f)
                    .background(Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable(enabled = item.onClick != null) { item.onClick?.invoke() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(item.value, color = AppTheme.TextPrimary, fontSize = valueSize, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.label, color = AppTheme.TextSecondary, fontSize = labelSize)
            }
        }
    }
}

@Composable
private fun AppStatDivider(height: Dp) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(height)
            .background(AppTheme.Divider, RoundedCornerShape(0.5.dp)),
    )
}
