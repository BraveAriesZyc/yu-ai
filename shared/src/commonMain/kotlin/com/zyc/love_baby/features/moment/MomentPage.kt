package com.zyc.love_baby.features.moment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
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
import com.tencent.kuikly.compose.foundation.lazy.LazyRow
import com.tencent.kuikly.compose.foundation.lazy.itemsIndexed
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.pager.HorizontalPager
import com.tencent.kuikly.compose.foundation.pager.rememberPagerState
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.rememberPullToRefreshState
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.base.FileModule
import com.zyc.love_baby.components.AppConfirmOverlay
import com.zyc.love_baby.components.AppEmptyHint
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconButton
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppInputField
import com.zyc.love_baby.components.AppLoading
import com.zyc.love_baby.components.AppLoadFailed
import com.zyc.love_baby.components.AppPrimaryButton
import com.zyc.love_baby.components.AppPullRefreshItem
import com.zyc.love_baby.components.AppSubPageHeader
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.keyboardLiftBottomDp
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.components.rememberKeyboardHeight
import com.zyc.love_baby.data.model.MomentInfo
import com.zyc.love_baby.state.chatDayLabelFriendly
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.network.ApiConfig
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.MomentApi
import com.zyc.love_baby.network.UserApi
import com.zyc.love_baby.state.SessionStore
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MomentPage — 我们的相册页(@Page 注册名 "momentPage"):双列瞬间墙 + 发布浮层 + 大图预览(M9)
 * 用法:我的页「我们的相册」菜单 RouterModule.openPage 打开;返回键 closePage。
 *      独立二级页(CheckinPager 同款壳,不走 MainPager 五 Tab)
 * 依赖:MomentApi/MomentInfo(页内自持不进 AppState,M7 时间线页先例——页销毁即弃,登出/解绑零
 *      reset);FileModule.pickImages/uploadImage(maxEdge=1080,选图本地预览+发布时上传,M15);couple 组 toast;
 *      chat.chatDayLabelFriendly(时间文案);components.AppLoading/AppLoadFailed(原 mood.Timeline
 *      同款,收编单源);RouterModule(返回)
 * 事件:返回钮 closePage;卡片点击开大图预览;FAB「+」开发布浮层;失败态「重试」重拉;
 *      「加载更多」beforeId=当前最小 id 翻页;下拉刷新(官方 pullToRefreshItem,ChatPage 先例)重拉首页
 * 功能:
 *  - 双列瞬间墙:LazyColumn + chunked(2) Row 自排(CheckinPage 日历同款自排,非 LazyVerticalGrid
 *    ——2.29 官方 pullToRefreshItem 只挂 LazyListScope(反编译 javap 锤实,scrollState 参数即
 *    LazyListState),计划里「网格+官方下拉刷新」二者取官方下拉刷新;M15 多图后行高随图数变高,
 *    生产等价先例同行有/无配文,Row 缺省 Top 对齐)
 *  - 卡片:图块统一 140dp 全宽 Crop+多图右上「N张」黑胶囊角标(2026-10-17 真机反馈二轮:三列
 *    迷你九宫格在半宽卡内 cell 过小弃用,全部图进预览横滑看)+ 配文(空串不显,maxLines=2)+
 *    我/TA 与时间 + 纯展示红心;点击进大图预览
 *  - 可见期 5s 静默轮询对账(M15,D-20c:点赞/对方增删不发 WS 靠轮询感知,M14 心愿清单同款;
 *    恒拉首页合并删账/增改账,data class 相等跳过,失败静默自愈)
 *  - 发布浮层:选图仅本地预览零网络(pickImages 回 content:// URI,KRImageAdapter 已扩 content:
 *    分支直载;M15 真机反馈「没上传不调接口,点击发布才是上传」)+ LazyRow 缩略条(右上 X 删除/
 *    条尾 Plus 追加)+ 发布时点热身+逐张 uploadImage(进度「上传中 i/N」,失败重试跳过已传张)
 *    → POST create(回包头插落态不重拉)
 *  - 大图预览:黑底 0.9 全屏 HorizontalPager 逐页 Fit + 多图页码胶囊 + 底部信息卡(配文/
 *    发布者/时间/点赞钮双方均可 D-20e 回包真值原位替换/删除钮仅上传者本人——服务端
 *    1_003_008_001 兜底,端上只显不拦校验);点击黑底关闭,信息卡 clickable 吞透传
 *  - F6 铁律:任一浮层开着 LazyColumn userScrollEnabled=false(遮罩只吃 tap 不吃拖拽)
 */
