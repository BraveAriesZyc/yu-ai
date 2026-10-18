package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.KeyboardOptions
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
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
 *  - 源 = features/couple/BindWidgets.BindInputField(其本体已改薄壳委托本件,既有调用方零改动)
 */
@Composable
internal fun AppInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    containerColor: Color = Color(0xFFF5F6FA),
    trailing: (@Composable () -> Unit)? = null,
    keyboardHeightChange: (KeyboardParams) -> Unit = {},
    maxLines: Int = 1,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .height(48.dp)
            .background(containerColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            placeholderColor = AppTheme.TextTertiary,
            autoFocus = false,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(color = AppTheme.TextPrimary, fontSize = 15.sp),
            keyboardHeightChange = keyboardHeightChange,
        )
        trailing?.invoke()
    }
}
