package com.zyc.love_baby.features.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.tencent.kuikly.compose.foundation.layout.widthIn
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.LazyListState
import com.tencent.kuikly.compose.foundation.lazy.LazyRow
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.lazy.itemsIndexed
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.maxLength
import com.tencent.kuikly.compose.material3.CircularProgressIndicator
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.pullToRefreshItem
import com.tencent.kuikly.compose.material3.rememberPullToRefreshState
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.SolidColor
import com.tencent.kuikly.compose.ui.input.pointer.PointerEventPass
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.input.pointer.positionChangeIgnoreConsumed
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.max
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.views.KeyboardParams
import com.zyc.love_baby.TextField
import com.zyc.love_baby.components.AppAvatar
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconButton
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.KuiklyTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.state.ChatMsg
import com.zyc.love_baby.state.ChatStore
import com.zyc.love_baby.state.CoupleBindState
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.MsgKind
import com.zyc.love_baby.state.SessionStore
import com.zyc.love_baby.state.appIconFromName
import com.zyc.love_baby.state.colorFromHex
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ChatPage — 聊天页:情侣对话气泡 + 心愿消息卡 + 快捷贴纸 + 输入框(M3 服务端同步版 + 视觉重排)
 * 用法:ChatPage(pager, myNickname = profile?.nickname.orEmpty(), pageVisible = selectedTab == 3),
 *      消息状态在 ChatStore(lastId 增量游标,与心情页联动);Pager 常驻五页仅组合一次,首拉
 *      bindState 驱动,后续刷新靠 MainPager 切 Tab 副作用 + 页内 5s 轮询
 * 依赖:ChatStore/ThemeStore/CoupleStore/SessionStore/AppIcons/BasicWidget TextField/AppIconButton;
 *      MessageApi 经 ChatStore 走透;ChatTime.kt(时间格式化);MessageModels MessageInfo.createTime
 * 入参:myNickname 我方昵称(MainPager 单一数据源),空串兜底头像首字「我」;伴侣昵称直读 CoupleStore;
 *      pageVisible = selectedTab == 3(MainPager 传入,targetPage 翻转即翻转,驱动进页无条件滚底)
 * 事件:发送文本/贴纸 → ChatStore.send(POST 成功才入列清输入,失败 toast 保留输入);心愿卡来自
 *      MoodPage 送出;失败态重试胶囊 → ChatStore.loadInitial
 * 功能:
 *  - 头部居中情侣构图:双头像对居中(我方半透白 + TA 白底主色字 + 中缝心形徽章)+ 标题副标题居中;
 *    气泡头像按昵称首字,TA 配色随主题(brandStart 14% 洗底),无硬编码粉;输入框单行≈44dp 与发送钮等高;
 *    键盘避让补偿原生 height 不含导航栏的缺口(高度 + navBarSafe + 12dp)
 *  - 消息列表(IM 三层时间体系):日期分隔条 + 同发送者连续消息聚合(头像只在组尾、组内紧凑)+
 *    组尾头像正下单行 HH:mm(两侧对称,2026-10-16 Q1:原 TA 方 42dp 落头像-气泡夹缝错位);
 *    分隔条/时间行内联在消息 item 内,消息与 LazyColumn 下标恒 1:1,
 *    轮询追加/增量补拉不位移索引(条目 [0]=pullToRefresh、[1]=顶部 Spacer、[2..size+1]=消息、
 *    [size+2]=尾部 Spacer)
 *  - 滚底四触发(2026-10-16 体验修复 P2/P3):①进页无条件回底(pageVisible)/②本端发送无条件回底
 *    (pendingSendScroll 标志 + triggerSendScroll 直接兜底:5s 轮询抢先把同条消息入列时 size 不变、
 *    size effect 不触发,兜底承接;键盘开着发送不再被贴底守卫拦死)/③追加仅贴底跟随(轮询/补拉收
 *    TA 消息,守卫防拽底 F0/F9)/④键盘弹起且贴底时跟随(keyboardHeightChange 回调捕获收缩前快照,
 *    等视口收缩 measure 落地后钉底)。目标一律尾部 Spacer 下标 size+2,且先 awaitMeasured 等
 *    measure 落地再 snap 版 scrollToItem(Kuikly 程序化滚动的 stale-totalItemsCount/陈旧
 *    canScrollForward 吞点会静默吞掉过早的滚动,详见 CLAUDE.md 铁律13)
 *  - 文本气泡 widthIn(max=260) 长句封顶折行(F3);心愿卡改心愿色洗底软卡(F10);气泡背景
 *    single-brush(2026-10-16 Q2:我方 brandBrush/TA 同色渐变纯白同走 brush 重载,混用 color
 *    重载会原生层冻结白层——发送气泡概率纯白根因,见 taBubbleBrush)
 *  - 首拉失败显式错误态 + 重试入口,与真空态分家(F4);首拉 key=SessionStore.userId(重登录闭环)
 *  - 软键盘避让:TextField keyboardHeightChange 回传键盘高度,输入行 bottom padding 切换顶起(F2/F7),
 *    列表视口随 weight 收缩由键盘跟随 effect 钉底,最新消息始终在输入条上方(P2,微信/QQ 形态)
 *  - 拖拽收键盘(2026-10-16 Q3):键盘开着手指拖列表竖向越 touchSlop 即 clearFocus(微信语义),
 *    列表容器被动观察器不消费事件不扰原生滚动;程序化滚底无指针事件天然不误伤(LazyColumn 注释)
 *  - 下拉刷新/停留 5s 轮询/切 Tab 补拉在 ChatStore 串行(指示器不闪现);文本 500 字上限 + 3 行输入
 *  - ⚠ partnerReply 本地假回复已随 M3 整体移除,TA 的消息靠 message/list 增量拉取(§7.4)
 */