@Page("momentPage", supportInLocal = true)
internal class MomentPager : BasePager() {

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            MomentPage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }
}

/** 每页条数(与服务端默认一致;回包条数 < 该值判无更多) */
private const val PAGE_SIZE = 30

@Composable
internal fun MomentPage(pager: Pager) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    // 游标分页:回包条数 = PAGE_SIZE 才可能有下一页(端上判据,服务端 limit 上限 100 不会截断 30)
    var hasMore by remember { mutableStateOf(true) }
    var showAdd by remember { mutableStateOf(false) }
    // 大图预览目标(null = 关闭);非空时 LazyColumn 关滚动(F6)
    var preview by remember { mutableStateOf<MomentInfo?>(null) }
    var previewDeleting by remember { mutableStateOf(false) }
    // 删除确认目标(M18):瞬间删除从一键直删改二次确认——不可逆内容+预览浮层里删除钮与
    // 点赞钮相邻易误触(此前是全 App 唯一无确认的不可逆删除入口);独立页页内挂载无底栏问题
    var deleteTarget by remember { mutableStateOf<MomentInfo?>(null) }
    // 点赞单飞锁(M15):在飞期间轮询跳过、预览点赞钮拦截
    var likeToggling by remember { mutableStateOf(false) }
    // 页内自持(M7 时间线页先例):id 倒序新→旧,服务端序原样保留不重排
    val moments = remember { mutableStateListOf<MomentInfo>() }
    // 官方下拉刷新与列表手势共享同一 state 实例(2.29 pullToRefreshItem 的 scrollState 参数
    // 必须是 LazyColumn 自身的 LazyListState,否则手势判定错位)
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState(isRefreshing = refreshing)

    // 拉首页(beforeId=0):load 进页 / 下拉刷新共用;成功清空重填,失败置 loadFailed(刷新静默)。
    // 回 ok 供下拉刷新分支做最短驻留与失败 toast;loadFailed 仅在列表为空时才置——
    // 已有内容时刷新失败保留原列表(切失败态会让整个墙消失,真机反馈「不知道好没好」的一部分),
    // 首拉/重试路径列表必空,语义字节不变
    suspend fun fetchFirst(): Boolean {
        val result = MomentApi.list(pager, "0", PAGE_SIZE)
        val ok = result is ApiResult.Ok
        if (ok) {
            val list = MomentInfo.listFrom((result as ApiResult.Ok).dataArray)
            // hasMore 绝对值重算仅限此处:fetchFirst 清空重填(进页/下拉刷新),已加载知识整体
            // 重置;轮询 fetchSilentMerge 不清列表,hasMore 只降不升(见其③注释)
            hasMore = list.size >= PAGE_SIZE
            moments.clear()
            moments.addAll(list)
        }
        loadFailed = !ok && moments.isEmpty()
        return ok
    }

    fun load() {
        scope.launch {
            loading = true
            fetchFirst()
            loading = false
        }
    }

    fun loadMore() {
        if (loadingMore || !hasMore || moments.isEmpty()) return
        loadingMore = true
        scope.launch {
            // 列表 id 倒序,尾部即当前最小 id(游标)
            val result = MomentApi.list(pager, moments.last().id, PAGE_SIZE)
            loadingMore = false
            if (result is ApiResult.Ok) {
                val list = MomentInfo.listFrom(result.dataArray)
                hasMore = list.size >= PAGE_SIZE
                moments.addAll(list)
            } else {
                toastCoupleResult(pager, result)
            }
        }
    }

    fun removeMoment(info: MomentInfo) {
        if (previewDeleting) return
        previewDeleting = true
        scope.launch {
            val result = MomentApi.delete(pager, info.id)
            previewDeleting = false
            if (result is ApiResult.Ok) {
                moments.removeAll { it.id == info.id }
                preview = null
                toastCouple(pager, "已删除")
            } else {
                toastCoupleResult(pager, result)
            }
        }
    }

    // 点赞/取消(M15,单飞):回包操作后服务端真值 {likeCount,liked} 按 id 原位替换(D-20e,
    // 禁端上乐观推导);preview 是独立状态副本,替换时同步落态
    fun toggleLike(info: MomentInfo) {
        if (likeToggling) return
        likeToggling = true
        scope.launch {
            val result = MomentApi.like(pager, info.id, !info.liked)
            likeToggling = false
            if (result is ApiResult.Ok) {
                val data = result.data
                val updated = info.copy(
                    liked = data.optBoolean("liked", !info.liked),
                    likeCount = data.optInt("likeCount", info.likeCount),
                )
                val index = moments.indexOfFirst { it.id == info.id }
                if (index >= 0) moments[index] = updated
                if (preview?.id == info.id) preview = updated
            } else {
                toastCoupleResult(pager, result)
            }
        }
    }

    // 5s 静默轮询对账(M14 心愿清单同款解法,D-20c:点赞/对方增删不发 WS 不发消息,靠轮询感知):
    // 恒拉首页 beforeId=0 limit=PAGE_SIZE,失败静默自愈;四类在飞动作期间跳过(动作回包自带真值,
    // 轮询抢跑会覆盖新鲜态);浮层开着不拦——preview 是独立副本,合并时同步真值
    suspend fun fetchSilentMerge() {
        val result = MomentApi.list(pager, "0", PAGE_SIZE)
        if (result !is ApiResult.Ok) return
        val fresh = MomentInfo.listFrom(result.dataArray)
        // ①删账:id 倒序,首页窗口 = id ≥ fresh 最小 id;窗口内消失的条目移除,窗口外更老条目
        //   删除不可见 = 接受(R2),下拉刷新全量纠偏
        val freshIds = fresh.map { it.id }.toSet()
        val windowMinId = fresh.lastOrNull()?.id?.toLongOrNull()
        if (windowMinId != null) {
            moments.removeAll {
                val mid = it.id.toLongOrNull()
                mid != null && mid > windowMinId && it.id !in freshIds
            }
        }
        // ②增/改账:按服务端序(新→旧)遍历——已存在则 data class 相等跳过/原位替换(likeCount/
        //   liked/多图字段变化即触发),新条目插 anchor 后(保持服务端相对序);替换同步 preview
        var anchor = 0
        for (item in fresh) {
            val index = moments.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                if (moments[index] != item) {
                    moments[index] = item
                    if (preview?.id == item.id) preview = item
                }
                anchor = index + 1
            } else {
                moments.add(minOf(anchor, moments.size), item)
                anchor++
            }
        }
        // ③hasMore 只降不升(2026-10-17 真机反馈「加载更多反复复现」修复):轮询恒拉满页首窗口,
        //   若置 true 会把已加载到底的 hasMore 反复打回(按钮复现,点了又拉不出新条目);窗口
        //   不满页=对方删光/缩容,降 false。置 true 只属 loadMore 追加页回包与 fetchFirst(其
        //   清空重填、已加载知识整体重置,绝对值重算安全)
        if (fresh.size < PAGE_SIZE) hasMore = false
    }

    // 独立页每次打开都是新 Pager 组合,LaunchedEffect(Unit) 即每次进入都重拉
    LaunchedEffect(Unit) { load() }

    // 可见期 5s 轮询(WishListPage fetchSilent 同款:独立 @Page 组合在即活动在,无需 pageVisible)
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            if (!loading && !refreshing && !loadingMore && !previewDeleting && !likeToggling) {
                fetchSilentMerge()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 二级页头单源化 AppSubPageHeader(38dp 白 25% 圆返回钮 + Spacer10 + 标题列,
            // 与原件手写件同构,视觉逐字一致)
            AppSubPageHeader("我们的相册", "每一帧都值得收藏", onBack = {
                pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
            })
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                // 浮层(发布/大图预览)开着时关列表滚动:遮罩 clickable 不吃拖拽,
                // 背景列表原生滚动仍收事件(MinePage/MoodPage 同款,2026-10-17 真机实证)
                userScrollEnabled = !showAdd && preview == null,
            ) {
                // 下拉刷新(官方 pullToRefreshItem 须为首个 item,ChatPage 先例):重拉首页。
                // 最短驻留 + 失败 toast 已收口在 AppPullRefreshItem(components,三页单源,
                // REFRESH_MIN_VISIBLE_MS=600L 同常数;成功不 toast,数据自己会更新,肉眼可辨)
                AppPullRefreshItem(
                    pullState = pullState,
                    listState = listState,
                    scope = scope,
                    setRefreshing = { refreshing = it },
                    pager = pager,
                    onRefresh = { fetchFirst() },
                )
                when {
                    loading -> item {
                        // 三态件收编 components.AppLoading/AppLoadFailed(mood 域 Timeline
                        // 同款原件原样迁入,视觉逐字一致);外层 48dp 垫 Box 保留,叠加几何不变
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) { AppLoading() }
                    }
                    loadFailed -> item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) { AppLoadFailed { load() } }
                    }
                    moments.isEmpty() -> item {
                        // 空态收编 components.AppEmptyHint(56dp brandStart 10% 圆 + Heart 26dp +
                        // 14sp 标题 + 11sp 副文案,与原件逐字一致,iconSize 26 显式传参)
                        AppEmptyHint(
                            AppIconType.Heart,
                            "还没有瞬间",
                            modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                            hint = "点右下角 + 晒一张今天的照片吧",
                            iconSize = 26.dp,
                        )
                    }
                    else -> {
                        // 双列自排:chunked(2) Row(CheckinPage 日历同款);头插/删除按整行重排,量级可接受
                        moments.chunked(2).forEach { rowItems ->
                            item(
                                key = "moment-${rowItems.first().id}",
                                contentType = "momentRow", // 行同构,与空态/加载更多脚项区分复用池(M18 收口)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = AppTheme.PageHPadding, vertical = 6.dp),
                                ) {
                                    MomentCard(
                                        info = rowItems[0],
                                        modifier = Modifier.weight(1f),
                                        onClick = { preview = rowItems[0] },
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    if (rowItems.size > 1) {
                                        MomentCard(
                                            info = rowItems[1],
                                            modifier = Modifier.weight(1f),
                                            onClick = { preview = rowItems[1] },
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f)) // 奇数尾行补位
                                    }
                                }
                            }
                        }
                        if (hasMore) {
                            item(key = "load-more") {
                                // 胶囊三层写法(铁律 14/15,M12 三轮定稿,2026-10-17 真机反馈同源:
                                // 背景直挂 Text 落原生 TextView 块,文字 gravity 失控在胶囊内偏移
                                // =「边距对齐似乎有问题」):外层 Box 容器居中 → 内层 Box 载
                                // background+clickable+padding → Text 作子节点
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (loadingMore) Color.Transparent else theme.brandStart.copy(alpha = 0.08f),
                                                RoundedCornerShape(14.dp),
                                            )
                                            .clickable { if (!loadingMore) loadMore() }
                                            .padding(horizontal = 16.dp, vertical = 7.dp),
                                    ) {
                                        Text(
                                            if (loadingMore) "加载中…" else "加载更多",
                                            color = if (loadingMore) AppTheme.TextTertiary else theme.brandStart,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "bottom-safe") { Spacer(modifier = Modifier.height(90.dp + navBarSafePaddingDp())) }
            }
        }

        // 右下 FAB 新增(浮层打开时被最后组合的浮层覆盖,不可误触;AnniversaryPage 同款)
        AppIconButton(
            AppIconType.Plus,
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp + navBarSafePaddingDp()),
        )

        if (showAdd) {
            MomentAddOverlay(
                pager = pager,
                onPublished = { info ->
                    // 发布成功头插落态(create 回包含服务端 id/createTime)。不动 hasMore:
                    // 发布只头插新条目,不改变「下方还有无更老未加载条目」的事实——置 true
                    // 会让「加载更多」闪现数秒等轮询纠偏(2026-10-17 真机反馈)
                    moments.add(0, info)
                },
                onDismiss = { showAdd = false },
            )
        }

        preview?.let { info ->
            MomentPreviewOverlay(
                info = info,
                likeSubmitting = likeToggling,
                deleting = previewDeleting,
                onToggleLike = { toggleLike(it) },
                onDelete = { deleteTarget = info },
                onDismiss = { preview = null },
            )
        }

        // 删除确认浮层(M18):后于 preview 组合盖住预览;确认前先清 target 关浮层再执行删除,
        // 删除中(previewDeleting)拦全部出口防重复提交
        deleteTarget?.let { target ->
            AppConfirmOverlay(
                title = "删除这条瞬间?",
                message = "删除后双方都看不到这条瞬间了,照片无法恢复。确定要删除吗?",
                busy = previewDeleting,
                onConfirm = {
                    deleteTarget = null
                    removeMoment(target)
                },
                onDismiss = { if (!previewDeleting) deleteTarget = null },
            )
        }
    }
}

