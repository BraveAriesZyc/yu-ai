package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.TextUnit
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.zyc.love_baby.Button

/**
 * AppPrimaryButton — 品牌渐变主按钮(表单提交/面板主操作通用件)
 * 用法:AppPrimaryButton("登 录", submitting = submitting) { submit() }
 *      (onClick 收尾,支持尾随 lambda;宽度恒 fillMaxWidth,高度/圆角/字号可调)
 * 依赖:BasicWidget 同包 Button(pointerInput 与 background 同节点,命中可靠);
 *      ThemeStore.current.brandBrush(懒缓存渐变,铁律⑧)
 * 事件:onClick( submitting=true 期间拦截);submitting 只换文案不改背景
 * 功能:
 *  - submitting 期间只拦点击与换 busyText 文案,不改背景(条件换 brush 会在原生层残留旧值,
 *    AuthPrimaryButton/BindPrimaryButton 同款约定,铁律④衍生)
 *  - 源 = features/couple/BindWidgets.BindPrimaryButton(与 features/auth/AuthWidgets.
 *    AuthPrimaryButton 逐字同构),二域同名原件改薄壳委托本件,既有调用方零改动
 */
@Composable
internal fun AppPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    submitting: Boolean = false,
    busyText: String = "请稍候…",
    height: Dp = 48.dp,
    corner: Dp = 24.dp,
    fontSize: TextUnit = 16.sp,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier.fillMaxWidth()
            .height(height)
            .background(ThemeStore.current.brandBrush, RoundedCornerShape(corner)),
        onClick = { if (!submitting) onClick() },
    ) {
        Text(
            if (submitting) busyText else text,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
        )
    }
}
