@file:OptIn(com.tencent.kuikly.compose.foundation.ExperimentalFoundationApi::class)

package com.zyc.love_baby.features.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.combinedClickable
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
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.SolidColor
import com.tencent.kuikly.compose.ui.input.pointer.PointerEventPass
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.input.pointer.positionChangeIgnoreConsumed
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.max
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.views.KeyboardParams
import com.zyc.love_baby.TextField
import com.zyc.love_baby.base.FileModule
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
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.features.profile.PROFILE_WHO_MINE
import com.zyc.love_baby.features.profile.PROFILE_WHO_PARTNER
import com.zyc.love_baby.features.profile.openProfilePage
import com.zyc.love_baby.network.ApiConfig
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.UserApi
import com.zyc.love_baby.state.ChatMsg
import com.zyc.love_baby.state.ChatStore
import com.zyc.love_baby.state.CoupleBindState
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.MsgKind
import com.zyc.love_baby.state.SessionStore
import com.zyc.love_baby.state.appIconFromName
import com.zyc.love_baby.state.colorFromHex
import com.zyc.love_baby.state.nowEpochMs
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlinx.coroutines.delay
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
 *  - 图片消息(M10 预览确认流程,M15 真机反馈改「发送时才上传」):输入行图片按钮 → pickImages
 *    仅选图回本地 file:// 副本(零网络,原生落盘规避 content URI 授权衰减)→ 输入条上方浮
 *    「预览条」(缩略图+✕取消+发送)→ 点发送才热身+uploadImage(1080) 上传 → ChatStore.sendImage
 *    入聊天(M12 起发送后 2 分钟内可长按撤回,确认条防误发)。上传动作收口页内 sendPendingImage
 *    (瞬态 UI 态不进 ChatStore,同 pendingSendScroll 裁决;取消/重选零孤儿文件);
 *  - 消息撤回(M12):发送后 2 分钟内长按自己发的气泡(文本/心愿/图片)→ 确认浮层(挂 MainPager
 *    根,见参数注释)→ ChatStore.recall(POST recall,服务端权威校验归属+时间窗,recalled 墓碑列
 *    不删行);本端 Ok
 *    即地变灰条,TA 在线走 WS love-message-recall 信号(ChatPushStore→ChatStore.applyRecall),
 *    离线靠切 Tab 进页 ChatStore.resync 全量重拉纠偏(增量拉按 id>lastId 扫不到老消息变更)。
 *    墓碑行整行替换居中灰条提示(你/对方撤回了一条消息)并打断分组;长按走 combinedClickable
 *    (Text/Wish 用 interactionSource=null+indication=null 长重载——短重载取 LocalIndication
 *    会给原本无手势的气泡凭空加按压视觉;Image 与既有 clickable 指示器持平走短重载)
 *    发送收口 ChatStore.sendImage(appendDedupe/不推游标语义单源)。图片气泡 180dp 定方 Crop(高度恒定,
 *    LazyColumn item 不随加载跳动),点击开全屏大图浮层(黑 0.9+Fit+点击关闭;F6:浮层开着列表
 *    userScrollEnabled=false,MainPager 横滑随浮层开合经 overlayActive 一并关——遮罩只吃 tap 不吃拖拽)
 *  - ⚠ partnerReply 本地假回复已随 M3 整体移除,TA 的消息靠 message/list 增量拉取(§7.4)
 */
private val quickStickers = listOf("贴贴", "mua~", "想你了", "举高高", "晚安鸭")

/** 文本长度上限(与服务端 AppMessageSendReqVO @Length(max=500) 对齐;maxLength 原生层拒绝 + take 兜底) */
private const val INPUT_MAX_LENGTH = 500

/** 聊天图片上传压缩上限(maxEdge,与 M9 相册同口径 1080):气泡/预览条按方图展示,服务端 content 列 ≤512 存 URL */
private const val CHAT_IMAGE_MAX_EDGE = 1080

