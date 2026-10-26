package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppDialogButtonRow — 弹层/卡片底部双钮行(左次右主,等宽 42dp/R21 胶囊,间距 12dp)
 * 用法:置于卡片内容尾部;左钮固定灰底(AppTheme.GrayButtonBrush)+TextPrimary 14sp Medium;
 *      右钮三参由调用方定——底刷 rightBrush(主操作惯例 ThemeStore.current.brandBrush,
 *      2026-10-18 真机定案:单色 brandEnd 是渐变浅端,默认蜜桃主题下白字发白像禁用态,
 *      全仓主按钮一律 brandBrush 渐变——AppCards 登录钮/CheckinPage 打卡钮同款)、
 *      字色 rightTextColor(默认 White;灰底右钮传 TextPrimary)、字重 rightFontWeight
 *      (**无默认值,逐调用点显式传**——Medium/Bold 现场分布对半,默认值必埋静默回归)。
 *      ⚠ 忙碌闸(提交在飞拦双钮)与右钮文案切换(如「保存中…」)由调用方在回调/文案侧自理,
 *      本组件不做 busy 语义(各现场闸口/文案规则不同,收进组件反而埋语义分叉)
 * 依赖:AppTheme(SecondaryBg/TextPrimary/GrayButtonBrush/DangerButtonBrush 渐变单例);
 *      红底右钮传 AppTheme.DangerButtonBrush(Danger 实色归一同色渐变,铁律⑦)
 * 事件:onLeft/onRight 直通(无内置拦截)
 * 功能:2026-10-26 自 AppConfirmOverlay / WishListPage 心愿详情双组 / ProfileEditFields
 *      保存行收编(四处 42dp/R21/12dp 间距逐字同构;WishListPage 单钮全宽形态与 ChatPage
 *      无定高变体不在收编范围)
 */
@Composable
fun AppDialogButtonRow(
    leftText: String,
    rightText: String,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    rightBrush: Brush,
    rightFontWeight: FontWeight,
    modifier: Modifier = Modifier,
    leftBrush: Brush = AppTheme.GrayButtonBrush,
    rightTextColor: Color = Color.White,
) {
    Row(modifier = modifier) {
        Box(
            modifier = Modifier.weight(1f)
                .height(42.dp)
                .background(leftBrush, RoundedCornerShape(21.dp))
                .clickable(onClick = onLeft),
            contentAlignment = Alignment.Center,
        ) {
            Text(leftText, color = AppTheme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier.weight(1f)
                .height(42.dp)
                .background(rightBrush, RoundedCornerShape(21.dp))
                .clickable(onClick = onRight),
            contentAlignment = Alignment.Center,
        ) {
            Text(rightText, color = rightTextColor, fontSize = 14.sp, fontWeight = rightFontWeight)
        }
    }
}
