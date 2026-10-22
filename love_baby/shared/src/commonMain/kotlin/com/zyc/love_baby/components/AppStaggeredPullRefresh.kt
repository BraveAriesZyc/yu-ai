package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.gestures.awaitEachGesture
import com.tencent.kuikly.compose.foundation.gestures.awaitFirstDown
import com.tencent.kuikly.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.input.pointer.PointerEventPass
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.input.pointer.positionChangeIgnoreConsumed
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * AppStaggeredPullRefresh — 瀑布流(LazyVerticalStaggeredGrid)下拉刷新通用件(M24)
 * 用法:LazyVerticalStaggeredGrid(state = gridState,
 *            modifier = ...staggeredPullRefresh(gridState, { refreshing },
 *                         onPull = { pullPx.value = it },
 *                         onRelease = { launchPullRefresh(scope, { refreshing = it }, pager) { fetchFirst() } }), ...)
 *        // 根 Box 内悬浮指示器(独立 @Composable=重组边界,逐帧下拉只重组本组件不传页):
 *        StaggeredPullIndicator(pullPx, refreshing, thresholdPx, Modifier.align(Alignment.TopCenter))
 * 依赖:铁律 13④ 被动观察器范式(AppGestures.dragDismissKeyboard 全仓单源先例);launchPullRefresh
 *      (AppPullRefresh 最短驻留+失败 toast 单源);铁律 13④ 与手势相关注意事项见下
 * 事件:到顶下拉越阈松手触发 onRelease(业务刷新);onPull 逐帧上报拉距(px,阻尼后,松手回 0)
 * 功能:
 *  - 为什么自绘:2.29 官方 pullToRefreshItem 只挂 LazyListScope(scrollState 参数硬类型
 *    LazyListState,反编译锤实),挂不上 staggered grid——本件用被动观察器手势 + 悬浮胶囊
 *    指示器等价交付(轻提示交互,不做跟手位移的转轮)
 *  - 到顶判定用 gridState.canScrollBackward(勿用 isScrollInProgress,铁律 13④ 同款失灵);
 *    拉起后改下滚(canScrollBackward 翻 true)即放弃本次,拉距归零
 *  - ⚠ 铁律 13④ 三不可妥协点照抄 dragDismissKeyboard:①全程不 consume(原生滚动/惯性/子项
 *    点击无感);②pass=Initial + requireUnconsumed=false + 位移读 positionChangeIgnoreConsumed
 *    (原生滚动事件带预消费标记);③key=gridState(页内稳定实例;勿用逐帧变化值,重组会重启
 *    手势协程);refreshing 经 lambda 读最新值不入 key(刷新态翻转不杀进行中手势)。
 *    手势 handler 内勿用 withTimeoutOrNull(2.29 no-op 存根)
 *  - 阻尼 0.55 + 上限 1.6×阈值(超出即饱和,拉再远不无限跟手);松手未越阈静默归零
 *  - 已知外观风险:iOS 原生 bounce 可能与指示器双移动(主目标端 Android 无此问题;二期按
 *    contentOffset 校正)
 */

/** 触发刷新的下拉阈值(松手时拉距 ≥ 此值才刷新) */
internal const val PULL_REFRESH_THRESHOLD_DP = 64

/** 指示器收口阈值 px(由调用方以 LocalDensity 换算传入,与手势判定同值) */
@Composable
internal fun rememberPullRefreshThresholdPx(): Float =
    with(LocalDensity.current) { PULL_REFRESH_THRESHOLD_DP.dp.toPx() }

/**
 * 瀑布流下拉刷新手势(被动观察器,不消费任何事件):
 * onPull 逐帧上报拉距(0 = 已归零),onRelease 仅在「到顶下拉越阈松手且不在刷新中」触发
 */
@Composable
internal fun Modifier.staggeredPullRefresh(
    gridState: LazyStaggeredGridState,
    refreshing: () -> Boolean,
    onPull: (pullPx: Float) -> Unit,
    onRelease: () -> Unit,
): Modifier {
    val thresholdPx = rememberPullRefreshThresholdPx()
    return pointerInput(gridState) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            var tracking = false
            var armed = false
            var pull = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (tracking && armed && !refreshing()) onRelease()
                    if (pull != 0f) onPull(0f)
                    break
                }
                if (!tracking) {
                    // 未跟踪态:列表到顶 + 不在刷新中才开始跟踪(列表中部下拉 = 原生滚动,无感)
                    if (!refreshing() && !gridState.canScrollBackward) tracking = true
                } else if (gridState.canScrollBackward) {
                    // 拉起后改下滚(或刷新中列表恢复可上滚):放弃本次,拉距归零
                    tracking = false
                    armed = false
                    pull = 0f
                    onPull(0f)
                } else {
                    val dy = change.positionChangeIgnoreConsumed().y
                    pull = (pull + dy * 0.55f).coerceIn(0f, thresholdPx * 1.6f)
                    onPull(pull)
                    armed = pull >= thresholdPx
                }
            }
        }
    }
}

/**
 * 悬浮刷新指示胶囊(挂调用页根 Box TopCenter):独立 @Composable = 重组边界,逐帧下拉只
 * 重组本组件;translationY/alpha 随拉距渐入(拉距回 0 或刷新结束即整件消失)。三层胶囊
 * 写法(铁律 14/15:外层容器居中 → 内层 Box 载 background+padding → Text 子节点)
 */
@Composable
internal fun StaggeredPullIndicator(
    pullPx: State<Float>,
    refreshing: Boolean,
    thresholdPx: Float,
    modifier: Modifier = Modifier,
) {
    val pull = pullPx.value
    if (pull <= 0f && !refreshing) return
    Box(
        modifier = modifier.graphicsLayer {
            translationY = pull * 0.6f
            alpha = (pull / (thresholdPx * 0.6f)).coerceIn(0.3f, 1f)
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                when {
                    refreshing -> "刷新中…"
                    pull >= thresholdPx -> "松开刷新"
                    else -> "下拉刷新"
                },
                color = Color.White,
                fontSize = 12.sp,
            )
        }
    }
}
