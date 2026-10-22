@file:OptIn(ExperimentalFoundationApi::class)

package com.zyc.love_baby

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.extension.keyboardHeightChange
import com.tencent.kuikly.compose.extension.placeHolder
import com.tencent.kuikly.compose.foundation.ExperimentalFoundationApi
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.text.BasicTextField
import com.tencent.kuikly.compose.foundation.text.KeyboardActions
import com.tencent.kuikly.compose.foundation.text.KeyboardOptions
import com.tencent.kuikly.compose.foundation.text.maxLength
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.focus.FocusRequester
import com.tencent.kuikly.compose.ui.focus.focusRequester
import com.tencent.kuikly.compose.ui.focus.onFocusChanged
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.SolidColor
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.layout.onGloballyPositioned
import com.tencent.kuikly.compose.ui.layout.onSizeChanged
import com.tencent.kuikly.compose.ui.layout.positionInRoot
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.text.TextLayoutResult
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.IntSize
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.views.KeyboardParams
import kotlinx.coroutines.delay

@Composable
internal fun Button(
    onClick: () -> Unit = {},
    onClick2: (Offset) -> Unit = {},
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit) = {}
) {
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    val onClickUpdate = rememberUpdatedState(onClick)
    val onClick2Update = rememberUpdatedState(onClick2)
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .onGloballyPositioned {
                rootPosition = it.positionInRoot()
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // 获取点击的相对于 Box 的坐标
                    val clickedPositionInBox = offset
                    // 转换为相对于根节点的位置
                    val clickedPositionInRoot = with(density) {
                        Offset(
                            (clickedPositionInBox.x + rootPosition.x).toDp().value,
                            (clickedPositionInBox.y + rootPosition.y).toDp().value
                        )
                    }
                    onClickUpdate.value.invoke()
                    onClick2Update.value.invoke(clickedPositionInRoot)
                }
            } then modifier, contentAlignment = Alignment.Center) {
        content()
    }
}

internal class Border(
    val lineWidth: Dp,
    val lineStyle: BorderStyle,
    val color: Color
)

internal fun Modifier.border(border: Border) =
    this.border(width = border.lineWidth, color = border.color)

@Composable
internal fun TextField(
    modifier: Modifier = Modifier,
    value: String = "",
    placeholder: String = "",
    autoFocus: Boolean = false, // 默认不自动弹键盘(键盘铁律①;需聚焦的调用方显式传 true)
    onValueChange: (String) -> Unit,
    onBlur: () -> Unit = {},
    onFocus: () -> Unit = {},
    keyboardHeightChange: (KeyboardParams) -> Unit = {},
    textStyle: TextStyle = TextStyle.Default,
    placeholderColor: Color? = null,
    cursorBrush: Brush = SolidColor(Color.Black),
    maxLines: Int = Int.MAX_VALUE,
    // 最大输入长度(渲染层直接拒绝超限输入):官方禁止在 onValueChange 里 take() 截断——文本回退
    // 会造成光标跳变/回吞字符(core-components.md #5);Int.MAX_VALUE = 不限
    maxLength: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var lastFocus by remember { mutableStateOf(false) }

    val currentOnBlur by rememberUpdatedState(onBlur)
    val currentOnFocus by rememberUpdatedState(onFocus)
    val currentKeyboardHeightChange by rememberUpdatedState(keyboardHeightChange)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnTextLayout by rememberUpdatedState(onTextLayout)

    val updatedModifier = modifier
        .keyboardHeightChange(currentKeyboardHeightChange)
        .focusRequester(focusRequester)
        .onFocusChanged {
            if (it.isFocused) {
                if (!lastFocus) {
                    currentOnFocus()
                    lastFocus = true
                }
            } else if (lastFocus) {
                currentOnBlur()
                lastFocus = false
            }
        }
        .let {
            if (placeholderColor != null) {
                it.placeHolder(placeholder, placeholderColor)
            } else it
        }
        .let { if (maxLength != Int.MAX_VALUE) it.maxLength(maxLength) else it }
    BasicTextField(
        modifier = updatedModifier,
        value = value,
        onValueChange = currentOnValueChange,
        textStyle = textStyle,
        maxLines = maxLines,
        onTextLayout = currentOnTextLayout,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        cursorBrush = cursorBrush
    )

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(100)
            focusRequester.requestFocus()
        }
    }
}

@Composable
internal fun Modifier.onSizeChangedDp(
    onSizeChanged: (IntSize) -> Unit
): Modifier {
    val density = LocalDensity.current
    return this.onSizeChanged {
        with(density) {
            val dpSize = IntSize(
                width = it.width.toDp().value.toInt(),
                height = it.height.toDp().value.toInt()
            )
            onSizeChanged(dpSize)
        }
    }
}