/** 图片气泡定方边长(180dp):高度恒定 → LazyColumn item 高度不随图片加载变化不跳;与输入区上限协调 */
private val IMAGE_BUBBLE_SIZE = 180.dp

/** 撤回时间窗(毫秒,与服务端 RECALL_WINDOW_SECONDS=120 对齐):端上预判可长按,服务端权威兜底超窗 */
private const val RECALL_WINDOW_MS = 120_000L

// 下拉刷新指示器最短可见时长(三页同值:MomentPage/ChatPage/WishListPage,注释互引):
// 本地服务端往返几十 ms,不设下限则「刷新中…」一闪而过,用户无法确认刷新是否发生
private const val REFRESH_MIN_VISIBLE_MS = 600L

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
fun ChatPage(
    pager: Pager,
    myNickname: String = "",
    myAvatar: String = "",
    pageVisible: Boolean = false,
    // M12 二轮修复(2026-10-17 真机反馈):两枚全屏浮层(大图预览/撤回确认)状态提升 MainPager
    // 根挂载——页内浮层挂 Tab 子树 z 序低于浮岛底栏,盖不住也拦不住;首轮「浮层开着底栏不组合」
    // 真机反馈生硬(底栏直接消失),弃用。浮层本体移 ChatImagePreviewOverlay/ChatRecallConfirmOverlay
    // 由 MainPager 挂根 Box 底栏之后(与解绑/昵称/登出/心情管理四浮层同语言:黑幕盖住底栏、
    // 遮罩挡触摸、底栏压暗可见不可点),本页经「值 + 回写」双通道读写,不再持有浮层状态
    imagePreviewUrl: String = "",
    onImagePreviewChange: (String) -> Unit = {},
    recallTargetId: String = "",
    onRecallTargetChange: (String) -> Unit = {},
) {
    val theme = ThemeStore.current
    var inputText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var keyboardHeight by remember { mutableStateOf(0f) } // 键盘高度(dp,KeyboardParams.height),0 = 收起
    // M10 图片消息三态(瞬态 UI 态留页内,同 pendingSendScroll 裁决;ChatStore 只收最终发送):
    // M15 真机反馈「没上传不调接口,点击发送才是上传」——选图与上传拆开:pickImage 仅选图零网络
    // (imagePicking 系统相册往返中,拦重复点图片钮);pendingImageUri 预览条本地 URI(空串=无待
    // 发图,✕ 取消/发送成功即清,取消零孤儿文件);imageUploading 仅发送时点的原生上传中(预览条
    // 蒙层+禁取消/重发)。全屏大图浮层状态(imagePreviewUrl)不在此持有——2026-10-17 二轮修复
    // 起由 MainPager 持有并经参数下传(见上方参数注释)
    var imagePicking by remember { mutableStateOf(false) }
    var imageUploading by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf("") }
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

    // M15 仅选图(pickImages,零网络):guard 选图中/上传中 → clearFocus 收键盘 → getModule 判空
    // 降级(iOS/ohos/H5 未桥接端 toast)→ suspendCoroutine 包 pickImages(1)。成功且仍处登录+BOUND
    // 才落 pendingImageUri(防登出/解绑竞态晚到回包写回脏态);已有待发图时再点 = 重选覆盖;
    // code=1 用户取消静默(反复试探动作,不 toast)。热身/上传已挪发送时点(用户裁决)
    fun pickImage() {
        if (imagePicking || imageUploading) return
        focusManager.clearFocus()
        imagePicking = true
        scope.launch {
            var failMsg = "选择图片失败,请重试"
            var cancelled = false
            val pickedUri = try {
                val module = pager.getModule<FileModule>(FileModule.MODULE_NAME)
                if (module == null) {
                    failMsg = "当前端暂不支持选择照片"
                    null
                } else {
                    suspendCoroutine<String?> { cont ->
                        module.pickImages(1) { result ->
                            val code = result?.optInt("code", -1) ?: -1
                            val data = result?.optString("data").orEmpty()
                            if (code == 0 && data.isNotEmpty()) {
                                cont.resume(data)
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
            imagePicking = false
            if (cancelled) return@launch
            if (pickedUri.isNullOrEmpty()) {
                toastCouple(pager, failMsg)
            } else if (SessionStore.userId.isNotEmpty() &&
                CoupleStore.bindState == CoupleBindState.BOUND
            ) {
                pendingImageUri = pickedUri
            }
        }
    }

    // M15 预览条发送 = 上传时点(用户裁决):热身(原生直传不经 HttpClient,无 401 单飞刷新,
    // 先焐热 token)→ uploadImage 单张压缩上传(后台线程压缩)→ ChatStore.sendImage 入列。
    // 上传失败 toast 保留预览图可重发(重试重新上传,服务端多一份无引用文件可接受);
    // 复用 sending 态:发送期间输入条 Send 钮同步 loading,视觉一致
    fun sendPendingImage() {
        if (sending || imageUploading || imagePicking || pendingImageUri.isEmpty()) return
        sending = true
        imageUploading = true
        scope.launch {
            UserApi.getUser(pager) // 热身:焐热 token(选图阶段零 API 调用,热身属发送动作一部分)
            var failMsg = "上传失败,请重试"
            var fileUrl: String? = null
            try {
                val module = pager.getModule<FileModule>(FileModule.MODULE_NAME)
                if (module == null) {
                    failMsg = "当前端暂不支持发送照片"
                } else {
                    fileUrl = suspendCoroutine { cont ->
                        module.uploadImage(
                            ApiConfig.url(pager, "/infra/file/upload"),
                            SessionStore.accessToken,
                            pendingImageUri,
                            CHAT_IMAGE_MAX_EDGE,
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
            if (fileUrl.isNullOrEmpty()) {
                imageUploading = false
                sending = false
                toastCouple(pager, failMsg)
                return@launch
            }
            imageUploading = false
            val result = ChatStore.sendImage(pager, fileUrl)
            sending = false
            if (result is ApiResult.Ok) {
                pendingImageUri = ""
                triggerSendScroll()
            } else {
                toastCoupleResult(pager, result)
            }
        }
    }

    // M12 撤回提交随浮层状态提升迁往 MainPager(submitChatRecall):浮层与提交态同属浮层语言
    // 的一个整体,页内只保留「开浮层」的动作(onRecallTargetChange)

    // 昵称首字(§1/§7):take(1) 按 code point,emoji 昵称不截坏;空昵称兜底「TA」/「我」。
    // 头像 URL(文件服务里程碑):我方走 MainPager 下发的 profile.avatar,TA 方走 CoupleStore
    // couple/get 回查;空走首字形态(AppAvatar 内部分支,加载中背景圆透出占位)
    val partnerName = CoupleStore.couple?.partnerNickname?.trim().orEmpty().ifEmpty { "TA" }
    val partnerInitial = partnerName.take(1)
    val myInitial = myNickname.trim().take(1).ifEmpty { "我" }
    val partnerAvatar = CoupleStore.couple?.partnerAvatar.orEmpty()

    // M13 头部动态副标题(D-5 优先级:正在输入… > 在线 > 默认):纯字符串替换,AppHeaderTitle
    // 零改动;partnerOnline null(未知/离页重置/未桥接端降级)回落默认;typing 位由 WS 信号驱动、
    // 4s 过期收口(下方 effect)
    val headerSubtitle = when {
        ChatStore.partnerTyping -> "正在输入…"
        CoupleStore.partnerOnline == true -> "在线"
        else -> "和${partnerName}的悄悄话"
    }

    // 首拉(§7.4):首组合空游标 → 最近一页正序覆盖;loadInitial 幂等(initialLoaded 标志)。
    // key 从 Unit 改为 userId:常驻堆叠页跨登出/换号不重组合,Unit key 会残留上一会话的失败标志,
    // userId 变化即重判(拉取协议本身不变);失败置 loadFailed 显式错误态,成功 messages 非空自动消失
    // 首拉(登录+已绑定才拉;Pager 化改造,语义同 TodoPage):五页冷启动即组合,未登录/未绑定
    // 不拉防脏失败态;校准/绑定翻 BOUND 时首拉(UNBOUND→BOUND 重绑场景 = D-11 清场后从零首拉);
    // 登出 userId 清空即复位失败态
    LaunchedEffect(SessionStore.userId, CoupleStore.bindState) {
        if (SessionStore.userId.isEmpty() || CoupleStore.bindState != CoupleBindState.BOUND) {
            loadFailed = false
            pendingImageUri = "" // M10 登出/解绑清场:待发图弃
            onImagePreviewChange("")
            onRecallTargetChange("") // M12 浮层清场走回写(MainPager 清态;在飞提交结果落 ChatStore 幂等无害)
            return@LaunchedEffect
        }
        loadFailed = ChatStore.loadInitial(pager) !is ApiResult.Ok
        ChatStore.setChatVisible(pager, pageVisible) // M11:翻 BOUND 后重断言可见态(reset 清 false 而 pageVisible 未变的停留场景)
    }
    // M10/M12 切 Tab 关全屏浮层(pageVisible=false 即离页):全屏浮层跨 Tab 驻留不可见且拦手势
    // (大图黑幕/撤回确认卡);预览条(非全屏)保留——上传协程属常驻组合的 rememberCoroutineScope,
    // 切 Tab 不中断,回来即绪
    LaunchedEffect(pageVisible) {
        if (!pageVisible) {
            onImagePreviewChange("")
            onRecallTargetChange("") // M12 撤回确认浮层同随离页收口(回写 MainPager)
        }
        ChatStore.setChatVisible(pager, pageVisible) // M11:进出页上报,置 true 即主动 sync 一次(与补拉尾钩子双保险,D6)
    }
    // M13 伴侣在线轮询(仅停留本页,D-2):进页先打一枪再 5s 周期;离页/未登录/解绑 guard 失败即
    // 清 partnerOnline(副标题回默认,防陈旧「在线」);key 带 bindState,重绑翻 BOUND 即时重启。
    // 轮询失败静默保留现值(CoupleStore.refreshOnline 内部降级),下个周期自愈;不并入 MainPager
    // 的 5s 消息轮询——在线态只在聊天页有消费方,自持生命周期离页即停
    LaunchedEffect(pageVisible, CoupleStore.bindState) {
        if (!pageVisible || SessionStore.userId.isEmpty() ||
            CoupleStore.bindState != CoupleBindState.BOUND
        ) {
            CoupleStore.clearPartnerOnline()
            return@LaunchedEffect
        }
        while (true) {
            CoupleStore.refreshOnline(pager)
            delay(CoupleStore.PRESENCE_POLL_INTERVAL_MS)
        }
    }
    // M13 typing 过期收口(D-4/D-18 无停止信号,端上 4s 唯一截止):typingSeq 每收到新信号 ++,
    // LaunchedEffect 以其为 key 重启,旧 delay(4000) 被取消——连续输入不断续期,停手 4s 静默落回
    // (4s > 上报节流 3s 不闪断);seq==0(从未收到/reset 复位)不挂计时
    LaunchedEffect(ChatStore.typingSeq) {
        if (ChatStore.typingSeq == 0) return@LaunchedEffect
        delay(ChatStore.TYPING_EXPIRE_MS)
        ChatStore.clearPartnerTyping()
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
                    // 双头像可点进对应资料页(2026-10-18 profilePage 入口):align 移到外包 Box,
                    // 中缝心形徽章不动(无 pointer handler,点击穿透进不了两侧 clickable)
                    Box(
                        modifier = Modifier.align(Alignment.CenterStart)
                            .clickable { openProfilePage(pager, PROFILE_WHO_MINE) },
                    ) {
                        AppAvatar(
                            myInitial,
                            size = 40.dp,
                            bgColor = Color.White.copy(alpha = 0.30f),
                            fontSize = 15.sp,
                            avatarUrl = myAvatar,
                        )
                    }
                    Box(
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .clickable { openProfilePage(pager, PROFILE_WHO_PARTNER) },
                    ) {
                        AppAvatar(
                            partnerInitial,
                            size = 40.dp,
                            bgColor = Color.White,
                            textColor = theme.brandStart,
                            fontSize = 15.sp,
                            avatarUrl = partnerAvatar,
                        )
                    }
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
                AppHeaderTitle("悄悄话", headerSubtitle)
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
                // M10/M12 F6:全屏浮层(大图/撤回确认)开着时关列表滚动(遮罩只吃 tap 不吃拖拽,拖列表会穿透到浮层下层)
                userScrollEnabled = imagePreviewUrl.isEmpty() && recallTargetId.isEmpty(),
            ) {
                // 最短驻留(2026-10-17 真机反馈「松手立马恢复原状,不知道好没好」,三页同款:
                // MomentPage/WishListPage):本地服务端几十 ms 即回,「加载中…」一闪而过——
                // 不足 REFRESH_MIN_VISIBLE_MS 补齐再收;失败 toast(历史拉失败原本完全静默)。
                // M19 语义正位:顶部=最旧消息端,下拉=加载更早历史(原下拉拉增量在顶部几乎恒空,
                // 拉新靠 WS+5s 轮询+回前台既有通道);前插后滚动零改动(Kuikly LazyList 首可见项
                // 按 key 重锚,铁律13③,2.29 源码核实——手动 scrollToItem 反而会把历史藏到视口上方)
                pullToRefreshItem(
                    state = pullState,
                    onRefresh = {
                        refreshing = true
                        scope.launch {
                            val t0 = nowEpochMs()
                            val noMore = !ChatStore.hasMore
                            val ok = noMore || ChatStore.loadHistory(pager) is ApiResult.Ok
                            if (ok) loadFailed = false
                            val elapsed = nowEpochMs() - t0
                            if (elapsed < REFRESH_MIN_VISIBLE_MS) delay(REFRESH_MIN_VISIBLE_MS - elapsed)
                            when {
                                noMore -> toastCouple(pager, "没有更早的消息了")
                                !ok -> toastCouple(pager, "加载失败，请重试")
                            }
                            refreshing = false
                        }
                    },
                    scrollState = listState,
                ) { progress, isRefreshing, _ ->
                    Text(
                        when {
                            isRefreshing -> "加载中…"
                            progress >= 1f -> "松手加载"
                            else -> "下拉加载更早"
                        },
                        color = AppTheme.TextTertiary,
                        fontSize = 12.sp,
                    )
                }
                item { Spacer(modifier = Modifier.height(14.dp)) }
                // 空态 / 失败态分家(F4,§6):首拉失败不再落「还没有悄悄话」假空态误导;
                // 首拉在飞期间三态都不落(initialLoaded 为门)——列表区静默空白,消息到达直接
                // 出列,不闪空态文案(2026-10-17 真机反馈「进聊天页闪烁」,微信/QQ 语义:加载期
                // 不转圈不落空态,拿到空回包才确认真空)
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
                        } else if (ChatStore.initialLoaded) {
                            ChatEmptyHint(modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
                        }
                    }
                }
                itemsIndexed(
                    messages,
                    key = { _, m -> m.id }, // appendDedupe 保证唯一
                    // contentType 五池(2026-10-16 Q2 起隔离;M10 加 image-mine/image-ta):wish/图片/文本
                    // 各自再按 mine/TA 隔离,气泡视图槽位永不跨池复用——即便未来引入 color 路径背景或
                    // 图片气泡做 mine/TA 分化,池子隔离也兜底(每池上限 7,聊天量级无感)
                    contentType = { _, m ->
                        when {
                            m.recalled -> "recall" // M12 墓碑行独立池(灰条布局与气泡完全不同,槽位隔离)
                            m.kind == MsgKind.WISH -> "wish"
                            m.kind == MsgKind.IMAGE -> if (m.isMine) "image-mine" else "image-ta"
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
                    // M12 墓碑打断分组:灰条行无头像无气泡,相邻普通消息一律按组首/组尾重画头像与时间行
                    val groupStart = prev == null || prev.isMine != msg.isMine ||
                        showDayDivider || prev.recalled
                    val groupTail = next == null || next.isMine != msg.isMine ||
                        nextHasDivider || next.recalled
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
                            // 日期分隔条:整宽居中胶囊。三层写法(2026-10-17 二轮真机反馈定稿):
                            // ①外层 Box(fillMaxWidth)+contentAlignment=Center 容器居中(ColumnScope.align
                            // 的 ParentData 通道真机不可靠);②背景+padding 挂内层 Box、Text 作子节点——
                            // 背景直挂 Text 会落在原生 TextView 上(可能被拉宽,文字 start 偏左不居中),
                            // 全 App 真机验证过的胶囊(贴纸/发送/重试)全是背景挂 Box 写法,勿再直挂 Text
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .background(AppTheme.TextTertiary.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
                                        .padding(horizontal = 10.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        chatDayLabel(msg.createTime),
                                        fontSize = 10.sp,
                                        color = AppTheme.TextSecondary,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (msg.recalled) {
                            // M12 墓碑行:整行替换为居中胶囊灰条(同日期分隔条胶囊语言,alpha 0.10 底),
                            // 不画头像/气泡/时间行;上方日期分隔条照常保留。三层写法同日期条(外层
                            // 容器居中 + 背景挂内层 Box、Text 作子节点,勿背景直挂 Text)
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .background(AppTheme.TextTertiary.copy(alpha = 0.10f), RoundedCornerShape(9.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        if (msg.isMine) "你撤回了一条消息" else "对方撤回了一条消息",
                                        fontSize = 11.sp,
                                        color = AppTheme.TextTertiary,
                                    )
                                }
                            }
                        } else {
                            // M12 长按撤回:仅自己发的消息且在 2 分钟窗内可长按(else 分支已排除
                            // recalled;超窗在屏消息长按无反应、服务端权威兜底,不做定时驱逐);
                            // onRecall 先收键盘再开浮层,浮层不被键盘顶住
                            val canRecall = msg.isMine && msg.createTime > 0L &&
                                nowEpochMs() - msg.createTime <= RECALL_WINDOW_MS
                            val onRecall: (() -> Unit)? = if (canRecall) {
                                {
                                    focusManager.clearFocus()
                                    onRecallTargetChange(msg.id) // M12 二轮:开浮层=回写 MainPager(浮层挂根 Box)
                                }
                            } else {
                                null
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
                                    MsgKind.TEXT -> TextBubble(
                                        msg.text,
                                        mine = msg.isMine,
                                        groupTail = groupTail,
                                        theme = theme,
                                        onLongClick = onRecall,
                                    )
                                    MsgKind.WISH -> WishBubble(msg, theme = theme, onLongClick = onRecall) // 卡片语言,不吃撇角
                                    // M10 图片气泡:定方 Crop 不吃撇角;点击开全屏大图(先收键盘),长按可撤回(M12)
                                    MsgKind.IMAGE -> ImageBubble(
                                        msg.imageUrl,
                                        mine = msg.isMine,
                                        onClick = {
                                            focusManager.clearFocus()
                                            onImagePreviewChange(msg.imageUrl) // M12 二轮:回写 MainPager
                                        },
                                        onLongClick = onRecall,
                                    )
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

            // M10 图片预览条(预览确认流程防误发;M12 起发送后 2 分钟内可长按撤回):有待发图时
            // 显示在输入条上方(发送上传中蒙层同条),随输入行键盘避让 padding 整体顶起(普通
            // Column 节点,无需特殊处理)
            if (imageUploading || pendingImageUri.isNotEmpty()) {
                ChatImagePreviewBar(
                    url = pendingImageUri,
                    uploading = imageUploading,
                    sending = sending,
                    onCancel = { if (!imageUploading) pendingImageUri = "" },
                    onSend = { sendPendingImage() },
                )
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
                // M10 图片入口:36dp 轻量 ghost 钮(与 44dp brand 渐变发送钮区分主次);
                // guard 选图中/上传中拦重复点击(pickImage 内也拦,双保险)
                Box(
                    modifier = Modifier.size(36.dp)
                        .clickable { if (!imagePicking && !imageUploading) pickImage() },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIconType.Image, AppTheme.TextSecondary, Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier.weight(1f)
                        .background(AppTheme.CardBg, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it.take(INPUT_MAX_LENGTH)
                            // M13 输入上报(无停止信号,3s 节流在 Store):有输入才报(清空不算输入中),
                            // fire-and-forget 失败静默
                            if (it.isNotEmpty()) ChatStore.noteTyping(pager)
                        },
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

        // M10/M12 全屏大图预览与撤回确认两枚浮层不再页内挂载——2026-10-17 二轮修复(真机反馈
        // 页内浮层盖不住浮岛底栏)起状态提升 MainPager 根 Box(底栏之后)统一挂载,见 MainPager;
        // 本页仅经 imagePreviewUrl/recallTargetId 参数驱动列表滚动门(userScrollEnabled)
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
 * 冻结在渐变层上方,复用槽位上我方气泡概率渲染成纯白(真机复现,详见 taBubbleBrush 注释)。
 * M12 长按撤回:onLongClick 非空挂 combinedClickable 长重载(interactionSource=null+
 * indication=null——短重载取 LocalIndication 会给原本无手势的气泡凭空加按压视觉,全 App
 * 无 Provider 也生效);null 时无任何手势节点,槽位复用安全
 */
@Composable
private fun TextBubble(
    text: String,
    mine: Boolean,
    groupTail: Boolean,
    theme: KuiklyTheme,
    onLongClick: (() -> Unit)? = null, // M12:非空 = 可撤回,长按弹确认浮层
) {
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
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {},
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                }
            )
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
 * 渲染不空转;不做状态角标(love_message 无兑现状态字段,不造假状态)。
 * M12 长按撤回:同 TextBubble——onLongClick 非空挂 combinedClickable 长重载
 * (interactionSource=null+indication=null),不给无手势卡片凭空加按压视觉
 */
@Composable
private fun WishBubble(msg: ChatMsg, theme: KuiklyTheme, onLongClick: (() -> Unit)? = null) {
    val wishColor = if (msg.colorHex.isEmpty()) theme.brandStart else colorFromHex(msg.colorHex, theme.brandStart)
    val wishIcon = appIconFromName(msg.iconName)
    Column(
        modifier = Modifier.widthIn(max = 250.dp)
            .background(wishColor.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {},
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                }
            )
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

/**
 * ImageBubble — 图片消息气泡(M10):180dp 定方 Crop 圆角(高度恒定 → LazyColumn item 高度不随
 * 图片加载变化不跳,槽位复用稳定);浅灰占位底恒驻(加载中/失败透出,MomentCard 同色同思路)。
 * ⚠ 不做 mine/TA 背景分化(mine 参数仅留签名占位):占位底恒 background(color=) 单值,
 * 规避 Q2 color/brush 重载混用坑;不吃 TextBubble 的撇角(图片无方向语义)。
 * M12:clickable 换 combinedClickable 短重载(onClick 开大图 + onLongClick 撤回)——短重载
 * 取 LocalIndication 与原 clickable 指示器行为持平,长按语义新增不改变点击视觉
 */
@Composable
private fun ImageBubble(
    url: String,
    mine: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null, // M12:非空 = 可撤回,长按弹确认浮层
) {
    Box(
        modifier = Modifier
            .background(Color(0xFFF5F6FA), RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .size(IMAGE_BUBBLE_SIZE),
    ) {
        Image(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
            painter = rememberAsyncImagePainter(url),
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * ChatImagePreviewBar — 待发图片预览条(M10 预览确认流程;M15 真机反馈改「发送时才上传」):
 * 选图(仅本地预览)→此处确认→点发送才压缩上传+入聊天(确认条防误发;M12 起发送后 2 分钟内
 * 可长按撤回)。缩略图 56dp 方(浅灰底恒驻;发送上传中黑 28% 蒙层+「上传中…」,MomentAddOverlay
 * 同款);✕ 取消仅非上传中可点(上传不可中断,取消/重选零孤儿文件);发送胶囊
 * uploading/sending 降透明禁用(guard 另在 sendPendingImage)。背景与 clickable 同节点(铁律⑦)
 */
@Composable
private fun ChatImagePreviewBar(
    url: String,
    uploading: Boolean,
    sending: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    val theme = ThemeStore.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = AppTheme.PageHPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(56.dp)
                .background(Color(0xFFF5F6FA), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (url.isNotEmpty()) {
                Image(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    painter = rememberAsyncImagePainter(url),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }
            if (uploading) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("上传中…", color = Color.White, fontSize = 11.sp)
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text("发送后 2 分钟内可长按撤回", color = AppTheme.TextTertiary, fontSize = 11.sp) // M12 文案回改(D9)
        Spacer(modifier = Modifier.weight(1f))
        // ✕ 取消:清预览条
        Box(
            modifier = Modifier.size(30.dp)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconType.Close, AppTheme.TextTertiary, Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(6.dp))
        // 发送胶囊:上传中/发送中降透明禁用
        Box(
            modifier = Modifier.background(theme.brandBrush, RoundedCornerShape(17.dp))
                .clickable(enabled = !uploading && !sending) { onSend() }
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(
                "发送",
                color = Color.White.copy(alpha = if (uploading || sending) 0.6f else 1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * ChatImagePreviewOverlay — 全屏大图预览浮层(M10, MomentPreviewOverlay 轻量版:无配文/无删除):
 * 黑 0.9 全屏 + ContentScale.Fit + 点击任意处关闭。挂 MainPager 根 Box 底栏之后(M12 二轮修复
 * 状态提升,黑幕盖住底栏、遮罩挡触摸);ChatPage 经参数开合,开着时列表滚动与 MainPager 横滑
 * 均已关(F6)
 */
@Composable
internal fun ChatImagePreviewOverlay(url: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            modifier = Modifier.fillMaxSize(),
            painter = rememberAsyncImagePainter(url),
            contentDescription = null,
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * ChatRecallConfirmOverlay — 撤回确认浮层(M12):黑 0.45 幕 + 白卡 20dp 圆角,「撤回这条消息?」
 * + 说明文案,取消(灰)/撤回(Danger)双钮;submitting 期间幕/双钮防点、撤回钮显「撤回中…」。
 * 挂 MainPager 根 Box 底栏之后(M12 二轮修复状态提升,与大图浮层同层);开着时列表滚动与
 * MainPager 横滑一并关;确认走 MainPager.submitChatRecall(POST Ok → ChatStore 已地置墓碑,
 * 关浮层即时见灰条)
 */
@Composable
internal fun ChatRecallConfirmOverlay(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { if (!submitting) onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp)
                .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "撤回这条消息?",
                color = AppTheme.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "撤回后双方都会看到一条撤回提示,此操作不可恢复。",
                color = AppTheme.TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.weight(1f)
                        .background(AppTheme.Divider, RoundedCornerShape(22.dp))
                        .clickable { if (!submitting) onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消", color = AppTheme.TextSecondary, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier.weight(1f)
                        .background(AppTheme.Danger, RoundedCornerShape(22.dp))
                        .clickable { if (!submitting) onConfirm() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (submitting) "撤回中…" else "撤回",
                        color = Color.White.copy(alpha = if (submitting) 0.6f else 1f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
