package com.zyc.love_baby.features.wish

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.gestures.awaitEachGesture
import com.tencent.kuikly.compose.foundation.gestures.awaitFirstDown
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.LazyListState
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.maxLength
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.pullToRefreshItem
import com.tencent.kuikly.compose.material3.rememberPullToRefreshState
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.input.pointer.PointerEventPass
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.input.pointer.positionChangeIgnoreConsumed
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.max
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.views.KeyboardParams
import com.zyc.love_baby.TextField
import com.zyc.love_baby.onSizeChangedDp
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.components.AppCheckbox
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconButton
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppTag
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.data.model.WishInfo
import com.zyc.love_baby.features.chat.chatDayLabelFriendly
import com.zyc.love_baby.features.chat.chatHHmm
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.features.mood.TimelineLoadFailed
import com.zyc.love_baby.features.mood.TimelineLoading
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.WishApi
import com.zyc.love_baby.state.SessionStore
import com.zyc.love_baby.state.nowEpochMs
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 文本长度上限(与服务端 AppWishCreateReqVO @Length(max=64) 对齐;maxLength 原生层拒绝 + take 兜底) */
private const val WISH_INPUT_MAX_LENGTH = 64

// 下拉刷新指示器最短可见时长(三页同值:MomentPage/ChatPage/WishListPage,注释互引):
// 本地服务端往返几十 ms,不设下限则「刷新中…」一闪而过,用户无法确认刷新是否发生
private const val REFRESH_MIN_VISIBLE_MS = 600L

// 历史区折叠预览条数(2026-10-18 重构):已完成默认只显最近 N 条,其余收进「展开更早的 X 条」
// ——历史不喧宾夺主,进页视口直达待点亮区才是行动项
private const val WISH_DONE_PREVIEW_COUNT = 3

/**
 * WishListPage — 恋爱清单页(@Page 注册名 "wishListPage"):共享心愿清单 + 勾选完成自动发庆祝消息(M14)
 * 用法:我的页「恋爱清单」菜单 RouterModule.openPage 打开;返回键 closePage。
 *      独立二级页(MomentPager 同款壳,不走 MainPager 五 Tab)
 * 依赖:WishApi/WishInfo(页内自持不进 AppState,D-19g——页销毁即弃,登出/解绑零 reset 接线);
 *      AppCheckbox(勾选);chat.chatDayLabelFriendly(完成时间文案);mood.TimelineLoading/LoadFailed;
 *      couple 组 toast;RouterModule(返回)
 * 事件:返回钮 closePage;点行卡片/勾选框 → 详情弹框(创建/完成时间 + 操作;2026-10-17 交互修订:
 *      完成与取消完成一律经弹框确认,防误点亮——完成会自动发庆祝消息);弹框 [已完成]/[取消完成]
 *      仅对方的行显示(发布者不能点亮自己许的心愿,2026-10-17 权限修订 D-19)→ done(单口双向,
 *      成功用回包真值原位替换本地行 D-19h,失败 toast);删除(仅 wish.userId == 自己显示)→
 *      确认浮层 → 乐观移除失败原位回滚;下拉刷新(官方 pullToRefreshItem 须为首个 item,
 *      MomentPage/ChatPage 先例);添加条 POST 成功才清输入
 * 功能:
 *  - couple 共享清单(双方互见;点亮=对方):服务端 create_time asc, id asc 全量 → 端上两语义
 *    分区(2026-10-18 重构,D-19f 定稿):「已点亮」历史区置顶按 doneTime 倒序+默认折叠只显
 *    最近 3 条(展开钮全量);「待点亮」活跃队列居列表尾(服务端序 id 升序)——最新心愿恒在
 *    输入条正上方;进页/键盘跟随(仅贴底)/添加成功三处均「snap 滚到底」露出最新(微信同构,
 *    ChatPage/TodoPage 生产范式:全 snap scrollToItem+awaitMeasured,禁 animateScrollToItem)
 *  - 勾选完成:done=true 服务端 CAS 恰发一条「我们一起完成了「title」🎉」到悄悄话(端上零新增
 *    消息逻辑,勾选者本端该消息靠切聊天 Tab 增量拉自愈);重复勾选幂等(回包真值不变)
 *  - 时间展示:行副文案与详情弹框均显「创建于 …」/「…完成于 …」(友好日+时刻,GMT+8);
 *    自己许的未完成行附「等TA点亮」;删除仅创建者可见(1_003_009_001 服务端兜底)
 *  - 同步:可见期 5s 静默轮询对账(组合在即活动在,返回键销毁即停),对方增删改/勾选 ≤5s
 *    感知;写在飞/弹层挂着跳过,失败静默下周期自愈(D-16 轮询唯一真相)
 *  - 底部悬浮添加条照 TodoPage 逐常量(键盘避让 max 公式/take+maxLength 双保险/POST 成功才清输入/
 *    新行 awaitMeasured 滚可见);删除确认浮层挂着时列表 userScrollEnabled=false(F6)
 */
