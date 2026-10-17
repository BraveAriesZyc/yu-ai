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
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.pullToRefreshItem
import com.tencent.kuikly.compose.material3.rememberPullToRefreshState
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.views.KeyboardParams
import com.zyc.love_baby.TextField
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.base.FileModule
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconButton
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.data.model.MomentInfo
import com.zyc.love_baby.features.chat.chatDayLabelFriendly
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.features.mood.TimelineLoadFailed
import com.zyc.love_baby.features.mood.TimelineLoading
import com.zyc.love_baby.network.ApiConfig
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.MomentApi
import com.zyc.love_baby.network.UserApi
import com.zyc.love_baby.state.SessionStore
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.launch

/**
 * MomentPage — 我们的相册页(@Page 注册名 "momentPage"):双列瞬间墙 + 发布浮层 + 大图预览(M9)
 * 用法:我的页「我们的相册」菜单 RouterModule.openPage 打开;返回键 closePage。
 *      独立二级页(CheckinPager 同款壳,不走 MainPager 五 Tab)
 * 依赖:MomentApi/MomentInfo(页内自持不进 AppState,M7 时间线页先例——页销毁即弃,登出/解绑零
 *      reset);FileModule.pickAndUpload(maxEdge=1080,M4 上传链路复用);couple 组 toast;
 *      chat.chatDayLabelFriendly(时间文案);mood.TimelineLoading/LoadFailed;RouterModule(返回)
 * 事件:返回钮 closePage;卡片点击开大图预览;FAB「+」开发布浮层;失败态「重试」重拉;
 *      「加载更多」beforeId=当前最小 id 翻页;下拉刷新(官方 pullToRefreshItem,ChatPage 先例)重拉首页
 * 功能:
 *  - 双列瞬间墙:LazyColumn + chunked(2) Row 自排(CheckinPage 日历同款自排,非 LazyVerticalGrid
 *    ——2.29 官方 pullToRefreshItem 只挂 LazyListScope(反编译 javap 锤实,scrollState 参数即
 *    LazyListState),计划里「网格+官方下拉刷新」二者取官方下拉刷新;图片定高故行高恒对齐)
 *  - 卡片:图定高 140dp Crop 圆角(浅灰底常驻充当加载占位)+ 配文(空串不显)+ 我/TA 与时间;
 *    点击进大图预览
 *  - 发布浮层:选图即上传(pickAndUpload maxEdge=1080,suspendCoroutine 包装,MainPager
 *    submitAvatarUpload 同款)+ 配文输入(定高行居中)+ 发布(POST create,回包头插落态不重拉)
 *  - 大图预览:黑底 0.9 全屏 Fit 展示 + 底部信息卡(配文/发布者/时间/删除钮仅上传者本人
 *    ——服务端 1_003_008_001 兜底,端上只显不拦校验);点击黑底关闭,信息卡 clickable 吞透传
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
    // 页内自持(M7 时间线页先例):id 倒序新→旧,服务端序原样保留不重排
    val moments = remember { mutableStateListOf<MomentInfo>() }
    // 官方下拉刷新与列表手势共享同一 state 实例(2.29 pullToRefreshItem 的 scrollState 参数
    // 必须是 LazyColumn 自身的 LazyListState,否则手势判定错位)
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState(isRefreshing = refreshing)

    // 拉首页(beforeId=0):load 进页 / 下拉刷新共用;成功清空重填,失败置 loadFailed(刷新静默)
    suspend fun fetchFirst() {
        val result = MomentApi.list(pager, "0", PAGE_SIZE)
        val ok = result is ApiResult.Ok
        if (ok) {
            val list = MomentInfo.listFrom((result as ApiResult.Ok).dataArray)
            hasMore = list.size >= PAGE_SIZE
            moments.clear()
            moments.addAll(list)
        }
        loadFailed = !ok
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

    // 独立页每次打开都是新 Pager 组合,LaunchedEffect(Unit) 即每次进入都重拉
    LaunchedEffect(Unit) { load() }

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
                AppHeaderTitle("我们的相册", "每一帧都值得收藏")
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                // 浮层(发布/大图预览)开着时关列表滚动:遮罩 clickable 不吃拖拽,
                // 背景列表原生滚动仍收事件(MinePage/MoodPage 同款,2026-10-17 真机实证)
                userScrollEnabled = !showAdd && preview == null,
            ) {
                // 下拉刷新(官方 pullToRefreshItem 须为首个 item,ChatPage 先例):重拉首页
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
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) { TimelineLoading() }
                    }
                    loadFailed -> item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) { TimelineLoadFailed { load() } }
                    }
                    moments.isEmpty() -> item { MomentEmptyHint() }
                    else -> {
                        // 双列自排:chunked(2) Row(CheckinPage 日历同款);头插/删除按整行重排,量级可接受
                        moments.chunked(2).forEach { rowItems ->
                            item(key = "moment-${rowItems.first().id}") {
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
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (loadingMore) "加载中…" else "加载更多",
                                        color = if (loadingMore) AppTheme.TextTertiary else theme.brandStart,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .background(
                                                if (loadingMore) Color.Transparent else theme.brandStart.copy(alpha = 0.08f),
                                                RoundedCornerShape(14.dp),
                                            )
                                            .clickable { if (!loadingMore) loadMore() }
                                            .padding(horizontal = 16.dp, vertical = 7.dp),
                                    )
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
                    moments.add(0, info) // 发布成功头插落态(create 回包含服务端 id/createTime)
                    hasMore = true
                },
                onDismiss = { showAdd = false },
            )
        }

        preview?.let { info ->
            MomentPreviewOverlay(
                info = info,
                deleting = previewDeleting,
                onDelete = { removeMoment(info) },
                onDismiss = { preview = null },
            )
        }
    }
}

/** 空态:软色 Heart 圆底 + 引导文案(AnniversaryEmptyHint 同款结构) */
@Composable
private fun MomentEmptyHint() {
    val theme = ThemeStore.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp)
                .background(theme.brandStart.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconType.Heart, theme.brandStart, Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("还没有瞬间", color = AppTheme.TextSecondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("点右下角 + 晒一张今天的照片吧", color = AppTheme.TextTertiary, fontSize = 11.sp)
    }
}