/**
 * 单卡:图块统一 140dp 全宽 Crop(浅灰底常驻充当加载占位),多图右上「N张」黑胶囊角标
 * (三层胶囊写法铁律 14/15;全部图进大图预览横滑——三列迷你九宫格半宽卡内 cell 过小,
 * 2026-10-17 真机反馈二轮弃用)+ 配文(空串不显,maxLines=2)+ 我/TA 与时间 + 纯展示红心
 * (不可点,点赞入口在预览浮层)。「我」= 主题色,「TA」= 暖橙(CheckinPage PartnerRingDot
 * 同款语义色);background+clickable 同节点(铁律⑤)。行高仅随配文有无变化,双列更整齐
 */
@Composable
private fun MomentCard(info: MomentInfo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val mine = info.userId == SessionStore.userId
    val timeLabel = chatDayLabelFriendly(info.createTime)
    Column(
        modifier = modifier
            .background(AppTheme.CardBg, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .height(140.dp)
                .background(Color(0xFFF5F6FA), RoundedCornerShape(12.dp)),
        ) {
            Image(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                painter = rememberAsyncImagePainter(info.imageUrls.firstOrNull() ?: info.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
            if (info.imageUrls.size > 1) {
                // 「N张」角标(三层胶囊:外层 Box 容器定位 → 内层 Box 载 background+padding →
                // Text 子节点,铁律 14/15)
                Box(
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "${info.imageUrls.size}张",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        if (info.text.isNotEmpty()) {
            Spacer(modifier = Modifier.height(7.dp))
            Text(info.text, color = AppTheme.TextPrimary, fontSize = 13.sp, maxLines = 2)
        }
        Spacer(modifier = Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (mine) "我" else "TA",
                color = if (mine) ThemeStore.current.brandStart else Color(0xFFFF9A56),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (timeLabel.isNotEmpty()) {
                Text(timeLabel, color = AppTheme.TextTertiary, fontSize = 10.sp)
            }
            if (info.likeCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${info.likeCount}",
                    color = if (info.liked) AppTheme.Danger else AppTheme.TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.width(2.dp))
                AppIcon(
                    AppIconType.Heart,
                    if (info.liked) AppTheme.Danger else AppTheme.TextTertiary,
                    Modifier.size(13.dp),
                    filled = info.liked,
                )
            }
        }
    }
}

/**
 * MomentAddOverlay — 发布瞬间浮层(M15 多图:最多 9 张;独立页内根级挂载,一次性表单:成功即收,
 * AnniversaryAddOverlay 同款浮岛式键盘避让)。M15 真机反馈裁决「没上传不调接口,点击发布才是上传」:
 * 选图 FileModule.pickImages 仅回本地 file:// 副本(零压缩零上传零网络,原生落盘规避 content URI
 * 授权衰减),getUser 热身与逐张上传全部挪到发布时点——submit = 热身 → 串行 uploadImage
 * (进度「上传中 i/N」,uploadedMap 缓存已传 URL,失败重试跳过已传张)→ MomentApi.create 头插上报。
 * 取消选图(code=1)静默不 toast;getModule=null 渐进降级 toast
 */
@Composable
private fun MomentAddOverlay(
    pager: Pager,
    onPublished: (MomentInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val localUris = remember { mutableStateListOf<String>() } // 待发本地图(file:// 原生落盘副本,发布时才上传)
    // 已传缓存(路径→服务端 URL,跨 submit 存活):发布失败重试跳过已传张不重复产服务端文件;
    // 重选同图会落新文件新路径(不命中缓存重新上传,可接受)。删除缩略图不必清(不在 localUris 即不参与)
    val uploadedMap = remember { mutableMapOf<String, String>() }
    var draft by remember { mutableStateOf("") }
    var publishing by remember { mutableStateOf(false) }
    // 发布进度文案(空串 = 空闲):「上传中 i/N…」/「发布中…」,发布钮据此显态禁点
    var publishStep by remember { mutableStateOf("") }
    // 键盘高度状态对(components.rememberKeyboardHeight 单源,0 = 收起)
    val (keyboardHeight, onKeyboardHeightChange) = rememberKeyboardHeight()

    // 仅选图(pickImages,按余量多选 remain=9-已选):零网络零上传,本地 URI 直接进缩略条;
    // 回包双形态解析(maxCount=1 单 URI 字符串 / >1 JSON 数组字符串,D-20f 同款);code=1 用户
    // 取消静默(反复试探动作,旧「未选择图片」toast 属噪);热身/上传已挪发布时点
    fun pickImage() {
        if (publishing) return
        val remain = MOMENT_IMAGE_MAX - localUris.size
        if (remain <= 0) {
            toastCouple(pager, "最多 ${MOMENT_IMAGE_MAX} 张照片")
            return
        }
        scope.launch {
            var failMsg = "选择图片失败,请重试"
            var cancelled = false
            val uris = try {
                val module = pager.getModule<FileModule>(FileModule.MODULE_NAME)
                if (module == null) {
                    failMsg = "当前端暂不支持选择照片"
                    null
                } else {
                    suspendCoroutine<List<String>?> { cont ->
                        module.pickImages(remain) { result ->
                            val code = result?.optInt("code", -1) ?: -1
                            val data = result?.optString("data").orEmpty()
                            if (code == 0 && data.isNotEmpty()) {
                                if (data.startsWith("[")) {
                                    try {
                                        val arr = JSONArray(data)
                                        val list = ArrayList<String>(arr.length())
                                        for (i in 0 until arr.length()) {
                                            arr.optString(i).takeIf { !it.isNullOrEmpty() }?.let { list.add(it) }
                                        }
                                        cont.resume(list)
                                    } catch (_: Exception) {
                                        cont.resume(null)
                                    }
                                } else {
                                    cont.resume(listOf(data))
                                }
                            } else {
                                if (code == 1) cancelled = true // 用户取消:静默
                                else result?.optString("msg")?.takeIf { it.isNotEmpty() }?.let { failMsg = it }
                                cont.resume(null)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                null
            }
            if (cancelled) return@launch
            if (uris.isNullOrEmpty()) {
                toastCouple(pager, failMsg)
            } else {
                localUris.addAll(uris.take(remain)) // 防御:超余量截断
            }
        }
    }

    // 发布 = 上传时点(用户裁决):热身(原生直传不经 HttpClient 无 401 单飞刷新)→ 逐张
    // uploadImage 串行上传(uploadedMap 缓存已传 URL,失败重试跳过已传张不重复产文件)→
    // 全部成功才 create(半成品只可能是服务端孤儿图片文件,瞬间未落库,无脏数据)。失败 toast
    // 保留浮层与已选图,可改配文/删图后重试
    fun submit() {
        if (publishing) return
        if (localUris.isEmpty()) {
            toastCouple(pager, "先选一张照片吧")
            return
        }
        publishing = true
        scope.launch {
            UserApi.getUser(pager) // 热身:焐热 token(选图阶段零 API 调用,热身属发布动作一部分)
            var failMsg = ""
            for ((index, uri) in localUris.withIndex()) {
                if (uploadedMap.containsKey(uri)) continue // 重选同图防重传(同 URI 复用已传 URL)
                publishStep = "上传中 ${index + 1}/${localUris.size}…"
                var fileUrl: String? = null
                try {
                    val module = pager.getModule<FileModule>(FileModule.MODULE_NAME)
                    if (module == null) {
                        failMsg = "当前端暂不支持上传照片"
                    } else {
                        fileUrl = suspendCoroutine { cont ->
                            module.uploadImage(
                                ApiConfig.url(pager, "/infra/file/upload"),
                                SessionStore.accessToken,
                                uri,
                                MOMENT_MAX_EDGE,
                            ) { result ->
                                val code = result?.optInt("code", -1) ?: -1
                                val data = result?.optString("data").orEmpty()
                                if (code == 0 && data.isNotEmpty()) {
                                    cont.resume(data)
                                } else {
                                    result?.optString("msg")?.takeIf { it.isNotEmpty() }?.let { failMsg = it }
                                    cont.resume(null)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    null
                }
                if (fileUrl == null) {
                    if (failMsg.isEmpty()) failMsg = "上传失败,请重试"
                    publishing = false
                    publishStep = ""
                    toastCouple(pager, failMsg)
                    return@launch
                }
                uploadedMap[uri] = fileUrl
            }
            publishStep = "发布中…"
            val result = MomentApi.create(pager, localUris.mapNotNull { uploadedMap[it] }, draft.trim())
            publishing = false
            publishStep = ""
            if (result is ApiResult.Ok) {
                val info = MomentInfo.from(result.data)
                if (info != null) onPublished(info)
                toastCouple(pager, "已发布")
                focusManager.clearFocus()
                onDismiss()
            } else {
                toastCoupleResult(pager, result)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize()
                // 键盘起时整体顶到键盘上方(原生 KeyboardParams.height 不含导航栏区域的补偿
                // 已收口 components.keyboardLiftBottomDp = 键盘高 + navBar + 12dp,与原件
                // 逐字一致),收起归零回正中(AnniversaryAddOverlay 同款几何)
                .padding(
                    bottom = if (keyboardHeight.value > 0f) {
                        keyboardLiftBottomDp(keyboardHeight.value)
                    } else {
                        0.dp
                    },
                ),
            contentAlignment = if (keyboardHeight.value > 0f) Alignment.BottomCenter else Alignment.Center,
        ) {
            // 灰幕:收键盘 + 关浮层一体
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable {
                        focusManager.clearFocus()
                        onDismiss()
                    },
            )
            Column(
                modifier = Modifier.padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                    .clickable { } // 吞点击防透传(铁律⑤:background 与 clickable 同节点)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Text(
                    "发布瞬间",
                    color = AppTheme.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(14.dp))
                // 选图区(M15 多图):未选 = 虚位引导(Plus + 文案);已选 = LazyRow 缩略条
                // (72dp 方图,本地 URI 直载 + 右上 X 删除 + 条尾余量内 Plus 追加;LazyRow 横向
                // 嵌在竖向 Column = 跨轴嵌套合法铁律);发布中拦选图/删图
                if (localUris.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .height(150.dp)
                            .background(Color(0xFFF5F6FA), RoundedCornerShape(14.dp))
                            .clickable { if (!publishing) pickImage() },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AppIcon(AppIconType.Plus, Color(0xFFC8CCD8), Modifier.size(30.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("点击选择照片", color = AppTheme.TextSecondary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text("从手机相册选,可多选至 ${MOMENT_IMAGE_MAX} 张,发布后 TA 也能看到", color = AppTheme.TextTertiary, fontSize = 10.sp)
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(localUris) { index, uri ->
                                Box(modifier = Modifier.size(72.dp)) {
                                    Image(
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                        painter = rememberAsyncImagePainter(uri),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                    )
                                    // 右上 X 删除(黑 45% 圆,发布中拦)
                                    Box(
                                        modifier = Modifier.align(Alignment.TopEnd)
                                            .padding(3.dp)
                                            .size(18.dp)
                                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                                            .clickable { if (!publishing) localUris.removeAt(index) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AppIcon(AppIconType.Close, Color.White, Modifier.size(10.dp))
                                    }
                                }
                            }
                            if (localUris.size < MOMENT_IMAGE_MAX) {
                                item {
                                    // 条尾追加块(余量内才显;background+clickable 同节点铁律⑤)
                                    Box(
                                        modifier = Modifier.size(72.dp)
                                            .background(Color(0xFFF5F6FA), RoundedCornerShape(10.dp))
                                            .clickable { if (!publishing) pickImage() },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AppIcon(AppIconType.Plus, Color(0xFFC8CCD8), Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                // 配文输入行(AppInputField 单源:固定高挂行容器、TextField 居中,原生 gravity
                // 恒 TOP|START 铁律);≤64 字服务端 @Length 兜底,端上 take 同步拦。
                // maxLines 显式传 Int.MAX_VALUE 维持原件缺省(未限行,48dp 定高容器裁剪行为不变)
                AppInputField(
                    value = draft,
                    onValueChange = { draft = it.filterNot { ch -> ch == '\n' }.take(MOMENT_TEXT_MAX) },
                    placeholder = "说点什么…(可不填)",
                    maxLines = Int.MAX_VALUE,
                    keyboardHeightChange = onKeyboardHeightChange,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${draft.length}/$MOMENT_TEXT_MAX",
                        color = AppTheme.TextTertiary,
                        fontSize = 11.sp,
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                // 发布钮收编 components.AppPrimaryButton(brandBrush 渐变主钮 + submitting
                // 拦点;44dp/22dp 圆角/15sp 对齐原件,publishStep 非空 = submitting、进度文案
                // 经 busyText 承载,守卫语义与原件逐字一致)
                AppPrimaryButton(
                    "发 布",
                    submitting = publishStep.isNotEmpty(),
                    busyText = publishStep,
                    height = 44.dp,
                    corner = 22.dp,
                    fontSize = 15.sp,
                ) { submit() }
            }
        }
    }
}

/**
 * MomentPreviewOverlay — 大图预览浮层(M15 多图横滑 + 点赞;黑底 0.9 全屏):图区 HorizontalPager
 * 逐页 ContentScale.Fit(MainPager 五 Tab 同款 API,浮层内嵌 pager 仓内首例——横滑手势与黑底
 * clickable 关闭共存,R1 真机目测)+ 多图页码胶囊(读 currentPage 重组即更新,零额外状态)+
 * 底部信息卡(配文粗体/发布者+时间/点赞钮双方均可含自己 D-20e/删除钮仅上传者本人——mine 由
 * 调用方 SessionStore.userId 判定,服务端 1_003_008_001 仍兜底);点击黑底关闭,信息卡
 * clickable 吞透传防误关;删除中拦所有出口
 */
@Composable
private fun MomentPreviewOverlay(
    info: MomentInfo,
    likeSubmitting: Boolean,
    deleting: Boolean,
    onToggleLike: (MomentInfo) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mine = info.userId == SessionStore.userId
    val timeLabel = chatDayLabelFriendly(info.createTime)
    val urls = if (info.imageUrls.isEmpty()) listOf(info.imageUrl) else info.imageUrls
    val previewPagerState = rememberPagerState(pageCount = { urls.size })
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable { if (!deleting) onDismiss() },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))
            // 大图横滑区 Fit:不裁切看全貌(黑底透出,加载中纯黑无占位可接受——预览必先过缩略图);
            // 单图 pager 页数 1 退化同静态 Image,零分支
            Box(modifier = Modifier.fillMaxWidth().weight(2.2f)) {
                HorizontalPager(
                    state = previewPagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 12.dp,
                ) { pageIndex ->
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = rememberAsyncImagePainter(urls[pageIndex]),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                    )
                }
                if (urls.size > 1) {
                    // 页码胶囊(TopCenter):currentPage 变化触发重组,零额外状态
                    Box(
                        modifier = Modifier.align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "${previewPagerState.currentPage + 1}/${urls.size}",
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            // 底部信息卡:clickable{} 吞点击防透传到黑底误关
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 30.dp + navBarSafePaddingDp())
                    .background(Color.White.copy(alpha = 0.96f), RoundedCornerShape(18.dp))
                    .clickable { }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (info.text.isNotEmpty()) {
                    Text(info.text, color = AppTheme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // 发布者+时间两行小字占 weight(1f) 弹性压缩,点赞/删除钮固定 intrinsic 宽在右
                // (2026-10-17 真机反馈:单行塞四组时尾部「删除」被裁显不全——钮恒完整)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (mine) "我发布的" else "TA 发布的",
                            color = if (mine) ThemeStore.current.brandStart else Color(0xFFFF9A56),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                        if (timeLabel.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(timeLabel, color = AppTheme.TextTertiary, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    // 点赞钮(M15):双方均可赞任意瞬间含自己(D-20e);回包真值经 toggleLike
                    // 原位替换,此处只读 info 落态;likeSubmitting/删除中拦。
                    // ⚠ 不得挂透明 background+RoundedCornerShape:纯装饰圆角也触发原生轮廓
                    // 裁剪削掉贴边文字(2026-10-17 真机反馈「删除文字显示不完整」根因),
                    // 无实底热区一律裸 clickable+padding
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { if (!likeSubmitting && !deleting) onToggleLike(info) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        AppIcon(
                            AppIconType.Heart,
                            if (info.liked) AppTheme.Danger else AppTheme.TextTertiary,
                            Modifier.size(16.dp),
                            filled = info.liked,
                        )
                        if (info.likeCount > 0) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "${info.likeCount}",
                                color = if (info.liked) AppTheme.Danger else AppTheme.TextTertiary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                    if (mine) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (deleting) "删除中…" else "删除",
                            color = AppTheme.Danger,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            // 裸 clickable+padding(透明圆角背景会轮廓裁剪削字,见上⚠)
                            modifier = Modifier
                                .clickable { if (!deleting) onDelete() }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        // 右上关闭钮(黑底上白 X,确定性出口;点击黑底任意处同样可关)
        Box(
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = 12.dp + navBarSafePaddingDp(), end = 16.dp)
                .size(36.dp)
                .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                .clickable { if (!deleting) onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconType.Close, Color.White, Modifier.size(18.dp))
        }
    }
}

/** 相册场景压缩边长(大图预览清晰;缺省 512 是头像场景,M9 冻结决策) */
private const val MOMENT_MAX_EDGE = 1080

/** 配文长度上限(与服务端 @Length(max=64) 对齐) */
private const val MOMENT_TEXT_MAX = 64

/** 单条瞬间图片上限(与服务端 @Size(max=9) 对齐,M15;发布浮层按余量多选) */
private const val MOMENT_IMAGE_MAX = 9
