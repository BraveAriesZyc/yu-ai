@file:OptIn(com.tencent.kuikly.compose.foundation.ExperimentalFoundationApi::class)

package com.zyc.love_baby.features.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.tencent.kuikly.compose.BackHandler
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
import com.tencent.kuikly.compose.foundation.layout.aspectRatio
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
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
import com.tencent.kuikly.compose.foundation.lazy.grid.GridCells
import com.tencent.kuikly.compose.foundation.lazy.grid.LazyVerticalGrid
import com.tencent.kuikly.compose.foundation.lazy.grid.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
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
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.views.KeyboardParams
import com.zyc.love_baby.TextField
import com.zyc.love_baby.base.AudioModule
import com.zyc.love_baby.base.BridgeModule
import com.zyc.love_baby.base.FileModule
import com.zyc.love_baby.base.openLovePage
import com.zyc.love_baby.components.AppAvatar
import com.zyc.love_baby.components.AppDateCapsule
import com.zyc.love_baby.components.AppEmptyHint
import com.zyc.love_baby.components.AppGhostIconButton
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconButton
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppPillChip
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.KuiklyTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.dragDismissKeyboard
import com.zyc.love_baby.components.inputBarBottomPaddingDp
import com.zyc.love_baby.components.runAfterBlur
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.features.profile.PROFILE_WHO_MINE
import com.zyc.love_baby.features.profile.PROFILE_WHO_PARTNER
import com.zyc.love_baby.features.profile.openProfilePage
import com.zyc.love_baby.network.ApiConfig
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.UserApi
import com.zyc.love_baby.state.ChatMsg
import com.zyc.love_baby.state.ChatStickers
import com.zyc.love_baby.state.ChatStore
import com.zyc.love_baby.state.CoupleBindState
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.EmojiStore
import com.zyc.love_baby.state.LoveConfigStore
import com.zyc.love_baby.state.MSG_SEND_FAILED
import com.zyc.love_baby.state.MSG_SEND_SENDING
import com.zyc.love_baby.state.MSG_SEND_SENT
import com.zyc.love_baby.state.MsgKind
import com.zyc.love_baby.state.SessionStore
import com.zyc.love_baby.state.appIconFromName
import com.zyc.love_baby.state.chatDayIndex
import com.zyc.love_baby.state.chatDayLabel
import com.zyc.love_baby.state.formatMmss
import com.zyc.love_baby.state.chatHHmm
import com.zyc.love_baby.state.colorFromHex
import com.zyc.love_baby.state.nowEpochMs
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Job
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
 *      MessageApi 经 ChatStore 走透;state/TimeKit.kt(时间格式化);MessageModels MessageInfo.createTime
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
 *  - 图片消息(M10 起步;M24 修订二轮:乐观直发,预览确认流程弃用):输入行图片按钮 → pickMedia
 *    系统照片选择器混选(图片/视频同屏单选,本地 file:// 副本,原生落盘规避 content URI 授权
 *    衰减)→ 回包按 kind 分派 launchImageSend/launchVideoSend:乐观入列(临时 local_ id,气泡
 *    即现——图片直接渲染本地源;视频先抽帧封面再入列带真缩略图)→ 在途上传(压缩 1080/双上传)
 *    → POST 回包 confirmPending 换真身(消息状态见下条);M15「发送时才上传」与 M10/M23 预览条
 *    (ChatImagePreviewBar/ChatVideoPreviewBar)整体删除(用户裁决「选完直接在信息列表里」)
 *  - 乐观发送(M24 修订二轮,微信语义):所有出站消息(文本/贴纸/卡片/媒体)即点即现——
 *    ChatStore.appendPending 乐观入列(临时 id,sendState=SENDING)→ POST 回包 finalizeSendResult
 *    收口:成功 confirmPending 换真身/失败 markSendFailed。气泡左侧状态件:SENDING=灰钟表、
 *    FAILED=红圆白「!」点击原位重发(resendChatMsg,媒体重走上传链);失败态媒体气泡点击一律
 *    改派重发(不开大图/不播放/不外放/不跳页);乐观在途/失败消息不可长按撤回(无服务端 id)。
 *    缓存恢复时上一进程的 SENDING 死态强制转 FAILED(AppState chatMsgFrom)
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
 *  - M24 输入区微信化(用户裁决;同日多轮修订):voiceMode 声音开关切「按住 说话」胶囊——按下即录/
 *    松开发送/上滑超阈翻红取消/滑回恢复(HoldToTalkCapsule 主动消费手势 + RecordHudOverlay
 *    中央 HUD,M23 录音条 UI 弃用;五轮:开关图标随态换 Mic↔Keyboard[微信式切回供能],
 *    语音态聊天框不显场——「只有在输入的时候可以有」);表情面板 3×8 网格点选即插入输入框
 *    (五轮裁决「选择的表情包在输入框里,不应该有个专门显示的」——删专显预览行,发送走输入行
 *    发送钮,右下退格键删改);mediaPanel 三态(0 快捷贴纸条[输入行上方]/1 表情网格/2 动作面板
 *    仅剩心愿墙卡——1/2 移到输入行下方微信式贴底整幅 CardBg 面板,修订裁决「表情包弹出来后
 *    在输入框下面」);图片钮直选混选(修订裁决「不要弹框,点击图片 icon 直接选择」,见图片消息条)
 *  - M25 字典服务端化:快捷贴纸条 5 条文案走 LoveConfigStore.quick_stickers 下发(内置兜底同值);
 *    表情面板 24 枚死网格退役,改 EmojiStore 分类 tab + 滚动网格(池空走 ChatStickers 兜底单分类)
 *  - ⚠ partnerReply 本地假回复已随 M3 整体移除,TA 的消息靠 message/list 增量拉取(§7.4)
 */

/** 文本长度上限(与服务端 AppMessageSendReqVO @Length(max=500) 对齐;TextField maxLength 渲染层拒绝) */
private const val INPUT_MAX_LENGTH = 500

/** 聊天图片上传压缩上限(maxEdge,与 M9 相册同口径 1080):气泡/预览条按方图展示,服务端 content 列 ≤512 存 URL */
private const val CHAT_IMAGE_MAX_EDGE = 1080

/** 视频体量守卫(M23):16MB = 服务端请求体上限(与头像/图片上传同源限制),超限拒选不白传 */
private const val VIDEO_MAX_BYTES = 16L * 1024 * 1024

/** 语音单条时长上限(秒):原生 setMaxDuration 到点自动停 + 端上秒表双保险;服务端 @Max(600) 富余 */
private const val VOICE_MAX_SECONDS = 60

/** M24 按住说话上滑取消阈值(dp,down 起点绝对差):越阈 HUD 翻红「松开手指 取消发送」,滑回自然恢复 */
private const val VOICE_CANCEL_SLIDE_DP = 72

/** M24 录音 HUD 波纹条高度档(dp):recordSeconds 逐秒轮转取档,1fps 确定性摆动(铁律 10① 零动画 API) */
private val RECORD_WAVE_HEIGHTS_DP = listOf(8, 18, 26, 16)

/** 图片气泡定方边长(180dp):高度恒定 → LazyColumn item 高度不随图片加载变化不跳;与输入区上限协调 */
private val IMAGE_BUBBLE_SIZE = 180.dp

/** 撤回时间窗(毫秒,与服务端 RECALL_WINDOW_SECONDS=120 对齐):端上预判可长按,服务端权威兜底超窗 */
private const val RECALL_WINDOW_MS = 120_000L

// 下拉刷新指示器最短可见时长(三页同值:MomentPage/ChatPage/WishListPage,注释互引):
// 本地服务端往返几十 ms,不设下限则「刷新中…」一闪而过,用户无法确认刷新是否发生
private const val REFRESH_MIN_VISIBLE_MS = 600L