private val quickStickers = listOf("贴贴", "mua~", "想你了", "举高高", "晚安鸭")

/** 文本长度上限(与服务端 AppMessageSendReqVO @Length(max=500) 对齐;maxLength 原生层拒绝 + take 兜底) */
private const val INPUT_MAX_LENGTH = 500

/**
 * TA 气泡底:同色渐变(视觉=纯白,AppAvatar 同款先例)。⚠ 必须与我方 brandBrush 同走
 * background(brush=) 重载——Kuikly 的 background(color=) 与 background(brush=) 写两个不同
 * 原生 prop(backgroundColor/backgroundImage),映射原生视图 LayerDrawable 的两个独立图层且
 * 层序首次创建后冻结:同一气泡视图若先后走过两重载(Lazy 槽位跨 mine/TA 复用),白实色层会
 * 冻结在渐变层上方,此后该视图上所有我方气泡被白层遮盖成「纯白气泡+白字」(2026-10-16 真机
 * 概率复现,Q2)。纯色一律同色渐变归一,仅参数值切换、重载类型不得切换(铁律⑦)
 */
private val taBubbleBrush = Brush.linearGradient(listOf(AppTheme.CardBg, AppTheme.CardBg))

@Composable
fun ChatPage(pager: Pager, myNickname: String = "", myAvatar: String = "", pageVisible: Boolean = false) {
    val theme = ThemeStore.current
    var inputText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var keyboardHeight by remember { mutableStateOf(0f) } // 键盘高度(dp,KeyboardParams.height),0 = 收起
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // P3a 发送滚底标志(2026-10-16):本端发送(文本/贴纸)POST 成功置位,由 messages.size effect
    // 与 triggerSendScroll 的直接兜底协程双路消费——无条件滚底绕过贴底守卫(键盘开着时守卫恒
    // false,正是「发送不滚底」的主诉根因)。两处「判定+清位」之间均无挂起点,Kuikly 单线程
    // 事件循环下恰一方消费,双触发同目标幂等。ChatStore 不混 UI 滚动状态(验证员裁决:
    // AppState.kt 零改动),标志留页内
    var pendingSendScroll by remember { mutableStateOf(false) }
    // 上一帧消息条数:0→非空转变视为「首屏落位」而非「追加跟随」,无条件回底——用户停在聊天 Tab
    // 时首拉才返回(空→非空、锚在顶部、新鲜贴底判定为 false)的场景走守卫会停在最旧消息
    var lastSeenSize by remember { mutableStateOf(0) }
    // P2 键盘跟随捕获态(捕获时机见 onKeyboardHeightChange):回调内读到的收缩前贴底态/收缩前
    // 视口高 + 触发序号(序号变化驱动下方消费 effect 重启,一帧一协程,后帧取消前帧)
    var kbFollowNearBottom by remember { mutableStateOf(false) }
    var kbFollowPreViewport by remember { mutableStateOf(0) }
    var kbFollowSeq by remember { mutableStateOf(0) }
    // P3a 发送滚底触发器(2026-10-16 评审加固):置标志 + 直接启动兜底滚底。为什么不能只靠
    // messages.size effect:5s 轮询可能抢在 send 回包前把同一条消息 appendDedupe 入列(服务端
    // 已落库 send,回包晚到),send 返回 Ok 时 dedupe 空转、size 不变,effect 永不重启 → 标志
    // 滞留,发送不滚底(主诉复现),且残留标志会在之后某次无关 size 变化时误发一次滚底。
    // 兜底同样先 awaitMeasured 等 measure 落地(规避 stale totalItemsCount 吞点)再 snap 滚底。
    // remember 缓存满足铁律⑩(捕获的 scope/listState/页内标志委托均跨重组稳定)
    val triggerSendScroll: () -> Unit = remember(scope, listState) {
        {
            pendingSendScroll = true
            scope.launch {
                listState.awaitMeasured(ChatStore.messages.size + 3)
                if (pendingSendScroll) {
                    pendingSendScroll = false
                    listState.scrollToItem(ChatStore.messages.size + 2)
                }
            }
        }
    }
    val messages = ChatStore.messages
    // Q3 拖拽收键盘(2026-10-16):键盘开着时手指拖列表 → clearFocus 收键盘(微信语义)。
    // 观察器挂列表容器(见 LazyColumn modifier),被动不消费事件;程序化滚底(scrollToItem
    // 只写原生偏移)不产生任何指针事件,天然不误伤发送/进页/贴底跟随滚底(铁律13)
    val focusManager = LocalFocusManager.current

    // 昵称首字(§1/§7):take(1) 按 code point,emoji 昵称不截坏;空昵称兜底「TA」/「我」。
    // 头像 URL(文件服务里程碑):我方走 MainPager 下发的 profile.avatar,TA 方走 CoupleStore
    // couple/get 回查;空走首字形态(AppAvatar 内部分支,加载中背景圆透出占位)
    val partnerName = CoupleStore.couple?.partnerNickname?.trim().orEmpty().ifEmpty { "TA" }
    val partnerInitial = partnerName.take(1)
    val myInitial = myNickname.trim().take(1).ifEmpty { "我" }
    val partnerAvatar = CoupleStore.couple?.partnerAvatar.orEmpty()

    // 首拉(§7.4):首组合空游标 → 最近一页正序覆盖;loadInitial 幂等(initialLoaded 标志)。
    // key 从 Unit 改为 userId:常驻堆叠页跨登出/换号不重组合,Unit key 会残留上一会话的失败标志,
    // userId 变化即重判(拉取协议本身不变);失败置 loadFailed 显式错误态,成功 messages 非空自动消失
    // 首拉(登录+已绑定才拉;Pager 化改造,语义同 TodoPage):五页冷启动即组合,未登录/未绑定
    // 不拉防脏失败态;校准/绑定翻 BOUND 时首拉(UNBOUND→BOUND 重绑场景 = D-11 清场后从零首拉);
    // 登出 userId 清空即复位失败态
    LaunchedEffect(SessionStore.userId, CoupleStore.bindState) {
        if (SessionStore.userId.isEmpty() || CoupleStore.bindState != CoupleBindState.BOUND) {
            loadFailed = false
            return@LaunchedEffect
        }
        loadFailed = ChatStore.loadInitial(pager) !is ApiResult.Ok
    }
    // 下拉刷新(§7.4):官方 pullToRefreshItem(须为 LazyColumn 首个 item),刷新走 loadIncrement;
    // Store 层在飞串行等待(F13),指示器到真正拉完才收起;成功即消除失败态
    val pullState = rememberPullToRefreshState(isRefreshing = refreshing)
    // ── 滚底触发(2026-10-16 体验修复 P2/P3,整体替换旧「animateScrollToItem + 陈旧守卫」路径):
    // Kuikly 程序化滚动有两个静默吞点(详见 CLAUDE.md 铁律13)——①layoutInfo 只反映上一次 measure,
    // 追加后立刻 scrollToItem(新目标)撞 stale totalItemsCount 越界守卫被静默吞;②animateScrollToItem
    // 首帧 scrollBy 撞陈旧 canScrollForward=false 闸门返回 0 消耗,动画按「Hit the end」取消。
    // 故全部改 snap 版 scrollToItem( suspend + forceRemeasure)且先 awaitMeasured 等含新数据的
    // measure 真正落地,目标一律尾部 Spacer 下标 size+2(条目结构见类注释)
    LaunchedEffect(messages.size) {
        val prevSize = lastSeenSize
        lastSeenSize = ChatStore.messages.size
        val current = ChatStore.messages // effect 内直读 Store,防捕获重组期旧列表实例
        if (!pageVisible || current.isEmpty()) return@LaunchedEffect
        listState.awaitMeasured(current.size + 3) // 非空总项数 = 消息数 + 3
        if (pendingSendScroll) {
            // ② 本端发送:明确的到底意图,无条件滚(键盘开着时贴底守卫恒 false,守卫路径拦死发送滚底)
            pendingSendScroll = false
            listState.scrollToItem(current.size + 2)
        } else if (prevSize == 0) {
            // 空→非空 = 首屏落位:无条件回底(不走守卫)
            listState.scrollToItem(current.size + 2)
        } else if (!listState.isScrollInProgress && listState.isNearBottom()) {
            // ③ 追加跟随:新鲜读贴底才滚 + 用户拖拽/惯性中不拽(F0/F9 上翻读历史语义)
            listState.scrollToItem(current.size + 2)
        }
    }
    // ① 进页滚底(P3b,微信语义:进入会话 = 看最新):pageVisible 由 MainPager 传 selectedTab == 3
    // (targetPage,点切瞬移/拖拽过半即翻转)。冷启动首拉在页外完成、再进入无新消息等
    // 「size 不变则无任何触发点」的空窗由此承接;与追加跟随并存幂等(同目标同落点)
    LaunchedEffect(pageVisible) {
        if (pageVisible && ChatStore.messages.isNotEmpty()) {
            val current = ChatStore.messages
            listState.awaitMeasured(current.size + 3)
            listState.scrollToItem(current.size + 2)
        }
    }
    // ④ 键盘跟随消费(P2):等本次键盘步进对应的视口收缩 measure 真正落地(视口高 < 回调捕获的
    // 收缩前值;layoutInfoState 是 neverEqualPolicy 的 mutableState,每次 measure 必发新值,
    // 收缩已发生则 snapshotFlow 首帧即满足),捕获时贴底才钉底——上翻读历史者全程不拽。
    // 收起/候选栏收起(视口变高)永不满足谓词,500ms 超时兜底丢弃,lazy clamp 自动保持贴底,
    // 无需反向恢复;IME 动画逐帧步进每帧重启本 effect,后帧取消前帧,最终帧钉住终态
    LaunchedEffect(kbFollowSeq) {
        if (kbFollowSeq == 0) return@LaunchedEffect
        val preViewport = kbFollowPreViewport
        withTimeoutOrNull(500) {
            snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it < preViewport }
        } ?: return@LaunchedEffect
        if (kbFollowNearBottom && ChatStore.messages.isNotEmpty()) {
            listState.scrollToItem(ChatStore.messages.size + 2)
        }
    }
    // P2 键盘跟随捕获:onKeyboardHeightChange 回调是「本次重组/测量前」唯一可靠读点——
    // LaunchedEffect 里读 layoutInfo 时视口已收缩完,贴底判定与收缩前视口高双双失真,故必须在
    // 回调内捕获快照(remember 化,铁律⑩;BasicWidget 内部 rememberUpdatedState 透传)。
    // 只跟上升(收起不滚);钉底后 canScrollForward=false 使后续步进帧贴底判定自持为真,跟随链不断
    val onKeyboardHeightChange: (KeyboardParams) -> Unit = remember {
        { params: KeyboardParams ->
            val rising = params.height > keyboardHeight // 写前读 = 上一帧键盘高
            if (rising && params.height > 0f) {
                val info = listState.layoutInfo // 收缩前快照(此帧重组/测量尚未发生)
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                kbFollowNearBottom = !listState.canScrollForward ||
                    lastVisible >= info.totalItemsCount - 2
                kbFollowPreViewport = info.viewportSize.height
                kbFollowSeq++
            }
            keyboardHeight = params.height
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 渐变头部(2026-10-17 重构:居中舞台构图 → 左对齐紧凑行,与四个兄弟 Tab 头部
            // 语言统一;原双头像簇+压缝心形徽章母题保留,整簇缩进行内,头部省 ~40dp 给消息列表)
            AppGradientHeader {
                // 双头像簇(86x40 舞台):左我(白 30% 磨砂配角)右 TA(白底主色字视觉焦点),
                // 中缝 18dp 白圆心形徽章点题
                Box(modifier = Modifier.width(86.dp).height(40.dp)) {
                    AppAvatar(
                        myInitial,
                        size = 40.dp,
                        bgColor = Color.White.copy(alpha = 0.30f),
                        fontSize = 15.sp,
                        avatarUrl = myAvatar,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    AppAvatar(
                        partnerInitial,
                        size = 40.dp,
                        bgColor = Color.White,
                        textColor = theme.brandStart,
                        fontSize = 15.sp,
                        avatarUrl = partnerAvatar,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                    Box(
                        modifier = Modifier.align(Alignment.Center)
                            .size(18.dp)
                            .background(Color.White, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIconType.Heart, theme.brandStart, Modifier.size(9.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                AppHeaderTitle("悄悄话", "和${partnerName}的悄悄话")
            }

            // 消息列表(§2)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    // Q3 拖拽收键盘:被动手势观察器(微信语义,键盘开着拖列表即收)。
                    // 三个不可妥协点(2.29 源码锤实):①全程不调 change.consume()——Compose
                    // 侧消费移动才会 preventTouch 阻断原生触摸,不消费 = 原生滚动/惯性/子项
                    // 点击/下拉刷新全部无感;②pass 一律 Initial + requireUnconsumed=false,
                    // 位移读 positionChangeIgnoreConsumed()——原生层接管滚动后事件带预消费
                    // 标记,普通 positionChange() 对已消费事件返回 Zero 会检测失灵;③竖向
                    // 位移越 touchSlop 且竖向占优(dy>dx)才收——横滑翻页由 MainPager 切 Tab
                    // 的 clearFocus 承接,轻点气泡/按钮不越 slop 不收。key=focusManager(页内
                    // 稳定实例;勿用逐帧变化值做 key,重组会重启手势协程)。手势 handler 内
                    // 勿用 withTimeoutOrNull(2.29 该函数是 no-op 存根)
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
            ) {
                pullToRefreshItem(
                    state = pullState,
                    onRefresh = {
                        refreshing = true
                        scope.launch {
                            val result = ChatStore.loadIncrement(pager)
                            if (result is ApiResult.Ok) loadFailed = false
                            refreshing = false
                        }
                    },
                    scrollState = listState,
                ) { progress, isRefreshing, _ ->
                    Text(
                        when {
                            isRefreshing -> "刷新中…"
                            progress >= 1f -> "松手刷新"
                            else -> "下拉刷新"
                        },
                        color = AppTheme.TextTertiary,
                        fontSize = 12.sp,
                    )
                }
                item { Spacer(modifier = Modifier.height(14.dp)) }
                // 空态 / 失败态分家(F4,§6):首拉失败不再落「还没有悄悄话」假空态误导
                if (messages.isEmpty()) {
                    item {
                        if (loadFailed) {
                            ChatLoadError(
                                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                                onRetry = {
                                    scope.launch {
                                        loadFailed = ChatStore.loadInitial(pager) !is ApiResult.Ok
                                    }
                                },
                            )
                        } else {
                            ChatEmptyHint(modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
                        }
                    }
                }
                itemsIndexed(
                    messages,
                    key = { _, m -> m.id }, // appendDedupe 保证唯一
                    // contentType 三分(2026-10-16 Q2):text 池按 mine/TA 隔离,气泡视图槽位
                    // 永不跨我方/对方复用——即便未来重新引入 color 路径背景,池子隔离也兜底
                    // (每池上限 7,聊天量级无感)
                    contentType = { _, m ->
                        when {
                            m.kind == MsgKind.WISH -> "wish"
                            m.isMine -> "text-mine"
                            else -> "text-ta"
                        }
                    },
                ) { idx, msg ->
                    val prev = messages.getOrNull(idx - 1)
                    val next = messages.getOrNull(idx + 1)
                    // 本条上方是否出日期分隔条(createTime<=0 → 整层静默隐藏,不造假数据)
                    val showDayDivider = msg.createTime > 0L &&
                        (
                            prev == null || prev.createTime <= 0L ||
                                chatDayIndex(prev.createTime) != chatDayIndex(msg.createTime)
                            )
                    // 下一条上方是否出分隔条(= 本条是被「跨天/会话尾」截断的组尾;
                    // 分隔条件并入分组判定,跨天同发送者的前一条不丢头像与时间)
                    val nextHasDivider = next != null && next.createTime > 0L &&
                        (msg.createTime <= 0L || chatDayIndex(msg.createTime) != chatDayIndex(next.createTime))
                    val groupStart = prev == null || prev.isMine != msg.isMine || showDayDivider
                    val groupTail = next == null || next.isMine != msg.isMine || nextHasDivider
                    val topPad = when {
                        showDayDivider -> 8.dp // 分隔条自带与上组的呼吸
                        groupStart -> 10.dp // 组间
                        else -> 3.dp // 组内紧凑
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = AppTheme.PageHPadding)
                            .padding(top = topPad),
                    ) {
                        if (showDayDivider) {
                            // 日期分隔条:单节点(背景+文字同节点,无 clickable),整宽居中胶囊
                            Text(
                                chatDayLabel(msg.createTime),
                                fontSize = 10.sp,
                                color = AppTheme.TextSecondary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                    .background(AppTheme.TextTertiary.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            if (!msg.isMine) {
                                if (groupTail) {
                                    Avatar(mine = false, name = partnerInitial, avatarUrl = partnerAvatar)
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    // 组首/组内透明占位(34 头像 + 8 间距),无背景无点击,纯布局节点
                                    Spacer(modifier = Modifier.width(42.dp))
                                }
                            }
                            when (msg.kind) {
                                MsgKind.TEXT -> TextBubble(msg.text, mine = msg.isMine, groupTail = groupTail, theme = theme)
                                MsgKind.WISH -> WishBubble(msg, theme = theme) // 卡片语言,不吃撇角
                            }
                            if (msg.isMine) {
                                Spacer(modifier = Modifier.width(8.dp))
                                if (groupTail) {
                                    Avatar(mine = true, name = myInitial, avatarUrl = myAvatar)
                                } else {
                                    Spacer(modifier = Modifier.width(34.dp))
                                }
                            }
                        }
                        // 组尾时间行(F8):挂在头像正下方(两侧对称)——我方 align(End) 贴右缘 =
                        // 我方头像正下;TA 方默认 Start(x=0)= TA 头像正下。原 TA 方
                        // padding(start=42) 落在头像(34dp)与气泡的夹缝,既不贴头像也不随气泡,
                        // 真机反馈「头像下面的时间位置不对」(2026-10-16 Q1),归头像竖向车道
                        if (groupTail && msg.createTime > 0L) {
                            Text(
                                chatHHmm(msg.createTime),
                                fontSize = 10.sp,
                                color = AppTheme.TextTertiary,
                                modifier = if (msg.isMine) {
                                    Modifier.align(Alignment.End).padding(top = 2.dp)
                                } else {
                                    Modifier.padding(top = 2.dp)
                                },
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(10.dp)) }
            }

            // 快捷贴纸(F17:白底胶囊在近白页底上对比度过低改 Divider 实底;sending 期间拦点击)
            LazyRow(
                modifier = Modifier.padding(horizontal = AppTheme.PageHPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(quickStickers) { sticker ->
                    Box(
                        modifier = Modifier.background(AppTheme.Divider, RoundedCornerShape(15.dp))
                            .clickable {
                                if (!sending) {
                                    sending = true
                                    scope.launch {
                                        val result = ChatStore.send(pager, sticker)
                                        sending = false
                                        if (result is ApiResult.Ok) {
                                            // 本端发送:置标志 + 直接兜底滚底(轮询抢先入列导致
                                            // size 不变时 size effect 不触发,见 triggerSendScroll)
                                            triggerSendScroll()
                                        } else {
                                            toastCoupleResult(pager, result)
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 13.dp, vertical = 6.dp),
                    ) {
                        Text(sticker, color = AppTheme.TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            // 输入行(F2/F7 软键盘避让):keyboardHeightChange 回传键盘高度(dp)。⚠ 原生 Android11PlusKeyboardWatcher
            // 的 height = imeInsets.bottom - navigationBars.bottom(反编译核实)——不含导航栏区域,
            // 键盘实际顶边比它高出一条 navBar,故须再叠加 navBarSafePaddingDp() 补齐,否则输入框
            // 下缘被导航栏区域上方的键盘盖住一条(实测「少一点」的根因);弹起时 bottom 切到
            // 「键盘高 + navBar + 12dp」把贴纸行/输入行整体顶到键盘上方(列表随 weight 压缩),
            // 收起回调归零恢复 86dp 浮岛底栏清让常数
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = AppTheme.PageHPadding)
                    .padding(
                        top = 4.dp,
                        bottom = max(
                            86.dp + navBarSafePaddingDp(),
                            keyboardHeight.dp + navBarSafePaddingDp() + 12.dp,
                        ),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                        .background(AppTheme.CardBg, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it.take(INPUT_MAX_LENGTH) },
                        placeholder = "说点什么甜甜的…",
                        autoFocus = false,
                        placeholderColor = AppTheme.TextTertiary,
                        textStyle = TextStyle(color = AppTheme.TextPrimary, fontSize = 13.sp),
                        maxLines = 3, // 长文本限 3 行竖向生长,不在 22dp 胶囊里无限撑高(F15)
                        keyboardHeightChange = onKeyboardHeightChange, // 写高度 + 捕获贴底快照(见上)
                        cursorBrush = remember(theme.brandStart) { SolidColor(theme.brandStart) }, // 光标随主题(§5)
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 13.dp) // 单行≈44dp 与发送钮等高、触达舒适;3 行时自然长高
                            .maxLength(INPUT_MAX_LENGTH), // 原生层拒绝超限输入(F15)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                // 发送:POST 成功才清输入(失败保留文案可重发,无脏数据 §7.4);发送中按钮降透明 +
                // 加载圈且不响应点击(F12),不靠静默吞点
                AppIconButton(
                    AppIconType.Send,
                    onClick = {
                        val text = inputText.trim()
                        if (text.isNotEmpty() && !sending) {
                            sending = true
                            scope.launch {
                                val result = ChatStore.send(pager, text)
                                sending = false
                                if (result is ApiResult.Ok) {
                                    inputText = ""
                                    // P3a:send 返回 Ok 时新消息已 appendDedupe 入列(AppState ChatStore)。
                                    // 置标志交由 messages.size effect 消费,并直接启动兜底滚底——若轮询
                                    // 抢先把同条消息入列则 size 不变、effect 不触发,兜底承接(见
                                    // triggerSendScroll);先 awaitMeasured 等 measure 落地再滚,防
                                    // stale totalItemsCount 吞点
                                    triggerSendScroll()
                                } else {
                                    toastCoupleResult(pager, result)
                                }
                            }
                        }
                    },
                    enabled = !sending,
                    loading = sending,
                )
            }
        }
    }
}

/**
 * 等列表对「包含目标条数」的那次 measure 真正落地再放行(聊天页所有程序化滚底的公共前置):
 * Kuikly 的 layoutInfo 只反映上一次 measure,追加消息后立刻 scrollToItem 会撞上陈旧的
 * totalItemsCount(= 旧 size + 3,恰等于新目标下标)被 LazyListState 越界守卫静默吞掉
 * (snapToItemIndexInternal 首行 index ≥ totalItemsCount 直接 return,forceRemeasure 只按
 * 上次约束重测不刷新数据);snapshotFlow 对 neverEqualPolicy 的 layoutInfoState 每次 measure
 * 必发新值,故以「totalItemsCount ≥ itemTotal」为落地信号。500ms 兜底超时(丢弃本次滚动优于
 * 错误滚动,下一个触发点会重试;不可去掉,防冷启动首拉在页外完成时 effect 长挂)
 */
private suspend fun LazyListState.awaitMeasured(itemTotal: Int) {
    withTimeoutOrNull(500) {
        snapshotFlow { layoutInfo.totalItemsCount }.first { it >= itemTotal }
    }
}

/**
 * 贴底判定(新鲜读,守卫语义同旧「新消息跟随」F0/F9):末条消息(下标 totalItemsCount-2,
 * 尾部 Spacer 前一位)仍在可视区、或本就无前向可滚(真贴底)——用户上翻读历史时不拽底。
 * ⚠ 必须在 awaitMeasured 之后或回调捕获点读取:视口收缩中读到的旧值会误判
 */
private fun LazyListState.isNearBottom(): Boolean {
    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
    return !canScrollForward || lastVisible >= info.totalItemsCount - 2
}

/** 空态引导:软色图标圆底 + 一句文案(与 TodoPage 空态同款视觉,§6) */
@Composable
private fun ChatEmptyHint(modifier: Modifier = Modifier) {
    val theme = ThemeStore.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(56.dp)
                .background(theme.brandStart.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconType.Chat, theme.brandStart, Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("还没有悄悄话,发一句吧", color = AppTheme.TextTertiary, fontSize = 13.sp)
    }
}

/**
 * 失败态(F4,§6):首拉失败且列表为空时替代空态,危险色图标座 + 重试胶囊;
 * loadInitial 失败不置 initialLoaded(AppState 既有语义)→ 天然可重试,成功后 messages 非空本态自动消失
 */
@Composable
private fun ChatLoadError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val theme = ThemeStore.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(56.dp)
                .background(AppTheme.Danger.copy(alpha = 0.08f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconType.Info, AppTheme.Danger, Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("悄悄话加载失败", color = AppTheme.TextSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(14.dp))
        // 重试胶囊:背景与 clickable 同节点(铁律⑦)
        Box(
            modifier = Modifier.background(theme.brandBrush, RoundedCornerShape(16.dp))
                .clickable { onRetry() }
                .padding(horizontal = 16.dp, vertical = 7.dp),
        ) {
            Text("点击重试", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 列表头像(§7):我方品牌渐变底白字,TA 方主题主色 14% 洗底同色字(随 5 套主题自动协调,
 * 无硬编码粉);avatarUrl 非空走远程图形态(文件服务里程碑);显示时机仅 groupTail(§2),
 * 组首/组内由调用方放透明占位
 */
@Composable
private fun Avatar(mine: Boolean, name: String, avatarUrl: String = "") {
    val theme = ThemeStore.current
    if (mine) {
        AppAvatar(
            name,
            size = 34.dp,
            bgBrush = theme.brandBrush,
            textColor = Color.White,
            fontSize = 13.sp,
            avatarUrl = avatarUrl,
        )
    } else {
        AppAvatar(
            name,
            size = 34.dp,
            bgColor = theme.brandStart.copy(alpha = 0.14f),
            textColor = theme.brandStart,
            fontSize = 13.sp,
            avatarUrl = avatarUrl,
        )
    }
}

/**
 * 文本气泡(§3):配色策略不变(我方品牌渐变/TA 白卡),只改尺寸与分组行为——
 * 18dp 圆角、组尾发送侧 6dp 撇角(组内一律全圆)、widthIn(max=260) 长句封顶折行(F3)、
 * 内边距 13/9。形状按 (mine, groupTail) remember 缓存(铁律⑤同因,避免每帧新建实例)。
 * ⚠ 背景两分支统一走 brush 重载(2026-10-16 Q2,TA=taBubbleBrush 同色渐变纯白):
 * color/brush 两个重载写不同原生 prop/图层且层序首建冻结,.then 分支混用会让白实色层
 * 冻结在渐变层上方,复用槽位上我方气泡概率渲染成纯白(真机复现,详见 taBubbleBrush 注释)
 */
@Composable
private fun TextBubble(text: String, mine: Boolean, groupTail: Boolean, theme: KuiklyTheme) {
    val bubbleShape = remember(mine, groupTail) {
        if (groupTail) {
            RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 6.dp,
                bottomEnd = if (mine) 6.dp else 18.dp,
            )
        } else {
            RoundedCornerShape(18.dp)
        }
    }
    Box(
        modifier = Modifier
            .background(
                brush = if (mine) theme.brandBrush else taBubbleBrush,
                shape = bubbleShape,
            )
            .widthIn(max = 260.dp) // 含内边距;360 屏 − 32 页边 − 42 头像列 − 26 呼吸
            .padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Text(
            text,
            color = if (mine) Color.White else AppTheme.TextPrimary,
            fontSize = 14.sp,
        )
    }
}

/**
 * WishBubble — 心愿消息卡(§4 重做):心愿色 8% 洗底单节点软卡(零描边,层次靠 8%/14% 两级 alpha),
 * 颜色语言参照 AppTag/待办标签洗底(tagColor.copy(alpha=0.12f) 先例);按快照字段还原(§5.2 两设备
 * 还原靠快照而非端上字典版本),colorHex/iconName 缺省或他端字典新增图标时兜底(主题主色/Heart),
 * 渲染不空转;不做状态角标(love_message 无兑现状态字段,不造假状态)
 */
@Composable
private fun WishBubble(msg: ChatMsg, theme: KuiklyTheme) {
    val wishColor = if (msg.colorHex.isEmpty()) theme.brandStart else colorFromHex(msg.colorHex, theme.brandStart)
    val wishIcon = appIconFromName(msg.iconName)
    Column(
        modifier = Modifier.widthIn(max = 250.dp)
            .background(wishColor.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp)
                    .background(wishColor.copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(wishIcon, wishColor, Modifier.size(17.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (msg.isMine) "我想要" else "TA 想要",
                color = AppTheme.TextSecondary,
                fontSize = 10.sp,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            msg.wishTitle.ifEmpty { "小心愿" },
            color = AppTheme.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            if (msg.isMine) "等 TA 来兑现哦" else "悄悄记下,去帮 TA 兑现吧",
            color = AppTheme.TextTertiary,
            fontSize = 10.sp,
        )
    }
}
