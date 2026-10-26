package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.maxLength
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.views.KeyboardParams
import com.zyc.love_baby.TextField

/**
 * AppInputPill — 底部输入条白胶囊(列表页悬浮输入岛的输入域,TodoPage/WishListPage 同型收口)
 * 用法:AppInputPill(inputText, { inputText = it.take(MAX) }, "想做点什么?", MAX,
 *      modifier = Modifier.weight(1f), onKeyboardHeightChange = onKeyboardHeightChange)
 * 依赖:AppTheme(TextTertiary 占位/TextPrimary 正文/CardBg 胶囊底);BasicWidget.TextField
 *      (String placeholder/keyboardHeightChange 键盘高度回调/maxLength 渲染层拒超限)
 * 事件:onValueChange 回吐输入(截断与否由调用方自理);onKeyboardHeightChange 键盘高度
 *      透传(浮岛顶起 + 列表尾 Spacer 让位由调用方消费)
 * 功能:
 *  - 白 R22 胶囊(Box 载底,铁律 15:背景挂 Box、文字控件作子节点)+ TextField 13sp/
 *    maxLines 3(限 3 行竖向生长,不在 22dp 胶囊里无限撑高)/内距 14·13(单行≈44dp 与
 *    侧旁添加钮等高)/autoFocus=false(铁律 13①)
 *  - 长度双保险:调用方 onValueChange 里 take + 组件内 Modifier.maxLength(原生层拒绝超限
 *    输入,官方定式防光标跳变/回吞);上限经 maxLength 参数传入
 *  - 发送/添加钮与业务派发(清输入、滚底跟随)留调用方——本件只管输入域
 */
@Composable
internal fun AppInputPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
    onKeyboardHeightChange: (KeyboardParams) -> Unit = {},
) {
    Box(
        modifier = modifier
            .background(AppTheme.CardBg, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            autoFocus = false,
            placeholderColor = AppTheme.TextTertiary,
            textStyle = TextStyle(color = AppTheme.TextPrimary, fontSize = 13.sp),
            maxLines = 3,
            keyboardHeightChange = onKeyboardHeightChange,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp)
                .maxLength(maxLength),
        )
    }
}