// M23:taBubbleBrush(TA 气泡底同色渐变)迁 ChatBubbles.kt 单源——富媒体四类气泡与 TextBubble
// 共用,完整 color/brush 重载冻结事故注释见该文件(internal 同包引用)

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
    // M24 修订 直选直发(微信语义,用户裁决「选择后直接进信息列表」):pickMedia 回包不经预览
    // 确认,直接乐观入列(ChatStore.appendPending 即点即现)→ 原生上传链 → POST 回包换真身/
    // 失败转红叹号重发(状态实时显示在气泡旁,见 launchImageSend 等)。mediaPicking 选媒体系统
    // 往返中拦重复点图片钮;sending 覆盖上传+POST 全链(输入条 Send 钮 loading 视觉一致,
    // 乐观发送后立即可打字发下一条,上传链不再长时间霸占 sending——发送收口即释放)。
    // 全屏大图浮层状态(imagePreviewUrl)不在此持有——2026-10-17 二轮修复起由 MainPager 持有
    // 并经参数下传(见上方参数注释)
    var mediaPicking by remember { mutableStateOf(false) }
    // M24 十三轮 相册弹层(MediaPickerSheet)开关:点图片钮不再走系统选择器链,改开应用内
    // 多选弹层(微信语义);发送回包经 sendPickedMedia 落盘分派既有上传链
    var showMediaPicker by remember { mutableStateOf(false) }
    // M24 修订 上传进度(微信语义「进度就是那个状态」):当前原生上传百分比 0-100;
    // -1 = 未知/非网络窗口(压缩/抽帧/热身/POST 阶段)。原生 inFlight 单飞 → 全局单进度无歧义
    var mediaProgress by remember { mutableStateOf(-1) }
    // M23 富媒体页内态(瞬态 UI 态留页内,同 image 三态裁决;ChatStore 只收最终发送):
    // mediaPanel 底部面板(M24 修订:0=快捷贴纸条 1=表情网格 2=动作面板[仅心愿墙卡],
    // 互斥共用槽位,普通 Column 节点非浮层;M24 修订起面板整体移到输入行下方——微信语义
    // 「表情包弹出来后在输入框下面」);录音 M24 改微信式按住说话(voiceMode 切按住说话胶囊,
    // recordStopRequested 防停止双击,收口统一走发起回调,见 startRecord 注释);
    // playingVoiceId 播放中语音消息 id(AudioModule 原生单飞兜底,自然播完按时长定时收)
    var mediaPanel by remember { mutableStateOf(0) }
    var recordMode by remember { mutableStateOf(false) }
    var recordStopRequested by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableStateOf(0) }
    var playingVoiceId by remember { mutableStateOf("") }
    var voiceMode by remember { mutableStateOf(false) } // M24 声音模式:true=输入框槽换「按住 说话」胶囊(微信语义)
    var recordCancelZone by remember { mutableStateOf(false) } // M24 录音 HUD 取消态(上滑越阈 true=红态;松手按态分叉发送/取消)
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
    // 观察器已单源化 components.dragDismissKeyboard(挂 LazyColumn modifier,被动不消费事件);
    // focusManager 本页仍用于主动收键盘动作(pickImage/长按撤回开浮层/图片气泡开大图)。
    // 程序化滚底(scrollToItem 只写原生偏移)不产生任何指针事件,天然不误伤跟随滚底(铁律13)
    val focusManager = LocalFocusManager.current

    // ── M24 修订 直选直发三链(微信语义,用户裁决「选择后直接进信息列表」):乐观入列
    // (ChatStore.appendPending 本地 file:// 即点即现)→ 原生上传链(发送时点不变,M15 裁决)→
    // POST 回包换真身(ChatStore.finalizeSendResult)/ 上传失败转红叹号。sending 只作链路互斥
    // (同时只跑一条上传链,原生 FileModule inFlight 单飞会拒并发),上传完即释放,POST 阶段
    // 不占 sending(乐观语义:气泡已现身,用户可立即打字发下一条)。重发(红叹号点击)走同链,
    // existingId 非空 = 原位复用临时 id(markSending→收口),不新建气泡。
    // 三轮修订:上传窗口挂进度轮询(蒙层进度环实时推进,200ms 一拍;压缩/抽帧阶段原生报 -1
    // → 蒙层显「上传中…」)

    /** 上传进度轮询(M24 三轮修订):200ms 一拍向原生查当前上传百分比写 mediaProgress。
     *  走独立 uploadProgress 查询方法,uploadImage/uploadMedia 单一收口契约零改动;
     *  原生 inFlight 单飞 → 全局单进度无歧义。调用方在上传收口后 cancel + 归位 -1 */
    fun startUploadProgressPoll(): Job = scope.launch {
        while (true) {
            val m = pager.getModule<FileModule>(FileModule.MODULE_NAME)
            if (m != null) {
                val pct = suspendCoroutine { cont ->
                    m.uploadProgress { r ->
                        val d = r?.optString("data").orEmpty()
                        cont.resume(
                            try {
                                JSONObject(d.ifEmpty { "{}" }).optInt("progress", -1)
                            } catch (_: Exception) {
                                -1
                            },
                        )
                    }
                }
                mediaProgress = pct
            }
            delay(200)
        }
    }

    /** 图片链:乐观入列 → 热身 → uploadImage 压缩上传(1080)→ POST send-image。
     *  M24 四轮 取闸队列化:乐观入列永不被 sending 拦(旧 guard 使上传链在飞时再选图=已选
     *  媒体静默丢弃),上传在协程内排队等闸——主线程 dispatcher 下 check-then-set 之间无悬挂点
     *  即原子,双链不会同时取闸触发原生 inFlight 单飞拒绝 */
    fun launchImageSend(uri: String, existingId: String = "") {
        val localId = if (existingId.isNotEmpty()) {
            ChatStore.markSending(pager, existingId)
            existingId
        } else {
            ChatStore.appendPending(
                pager,
                ChatMsg(
                    id = "",
                    isMine = true,
                    text = "",
                    kind = MsgKind.IMAGE,
                    imageUrl = uri,
                    createTime = nowEpochMs(),
                    sendState = MSG_SEND_SENDING,
                    localUri = uri,
                ),
            ).also { triggerSendScroll() }
        }
        scope.launch {
            while (sending) delay(150) // 排队等上传闸(队列化语义见 KDoc;不 drop)
            sending = true
            UserApi.getUser(pager) // 热身:焐热 token(选图阶段零 API 调用,热身属发送动作一部分)
            val progressJob = startUploadProgressPoll() // 上传窗口开表(Glide 压缩期原生报 -1 显上传中)
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
                            uri,
                            CHAT_IMAGE_MAX_EDGE,
                            directory = FileModule.DIR_CHAT,
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
            progressJob.cancel() // 上传收口停表归位(POST 阶段蒙层回「上传中…」)
            mediaProgress = -1
            sending = false
            if (fileUrl.isNullOrEmpty()) {
                ChatStore.markSendFailed(pager, localId)
                toastCouple(pager, failMsg)
            } else {
                val result = ChatStore.sendImage(pager, fileUrl, localId)
                if (result is ApiResult.Ok) {
                    triggerSendScroll()
                } else {
                    toastCoupleResult(pager, result)
                }
            }
        }
    }

    /** 视频链:热身 → ①抽帧封面(就绪即乐观入列,气泡带真缩略图;失败降级灰底兜底照常入列)
     *  → ②uploadMedia 传视频 → ③uploadMedia 传封面(可选)→ POST send-video(②③ 期间蒙层进度环)。
     *  三步串行(FileModule inFlight 单飞,并发会被「上传进行中」拒);抽帧/封面上传失败不阻断
     *  发送(M23 同裁决);重试重走全链,服务端多份无引用文件可接受。
     *  M24 四轮 取闸队列化(同 launchImageSend):选到视频遇上传链在飞=排队而非静默丢弃;
     *  抽帧留在闸内(不与在飞上传入原生并发) */
    fun launchVideoSend(uri: String, durationSec: Int, existingId: String = "") {
        scope.launch {
            while (sending) delay(150) // 排队等上传闸(队列化语义见 KDoc;不 drop)
            sending = true
            UserApi.getUser(pager) // 热身:焐热 token(原生直传不经 HttpClient 无 401 单飞刷新)
            var failMsg = "上传失败,请重试"
            var coverLocal = ""
            var videoUrl = ""
            var coverUrl = ""
            val module = pager.getModule<FileModule>(FileModule.MODULE_NAME)
            if (module == null) {
                failMsg = "当前端暂不支持发送视频"
            } else {
                // ① 抽帧封面(失败降级空串:封面缺失仅影响展示,不阻断发送)
                coverLocal = try {
                    suspendCoroutine { cont ->
                        module.videoCover(uri) { result ->
                            if ((result?.optInt("code", -1) ?: -1) == 0) {
                                cont.resume(result?.optString("data").orEmpty())
                            } else {
                                cont.resume("")
                            }
                        }
                    }
                } catch (_: Exception) {
                    ""
                }
            }
            // M24 三轮修订:抽帧后即乐观入列(气泡带本地封面即现,②③ 上传期间蒙层进度环实时
            // 推进——原实现误置整链上传完成后才入列,与「直接在信息列表里」裁决相悖)
            val localId = if (existingId.isNotEmpty()) {
                ChatStore.markSending(pager, existingId)
                existingId
            } else {
                ChatStore.appendPending(
                    pager,
                    ChatMsg(
                        id = "",
                        isMine = true,
                        text = "",
                        kind = MsgKind.VIDEO,
                        mediaUrl = uri,
                        coverUrl = coverLocal,
                        durationSec = durationSec,
                        createTime = nowEpochMs(),
                        sendState = MSG_SEND_SENDING,
                        localUri = uri,
                    ),
                ).also { triggerSendScroll() }
            }
            val progressJob = startUploadProgressPoll()
            if (module != null) {
                // ② 上传视频(必须成功,消息主体)
                videoUrl = try {
                    suspendCoroutine<String?> { cont ->
                        module.uploadMedia(
                            ApiConfig.url(pager, "/infra/file/upload"),
                            SessionStore.accessToken,
                            uri,
                            "vid_${nowEpochMs()}.mp4",
                            "video/mp4",
                            directory = FileModule.DIR_CHAT,
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
                } catch (_: Exception) {
                    null
                } ?: ""
                // ③ 上传封面(可选,失败降级空串)
                if (videoUrl.isNotEmpty() && coverLocal.isNotEmpty()) {
                    coverUrl = try {
                        suspendCoroutine { cont ->
                            module.uploadMedia(
                                ApiConfig.url(pager, "/infra/file/upload"),
                                SessionStore.accessToken,
                                coverLocal,
                                "cover_${nowEpochMs()}.jpg",
                                "image/jpeg",
                                directory = FileModule.DIR_CHAT,
                            ) { result ->
                                if ((result?.optInt("code", -1) ?: -1) == 0) {
                                    cont.resume(result?.optString("data").orEmpty())
                                } else {
                                    cont.resume("")
                                }
                            }
                        }
                    } catch (_: Exception) {
                        ""
                    }
                }
            }
            progressJob.cancel() // 上传收口停表归位(POST 阶段蒙层回「上传中…」)
            mediaProgress = -1
            sending = false
            if (videoUrl.isEmpty()) {
                ChatStore.markSendFailed(pager, localId)
                toastCouple(pager, failMsg)
            } else {
                val result = ChatStore.sendVideo(pager, videoUrl, coverUrl, durationSec.coerceAtLeast(1), localId)
                if (result is ApiResult.Ok) {
                    triggerSendScroll()
                } else {
                    toastCoupleResult(pager, result)
                }
            }
        }
    }

    /** 语音链:乐观入列(本地 file:// 即点即现可播)→ 热身 → uploadMedia 直传 → POST send-voice
     *  (startRecord 收口与新发共用;60s 自动停同走此链自动发送)。
     *  M24 四轮 取闸队列化(语音对齐微信之一):录音收口遇上传链在飞,旧 guard 整条静默丢弃
     *  (无气泡无 toast 的消息丢失)——现乐观入列永不被 sending 拦,上传在协程内排队等闸;
     *  主线程 dispatcher 下 check-then-set 之间无悬挂点即原子(防双语音链并发取闸触发原生
     *  inFlight 单飞拒绝) */
    fun launchVoiceSend(uri: String, durationSec: Int, existingId: String = "") {
        val localId = if (existingId.isNotEmpty()) {
            ChatStore.markSending(pager, existingId)
            existingId
        } else {
            ChatStore.appendPending(
                pager,
                ChatMsg(
                    id = "",
                    isMine = true,
                    text = "",
                    kind = MsgKind.VOICE,
                    mediaUrl = uri,
                    durationSec = durationSec,
                    createTime = nowEpochMs(),
                    sendState = MSG_SEND_SENDING,
                    localUri = uri,
                ),
            ).also { triggerSendScroll() }
        }
        scope.launch {
            while (sending) delay(150) // 排队等上传闸(队列化语义见 KDoc;不 drop)
            sending = true
            UserApi.getUser(pager) // 热身(录音阶段零 API 调用,热身属发送动作一部分)
            var uploadFail = "上传失败,请重试"
            var fileUrl: String? = null
            try {
                val m = pager.getModule<FileModule>(FileModule.MODULE_NAME)
                if (m == null) {
                    uploadFail = "当前端暂不支持发送语音"
                } else {
                    fileUrl = suspendCoroutine { cont ->
                        m.uploadMedia(
                            ApiConfig.url(pager, "/infra/file/upload"),
                            SessionStore.accessToken,
                            uri,
                            "voice_${nowEpochMs()}.m4a",
                            "audio/mp4",
                            directory = FileModule.DIR_CHAT,
                        ) { result2 ->
                            val c = result2?.optInt("code", -1) ?: -1
                            val d = result2?.optString("data").orEmpty()
                            if (c == 0 && d.isNotEmpty()) {
                                cont.resume(d)
                            } else {
                                result2?.optString("msg")?.takeIf { it.isNotEmpty() }?.let { uploadFail = it }
                                cont.resume(null)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                null
            }
            sending = false
            if (fileUrl.isNullOrEmpty()) {
                ChatStore.markSendFailed(pager, localId)
                toastCouple(pager, uploadFail)
            } else {
                val sendResult = ChatStore.sendVoice(pager, fileUrl, durationSec, localId)
                if (sendResult is ApiResult.Ok) {
                    triggerSendScroll()
                } else {
                    toastCoupleResult(pager, sendResult)
                }
            }
        }
    }

    /** M24 修订 失败重发分派(气泡旁红叹号 / 失败媒体气泡点击):按 kind 重走各自链,
     *  existingId 原位复用临时 id(markSending → 收口换真身/再败仍 FAILED)。TEXT/STICKER/CARD
     *  无上传链直接重 POST(不占 sending,POST 快且乐观气泡已在);WISH 不走乐观(心情页
     *  sendWishes 老路径)恒 SENT 不可达此分派 */
    fun resendChatMsg(msg: ChatMsg) {
        when (msg.kind) {
            MsgKind.IMAGE -> launchImageSend(msg.localUri.ifEmpty { msg.imageUrl }, msg.id)
            MsgKind.VIDEO -> launchVideoSend(msg.localUri.ifEmpty { msg.mediaUrl }, msg.durationSec, msg.id)
            MsgKind.VOICE -> launchVoiceSend(msg.localUri.ifEmpty { msg.mediaUrl }, msg.durationSec, msg.id)
            MsgKind.TEXT -> {
                ChatStore.markSending(pager, msg.id)
                scope.launch {
                    val result = ChatStore.send(pager, msg.text, msg.id)
                    if (result !is ApiResult.Ok) toastCoupleResult(pager, result)
                }
            }
            MsgKind.STICKER -> {
                ChatStore.markSending(pager, msg.id)
                scope.launch {
                    val result = ChatStore.sendSticker(pager, msg.stickerKey, msg.id)
                    if (result !is ApiResult.Ok) toastCoupleResult(pager, result)
                }
            }
            MsgKind.CARD -> {
                ChatStore.markSending(pager, msg.id)
                scope.launch {
                    val result = ChatStore.sendCard(pager, msg.cardType, msg.cardTitle, msg.cardSub, msg.cardIcon, msg.id)
                    if (result !is ApiResult.Ok) toastCoupleResult(pager, result)
                }
            }
            else -> Unit
        }
    }

    // M24 十三轮 相册弹层多选发送(sendPickedMedia,替代原 pickMedia 单选链——十轮应用内选择器
    // 与系统选择器三级链一并退役为回滚安全网):MediaPickerSheet「发送(N)」回调 uris(content://
    // 选择顺序)→ copyMedia 桥落盘收口(suspendCoroutine;原生逐张转 file:// + mime 判型 + 时长
    // 体量回读,JSON 数组回包)→ 登录+BOUND 检查 → 逐张分派既有链:video → launchVideoSend
    // (>16MB toast 跳过该张,不拦整批)/ image → launchImageSend——乐观入列 + while(sending)
    // 排队等闸语义自动继承。mediaPicking 复用防连点;个别张失败原生跳过,全失败 toast
    fun sendPickedMedia(uris: List<String>) {
        if (mediaPicking || uris.isEmpty()) return
        mediaPicking = true
        scope.launch {
            var failMsg = "发送失败,请重试"
            var picked: List<PickedMedia> = emptyList()
            val module = pager.getModule<FileModule>(FileModule.MODULE_NAME)
            if (module == null) {
                failMsg = "当前端暂不支持发送照片/视频"
            } else {
                try {
                    picked = suspendCoroutine { cont ->
                        module.copyMedia(uris) { result ->
                            val code = result?.optInt("code", -1) ?: -1
                            val data = result?.optString("data").orEmpty()
                            if (code == 0 && data.isNotEmpty()) {
                                val list = mutableListOf<PickedMedia>()
                                try {
                                    val arr = JSONArray(data)
                                    for (i in 0 until arr.length()) {
                                        val o = arr.optJSONObject(i) ?: continue
                                        list.add(
                                            PickedMedia(
                                                o.optString("uri"),
                                                o.optString("kind", "image"),
                                                o.optInt("duration"),
                                                o.optLong("size"),
                                            ),
                                        )
                                    }
                                } catch (_: Exception) {
                                }
                                cont.resume(list)
                            } else {
                                result?.optString("msg")?.takeIf { it.isNotEmpty() }?.let { failMsg = it }
                                cont.resume(emptyList())
                            }
                        }
                    }
                } catch (_: Exception) {
                    emptyList<PickedMedia>()
                }
            }
            mediaPicking = false
            val ok = picked.filter { it.uri.isNotEmpty() }
            if (ok.isEmpty()) {
                toastCouple(pager, failMsg)
                return@launch
            }
            if (SessionStore.userId.isNotEmpty() &&
                CoupleStore.bindState == CoupleBindState.BOUND
            ) {
                ok.forEach { p ->
                    if (p.kind == "video") {
                        if (p.size > VIDEO_MAX_BYTES) {
                            toastCouple(pager, "视频过大(超过 16MB),请压缩后再发")
                        } else {
                            launchVideoSend(p.uri, p.duration)
                        }
                    } else {
                        launchImageSend(p.uri)
                    }
                }
            }
        }
    }

    // M24 六轮 拍照(加号面板「拍照」卡):takePhoto 桥(ACTION_IMAGE_CAPTURE,FileProvider
    // EXTRA_OUTPUT 直落 filesDir/camera 全量原图)→ 收口走 launchImageSend 同图片链。结构同
    // pickMedia(声明位须在其后:launchImageSend 前向引用禁,同 pickMedia 教训);mediaPicking
    // 复用防连点(原生 inFlight 模块级单飞双保险),未登录/未绑定静默不派发
    fun takePhoto() {
        if (mediaPicking) return
        focusManager.clearFocus()
        mediaPicking = true
        scope.launch {
            var failMsg = "拍照失败,请重试"
            var cancelled = false
            var uri = ""
            val module = pager.getModule<FileModule>(FileModule.MODULE_NAME)
            if (module == null) {
                failMsg = "当前端暂不支持拍照"
            } else {
                try {
                    uri = suspendCoroutine { cont ->
                        module.takePhoto { result ->
                            val code = result?.optInt("code", -1) ?: -1
                            val data = result?.optString("data").orEmpty()
                            if (code == 0 && data.isNotEmpty()) {
                                cont.resume(data)
                            } else {
                                if (code == 1) cancelled = true // 用户取消:静默
                                else result?.optString("msg")?.takeIf { it.isNotEmpty() }?.let { failMsg = it }
                                cont.resume("")
                            }
                        }
                    }
                } catch (_: Exception) {
                    ""
                }
            }
            mediaPicking = false
            if (cancelled) return@launch
            if (uri.isEmpty()) {
                toastCouple(pager, failMsg)
            } else if (SessionStore.userId.isNotEmpty() &&
                CoupleStore.bindState == CoupleBindState.BOUND
            ) {
                launchImageSend(uri)
            }
        }
    }

    // M23 语音播放切换:AudioModule 单飞(原生起播前先停上一条,同刻至多一个实例);
    // playingVoiceId 页内态渲染播放/暂停图标——自然播完/被顶掉原生不回调(契约见 base/AudioModule),
    // 按消息时长 +1s 定时收口;提前点同一条 = 手动暂停即时清;未桥接端 toast 降级
    fun toggleVoicePlay(msg: ChatMsg) {
        if (recordMode) return // 录音中禁播(M24 四轮 微信语义;防扬声器回声灌麦,HUD 场景静默忽略)
        val module = pager.getModule<AudioModule>(AudioModule.MODULE_NAME)
        if (module == null) {
            toastCouple(pager, "当前端暂不支持播放语音")
            return
        }
        if (playingVoiceId == msg.id) {
            module.stopPlay()
            playingVoiceId = ""
            return
        }
        module.startPlay(msg.mediaUrl) { result ->
            val code = result?.optInt("code", -1) ?: -1
            if (code != 0) {
                playingVoiceId = ""
                toastCouple(pager, result?.optString("msg")?.takeIf { it.isNotEmpty() } ?: "语音播放失败")
            }
        }
        playingVoiceId = msg.id
        scope.launch {
            delay((msg.durationSec.coerceAtLeast(1) + 1) * 1000L)
            if (playingVoiceId == msg.id) playingVoiceId = "" // 自然播完收口(手动暂停/切播已先行改写)
        }
    }

    // M23 视频外放:BridgeModule.openUrlExternally(ACTION_VIEW 交系统播放器,App 内不建播放器);
    // Android 宿主已实现,iOS/ohos/H5 后续补齐(未实现分支静默 no-op,common 不依赖回执)
    fun openVideoExternally(url: String) {
        if (url.isEmpty()) return
        focusManager.clearFocus()
        pager.getModule<BridgeModule>(BridgeModule.MODULE_NAME)?.openUrlExternally(url)
    }

    // M23 录音(微信语义):起录即挂「唯一收口回调」——到 60s 原生自动停 / 松开
    // recordAudioStop(false) / 上滑取消 recordAudioStop(true) 三路结果统一从发起回调收口
    // (原生不偷换回调,stop 调用仅 ack,见 base/FileModule 单一收口通道注记)。code=0 定稿 →
    // launchVoiceSend 全链收口(到点自动停亦自动发,微信满 60s 自动发语义);code=1 取消静默;
    // 其余失败 toast。进入先 clearFocus 收键盘。
    // M24 四轮 语音对齐微信:①sending 出列 guard(录音域与上传 inFlight 分域可并发,旧 guard
    // 使上传期间按住说话静默无反应;上传互斥由 launchVoiceSend 队列化自持);②起录打断播放
    // (播放中按住说话即停声收播放态,兼防扬声器回声灌麦)
    fun startRecord() {
        if (recordMode || recordStopRequested) return
        focusManager.clearFocus()
        val module = pager.getModule<FileModule>(FileModule.MODULE_NAME)
        if (module == null) {
            toastCouple(pager, "当前端暂不支持发语音")
            return
        }
        playingVoiceId = "" // 起录打断播放(微信语义):清页内播放态
        pager.getModule<AudioModule>(AudioModule.MODULE_NAME)?.stopPlay() // 停原生播放(未在播 no-op)
        recordMode = true
        recordStopRequested = false
        recordSeconds = 0
        recordCancelZone = false // M24 HUD 复位点 1/3:起录即黑态
        module.recordAudioStart(VOICE_MAX_SECONDS) { result ->
            // 单一收口(三路同此):先复位录音条形态再分支
            val code = result?.optInt("code", -1) ?: -1
            val data = result?.optString("data").orEmpty()
            val failMsg = result?.optString("msg").orEmpty()
            recordMode = false
            recordStopRequested = false
            recordSeconds = 0
            recordCancelZone = false // M24 HUD 复位点 2/3:三路收口统一归黑态(下轮起录再置)
            if (code == 1) {
                return@recordAudioStart // 用户取消:静默(与 pickImage 同语义)
            }
            val parsed = try {
                if (code == 0 && data.isNotEmpty()) JSONObject(data) else null
            } catch (_: Exception) {
                null
            }
            if (parsed == null) {
                if (failMsg.isNotEmpty()) toastCouple(pager, failMsg)
                return@recordAudioStart
            }
            // M24 修订 乐观发送:定稿语音即入列现身(本地 file:// 即点即现可播),上传+POST
            // 收口换真身/失败转红叹号,全链收口 launchVoiceSend(重发共用)
            launchVoiceSend(parsed.optString("uri"), parsed.optInt("duration").coerceAtLeast(1))
        }
    }

    // M23 录音停止/取消:recordStopRequested 防停止双击(原生二次 stop「未在录音中」no-op 兜底);
    // 结果统一走发起回调(见 startRecord 注释),本调用回调仅 ack 占位
    fun recordStop(cancel: Boolean) {
        if (!recordMode || recordStopRequested) return
        recordStopRequested = true
        val module = pager.getModule<FileModule>(FileModule.MODULE_NAME)
        if (module == null) {
            // 理论不可达(startRecord 已判空):兜底复位防卡录音态
            recordMode = false
            recordStopRequested = false
            recordSeconds = 0
            return
        }
        module.recordAudioStop(cancel) { }
    }

    // M23 心愿墙卡片直发(动作面板,固定快照):不做心愿挑选器,避免与 kind=2 心愿消息语义
    // 混淆;点气泡跳 wishListPage(渲染分支 onClick)。type=wishWall 为服务端/端上约定的首个卡型。
    // M24 修订 乐观发送(即点即现→POST 回包换真身;M24 四轮 sending guard 出列:纯 POST 不碰
    // 原生上传,上传闸不拦)
    fun sendWishWallCard() {
        val localId = ChatStore.appendPending(
            pager,
            ChatMsg(
                id = "",
                isMine = true,
                text = "",
                kind = MsgKind.CARD,
                cardType = "wishWall",
                cardTitle = "我们的心愿墙",
                cardSub = "一起去实现愿望吧",
                cardIcon = "Star",
                createTime = nowEpochMs(),
                sendState = MSG_SEND_SENDING,
            ),
        )
        triggerSendScroll()
        scope.launch {
            val result = ChatStore.sendCard(pager, "wishWall", "我们的心愿墙", "一起去实现愿望吧", "Star", localId)
            if (result is ApiResult.Ok) {
                mediaPanel = 0
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
            // M24 修订 待发媒体预览态已删(选图/视频直入消息列表乐观发送);在途乐观消息
            // (sendState=SENDING/FAILED)随 messages 全量复位,无需单独清场
            mediaPanel = 0
            playingVoiceId = ""
            voiceMode = false // M24 声音模式随登出/解绑复位
            recordCancelZone = false // M24 HUD 取消态复位
            pager.getModule<AudioModule>(AudioModule.MODULE_NAME)?.stopPlay()
            if (recordMode) recordStop(true) // 录音中登出/解绑:取消录音(收口统一走发起回调)
            onImagePreviewChange("")
            onRecallTargetChange("") // M12 浮层清场走回写(MainPager 清态;在飞提交结果落 ChatStore 幂等无害)
            return@LaunchedEffect
        }
        // M23 本地缓存恢复:命中(initialLoaded=true)则 loadInitial 幂等跳过覆盖拉,历史秒出;
        // 紧接主动增量一次补齐缓存游标之后的缺口(不等 5s 心跳;失败静默,轮询兜底);未命中走原覆盖拉
        if (ChatStore.restoreCache(pager)) {
            ChatStore.loadIncrement(pager)
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
            // M23 离页收口:语音播放即停(五页常驻,不收口会跨 Tab 继续出声);面板/录音条复位
            // (录音中离页取消录音,收口统一走发起回调);待发视频预览保留(同待发图,回来即绪)
            playingVoiceId = ""
            pager.getModule<AudioModule>(AudioModule.MODULE_NAME)?.stopPlay()
            mediaPanel = 0
            recordCancelZone = false // M24 HUD 取消态离页复位(录音取消收口走发起回调;非录音态防滞留)
            if (recordMode) recordStop(true)
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
                mediaPanel = 0 // M23 键盘弹起即收面板(微信语义:键盘与底部面板互斥)
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
    // M23 录音秒表:recordMode 驱动每秒 +1,仅供 UI 显示——60s 到点由原生 setMaxDuration 自动停
    // (收口统一走发起回调,见 startRecord 注释),端上计时到顶不收口(双保险中原生为准)
    LaunchedEffect(recordMode) {
        while (recordMode && recordSeconds < VOICE_MAX_SECONDS) {
            delay(1000)
            recordSeconds++
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
                    // Q3 拖拽收键盘(微信语义,键盘开着拖列表即收):被动手势观察器已单源化到
                    // components.dragDismissKeyboard(铁律13④ 三不可妥协点与 key 约定见组件注释)
                    .dragDismissKeyboard(),
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
                            // 空态收编 components.AppEmptyHint(circled 形态:56dp brandStart 10% 圆 +
                            // Chat 图标 24dp;标题归一为组件 14sp TextSecondary 档)
                            AppEmptyHint(
                                AppIconType.Chat,
                                "还没有悄悄话,发一句吧",
                                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            )
                        }
                    }
                }
                itemsIndexed(
                    messages,
                    key = { _, m -> m.id }, // appendDedupe 保证唯一
                    // contentType 池(2026-10-16 Q2 起隔离;M10 加 image;M23 加 voice/video/sticker/card,
                    // 各再按 mine/TA 隔离共 13 池):气泡视图槽位永不跨池复用——即便未来引入 color 路径
                    // 背景或同池内背景重载分化,池子隔离也兜底(每池上限 7,聊天量级无感)
                    contentType = { _, m ->
                        when {
                            m.recalled -> "recall" // M12 墓碑行独立池(灰条布局与气泡完全不同,槽位隔离)
                            m.kind == MsgKind.WISH -> "wish"
                            m.kind == MsgKind.IMAGE -> if (m.isMine) "image-mine" else "image-ta"
                            m.kind == MsgKind.VOICE -> if (m.isMine) "voice-mine" else "voice-ta"
                            m.kind == MsgKind.VIDEO -> if (m.isMine) "video-mine" else "video-ta"
                            m.kind == MsgKind.STICKER -> if (m.isMine) "sticker-mine" else "sticker-ta"
                            m.kind == MsgKind.CARD -> if (m.isMine) "card-mine" else "card-ta"
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
                        // 图片/视频是大块实体(边缘即气泡边缘,无文字气泡 9dp 内边距缓冲),3dp 组内距
                        // 视觉上与相邻消息贴死(2026-10-22 真机反馈「图片和其他聊天没有间距」)
                        // → 此类上下一律按组间 10dp 呼吸;下一侧由下一条的 prev 判定天然覆盖
                        msg.kind == MsgKind.IMAGE || msg.kind == MsgKind.VIDEO ||
                            prev?.kind == MsgKind.IMAGE || prev?.kind == MsgKind.VIDEO -> 10.dp
                        else -> 3.dp // 组内紧凑
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = AppTheme.PageHPadding)
                            .padding(top = topPad),
                    ) {
                        if (showDayDivider) {
                            // 日期分隔条:整宽居中胶囊(AppDateCapsule 单源:三层安全写法铁律 14/15,
                            // 视觉常数 TextTertiary 12% 底 + 9dp 圆角 + 横 10/竖 3 + 10sp 与原件逐字一致)
                            AppDateCapsule(chatDayLabel(msg.createTime))
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (msg.recalled) {
                            // M12 墓碑行:整行替换为居中胶囊灰条(alpha 0.10 底 + 11sp TextTertiary +
                            // 竖 4dp,AppDateCapsule 参数对齐原件),不画头像/气泡/时间行;上方日期分隔条照常保留
                            AppDateCapsule(
                                if (msg.isMine) "你撤回了一条消息" else "对方撤回了一条消息",
                                bgAlpha = 0.10f,
                                fontSize = 11.sp,
                                textColor = AppTheme.TextTertiary,
                                verticalPadding = 4.dp,
                            )
                        } else {
                            // M12 长按撤回:仅自己发的消息且在 2 分钟窗内可长按(else 分支已排除
                            // recalled;超窗在屏消息长按无反应、服务端权威兜底,不做定时驱逐);
                            // onRecall 先收键盘再开浮层,浮层不被键盘顶住。M24 修订:乐观在途
                            // (SENDING)/失败(FAILED)消息无服务端 id 不可撤回——失败走红叹号重发
                            val canRecall = msg.isMine && msg.createTime > 0L &&
                                msg.sendState == MSG_SEND_SENT &&
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
                                // M24 修订 发送状态件(微信语义,仅本端乐观消息,气泡左侧贴底对齐):
                                // SENDING=灰钟表(发送中,随回包/失败自动消失);FAILED=红圆白「!」
                                // 点击原位重发(重走上传链/重 POST,见 resendChatMsg)。
                                // M24 修订美化:图/视频气泡自带状态蒙层(上传中/红!),不再叠加本件
                                // 避双份示能;语音画幅小不蒙层,维持左侧件
                                val mediaSelfIndicated = msg.kind == MsgKind.IMAGE || msg.kind == MsgKind.VIDEO
                                if (msg.isMine && msg.sendState != MSG_SEND_SENT && !mediaSelfIndicated) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (msg.sendState == MSG_SEND_SENDING) {
                                        AppIcon(AppIconType.Clock, AppTheme.TextTertiary, Modifier.size(16.dp))
                                    } else {
                                        Box(
                                            modifier = Modifier.size(18.dp)
                                                .background(AppTheme.Danger, RoundedCornerShape(9.dp))
                                                .clickable { resendChatMsg(msg) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "!",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
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
                                    // M10 图片气泡:定方 Crop 不吃撇角;点击开全屏大图(先收键盘),长按可撤回(M12)。
                                    // M24 修订:失败态点击改派原位重发(本地源无大图可开);状态蒙层随 sendState
                                    MsgKind.IMAGE -> ImageBubble(
                                        msg.imageUrl,
                                        mine = msg.isMine,
                                        sendState = msg.sendState,
                                        progress = mediaProgress, // SENDING 蒙层进度环(sending 单飞链全局单进度)
                                        onClick = {
                                            if (msg.sendState == MSG_SEND_FAILED) {
                                                resendChatMsg(msg)
                                            } else {
                                                focusManager.clearFocus()
                                                onImagePreviewChange(msg.imageUrl) // M12 二轮:回写 MainPager
                                            }
                                        },
                                        onLongClick = onRecall,
                                    )
                                    // M23 富媒体四类正式气泡(ChatBubbles.kt 单源):语音点击切播放
                                    // (playingVoiceId 页内态),视频点击外放,卡片点击跳恋爱清单;
                                    // 长按撤回 onRecall 通用(超窗 null,槽位复用安全)。
                                    // M24 修订:失败态点击统一改派原位重发(不播放/不外放/不跳页)
                                    MsgKind.VOICE -> VoiceBubble(
                                        msg,
                                        mine = msg.isMine,
                                        theme = theme,
                                        playing = playingVoiceId == msg.id,
                                        onClick = {
                                            if (msg.sendState == MSG_SEND_FAILED) {
                                                resendChatMsg(msg)
                                            } else {
                                                toggleVoicePlay(msg)
                                            }
                                        },
                                        onLongClick = onRecall,
                                    )
                                    MsgKind.VIDEO -> VideoBubble(
                                        msg,
                                        progress = mediaProgress, // SENDING 蒙层进度环(sending 单飞链全局单进度)
                                        onClick = {
                                            if (msg.sendState == MSG_SEND_FAILED) {
                                                resendChatMsg(msg)
                                            } else {
                                                openVideoExternally(msg.mediaUrl)
                                            }
                                        },
                                        onLongClick = onRecall,
                                    )
                                    MsgKind.STICKER -> StickerBubble(msg, onLongClick = onRecall)
                                    MsgKind.CARD -> CardBubble(
                                        msg,
                                        theme = theme,
                                        onClick = {
                                            if (msg.sendState == MSG_SEND_FAILED) {
                                                resendChatMsg(msg)
                                            } else {
                                                focusManager.clearFocus()
                                                pager.openLovePage("wishListPage") // M23 wishWall 卡点跳恋爱清单
                                            }
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

            // ── 快捷贴纸文本条(F17:白底胶囊在近白页底上对比度过低改 Divider 实底;sending 期间
            // 拦点击)。M24 修订:仅 mediaPanel==0 时显示——表情/动作面板移到输入行下方(微信式
            // 布局,用户裁决「表情包弹出来后在输入框下面」),不再共用此槽位。点击乐观发送:
            // 即点即现(乐观入列)→ POST 回包换真身/失败红叹号(同文本钮)。M25:文案走
            // LoveConfigStore.quick_stickers 下发(后台业务配置页可改,回前台即见;坏 JSON 兜底同值)
            if (mediaPanel == 0) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = AppTheme.PageHPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(LoveConfigStore.quickStickers) { sticker ->
                        Box(
                            modifier = Modifier.background(AppTheme.Divider, RoundedCornerShape(15.dp))
                                .clickable {
                                    if (!sending) {
                                        val localId = ChatStore.appendPending(
                                            pager,
                                            ChatMsg(
                                                id = "",
                                                isMine = true,
                                                text = sticker,
                                                kind = MsgKind.TEXT,
                                                createTime = nowEpochMs(),
                                                sendState = MSG_SEND_SENDING,
                                            ),
                                        )
                                        triggerSendScroll()
                                        scope.launch {
                                            val result = ChatStore.send(pager, sticker, localId)
                                            if (result is ApiResult.Ok) {
                                                // 本端发送:直接兜底滚底(轮询抢先入列导致 size 不变时
                                                // size effect 不触发,见 triggerSendScroll)
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
            }

            // 输入行(F2/F7 软键盘避让):keyboardHeightChange 回传键盘高度(dp)。⚠ 原生 Android11PlusKeyboardWatcher
            // 的 height = imeInsets.bottom - navigationBars.bottom(反编译核实)——不含导航栏区域,
            // 键盘实际顶边比它高出一条 navBar,故须再叠加 navBarSafePaddingDp() 补齐,否则输入框
            // 下缘被导航栏区域上方的键盘盖住一条(实测「少一点」的根因);弹起时 bottom 切到
            // 「键盘高 + navBar + 12dp」把贴纸行/输入行整体顶到键盘上方(列表随 weight 压缩),
            // 收起回调归零恢复 86dp 浮岛底栏清让常数。M23:recordMode 时左簇与输入框槽整体替换为
            // 录音条(取消 ghost + 计时胶囊 + 发送 brand 钮,形态切换非浮层无 z 序问题)。
            // M24 修订:表情/动作面板移到本行下方(微信式布局)——面板开着时本行只留 4dp 小间距
            // (面板自带 dock 清让 bottom padding),无面板时维持键盘避让公式
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = AppTheme.PageHPadding)
                    .padding(
                        top = 4.dp,
                        // 输入条 bottom 避让公式单源化(max(rest 86dp, 键盘高 + navBar + 12dp),
                        // 与原件逐字一致;⚠ 键盘 height 不含导航栏区域的补偿已在组件内收口)
                        bottom = if (mediaPanel != 0) 4.dp else inputBarBottomPaddingDp(keyboardHeight),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // M24 六轮 图片钮撤出输入行(用户裁决「图片不要在输入框左边,点击加号后下面显示
                // 很多选项」)——图片/拍照入口并入加号面板(mediaPanel==2,见下方动作面板)

                // M24 声音开关(微信语义):切「按住 说话」胶囊/文本输入。铁律 16:voiceMode 翻转
                // 会卸载持焦 TextField,先清焦再迟一拍翻转(components.runAfterBlur 单源,GatePanel
                // .switchPanel 同范式)让原生 blur 批处理先落;真机回归点:待办页输入框不抢焦不自滚。
                // M24 五轮(用户反馈「语音模式不显示聊天框,只有在输入的时候可以有」):图标随态换——
                // 文本态 Mic(进语音),语音态 Keyboard(微信同款显式切回出口,聊天框仅在输入态在场)
                AppGhostIconButton(
                    if (voiceMode) AppIconType.Keyboard else AppIconType.Mic,
                    onClick = {
                        if (recordMode) return@AppGhostIconButton
                        pager.runAfterBlur(focusManager) {
                            voiceMode = !voiceMode
                            mediaPanel = 0
                            if (voiceMode) recordCancelZone = false
                        }
                    },
                    size = 36.dp,
                    iconSize = 20.dp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (voiceMode) {
                    // M24 按住说话胶囊(与 TextField 同槽互斥):按下即录/松开发送/上滑取消,见组件注释
                    HoldToTalkCapsule(
                        onStart = { startRecord() },
                        onRelease = { cancel -> recordStop(cancel) },
                        onCancelZoneChange = { recordCancelZone = it },
                        modifier = Modifier.weight(1f).height(44.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier.weight(1f)
                            .background(AppTheme.CardBg, RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                // M13 输入上报(无停止信号,3s 节流在 Store):有输入才报(清空不算输入中),
                                // fire-and-forget 失败静默
                                if (it.isNotEmpty()) ChatStore.noteTyping(pager)
                            },
                            placeholder = "说点什么甜甜的…",
                            autoFocus = false,
                            placeholderColor = AppTheme.TextTertiary,
                            textStyle = TextStyle(color = AppTheme.TextPrimary, fontSize = 13.sp),
                            maxLines = 3, // 长文本限 3 行竖向生长,不在 22dp 胶囊里无限撑高(F15)
                            maxLength = INPUT_MAX_LENGTH, // 渲染层拒超限输入(F15;勿在 onValueChange take 截断,光标 bug)
                            keyboardHeightChange = onKeyboardHeightChange, // 写高度 + 捕获贴底快照(见上)
                            cursorBrush = remember(theme.brandStart) { SolidColor(theme.brandStart) }, // 光标随主题(§5)
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 13.dp) // 单行≈44dp 与发送钮等高、触达舒适;3 行时自然长高
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                // M24 表情面板开关(面板 M24 改 3×8 网格;与动作面板互斥,再点收起;先清键盘——面板与键盘互斥)。
                // M24 六轮(用户裁决「文字输入框的时候才能点击表情包按钮,语音的时候不显示」):
                // 仅文本输入态在场(voiceMode 隐藏;尾随 Spacer 同进 if 保 voiceMode 间距单份)
                if (!voiceMode) {
                    AppGhostIconButton(
                        AppIconType.Emoji,
                        onClick = {
                            focusManager.clearFocus()
                            mediaPanel = if (mediaPanel == 1) 0 else 1
                        },
                        size = 36.dp,
                        iconSize = 20.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                // M24 七轮修订(用户裁决「输入框有文字的时候不是新加一个发送按钮而是直接不显示
                // 加号然后显示发送按钮,这样不会显示的比较挤」):末位按钮槽二态互换——有文字=
                // 发送钮(乐观发送),无文字=加号动作面板开关(心愿墙/图片/拍照三卡)。任意时刻
                // 恰好一个按钮,行宽稳定不挤。⚠ 交互代价(微信同款):有文字时加号面板不可开,
                // 需先清空文字;语音态恒加号(六轮语义不变)。表情面板内发送胶囊属面板域,面板
                // 开着时键盘收起、点输入框唤键盘即收面板,与发送钮不同时在场
                if (!voiceMode && inputText.trim().isNotEmpty()) {
                    // 发送:M24 修订 乐观发送(微信语义)——即点即现:乐观入列(临时 id)+ 立即清
                    // 输入,POST 回包换真身/失败红叹号原位重发(旧「POST 成功才清输入,失败保留
                    // 文案」语义废弃)。sending(媒体上传链互斥)期间按钮降透明+加载圈不响应
                    // 点击(F12)。M24:voiceMode 时隐藏(槽位被按住说话胶囊占据)
                    AppIconButton(
                        AppIconType.Send,
                        onClick = {
                            val text = inputText.trim()
                            if (text.isNotEmpty() && !sending) {
                                inputText = ""
                                val localId = ChatStore.appendPending(
                                    pager,
                                    ChatMsg(
                                        id = "",
                                        isMine = true,
                                        text = text,
                                        kind = MsgKind.TEXT,
                                        createTime = nowEpochMs(),
                                        sendState = MSG_SEND_SENDING,
                                    ),
                                )
                                triggerSendScroll()
                                scope.launch {
                                    val result = ChatStore.send(pager, text, localId)
                                    if (result is ApiResult.Ok) {
                                        // P3a:send 返回 Ok 时回包真身已 confirmPending 入列(AppState)。
                                        // 直接启动兜底滚底——若轮询抢先把同条消息入列则列表不增、
                                        // size effect 不触发,兜底承接(见 triggerSendScroll);先
                                        // awaitMeasured 等 measure 落地再滚,防 stale totalItemsCount 吞点
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
                } else {
                    // M24 动作面板开关(心愿墙/图片/拍照三卡;与表情面板互斥)
                    AppGhostIconButton(
                        AppIconType.Plus,
                        onClick = {
                            focusManager.clearFocus()
                            mediaPanel = if (mediaPanel == 2) 0 else 2
                        },
                        size = 36.dp,
                        iconSize = 20.dp,
                    )
                }
            }

            // ── 表情/动作面板(M24 修订 移到输入行下方,微信式布局——用户裁决「表情包弹出来后
            // 在输入框下面」):mediaPanel 1=表情网格 2=动作面板[仅心愿墙卡](表情/加号钮开关,
            // 再点收起;键盘弹起在 onKeyboardHeightChange 内关面板互斥)。普通 Column 节点非浮层
            // (无铁律14 z 序问题);贴底整幅 CardBg 实底(微信式,与页面底区分),自带 dock 清让
            // bottom padding(面板开着时输入行只留 4dp 小间距,见输入行注释)。互斥下面板打开时
            // 键盘必收起,inputBarBottomPaddingDp(0)=86dp 浮岛底栏常数
            if (mediaPanel == 1) {
                // M25 表情面板字典服务端化(替代 M24 内置 3×8 静态网格):EmojiStore 分类 tab +
                // 当前分类 LazyVerticalGrid。点选链不变(M24 五轮裁决「选择的表情包在输入框里」:
                // 点选即 append 进输入框,发送走输入行发送钮,与快捷贴纸条同为 TEXT 链,超长拒插);
                // 退格行原样(面板开着键盘收起,无系统删除途径;emoji 代理对整体删)。
                // 兜底:池空/停用全删/拉取失败 → EmojiStore 回 ChatStickers.ORDER 包装单分类
                // 「常用」(kind=6 渲染字典原样复用,面板永不空白,与 M23 老版语义一致)
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(AppTheme.CardBg) // 贴底整幅实底(微信式面板)
                        .padding(horizontal = AppTheme.PageHPadding, vertical = 8.dp)
                        .padding(bottom = inputBarBottomPaddingDp(keyboardHeight)), // dock 清让,CardBg 随之铺满
                ) {
                    // 分类 tab 行(仅多分类渲染:单分类「常用」无切换价值;横向 in 竖向 = 跨轴,铁律⑦)
                    if (EmojiStore.categories.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(EmojiStore.categories) { cat ->
                                val selected = cat == EmojiStore.effectiveCategory
                                Box(
                                    // 选中态换色不增删节点(铁律③);clickable 与背景同节点(铁律⑤)
                                    modifier = Modifier
                                        .background(
                                            if (selected) theme.brandStart else AppTheme.Divider,
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable { EmojiStore.selectedCategory = cat }
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        cat,
                                        color = if (selected) Color.White else AppTheme.TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    // 网格区:8 列,死高度 138dp(3 行 × 46dp;超 24 枚面板内滚,父 Column 不滚
                    // 无同向嵌套滚动)——Kuikly lazy 网格须死高度,MediaPickerSheet 已实证可行
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier.fillMaxWidth().height(138.dp),
                    ) {
                        items(EmojiStore.currentEmojis) { info ->
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(3.dp)
                                    .clickable {
                                        // 插入输入框(超长拒插,与 TextField maxLength 同源守卫)
                                        if (inputText.length + info.emoji.length <= INPUT_MAX_LENGTH) {
                                            inputText += info.emoji
                                        }
                                    }
                                    .height(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(info.emoji, fontSize = 24.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        // 退格键(微信式右下角):删一个「字」——emoji 代理对整体删(dropLast(1) 会
                        // 留孤儿低代理渲染成豆腐块;检测尾部高低代理对后整对删)
                        Box(
                            modifier = Modifier.size(40.dp)
                                .clickable {
                                    val s = inputText
                                    inputText = when {
                                        s.isEmpty() -> s
                                        s.length >= 2 && s[s.length - 2].isHighSurrogate() && s[s.length - 1].isLowSurrogate() -> s.dropLast(2)
                                        else -> s.dropLast(1)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("⌫", fontSize = 20.sp, color = AppTheme.TextSecondary)
                        }
                    }
                }
            }
            if (mediaPanel == 2) {
                // M24 六轮 动作面板微信化(用户裁决「点击加号后有很多选项,许愿墙,图片,拍照等」):
                // 图片钮撤出输入行并入本面板(M24 十三轮起图片卡改开相册弹层多选,原 pickMedia
                // 混选链退役),拍照卡走 takePhoto 桥(收口同图片链)。图片/拍照卡点卡先收面板
                // 再动作(微信语义);心愿墙卡失败 toast 留面板可重试(sendWishWallCard 自管)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(AppTheme.CardBg) // 贴底整幅实底(与表情面板同形态)
                        .padding(horizontal = AppTheme.PageHPadding, vertical = 8.dp)
                        .padding(bottom = inputBarBottomPaddingDp(keyboardHeight)), // dock 清让,CardBg 随之铺满
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChatActionCard(AppIconType.Star, "心愿墙卡片", Modifier.weight(1f)) { sendWishWallCard() }
                    ChatActionCard(AppIconType.Image, "相册", Modifier.weight(1f)) {
                        focusManager.clearFocus()
                        mediaPanel = 0
                        showMediaPicker = true
                    }
                    ChatActionCard(AppIconType.Camera, "拍照", Modifier.weight(1f)) {
                        mediaPanel = 0
                        takePhoto()
                    }
                }
            }
        }

        // M24 录音 HUD(微信式按住说话反馈):recordMode 时页内中央浮动胶囊,取消态黑↔红同节点
        // background 属性切换(铁律 7);无全屏黑幕(中央区不与浮岛底栏重叠,不触铁律 14),
        // 不拦底层触摸(HUD 无 pointerInput,事件穿透无害——录音手势由按住说话胶囊 consume)
        if (recordMode) {
            RecordHudOverlay(
                cancelZone = recordCancelZone,
                seconds = recordSeconds,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // M24 十三轮 相册弹层(微信式多选):官方 ModalBottomSheet + LazyVerticalGrid 网格,
        // 打开时 queryMediaLibrary 拉库(content:// 缩略图 Glide 直出),多选(≤9)发送交回
        // sendPickedMedia 落盘分派既有上传链(乐观入列排队语义自动继承)
        MediaPickerSheet(
            visible = showMediaPicker,
            pager = pager,
            maxVideoBytes = VIDEO_MAX_BYTES,
            onToast = { toastCouple(pager, it) },
            onDismiss = { showMediaPicker = false },
            onSend = { uris ->
                showMediaPicker = false
                sendPickedMedia(uris)
            },
        )

        // M10/M12 全屏大图预览与撤回确认两枚浮层不再页内挂载——2026-10-17 二轮修复(真机反馈
        // 页内浮层盖不住浮岛底栏)起状态提升 MainPager 根 Box(底栏之后)统一挂载,见 MainPager;
        // 本页仅经 imagePreviewUrl/recallTargetId 参数驱动列表滚动门(userScrollEnabled)
    }
}

/**
 * ChatActionCard — 动作面板单卡(M23;M24 七轮 微信式改版,用户裁决「文字与 icon 上下对齐,
 * 一行三个的正方形」):正方形单元格(aspectRatio(1f) 由列宽推导边长),icon 上/标题下居中
 * 纵排;Divider 实底圆角方卡,背景与 clickable 同节点(铁律⑦),weight 由调用方经 modifier
 * 传入(RowScope,一行三个即 weight(1f)×3)
 */
@Composable
private fun ChatActionCard(
    icon: AppIconType,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(AppTheme.Divider, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .aspectRatio(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(icon, AppTheme.TextSecondary, Modifier.size(26.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, color = AppTheme.TextSecondary, fontSize = 12.sp)
    }
}

/** pickMedia 回包小载体(M24 修订):uri 本地 file:// 副本 / kind "image"|"video"(原生 mime
 *  判型)/ duration 秒(图片恒 0)/ size 字节(视频 16MB 守卫用,common 无文件 stat 能力) */
private data class PickedMedia(val uri: String, val kind: String, val duration: Int, val size: Long)

/**
 * HoldToTalkCapsule — 「按住 说话」胶囊(M24 微信语义,替代 M23 录音条):按下即起录、松手按
 * 取消态分叉发送/取消、上滑超阈翻取消态(滑回自然恢复)。主动消费版手势(按钮不在滚动容器,
 * 但需阻 MainPager 横滑把「按住」拖成「切 Tab」;dragDismissKeyboard 被动版不适用)。
 * ⚠ 手势基建三铁律:①pointerInput key 恒 Unit——voiceMode/recordMode 等易变状态作 key 会杀
 * 进行中手势循环致 up 丢失录音悬挂,回调经 rememberUpdatedState 包装读最新值(BasicWidget 先例);
 * ②down 即 consume + 全程 consume——阻父级横滑/下层点击穿透;③手势协程内勿用 withTimeoutOrNull
 * (2.29 no-op 存根)。取消判定用 down 起点绝对差(累计位移会因滑回不恢复)。
 * 竞态零新增:60s 原生自动停先到 → 发起回调复位 recordMode,HUD 消失+自动发送,迟到 up 被
 * recordStop 首行 !recordMode 拦截;up 先到 → recordStopRequested 闸 + 原生 recording 闸
 */
@Composable
private fun HoldToTalkCapsule(
    onStart: () -> Unit,
    onRelease: (cancel: Boolean) -> Unit,
    onCancelZoneChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val startRef = rememberUpdatedState(onStart)
    val releaseRef = rememberUpdatedState(onRelease)
    val zoneRef = rememberUpdatedState(onCancelZoneChange)
    Box(
        modifier = modifier
            // 背景与手势同节点(铁律 7);CardBg 22dp 圆角与 TextField 槽同形态,切换无跳变
            .background(AppTheme.CardBg, RoundedCornerShape(22.dp))
            .holdToTalkGesture(startRef, releaseRef, zoneRef),
        contentAlignment = Alignment.Center,
    ) {
        Text("按住 说话", color = AppTheme.TextSecondary, fontSize = 13.sp)
    }
}

/** 按住说话手势(阈值 px 换算在手势协程内,PointerInputScope 自带 Density):见组件注释 */
private fun Modifier.holdToTalkGesture(
    startRef: State<() -> Unit>,
    releaseRef: State<(Boolean) -> Unit>,
    zoneRef: State<(Boolean) -> Unit>,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        startRef.value() // 按下即起录(内含 recordMode/sending guard 与未桥接端降级 toast)
        var cancel = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                releaseRef.value(cancel) // 松手:红态=true 取消(静默),黑态=false 发送(recordStop 双击闸)
                change.consume()
                break
            }
            val inZone = (down.position.y - change.position.y) > VOICE_CANCEL_SLIDE_DP.dp.toPx()
            if (inZone != cancel) {
                cancel = inZone
                zoneRef.value(inZone)
            }
            change.consume() // 全程消费:阻 MainPager 横滑与下层点击
        }
    }
}

/**
 * RecordHudOverlay — 录音中央 HUD(M24):按住说话时页内中央浮动反馈;取消态黑↔红同节点
 * background 属性切换(两支均 color 重载,铁律 7 禁 .then 增删节点)。黑态 = Mic 图标 +
 * 4 根波纹条(recordSeconds 逐秒轮转高度档,1fps 确定性摆动零动画 API 铁律 10①)+ 计时 +
 * 操作提示;红态 = Close 图标 + 「松开手指 取消发送」
 */
@Composable
private fun RecordHudOverlay(cancelZone: Boolean, seconds: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                if (cancelZone) AppTheme.Danger.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.75f),
                RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 26.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (cancelZone) {
                AppIcon(AppIconType.Close, Color.White, Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("松开手指 取消发送", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                AppIcon(AppIconType.Mic, Color.White, Modifier.size(26.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(4) { i ->
                        val h = RECORD_WAVE_HEIGHTS_DP[(seconds + i) % RECORD_WAVE_HEIGHTS_DP.size]
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(width = 4.dp, height = h.dp)
                                .background(Color.White, RoundedCornerShape(2.dp)),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(formatMmss(seconds), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "松开发送 · 上滑取消 · ${VOICE_MAX_SECONDS}s 自动发送",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
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
        // 重试胶囊(AppPillChip bgBrush 渐变形态:圆角 16/内距 16·7/12sp Bold 原语义常数覆写照旧)
        AppPillChip(
            "点击重试",
            bgBrush = theme.brandBrush,
            corner = 16.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp),
            fontWeight = FontWeight.Bold,
        ) { onRetry() }
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
 * 取 LocalIndication 与原 clickable 指示器行为持平,长按语义新增不改变点击视觉。
 * M24 修订美化:乐观发送/失败走 ChatMediaStateOverlay 蒙层(黑 28%+白钟表「上传中…」/黑 50%+
 * 红!),乐观期渲染本地 file:// 源蒙层下即见,点击落宿主手势(失败=重发/常态=开大图)
 */
@Composable
private fun ImageBubble(
    url: String,
    mine: Boolean,
    sendState: Int = MSG_SEND_SENT, // M24 修订:乐观发送状态驱动蒙层(SENT 恒不渲染)
    progress: Int = -1, // M24 三轮修订:SENDING 蒙层进度环读数(-1=未知/压缩/非上传窗口)
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null, // M12:非空 = 可撤回,长按弹确认浮层
) {
    Box(
        modifier = Modifier
            .background(AppTheme.InputBg, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .size(IMAGE_BUBBLE_SIZE),
    ) {
        Image(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
            painter = rememberAsyncImagePainter(url),
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
        ChatMediaStateOverlay(sendState, progress)
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
    // 返回键关闭浮层(2026-10-22 浮层返回键接线):BackHandler 组合期注册/离场注销,浮层由
    // MainPager 按状态条件组合,关闭即注销,宿主返回恢复双击退出语义(Kuikly2.29 返回链路:
    // Activity → delegator.onBackPressed 同步问 consumed → Pager 分发栈顶 BackHandler)
    BackHandler { onDismiss() }
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
    // 返回键关闭浮层(同 ChatImagePreviewOverlay):提交中不关,与灰幕点击同闸
    BackHandler { if (!submitting) onDismiss() }
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
