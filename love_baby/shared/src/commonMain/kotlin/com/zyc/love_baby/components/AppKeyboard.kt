package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.max
import com.tencent.kuikly.core.views.KeyboardParams

/**
 * AppKeyboard — 软键盘避让几何收口(键盘高度状态 + 底部让位公式)
 * 用法:val (keyboardHeight, onKeyboardHeightChange) = rememberKeyboardHeight()
 *      输入条 bottom = inputBarBottomPaddingDp(keyboardHeight.value)
 *      列表尾 Spacer 高 = keyboardLiftBottomDp(keyboardHeight.value, 108.dp)
 * 依赖:navBarSafePaddingDp(同包,pageData.safeAreaInsets);KeyboardParams(Kuikly core views)
 * 事件:rememberKeyboardHeight 的回调对 (KeyboardParams) -> Unit,接 BasicWidget.TextField
 *      的 keyboardHeightChange 参数
 * 功能:
 *  - keyboardLiftBottomDp:键盘顶起量 = 键盘高 + 导航栏安全距 + extra(列表尾 Spacer 让位/
 *    浮层 bottom 避让;extra 惯例 12dp 呼吸)
 *  - inputBarBottomPaddingDp:悬浮输入条 bottom = max(静止位, 键盘顶起位)——静止位
 *    rest(默认 86dp,聊天/待办输入条离底高度)+ 导航栏;键盘开着取键盘高 + 12dp 呼吸
 *    (源 = ChatPage 844-857 / TodoPage 309-310 逐字同构公式)
 *  - rememberKeyboardHeight:「var keyboardHeight by remember { mutableStateOf(0f) } +
 *    onKeyboardHeightChange = remember { ... }」样板收口(源 = TodoPage 133-143;remember 化
 *    回调满足铁律⑩——勿传裸 lambda,每次重组换新实例会打断 BasicWidget 内部 rememberUpdatedState
 *    以外的监听);0 = 键盘收起
 */
@Composable
internal fun keyboardLiftBottomDp(keyboardHeight: Float, extra: Dp = 12.dp): Dp =
    keyboardHeight.dp + navBarSafePaddingDp() + extra

@Composable
internal fun inputBarBottomPaddingDp(keyboardHeight: Float, rest: Dp = 86.dp, extra: Dp = 12.dp): Dp =
    max(rest + navBarSafePaddingDp(), keyboardHeight.dp + navBarSafePaddingDp() + extra)

/** 键盘高度状态对:(只读高度 State, 回调)。高度 0 = 收起;回调 remember 化,实例组合期稳定 */
@Composable
internal fun rememberKeyboardHeight(): Pair<State<Float>, (KeyboardParams) -> Unit> {
    val keyboardHeight = remember { mutableStateOf(0f) }
    val onKeyboardHeightChange = remember {
        { params: KeyboardParams -> keyboardHeight.value = params.height }
    }
    return keyboardHeight to onKeyboardHeightChange
}