@Page("wishListPage", supportInLocal = true)
internal class WishListPager : BasePager() {

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            WishListPage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }
}

@Composable
internal fun WishListPage(pager: Pager) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    // 勾选单飞锁:防双击并发 done 端点(第二次落 CAS rows=0 幂等,但两次回包替换存在交错窗口)
    var toggling by remember { mutableStateOf(false) }
    // 删除确认目标(null = 浮层关);deleting = 确认按钮单飞
    var deleteTarget by remember { mutableStateOf<WishInfo?>(null) }
    var deleting by remember { mutableStateOf(false) }
    // 详情/操作弹框目标(null = 关):行点击进入,完成/取消完成经弹框按钮触发(2026-10-17 交互修订)
    var detailTarget by remember { mutableStateOf<WishInfo?>(null) }
    // 软键盘高度(dp),0 = 收起;驱动添加条 bottom 与列表尾 Spacer 顶起(TodoPage 同款)
    var keyboardHeight by remember { mutableStateOf(0f) }
    // 页内自持(D-19g):服务端序(create_time asc, id asc),分层排序在组合期重排
    val wishes = remember { mutableStateListOf<WishInfo>() }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState(isRefreshing = refreshing)
    val focusManager = LocalFocusManager.current
    // 键盘跟随(2026-10-18 真机反馈「顶起来内容直接不见了」重写,ChatPage kbFollow 范式
    // 464-491 移植):上升沿回调内(此刻 layoutInfo=键盘前最后 measure,重组未跑)捕获
    // 「键盘弹出前是否贴底」快照——贴底(isNearBottom:!canScrollForward 或末可见项已达
    // 倒数第二项)才跟随钉底,上翻读历史者键盘弹起不拽(原判据「最后可见项被键盘盖」把
    // 用户正在读的中部内容整个跳走,即「直接不见了」根因之一);IME 逐帧步进每帧 seq++
    // 重启消费 effect,后帧取消前帧,最终帧钉终态
    var kbWasNearBottom by remember { mutableStateOf(false) }
    var kbFollowSeq by remember { mutableStateOf(0) }
    val onKeyboardHeightChange: (KeyboardParams) -> Unit = remember {
        { params: KeyboardParams ->
            if (params.height > keyboardHeight && params.height > 0f) {
                val info = listState.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                kbWasNearBottom = !listState.canScrollForward || lastVisible >= info.totalItemsCount - 2
                kbFollowSeq++
            }
            keyboardHeight = params.height
        }
    }
    val myId = SessionStore.userId
    // 添加条实际高度(onSizeChangedDp 回写,2026-10-18 真机反馈「添加完后输入框高度变了」):
    // TextField maxLines=3 随内容竖向生长(多行 ~76dp),添加成功清空缩回单行(44dp)高度跳变
    // 是微信同款正常行为,但尾 Spacer 原按恒定 44dp 让位——多行时清单尾项被输入条盖住,
    // 几何不自洽;改实际高度驱动让位,任何行数下尾项呼吸完整
    var inputBarHeight by remember { mutableStateOf(44.dp) }

    // 键盘跟随消费:贴底才 snap 钉底到尾 Spacer(ChatPage 464-473 同款全 snap 禁 animate——
    // animateScrollToItem 是逐环 tween+teleport+收尾 snap,Kuikly 桥接渲染的原生
    // contentOffset 同步层对复杂动画路径追不平,真机「滚完一片空白」;snap 版
    // scrollToItem 的 forceRemeasure 用键盘后约束重测,尾 Spacer 新高(键盘让位 400dp)
    // 生效+measure 回填(LazyListMeasure.kt:232-255 顶对齐靠后项回拉前序项)=底对齐=
    // 最新项贴输入条上方呼吸位,TodoPage 同款高垫滚底真机验证可靠。列表短/非贴底不滚;
    // 收起不滚(键盘前贴底者 clamp 自持贴底,ChatPage 459-463 同款)
    LaunchedEffect(kbFollowSeq) {
        if (!kbWasNearBottom || keyboardHeight <= 0f) return@LaunchedEffect
        listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }

    // D-19f 分层(2026-10-18 重构定稿):两语义分区——「已点亮」历史区置顶按 doneTime 倒序
    // (desc=刚完成的紧贴活跃区,时间线自上而下单调:最早完成→最近完成→最早许愿→最新许愿),
    // 默认折叠只显最近 3 条(历史不喧宾夺主,展开钮全量);「待点亮」活跃队列居列表尾保持
    // 服务端序(id 升序=最老在上最新在下)。最新心愿恒=列表最后内容位(输入条正上方),
    // 键盘推升/添加成功走「滚到底」才成立(真机反馈「顶起来应显示最新的」);
    // 进页首拉后自动滚到底(轮16 真机反馈「进入页面也没有到最底部」,ChatPage 进页见最新同款)
    val doneList = wishes.filter { it.done }.sortedByDescending { it.doneTime }
    val activeList = wishes.filter { !it.done }
    val doneCount = doneList.size
    var doneExpanded by remember { mutableStateOf(false) }
    val visibleDone = if (doneExpanded || doneList.size <= WISH_DONE_PREVIEW_COUNT) {
        doneList
    } else {
        doneList.take(WISH_DONE_PREVIEW_COUNT)
    }
    val showDoneToggle = doneList.size > WISH_DONE_PREVIEW_COUNT

    // 拉全量:进页首拉 / 下拉刷新 / 失败重试共用;成功清空重填,失败置 loadFailed。
    // 回 ok 供下拉刷新分支做最短驻留与失败 toast;loadFailed 仅在列表为空时才置——
    // 已有内容时刷新失败保留原清单(切失败态会让整个列表消失),首拉/重试路径列表必空,
    // 语义字节不变(MomentPage fetchFirst 同款口径)
    suspend fun fetchFirst(): Boolean {
        val result = WishApi.list(pager)
        val ok = result is ApiResult.Ok
        if (ok) {
            wishes.clear()
            wishes.addAll(WishInfo.listFrom((result as ApiResult.Ok).dataArray))
        }
        loadFailed = !ok && wishes.isEmpty()
        return ok
    }

    // 静默对账拉取(可见期轮询专用):Ok 才按服务端真值全量覆盖;失败保留现值,不翻转 loadFailed
    // 不 toast(下个周期自愈);与本地现值结构相等时跳过(WishInfo data class),避免无谓
    // clear/addAll 打断浏览(key=id 槽位稳定,覆盖本身也不跳位,等值跳过纯省重组)
    suspend fun fetchSilent() {
        val result = WishApi.list(pager)
        if (result is ApiResult.Ok) {
            val fresh = WishInfo.listFrom(result.dataArray)
            if (fresh != wishes.toList()) {
                wishes.clear()
                wishes.addAll(fresh)
            }
        }
    }

    fun refresh() {
        if (loading || refreshing) return
        refreshing = true
        scope.launch {
            fetchFirst()
            refreshing = false
        }
    }

    // 勾选/取消(D-19h):成功用回包真值原位替换本地行(乐观翻转缺 doneBy/doneTime,必须回填
    // 服务端真值);失败 toast 本地不动(无乐观翻转即无回滚);入口仅详情弹框操作按钮
    // (2026-10-17 交互修订:行点击只开弹框;权限收口=弹框按钮仅对方的行显示)
    fun toggle(wish: WishInfo) {
        if (toggling) return
        toggling = true
        scope.launch {
            val result = WishApi.done(pager, wish.id, !wish.done)
            toggling = false
            if (result is ApiResult.Ok) {
                WishInfo.from(result.data)?.let { fresh ->
                    val idx = wishes.indexOfFirst { it.id == fresh.id }
                    if (idx >= 0) wishes[idx] = fresh else wishes.add(fresh)
                }
            } else {
                toastCoupleResult(pager, result)
            }
        }
    }

    // 删除确认(乐观移除 + 失败原位插回;服务端 1_003_009_001 兜底,端上按钮只对创建者显示)
    fun confirmDelete() {
        val target = deleteTarget ?: return
        if (deleting) return
        deleting = true
        val idx = wishes.indexOfFirst { it.id == target.id }
        wishes.removeAll { it.id == target.id }
        scope.launch {
            val result = WishApi.delete(pager, target.id)
            deleting = false
            if (result is ApiResult.Ok) {
                deleteTarget = null
                toastCouple(pager, "已删除")
            } else {
                if (idx >= 0) wishes.add(idx.coerceAtMost(wishes.size), target)
                toastCoupleResult(pager, result)
            }
        }
    }

    // 独立页每次打开都是新 Pager 组合,LaunchedEffect(Unit) 即每次进入都重拉(MomentPage 同款)。
    // 首拉成功后滚到最底部(2026-10-18 真机反馈「进入页面也没有到最底部」——原定位到活跃
    // 组头距底部还差整个活跃队列,且定位公式是另一条无声失败链):用户裁决进页=见最新,
    // 列表终态 item 总数的精确结构计数(进页滚底/追加跟随共用谓词):刷新头 1 + 已点亮区
    // (组头1+展示数+展开钮)+ 待点亮区(组头1+队列数)或活跃空提示 1 + 尾 Spacer 1;
    // 展示数折叠取 min(D,3) 展开设 D,展开钮 D>3 恒在(展开后文案变「收起历史」同一 item)。
    // 时序 skeptic F1:下界若用语义总数 wishes.size+2,D≥7 折叠态 total=A+8<D+A+2 恒不满足
    // →每次进页必等满 500ms 超时才跳底,谓词必须与真实结构逐项对齐
    fun expectedListTotal(): Int {
        val doneSize = wishes.count { it.done }
        val activeSize = wishes.size - doneSize
        val shownDone = if (doneExpanded) doneSize else minOf(doneSize, WISH_DONE_PREVIEW_COUNT)
        val doneToggle = if (doneSize > WISH_DONE_PREVIEW_COUNT) 1 else 0
        val doneSection = if (doneSize == 0) 0 else 1 + shownDone + doneToggle
        val activeSection = if (activeSize > 0) 1 + activeSize else if (doneSize > 0) 1 else 0
        return 1 + doneSection + activeSection + 1
    }

    // 进页滚到底(2026-10-18 轮16 真机反馈「进入页面也没有到最底部」;ChatPage 进页滚底
    // LaunchedEffect(pageVisible) 452-458 同款):目标下标运行时取 totalItemsCount-1=尾 Spacer
    // (顶对齐请求被 measure 回拉=底对齐),awaitMeasured 等含全数据的 measure 真正落地再滚,
    // 规避铁律13 陈旧越界静默吞;活跃空(全历史)/拉取失败不滚,视口自然在顶看历史/失败态
    LaunchedEffect(Unit) {
        val ok = fetchFirst()
        loading = false
        if (ok && wishes.count { !it.done } > 0) {
            listState.awaitMeasured(expectedListTotal())
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    // 追加跟随(ChatPage LaunchedEffect(messages.size) 431-448 分支③同款;interaction skeptic
    // I1:轮询 fetchSilent 只改数据不触发滚动,对方许愿 ≤5s 同步到时新卡沉在悬浮输入条后面
    // 仅露上缘):size 变化先 awaitMeasured 等新 measure 落地,再新鲜读贴底才滚+拖拽/惯性中
    // 不拽,上翻读历史者不拽;本端添加不走此路——adding 守卫让添加流(自带滚底)独占,防双滚
    // 竞争 scroll mutex
    LaunchedEffect(wishes.size) {
        if (loading || adding || wishes.isEmpty()) return@LaunchedEffect
        if (wishes.count { !it.done } == 0) return@LaunchedEffect // 全历史:无活跃队列可跟随
        listState.awaitMeasured(expectedListTotal())
        if (!listState.isScrollInProgress && listState.isNearBottom()) {
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    // 可见期 5s 静默轮询(2026-10-17 用户反馈「一边勾选另一边不同步」):独立 @Page 组合在即
    // 活动在(返回键 finish 销毁组合,协程随之取消,无需 pageVisible——那是 MainPager 五 Tab
    // 的参数),拉服务端真值对账覆盖,对方增删改/勾选 ≤5s 感知;庆祝消息本身走聊天 WS 秒达,
    // 清单状态以轮询为准(D-16 轮询唯一真相,chat 增量拉同哲学)。写操作在飞/弹层挂着时跳过
    // (toggle 回包原位替换、删除乐观移除各管收敛,轮询别插手),失败静默下周期自愈
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            // adding 也在守卫内(时序 skeptic F2):缺了它,添加请求在飞时轮询的 list 回包
            // 可能后于 create 回包处理,fresh(不含新心愿)全量覆盖把刚加的心愿本地抹掉,
            // 且 total 缩水使添加滚底的 awaitMeasured 不可满足(≤5s 下轮自愈但闪没)
            if (!adding && !toggling && !deleting && !refreshing && !loading &&
                deleteTarget == null && detailTarget == null
            ) {
                fetchSilent()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppGradientHeader {
                Box(
                    modifier = Modifier.size(38.dp)
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(19.dp))
                        .clickable {
                            pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIconType.ChevronLeft, Color.White, Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                AppHeaderTitle("恋爱清单", "把想一起做的事,一个个点亮")
                Spacer(modifier = Modifier.weight(1f))
                // 空表不显「0/0 已完成」(2026-10-18 重构:空态显计数是无意义噪音)
                if (wishes.isNotEmpty()) {
                    AppTag(
                        "$doneCount/${wishes.size} 已完成",
                        bgColor = Color.White.copy(alpha = 0.25f),
                        textColor = Color.White,
                        fontSize = 11.sp,
                        corner = 12.dp,
                        hPadding = 10.dp,
                        vPadding = 4.dp,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    // Q3 拖拽收键盘(TodoPage 同款被动观察器,三个不可妥协点见铁律13④):
                    // ①全程不消费事件(原生滚动/行点击无感)②Initial pass + 读忽略消费标记的位移
                    // ③竖向越 touchSlop 且竖向占优才收。key=focusManager 页内稳定实例
                    .pointerInput(focusManager) {
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
                    },
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                // 删除确认/详情弹框开着时关列表滚动(F6:遮罩 clickable 不吃拖拽,背景列表原生
                // 滚动仍收事件,MinePage/MomentPage 同款)
                userScrollEnabled = deleteTarget == null && detailTarget == null,
            ) {
                // 下拉刷新(官方 pullToRefreshItem 须为首个 item,MomentPage 先例):重拉全量。
                // 最短驻留(2026-10-17 真机反馈「松手立马恢复原状,不知道好没好」):本地服务端
                // 几十 ms 即回,「刷新中…」一闪而过——不足 REFRESH_MIN_VISIBLE_MS 补齐再收;
                // 失败 toast(成功不 toast,清单自己会更新,肉眼可辨)
                pullToRefreshItem(
                    state = pullState,
                    onRefresh = {
                        refreshing = true
                        scope.launch {
                            val t0 = nowEpochMs()
                            val ok = fetchFirst()
                            val elapsed = nowEpochMs() - t0
                            if (elapsed < REFRESH_MIN_VISIBLE_MS) delay(REFRESH_MIN_VISIBLE_MS - elapsed)
                            if (!ok) toastCouple(pager, "刷新失败，请重试")
                            refreshing = false
                        }
                    },
                    scrollState = listState,
                ) { _, isRefreshing, _ ->
                    Text(
                        if (isRefreshing) "刷新中…" else "下拉刷新",
                        color = AppTheme.TextTertiary,
                        fontSize = 12.sp,
                    )
                }
                when {
                    loading -> item {
                        TimelineLoading()
                    }
                    loadFailed -> item {
                        TimelineLoadFailed(onRetry = { refresh() })
                    }
                    wishes.isEmpty() -> item {
                        WishEmptyHint(modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
                    }
                }
                // 「已点亮」历史区(2026-10-18 重构):组头 + 折叠的最近记录 + 展开钮。
                // 分区头给历史/待办明确分界(原混排无分界,灰卡与亮卡直接连排分不清区块)
                if (wishes.isNotEmpty() && doneList.isNotEmpty()) {
                    item(key = "done-header", contentType = "wish-group-header") {
                        WishGroupHeader("已点亮", doneList.size, AppTheme.TextTertiary)
                    }
                    items(
                        visibleDone,
                        key = { it.id }, // 服务端 id 全域唯一;回包替换/乐观回滚均按 id 定位(槽位复用防错位)
                        contentType = { "wish-done" },
                    ) { wish ->
                        WishRow(
                            wish = wish,
                            myId = myId,
                            onOpen = {
                                focusManager.clearFocus()
                                detailTarget = wish
                            },
                            onDelete = {
                                focusManager.clearFocus()
                                deleteTarget = wish
                            },
                        )
                    }
                    if (showDoneToggle) {
                        item(key = "done-toggle", contentType = "wish-done-toggle") {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { doneExpanded = !doneExpanded },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (doneExpanded) "收起历史" else "展开更早的 ${doneList.size - WISH_DONE_PREVIEW_COUNT} 条",
                                    color = AppTheme.TextTertiary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
                // 「待点亮」活跃区:组头 + 队列(最老在上最新在下,输入条正上方=最新)。
                // 活跃空而历史非空:轻提示一行(全空走上面大空态,不重复)
                if (wishes.isNotEmpty() && activeList.isNotEmpty()) {
                    item(key = "active-header", contentType = "wish-group-header") {
                        WishGroupHeader("待点亮", activeList.size, ThemeStore.current.brandStart)
                    }
                    items(
                        activeList,
                        key = { it.id },
                        contentType = { "wish" },
                    ) { wish ->
                        WishRow(
                            wish = wish,
                            myId = myId,
                            // 开弹框先失焦收键盘(2026-10-18 真机反馈:键盘开着点清单项,详情框出来了
                            // 键盘没收,弹框与键盘叠着;ChatPage/MomentPage 浮层收口同款写法)
                            onOpen = {
                                focusManager.clearFocus()
                                detailTarget = wish
                            },
                            onDelete = {
                                focusManager.clearFocus()
                                deleteTarget = wish
                            },
                        )
                    }
                } else if (wishes.isNotEmpty() && doneList.isNotEmpty()) {
                    item(key = "active-empty", contentType = "wish-active-empty") {
                        Text(
                            "还没有待点亮的心愿,许一个吧",
                            color = AppTheme.TextTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = AppTheme.PageHPadding),
                        )
                    }
                }
                // 尾部 Spacer:给悬浮添加条让位(独立页自持几何,2026-10-18 修——原照抄 TodoPage
                // 的 86/150 底栏让位常数,本页是 @Page 二级页无浮岛底栏,收起时输入条悬空离底
                // 一大截;收起 = 条 bottom 12+条高+呼吸 20,键盘弹起同步长高 = 键盘高+navBar+
                // 12+条高+呼吸 20。条高原恒定 44dp,现 onSizeChangedDp 回写实际值——maxLines=3
                // 多行时同样让位完整,见 inputBarHeight 注释)
                item {
                    Spacer(
                        modifier = Modifier.height(
                            max(
                                32.dp + inputBarHeight + navBarSafePaddingDp(),
                                keyboardHeight.dp + navBarSafePaddingDp() + 32.dp + inputBarHeight,
                            ),
                        ),
                    )
                }
            }
        }

        // 底部添加条(悬浮浮岛;2026-10-18 修独立页几何——原照抄 TodoPage 的 86dp 收起让位,
        // 那是给 MainPager 浮岛底栏留的,本页为 @Page 二级页无底栏,收起应贴底只留呼吸 12dp
        // +系统导航栏安全区;键盘避让公式保留 TodoPage 定稿:键盘 height 不含导航栏区域,
        // 须叠加 navBarSafePaddingDp 补齐,弹起 bottom = 键盘高+navBar+12。
        // 2026-10-18 层级修:必须组合在两弹框之前——Kuikly 后组合者在上,原排最后导致
        // 弹框灰幕盖不住输入条(真机实证),调序后遮罩盖住输入条且触摸被灰幕吃掉)
        Row(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = AppTheme.PageHPadding)
                // 回写条体实际高度(不含 bottom padding)驱动尾 Spacer 让位
                .onSizeChangedDp { inputBarHeight = it.height.dp }
                .padding(
                    bottom = max(
                        12.dp + navBarSafePaddingDp(),
                        keyboardHeight.dp + navBarSafePaddingDp() + 12.dp,
                    ),
                )
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f)
                    .background(AppTheme.CardBg, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it.take(WISH_INPUT_MAX_LENGTH) },
                    placeholder = "许一个想一起完成的心愿",
                    autoFocus = false,
                    placeholderColor = AppTheme.TextTertiary,
                    textStyle = TextStyle(color = AppTheme.TextPrimary, fontSize = 13.sp),
                    maxLines = 3, // 长文本限 3 行竖向生长,不在 22dp 胶囊里无限撑高(TodoPage F15)
                    keyboardHeightChange = onKeyboardHeightChange,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                        .maxLength(WISH_INPUT_MAX_LENGTH), // 原生层拒绝超限输入,与 take 兜底双保险
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            // 添加按钮(POST 成功才清输入:失败保留文案可重试,TodoPage 先例)。新心愿落活跃区尾
            // = 列表最后内容位(D-19f 定稿),滚动走「滚到底」:顶对齐末项(Spacer)请求超出滚动
            // 上限被 clamp 到底 → 新项底缘贴输入条上方 20dp(微信发消息同款瞬间贴底)。
            // 末项下标运行时取 totalItemsCount-1——折叠/展开历史区使 item 序不再可静态算;
            // prevTotal+1 只作 awaitMeasured 信号(添加净增 1 项,铁律13:过早 scrollToItem 撞
            // stale totalItemsCount 越界守卫被静默吞)
            AppIconButton(
                AppIconType.Plus,
                onClick = {
                    val text = inputText.trim()
                    if (text.isNotEmpty() && !adding) {
                        adding = true
                        scope.launch {
                            val result = WishApi.create(pager, text)
                            if (result is ApiResult.Ok) {
                                inputText = ""
                                WishInfo.from(result.data)?.let { fresh ->
                                    // id 去重(时序 skeptic I2,对照 toggle 按 id 原位替换先例):极端交错
                                    // 下轮询快照已含新心愿、create 回包再 add → 同 id 双条,key=id 重复崩
                                    wishes.removeAll { it.id == fresh.id }
                                    val prevTotal = listState.layoutInfo.totalItemsCount
                                    wishes.add(fresh)
                                    listState.awaitMeasured(prevTotal + 1)
                                    listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
                                }
                            } else {
                                toastCoupleResult(pager, result)
                            }
                            // adding 收口后置(原在回包处理前):wishes.add 会触发追加跟随 effect,
                            // 添加流自带滚底,若此刻 adding 已翻 false,跟随 effect(守卫 !adding)会
                            // 与本流在 scroll mutex 上竞争双滚——延后收口让 effect 看到 adding=true
                            // 自行让路,顺带把 5s 轮询守卫(!adding)延伸覆盖滚动窗口
                            adding = false
                        }
                    }
                },
                iconSize = 22.dp,
            )
        }

        // 删除确认浮层(页面级,无底栏无需 MainPager 收口;组合在列表/添加条之后 = 后组合者在上)
        if (deleteTarget != null) {
            DeleteWishOverlay(
                deleting = deleting,
                onConfirm = { confirmDelete() },
                onDismiss = { if (!deleting) deleteTarget = null },
            )
        }

        // 详情/操作弹框(2026-10-17 交互修订:行点击弹出;完成/取消完成在此确认,防误点亮)
        detailTarget?.let { target ->
            WishDetailOverlay(
                wish = target,
                myId = myId,
                submitting = toggling,
                onToggle = {
                    detailTarget = null
                    toggle(target)
                },
                onClose = { detailTarget = null },
            )
        }
    }
}

/**
 * 分区组头(2026-10-18 重构):色点 + 区名 + 计数,给历史区/活跃区明确分界。
 * dotColor 语义:待点亮=brandStart(亮,进行中),已点亮=TextTertiary(灰,完成)
 */
@Composable
private fun WishGroupHeader(label: String, count: Int, dotColor: Color) {
    Row(
        modifier = Modifier.padding(horizontal = AppTheme.PageHPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).background(dotColor, RoundedCornerShape(3.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = AppTheme.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.width(6.dp))
        Text("$count", color = AppTheme.TextTertiary, fontSize = 11.sp)
    }
}

/**
 * 心愿行卡片:CardBg 圆角卡,整卡与勾选框点击均进详情弹框(2026-10-17 交互修订:完成/取消
 * 一律经弹框确认,防误点亮——完成会自动发庆祝消息;嵌套 clickable 内层消费不双触发);
 * 副文案按状态显创建/完成时间与「等TA点亮」
 */
@Composable
private fun WishRow(
    wish: WishInfo,
    myId: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val canToggle = wish.userId != myId
    Row(
        modifier = Modifier.padding(horizontal = AppTheme.PageHPadding, vertical = 4.dp)
            .fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 勾选框=状态指示,点击同整卡进弹框(不直接 toggle——操作收口在弹框)
        AppCheckbox(
            checked = wish.done,
            onCheckedChange = onOpen,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                wish.title,
                color = if (wish.done) AppTheme.TextTertiary else AppTheme.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
            // 副文案=时间线:创建时间恒显(服务端必回传,<=0 兜底不造假);完成行加完成人+完成时间;
            // 自己许的未完成行附「等TA点亮」(权限修订解释,不然点不开操作像坏了)
            val parts = mutableListOf<String>()
            if (wish.createTime > 0) parts.add("创建于 ${wishTimeLabel(wish.createTime)}")
            if (wish.done) {
                // 完成人文案:doneBy 是服务端真值(回包替换回填,D-19h);doneTime=0 兜底只显人
                val who = if (wish.doneBy == myId) "我" else "TA"
                parts.add(if (wish.doneTime > 0) "$who 完成于 ${wishTimeLabel(wish.doneTime)}" else "$who 完成")
            } else if (!canToggle) {
                parts.add("等TA点亮")
            }
            if (parts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    parts.joinToString(" · "),
                    color = AppTheme.TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        // 删除仅创建者可见(D-19e:伴侣不能删对方的心愿,防误删;服务端 1_003_009_001 兜底)
        if (wish.userId == myId) {
            Box(
                modifier = Modifier.size(30.dp)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIconType.Close, AppTheme.TextTertiary, Modifier.size(16.dp))
            }
        }
    }
}

/** "今天 14:30"/"10月15日 09:12"(友好日+时刻,GMT+8,chatDayLabelFriendly+chatHHmm 组合);<=0 空串 */
private fun wishTimeLabel(epochMs: Long): String {
    if (epochMs <= 0) return ""
    return "${chatDayLabelFriendly(epochMs)} ${chatHHmm(epochMs)}"
}

/**
 * WishDetailOverlay — 心愿详情/操作弹框(灰幕 + 居中白卡,DeleteWishOverlay 同款结构):
 * 显创建人/创建时间与完成人/完成时间;完成与取消完成在此确认(2026-10-17 交互修订,防误点亮——
 * 完成会自动发庆祝消息);发布者看自己的心愿只有详情无操作按钮(仅对方能勾,D-19 权限修订)。
 * submitting(=toggling)期间全部按钮拦点击;灰幕点击关闭
 */
@Composable
private fun WishDetailOverlay(
    wish: WishInfo,
    myId: String,
    submitting: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit,
) {
    val canToggle = wish.userId != myId
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { if (!submitting) onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 36.dp)
                .fillMaxWidth()
                .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                .clickable { }
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Text(wish.title, color = AppTheme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            // 时间线:创建恒显;完成行加完成人与完成时间(均服务端真值,<=0 兜底不造假)
            val creator = if (wish.userId == myId) "我" else "TA"
            Text(
                if (wish.createTime > 0) "$creator 创建于 ${wishTimeLabel(wish.createTime)}" else "$creator 创建的心愿",
                color = AppTheme.TextSecondary,
                fontSize = 13.sp,
            )
            if (wish.done) {
                Spacer(modifier = Modifier.height(4.dp))
                val who = if (wish.doneBy == myId) "我" else "TA"
                Text(
                    if (wish.doneTime > 0) "$who 完成于 ${wishTimeLabel(wish.doneTime)}" else "$who 已完成",
                    color = AppTheme.TextSecondary,
                    fontSize = 13.sp,
                )
            } else if (!canToggle) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("等TA点亮", color = AppTheme.TextSecondary, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            when {
                // 对方的未完成心愿:[取消] + [已完成](品牌色主操作)
                canToggle && !wish.done -> Row {
                    Box(
                        modifier = Modifier.weight(1f)
                            .height(42.dp)
                            .background(Color(0xFFF3F4F8), RoundedCornerShape(21.dp))
                            .clickable { if (!submitting) onClose() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("取消", color = AppTheme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        // 主操作钮用品牌渐变(2026-10-18 真机反馈「颜色不对」:原单色 brandEnd 是
                        // 渐变浅端,默认蜜桃主题 FFB199 极浅,白字发白像禁用态;全仓主按钮惯例
                        // =brandBrush 渐变——AppCards 登录钮/CheckinPage 打卡钮同款)
                        modifier = Modifier.weight(1f)
                            .height(42.dp)
                            .background(ThemeStore.current.brandBrush, RoundedCornerShape(21.dp))
                            .clickable { if (!submitting) onToggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("已完成", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // 对方的已完成心愿:[取消完成](不发消息) + [关闭]
                canToggle && wish.done -> Row {
                    Box(
                        modifier = Modifier.weight(1f)
                            .height(42.dp)
                            .background(Color(0xFFF3F4F8), RoundedCornerShape(21.dp))
                            .clickable { if (!submitting) onToggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("取消完成", color = AppTheme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier.weight(1f)
                            .height(42.dp)
                            .background(Color(0xFFF3F4F8), RoundedCornerShape(21.dp))
                            .clickable { if (!submitting) onClose() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("关闭", color = AppTheme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                // 自己的心愿/其余:只读详情,仅[关闭]
                else -> Box(
                    modifier = Modifier.fillMaxWidth()
                        .height(42.dp)
                        .background(Color(0xFFF3F4F8), RoundedCornerShape(21.dp))
                        .clickable { if (!submitting) onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("关闭", color = AppTheme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** 空态:旗帜图标 + 引导文案 */
@Composable
private fun WishEmptyHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(AppIconType.Flag, AppTheme.TextTertiary, Modifier.size(44.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "还没有心愿,许一个吧",
            color = AppTheme.TextSecondary,
            fontSize = 13.sp,
        )
    }
}

/**
 * DeleteWishOverlay — 删除心愿二次确认浮层(全屏灰幕 + 居中白卡,LogoutConfirmOverlay 同款结构):
 * 灰幕点击取消,卡片 background+clickable 同节点吞点击防透传(铁律⑤);deleting 期间所有出口拦点击。
 * 页面级挂载(本页无底栏,组合顺序 = 盖住列表与添加条,无需 MainPager 收口)
 */
@Composable
private fun DeleteWishOverlay(
    deleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { if (!deleting) onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 36.dp)
                .fillMaxWidth()
                .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                .clickable { }
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Text("删除心愿?", color = AppTheme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "删除后双方都看不到这条心愿了,已发出的完成消息不受影响。确定要删除吗?",
                color = AppTheme.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row {
                Box(
                    modifier = Modifier.weight(1f)
                        .height(42.dp)
                        .background(Color(0xFFF3F4F8), RoundedCornerShape(21.dp))
                        .clickable { if (!deleting) onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消", color = AppTheme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier.weight(1f)
                        .height(42.dp)
                        .background(AppTheme.Danger, RoundedCornerShape(21.dp))
                        .clickable { if (!deleting) onConfirm() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (deleting) "删除中…" else "删除",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * awaitMeasured — 等 LazyColumn 完成包含目标条数的 measure 再返回(TodoPage/ChatPage 同款实现,
 * 各自随页面演进,勿强行合并去重):snapshotFlow 对 layoutInfo 每次 measure 必发新值,以
 * 「totalItemsCount ≥ itemTotal」为落地信号;500ms 兜底超时(丢弃本次滚动优于错误滚动——
 * snapToItemIndexInternal 首行 index 越界直接 return)
 */
private suspend fun LazyListState.awaitMeasured(itemTotal: Int) {
    withTimeoutOrNull(500) {
        snapshotFlow { layoutInfo.totalItemsCount }.first { it >= itemTotal }
    }
}

/**
 * isNearBottom — 贴底判定(ChatPage.isNearBottom 936-940 逐字同款,各页一份勿合并):末项内容位
 * (下标 totalItemsCount-2,尾 Spacer 前一位)仍在可视区、或本就无前向可滚(真贴底)——用户
 * 上翻读历史时不拽底。⚠ 必须在 awaitMeasured 之后或回调捕获点读取:陈旧 measure 会误判
 */
private fun LazyListState.isNearBottom(): Boolean {
    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
    return !canScrollForward || lastVisible >= info.totalItemsCount - 2
}
