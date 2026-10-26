package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.heightIn
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.KeyboardOptions
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.input.KeyboardType
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.views.KeyboardParams
import com.zyc.love_baby.TextField

/**
 * AppInputField — 浅灰底圆角输入行(表单输入通用件)
 * 用法:AppInputField(nickname, { nickname = it }, "输入昵称",
 *      keyboardType = KeyboardType.Number, keyboardHeightChange = { ... })
 * 依赖:BasicWidget 同包 TextField(placeholder 为 String,autoFocus 一律显式 false,防弹窗
 *      即弹键盘);AppTheme(TextTertiary 占位色/TextPrimary 正文色)
 * 事件:onValueChange 透传;keyboardHeightChange 键盘高度回调透传(浮动输入条顶起用);
 *      trailing 槽(白卡右侧小 icon/小钮用,如「获取验证码」)
 * 功能:
 *  - 「容器定高 48dp + 内容居中」铁律(原生 gravity workaround,单源化注释):原生
 *    EditText/TextArea gravity 恒 TOP|START(KRTextAreaView 硬编码 setGravity(51),textAlign
 *    属性只改水平位),固定高直接压在 TextField 上会把文字/placeholder 顶到上缘——固定高
 *    48dp 挂在行容器上、TextField 自适应内容行高由 CenterVertically 居中(全仓输入框统一此模式)
 *  - 「视口 ≥ 单行真实排版」铁律(框内文字平移修复):原生 EditText 拖拽(AOSP Touch.onTouchEvent)
 *    垂直钳制上限 = 文本排版高 − 视口高,只要视口比排版矮几个像素(引擎给原生视图的高度 =
 *    影子测量 + 8% 行高冗余,真机含 fontPadding 的实际行排版可能超出它)就能拖着文字在框内
 *    平移——两个字也滚。heightIn 下限抬到 1.7×字号(≥ 1.45em 行排版 + 余量)后钳制上限恒 0,
 *    物理上不可再滚;sp→dp 走 LocalDensity(随系统大字体同步缩放),且仍 < 48dp 容器不破居中铁律
 *  - 源 = features/couple/BindWidgets.BindInputField(其本体已改薄壳委托本件,既有调用方零改动)
 */
@Composable
internal fun AppInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    containerColor: Color = AppTheme.InputBg,
    trailing: (@Composable () -> Unit)? = null,
    keyboardHeightChange: (KeyboardParams) -> Unit = {},
    maxLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE, // 透传 BasicWidget.TextField(渲染层拒超限输入,勿在 onValueChange take)
) {
    // 单行真实排版下限(1.7×字号 ≥ 含 fontPadding 的 1.45em 行排版 + 余量),压住原生
    // EditText 拖拽钳制上限(排版高 − 视口高)恒 ≤ 0 → 框内文字拖不动 = 平移修复,见头注释
    val oneRealLineMin = with(LocalDensity.current) { 15.sp.toDp() * 1.7f }
    Row(
        modifier = modifier.fillMaxWidth()
            .height(48.dp)
            .background(containerColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            modifier = Modifier.weight(1f)
                .heightIn(min = oneRealLineMin),
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            placeholderColor = AppTheme.TextTertiary,
            autoFocus = false,
            maxLines = maxLines,
            maxLength = maxLength,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(color = AppTheme.TextPrimary, fontSize = 15.sp),
            keyboardHeightChange = keyboardHeightChange,
        )
        trailing?.invoke()
    }
}
