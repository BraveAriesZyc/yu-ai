package com.zyc.love_baby.components

import com.tencent.kuikly.compose.foundation.lazy.LazyListScope
import com.tencent.kuikly.compose.foundation.lazy.LazyListState
import com.tencent.kuikly.compose.material3.PullToRefreshState
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.pullToRefreshItem
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.state.nowEpochMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AppPullRefresh — 列表下拉刷新通用件(最短驻留 + 失败 toast 收口)
 * 用法:LazyColumn(state = listState) {
 *          AppPullRefreshItem(pullState, listState, scope, { refreshing = it }, pager) {
 *              fetchFirst() // suspend,回 Boolean:成功 false 落失败 toast
 *          }
 *          ...
 *       }
 * 依赖:官方 pullToRefreshItem(com.tencent.kuikly.compose.material3,2.29——只挂 LazyListScope,
 *      scrollState 参数必须是 LazyColumn 自身的 LazyListState,否则手势判定错位);nowEpochMs
 *      (commonMain 禁 System.currentTimeMillis);AppFeedback.toast(失败 toast)
 * 事件:onRefresh 下拉触发(挂起,回 Boolean=本次刷新是否成功);失败 toast failToast 文案
 * 功能:
 *  - 最短驻留(2026-10-17 真机反馈「松手立马恢复原状,不知道好没好」,原 Moment/Wish/
 *    Inspiration 三页各自手写,收口单源):本地服务端几十 ms 即回,指示器还没展开完就开始
 *    收回动画,「刷新中…」一闪而过——请求耗时不足 REFRESH_MIN_VISIBLE_MS(600ms)时补齐
 *    再收,让状态至少完整显示一拍
 *  - 失败 toast(成功不 toast,数据自己会更新,肉眼可辨);成功后列表态由调用方 onRefresh
 *    内自行落(fetchFirst 清空重填等),本组件不碰列表数据
 *  - setRefreshing 透传 pullState 同源布尔(var refreshing by remember 的回写口,驱动指示器
 *    展开/收回);ChatPage 不走本件(其刷新语义是「加载更早历史」+noMore 提示,文案与分支不同)
 */
internal const val REFRESH_MIN_VISIBLE_MS = 600L

/**
 * 刷新执行单源(M24 从 AppPullRefreshItem 抽出,瀑布流下拉刷新 AppStaggeredPullRefresh 同用):
 * 最短驻留(请求耗时不足 REFRESH_MIN_VISIBLE_MS 补齐再收,「刷新中…」至少完整显示一拍)+
 * 失败 toast(成功不 toast,数据自己会更新,肉眼可辨);setRefreshing 回写调用方 refreshing 布尔
 */
internal fun launchPullRefresh(
    scope: CoroutineScope,
    setRefreshing: (Boolean) -> Unit,
    pager: Pager,
    onRefresh: suspend () -> Boolean,
    failToast: String = "刷新失败，请重试",
) {
    setRefreshing(true)
    scope.launch {
        val t0 = nowEpochMs()
        val ok = onRefresh()
        val elapsed = nowEpochMs() - t0
        if (elapsed < REFRESH_MIN_VISIBLE_MS) delay(REFRESH_MIN_VISIBLE_MS - elapsed)
        if (!ok) AppFeedback.toast(pager, failToast)
        setRefreshing(false)
    }
}

internal fun LazyListScope.AppPullRefreshItem(
    pullState: PullToRefreshState,
    listState: LazyListState,
    scope: CoroutineScope,
    setRefreshing: (Boolean) -> Unit,
    pager: Pager,
    onRefresh: suspend () -> Boolean,
    refreshingLabel: String = "刷新中…",
    idleLabel: String = "下拉刷新",
    failToast: String = "刷新失败，请重试",
) {
    pullToRefreshItem(
        state = pullState,
        onRefresh = { launchPullRefresh(scope, setRefreshing, pager, onRefresh, failToast) },
        scrollState = listState,
    ) { _, isRefreshing, _ ->
        Text(
            if (isRefreshing) refreshingLabel else idleLabel,
            color = AppTheme.TextTertiary,
            fontSize = 12.sp,
        )
    }
}
