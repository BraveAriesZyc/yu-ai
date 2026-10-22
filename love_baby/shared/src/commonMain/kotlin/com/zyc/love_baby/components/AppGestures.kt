package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.gestures.awaitEachGesture
import com.tencent.kuikly.compose.foundation.gestures.awaitFirstDown
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.input.pointer.PointerEventPass
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.input.pointer.positionChangeIgnoreConsumed
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
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
