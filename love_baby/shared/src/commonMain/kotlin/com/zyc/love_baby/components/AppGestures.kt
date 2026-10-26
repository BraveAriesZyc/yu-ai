package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.gestures.awaitEachGesture
import com.tencent.kuikly.compose.foundation.gestures.awaitFirstDown
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.focus.FocusManager
import com.tencent.kuikly.compose.ui.input.pointer.PointerEventPass
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.input.pointer.positionChangeIgnoreConsumed
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.setTimeout
import kotlin.math.abs

/**
 * dragDismissKeyboard — Modifier 扩展:竖向拖拽收键盘(Q3 微信语义,键盘开着拖列表即收)
 * 用法:LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().dragDismissKeyboard(), ...)
 * 依赖:LocalFocusManager(Kuikly compose runtime);无外部资源
 * 事件:命中判定后 clearFocus(触发键盘收起);不回调业务
 * 功能:
 *  - 被动手势观察器:不消费任何事件,原生滚动/惯性/子项点击/下拉刷新全部无感
 *  - ⚠️ 铁律 13④ 三条不可妥协点(2.29 源码锤实,本扩展为全仓单源实现,原 ChatPage/WishListPage/
 *    TodoPage 各自手写件后续统一直替;改动前必读):
 *    ①全程不调 change.consume()——Compose 侧消费移动才会 preventTouch 阻断原生触摸,不消费
 *      = 原生滚动/惯性/子项点击/下拉刷新全部无感;
 *    ②pass 一律 Initial + requireUnconsumed=false,位移读 positionChangeIgnoreConsumed()——
 *      原生层接管滚动后事件带预消费标记,普通 positionChange() 对已消费事件返回 Zero 会检测失灵;
 *    ③竖向位移越 touchSlop 且竖向占优(dy>dx)才收——横滑翻页由 MainPager 切 Tab 的 clearFocus
 *      承接,轻点气泡/按钮不越 slop 不收。
 *  - key=focusManager(页内稳定实例;勿用逐帧变化值做 key,重组会重启手势协程);手势 handler
 *    内勿用 withTimeoutOrNull(2.29 该函数是 no-op 存根)
 */
@Composable
internal fun Modifier.dragDismissKeyboard(): Modifier {
    val focusManager = LocalFocusManager.current
    return pointerInput(focusManager) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            var dx = 0f
            var dy = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.positionChangeIgnoreConsumed()
                dx += abs(delta.x)
                dy += abs(delta.y)
                if (dy > viewConfiguration.touchSlop && dy > dx) {
                    focusManager.clearFocus()
                    break
                }
            }
        }
    }
}

/**
 * runAfterBlur — 铁律 16 单源:先清焦,action 迟一拍执行(原 ProfileEditFields/GatePanel/
 * MoodOptionManageOverlay/ChatPage 四份同体,2026-10-26 收口)。
 * 机制:compose clearFocus 只同步清 compose 焦点,原生 blur 经 setTimeout(0) 批处理异步落地;
 * action 与 blur 同回合执行会竞速——持焦输入框带焦点被移除时,Android 把窗口焦点转给待办页
 * 输入框(五页全驻留),Pager 滚去露出它 = 主界面乱滚(2026-10-18 ProfileEditOverlay 真机复现,
 * 1570d07 根因)。action 推迟一拍入同一 MessageQueue,按入队序必然排在 blur 之后,卸载/切换时
 * 树中已无持焦视图。
 * 命名中性勿叫 dismiss:关浮层之外,ChatPage 语音/文本模式切换、GatePanel 登录面板切换同用。
 * ⚠ 仅限 MainPager 横向 Pager 域内的浮层/面板(@Page 独立子页无待办页驻留问题,勿滥用);
 * ⚠ 输入框祖先链不得加 focusProperties exit 拦截(ProfileEditFields 原注)
 */
internal fun Pager.runAfterBlur(focusManager: FocusManager, action: () -> Unit) {
    focusManager.clearFocus()
    setTimeout(0, action)
}