/**
 * 单卡:定高图(Crop 圆角,浅灰底常驻充当加载占位)+ 配文(空串不显)+ 我/TA 与时间。
 * 「我」= 主题色,「TA」= 暖橙(CheckinPage PartnerRingDot 同款语义色);
 * background+clickable 同节点(铁律⑤)
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
                painter = rememberAsyncImagePainter(info.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        }
        if (info.text.isNotEmpty()) {
            Spacer(modifier = Modifier.height(7.dp))
            Text(info.text, color = AppTheme.TextPrimary, fontSize = 13.sp)
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
        }
    }
}

/**
 * MomentAddOverlay — 发布瞬间浮层(独立页内根级挂载,一次性表单:成功即收,AnniversaryAddOverlay
 * 同款浮岛式键盘避让)。选图区点击 → FileModule.pickAndUpload(maxEdge=1080)选图即上传
 * (suspendCoroutine 包装,MainPager.submitAvatarUpload 先例:先 getUser 热身保 token 新鲜
 * ——原生直传不经 HttpClient 无 401 单飞;getModule=null 渐进降级 toast),上传中蒙层拦点击;
 * 成功显缩略图,可再点重选(覆盖 uploadedUrl)。发布校验:未选图 toast 拦;POST create 成功
 * 回包 MomentInfo 头插上报后关浮层,失败 toast 不关
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
    var uploadedUrl by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var publishing by remember { mutableStateOf(false) }
    var keyboardHeight by remember { mutableStateOf(0f) }
    val onKeyboardHeightChange: (KeyboardParams) -> Unit = remember {
        { params: KeyboardParams -> keyboardHeight = params.height }
    }

    // 选图+压缩+直传一条龙(maxEdge=1080 供大图预览清晰);链路与 MainPager.submitAvatarUpload
    // 同构:热身 → getModule 判空降级 → suspendCoroutine 一次性回调收口
    fun pickImage() {
        if (uploading) return
        uploading = true
        scope.launch {
            UserApi.getUser(pager) // 热身:原生直传不经 HttpClient(无 401 单飞刷新),先焐热 token
            var failMsg = "上传失败,请重试"
            val fileUrl = try {
                val module = pager.getModule<FileModule>(FileModule.MODULE_NAME)
                if (module == null) {
                    failMsg = "当前端暂不支持选择照片"
                    null
                } else {
                    suspendCoroutine<String?> { cont ->
                        module.pickAndUpload(
                            ApiConfig.url(pager, "/infra/file/upload"),
                            SessionStore.accessToken,
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
            uploading = false
            if (fileUrl.isNullOrEmpty()) {
                toastCouple(pager, failMsg)
            } else {
                uploadedUrl = fileUrl // 选图即上传:成功直接落缩略图;重选覆盖
            }
        }
    }

    fun submit() {
        if (publishing) return
        if (uploadedUrl.isEmpty()) {
            toastCouple(pager, "先选一张照片吧")
            return
        }
        publishing = true
        scope.launch {
            val result = MomentApi.create(pager, uploadedUrl, draft.trim())
            publishing = false
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
                // 键盘起时整体顶到键盘上方(原生 KeyboardParams.height 不含导航栏区域),
                // 收起归零回正中(AnniversaryAddOverlay 同款几何)
                .padding(
                    bottom = if (keyboardHeight > 0f) {
                        keyboardHeight.dp + navBarSafePaddingDp() + 12.dp
                    } else {
                        0.dp
                    },
                ),
            contentAlignment = if (keyboardHeight > 0f) Alignment.BottomCenter else Alignment.Center,
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
                // 选图区:未选 = 虚位引导(Plus + 文案);已选 = 缩略图 + 右上「重选」角标;
                // 上传中蒙层拦点击(background 常驻不分支,铁律③)
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .height(150.dp)
                        .background(Color(0xFFF5F6FA), RoundedCornerShape(14.dp))
                        .clickable { if (!uploading) pickImage() },
                ) {
                    if (uploadedUrl.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AppIcon(AppIconType.Plus, Color(0xFFC8CCD8), Modifier.size(30.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("点击选择照片", color = AppTheme.TextSecondary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text("从手机相册选一张,发布后 TA 也能看到", color = AppTheme.TextTertiary, fontSize = 10.sp)
                        }
                    } else {
                        Image(
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                            painter = rememberAsyncImagePainter(uploadedUrl),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(11.dp))
                                .clickable { if (!uploading) pickImage() }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("重选", color = Color.White, fontSize = 11.sp)
                        }
                    }
                    if (uploading) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("上传中…", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                // 配文输入行:固定高挂行容器、TextField 自适应行高居中(原生 gravity 恒 TOP|START,
                // AnnivInputRow 同款铁律);≤64 字服务端 @Length 兜底,端上 take 同步拦
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .height(48.dp)
                        .background(Color(0xFFF5F6FA), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        modifier = Modifier.weight(1f),
                        value = draft,
                        onValueChange = { draft = it.filterNot { ch -> ch == '\n' }.take(MOMENT_TEXT_MAX) },
                        placeholder = "说点什么…(可不填)",
                        placeholderColor = AppTheme.TextTertiary,
                        autoFocus = false,
                        textStyle = TextStyle(color = AppTheme.TextPrimary, fontSize = 15.sp),
                        keyboardHeightChange = onKeyboardHeightChange,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${draft.length}/$MOMENT_TEXT_MAX",
                        color = AppTheme.TextTertiary,
                        fontSize = 11.sp,
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                PublishButton(publishing) { submit() }
            }
        }
    }
}

/** 发布钮(品牌渐变圆角大钮,BindPrimaryButton 语义本域私有复制——浮层提交场景文案不同) */
@Composable
private fun PublishButton(publishing: Boolean, onClick: () -> Unit) {
    val theme = ThemeStore.current
    Box(
        modifier = Modifier.fillMaxWidth()
            .height(44.dp)
            .background(theme.brandBrush, RoundedCornerShape(22.dp))
            .clickable { if (!publishing) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (publishing) "发布中…" else "发 布",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * MomentPreviewOverlay — 大图预览浮层(黑底 0.9 全屏,新写无先例):大图 ContentScale.Fit
 * 最大化展示 + 底部信息卡(配文粗体/发布者+时间/删除钮仅上传者本人——mine 由调用方
 * SessionStore.userId 判定,服务端 1_003_008_001 仍兜底);点击黑底关闭,信息卡 clickable
 * 吞透传防误关;删除中拦所有出口
 */
@Composable
private fun MomentPreviewOverlay(
    info: MomentInfo,
    deleting: Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mine = info.userId == SessionStore.userId
    val timeLabel = chatDayLabelFriendly(info.createTime)
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable { if (!deleting) onDismiss() },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))
            // 大图 Fit:不裁切看全貌(黑底透出,加载中纯黑无占位可接受——预览必先过缩略图)
            Image(
                modifier = Modifier.fillMaxWidth().weight(2.2f),
                painter = rememberAsyncImagePainter(info.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Fit,
            )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (mine) "我发布的" else "TA 发布的",
                        color = if (mine) ThemeStore.current.brandStart else Color(0xFFFF9A56),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (timeLabel.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(timeLabel, color = AppTheme.TextTertiary, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (mine) {
                        Text(
                            if (deleting) "删除中…" else "删除",
                            color = AppTheme.Danger,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .background(Color.Transparent, RoundedCornerShape(14.dp))
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
