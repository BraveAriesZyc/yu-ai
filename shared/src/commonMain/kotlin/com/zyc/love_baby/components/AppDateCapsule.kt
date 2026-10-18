package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.TextUnit
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppDateCapsule — 弱灰日期胶囊(聊天日期分隔条/墓碑条/时间线组头日期通用件)
 * 用法:AppDateCapsule(chatDayLabel(msg.createTime))(列表内整宽居中,默认形态);
 *      AppDateCapsule(chatDayLabelFriendly(w.createTime))(时间线组头同款)
 * 依赖:AppTheme(TextTertiary 底/TextSecondary 字)
 * 事件:无(纯展示,无 clickable)
 * 功能:
 *  - 三层安全写法(单源化注释,2026-10-17 二轮真机反馈定稿):①外层 Box(fillMaxWidth)+
 *    contentAlignment=Center 容器居中(ColumnScope.align 的 ParentData 通道真机不可靠;
 *    centered=false 时不 fill,由调用方外层决定位置);②背景+padding 挂内层 Box、Text 作
 *    子节点——背景直挂 Text 会落在原生 TextView 上(可能被拉宽,文字 start 偏左不居中),
 *    全 App 真机验证过的胶囊(贴纸/发送/重试)全是背景挂 Box 写法
 *  - 视觉常数(源 = ChatPage 日期分隔条):TextTertiary 12% 底 + 9dp 圆角 + 横 10/竖 3 内距 +
 *    10sp 字;墓碑条变体调 bgAlpha=0.10f、fontSize=11.sp、textColor=TextTertiary、
 *    verticalPadding=4.dp
 */
@Composable
internal fun AppDateCapsule(
    text: String,
    modifier: Modifier = Modifier,
    bgAlpha: Float = 0.12f,
    fontSize: TextUnit = 10.sp,
    textColor: Color = AppTheme.TextSecondary,
    bg: Color = AppTheme.TextTertiary,
    verticalPadding: Dp = 3.dp,
    centered: Boolean = true,
) {
    Box(
        modifier = if (centered) modifier.fillMaxWidth() else modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .background(bg.copy(alpha = bgAlpha), RoundedCornerShape(9.dp))
                .padding(horizontal = 10.dp, vertical = verticalPadding),
        ) {
            Text(text, fontSize = fontSize, color = textColor)
        }
    }
}
