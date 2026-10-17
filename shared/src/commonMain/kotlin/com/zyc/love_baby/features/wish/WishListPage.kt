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
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.features.mood.TimelineLoadFailed
import com.zyc.love_baby.features.mood.TimelineLoading
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.WishApi
import com.zyc.love_baby.state.SessionStore
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 文本长度上限(与服务端 AppWishCreateReqVO @Length(max=64) 对齐;maxLength 原生层拒绝 + take 兜底) */
private const val WISH_INPUT_MAX_LENGTH = 64

/**
 * WishListPage — 恋爱清单页(@Page 注册名 "wishListPage"):共享心愿清单 + 勾选完成自动发庆祝消息(M14)
 * 用法:我的页「恋爱清单」菜单 RouterModule.openPage 打开;返回键 closePage。
 *      独立二级页(MomentPager 同款壳,不走 MainPager 五 Tab)
 * 依赖:WishApi/WishInfo(页内自持不进 AppState,D-19g——页销毁即弃,登出/解绑零 reset 接线);
 *      AppCheckbox(勾选);chat.chatDayLabelFriendly(完成时间文案);mood.TimelineLoading/LoadFailed;
 *      couple 组 toast;RouterModule(返回)
 * 事件:返回钮 closePage;勾选(仅对方能勾——发布者不能点亮自己许的心愿,2026-10-17 真机反馈
 *      修订 D-19;整卡点击与勾选框均可触发,TodoPage 同款嵌套 clickable 内层消费不双触发)→
 *      done(单口双向,成功用回包真值原位替换本地行 D-19h,失败 toast);删除(仅 wish.userId ==
 *      自己显示)→ 确认浮层 → 乐观移除失败原位回滚;下拉刷新(官方 pullToRefreshItem 须为首个
 *      item,MomentPage/ChatPage 先例);添加条 POST 成功才清输入
 * 功能:
 *  - couple 共享清单(双方互见;勾选=对方点亮):服务端 create_time asc, id asc 全量 → 端上分层排序
 *    (D-19f):进行中在前(服务端序同 id 升序),已完成沉底按 doneTime 倒序
 *  - 勾选完成:done=true 服务端 CAS 恰发一条「我们一起完成了「title」🎉」到悄悄话(端上零新增
 *    消息逻辑,勾选者本端该消息靠切聊天 Tab 增量拉自愈);重复勾选幂等(回包真值不变)
 *  - 已完成行副文案 = 勾选者(我/TA)· 友好时间;自己许的未完成行副文案「等TA点亮」;
 *    删除仅创建者可见(1_003_009_001 服务端兜底)
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
    // 软键盘高度(dp),0 = 收起;驱动添加条 bottom 与列表尾 Spacer 顶起(TodoPage 同款)
    var keyboardHeight by remember { mutableStateOf(0f) }
    // 页内自持(D-19g):服务端序(create_time asc, id asc),分层排序在组合期重排
    val wishes = remember { mutableStateListOf<WishInfo>() }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState(isRefreshing = refreshing)
    val focusManager = LocalFocusManager.current
    val onKeyboardHeightChange: (KeyboardParams) -> Unit = remember {
        { params: KeyboardParams -> keyboardHeight = params.height }
    }
    val myId = SessionStore.userId

    // D-19f 分层排序:进行中在前(保持服务端序 = id 升序),已完成沉底按 doneTime 倒序
    // (doneTime=0 兜底排最后)
    val display = wishes.filter { !it.done } + wishes.filter { it.done }.sortedByDescending { it.doneTime }
    val doneCount = wishes.count { it.done }

    // 拉全量:进页首拉 / 下拉刷新 / 失败重试共用;成功清空重填,失败置 loadFailed
    suspend fun fetchFirst() {
        val result = WishApi.list(pager)
        val ok = result is ApiResult.Ok
        if (ok) {
            wishes.clear()
            wishes.addAll(WishInfo.listFrom((result as ApiResult.Ok).dataArray))
        }
        loadFailed = !ok
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
    // 服务端真值);失败 toast 本地不动(无乐观翻转即无回滚);入口仅对方的行(权限在 WishRow 收口)
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

    // 独立页每次打开都是新 Pager 组合,LaunchedEffect(Unit) 即每次进入都重拉(MomentPage 同款)
    LaunchedEffect(Unit) {
        fetchFirst()
        loading = false
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
                // 删除确认浮层开着时关列表滚动(F6:遮罩 clickable 不吃拖拽,背景列表原生滚动
                // 仍收事件,MinePage/MomentPage 同款)
                userScrollEnabled = deleteTarget == null,
            ) {
                // 下拉刷新(官方 pullToRefreshItem 须为首个 item,MomentPage 先例)
                pullToRefreshItem(
                    state = pullState,
                    onRefresh = {
                        refreshing = true
                        scope.launch {
                            fetchFirst()
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
                    display.isEmpty() -> item {
                        WishEmptyHint(modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
                    }
                }
                items(
                    display,
                    key = { it.id }, // 服务端 id 全域唯一;回包替换/乐观回滚均按 id 定位(槽位复用防错位)
                    contentType = { if (it.done) "wish-done" else "wish" },
                ) { wish ->
                    WishRow(
                        wish = wish,
                        myId = myId,
                        onToggle = { toggle(wish) },
                        onDelete = { deleteTarget = wish },
                    )
                }
                // 尾部 Spacer:给悬浮添加条让位(TodoPage 同款闭键常数 150 = 输入条上缘 86+44 再 +20
                // 呼吸;键盘弹起同步长高,滚底的新条目完整落在键盘/输入条上方)
                item {
                    Spacer(
                        modifier = Modifier.height(
                            max(
                                150.dp + navBarSafePaddingDp(),
                                keyboardHeight.dp + navBarSafePaddingDp() + 70.dp,
                            ),
                        ),
                    )
                }
            }
        }

        // 删除确认浮层(页面级,无底栏无需 MainPager 收口;组合在列表/添加条之后 = 后组合者在上)
        if (deleteTarget != null) {
            DeleteWishOverlay(
                deleting = deleting,
                onConfirm = { confirmDelete() },
                onDismiss = { if (!deleting) deleteTarget = null },
            )
        }

        // 底部添加条(悬浮浮岛;软键盘避让 2026-10-16 TodoPage 定稿几何:键盘 height 不含导航栏
        // 区域,须叠加 navBarSafePaddingDp 补齐;弹起 bottom = 键盘高+navBar+12,收起恢复浮岛常数)
        Row(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = AppTheme.PageHPadding)
                .padding(
                    bottom = max(
                        86.dp + navBarSafePaddingDp(),
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
            // 添加按钮(POST 成功才清输入:失败保留文案可重试,TodoPage 先例)。服务端 create_time asc
            // 同序 → 新心愿落活跃块尾;display 中下标 = 活跃数-1,awaitMeasured 等 measure 落地
            // 再滚(铁律13:过早 scrollToItem 撞 stale totalItemsCount 越界守卫被静默吞)
            AppIconButton(
                AppIconType.Plus,
                onClick = {
                    val text = inputText.trim()
                    if (text.isNotEmpty() && !adding) {
                        adding = true
                        scope.launch {
                            val result = WishApi.create(pager, text)
                            adding = false
                            if (result is ApiResult.Ok) {
                                inputText = ""
                                WishInfo.from(result.data)?.let { fresh ->
                                    wishes.add(fresh)
                                    val targetIndex = wishes.count { !it.done } - 1
                                    if (targetIndex >= 0) {
                                        listState.awaitMeasured(targetIndex + 1)
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
                            } else {
                                toastCoupleResult(pager, result)
                            }
                        }
                    }
                },
                iconSize = 22.dp,
            )
        }
    }
}

/**
 * 心愿行卡片:CardBg 圆角卡 + 整卡可点(仅对方的行,TodoPage 同款;嵌套 clickable 内层消费
 * 不双触发)。勾选权限 D-19 修订(2026-10-17 真机反馈):发布者不能点亮自己许的心愿(自己许,
 * TA来完成)——卡与勾选框对发布者均不响应,未完成行副文案给「等TA点亮」解释
 */
