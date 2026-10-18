package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.size
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
 * AppCalendar — 月历通用小件(双标记环点 / 月份导航行)
 * 用法:AppRingDot(theme.brandStart, 7.dp)(「我」实心点语义其实用裸 Box,环才是本件);
 *      AppMonthNav("2026年10月", onPrev = { shift(-1) }, onNext = { shift(1) })
 * 依赖:AppIcons/AppTheme(TextSecondary 箭头色/TextPrimary 月文字/CardBg 环内芯)
 * 事件:AppMonthNav 的 onPrev/onNext(翻月)
 * 功能:
 *  - AppRingDot:TA 空心环(源 = CheckinPage.PartnerRingDot / MoodPage.MoodDayCell 语义,
 *    9dp 外圈 + 5dp 内芯嵌套双圆)——⚠ 原生 border 描边不跟随圆角(画出来是直角框,
 *    AppCheckbox 注释同知识),环必须用「外圆 + 同底色内芯圆」嵌套画法,内芯色默认 CardBg
 *    (落在卡片上即「挖洞」效果);落在异色底上时显式传 innerColor
 *  - AppMonthNav:‹ 年月 › 导航行(源 = CheckinPage.MonthNavArrow 30dp / MoodPage.
 *    MonthNavButton 26dp / MoodTimelinePage.TimelineMonthNavButton 30dp 白底,参数漂移
 *    归一:buttonSize 默认 30dp、iconSize 默认 16dp,月文字 15sp Bold;左右双 Spacer(weight)
 *    保证文字绝对居中——SpaceBetween 布局依赖两侧等宽才居中,本件用 Spacer 更稳)
 */
@Composable
internal fun AppRingDot(
    color: Color,
    size: Dp = 9.dp,
    innerColor: Color = AppTheme.CardBg,
    innerSize: Dp = 5.dp,
) {
    Box(
        modifier = Modifier.size(size)
            .background(color, RoundedCornerShape((size.value / 2f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(innerSize)
                .background(innerColor, RoundedCornerShape((innerSize.value / 2f).dp)),
        )
    }
}

/** 月份导航行:‹ label ›,左右箭头 + label 绝对居中 */
@Composable
internal fun AppMonthNav(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 30.dp,
    buttonBg: Color = Color.Transparent,
    iconSize: Dp = 16.dp,
    labelTextSize: TextUnit = 15.sp,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppMonthNavArrow(AppIconType.ChevronLeft, onPrev, buttonSize, buttonBg, iconSize)
        Spacer(modifier = Modifier.weight(1f))
        Text(label, color = AppTheme.TextPrimary, fontSize = labelTextSize, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        AppMonthNavArrow(AppIconType.ChevronRight, onNext, buttonSize, buttonBg, iconSize)
    }
}

/** 月份切换箭头钮:background(含透明)与 clickable 同节点(铁律⑤,命中测试以背景视图为准) */
@Composable
private fun AppMonthNavArrow(
    icon: AppIconType,
    onClick: () -> Unit,
    size: Dp,
    bg: Color,
    iconSize: Dp,
) {
    Box(
        modifier = Modifier.size(size)
            .background(bg, RoundedCornerShape((size.value / 2f).dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, AppTheme.TextSecondary, Modifier.size(iconSize))
    }
}