@Composable
private fun WishRow(
    wish: WishInfo,
    myId: String,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val canToggle = wish.userId != myId
    Row(
        modifier = Modifier.padding(horizontal = AppTheme.PageHPadding, vertical = 4.dp)
            .fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(16.dp))
            .then(if (canToggle) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 对方的行:勾选框可点(与整卡同回调,内层消费不双触发);自己的行:null=纯状态指示
        AppCheckbox(
            checked = wish.done,
            onCheckedChange = if (canToggle) onToggle else null,
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
            if (wish.done) {
                Spacer(modifier = Modifier.height(2.dp))
                // 勾选者文案:doneBy 是服务端真值(回包替换回填,D-19h);doneTime=0 兜底只显人
                val who = if (wish.doneBy == myId) "我" else "TA"
                Text(
                    if (wish.doneTime > 0) "$who · ${chatDayLabelFriendly(wish.doneTime)}" else "$who 完成",
                    color = AppTheme.TextSecondary,
                    fontSize = 11.sp,
                )
            } else if (!canToggle) {
                // 自己许的未完成心愿:勾选权限收窄的解释文案(不然点不动像坏了)
                Spacer(modifier = Modifier.height(2.dp))
                Text("等TA点亮", color = AppTheme.TextSecondary, fontSize = 11.sp)
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
