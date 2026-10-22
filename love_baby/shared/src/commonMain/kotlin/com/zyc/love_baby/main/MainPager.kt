package com.zyc.love_baby.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.pager.HorizontalPager
import com.tencent.kuikly.compose.foundation.pager.rememberPagerState
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.compose.profiler.RecompositionProfiler
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.components.AppBottomBar
import com.zyc.love_baby.components.AppConfirmOverlay
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.data.model.UserProfile
import com.zyc.love_baby.features.auth.GatePanel
import com.zyc.love_baby.features.chat.ChatImagePreviewOverlay
import com.zyc.love_baby.features.chat.ChatPage
import com.zyc.love_baby.features.chat.ChatRecallConfirmOverlay
import com.zyc.love_baby.features.couple.BindingPanel
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.features.mine.MineAboutOverlay
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.AuthApi
import com.zyc.love_baby.network.UserApi
import com.zyc.love_baby.state.AnniversaryStore
import com.zyc.love_baby.state.ChatPushStore
import com.zyc.love_baby.state.ChatStore
import com.zyc.love_baby.state.CheckinStore
import com.zyc.love_baby.state.CoupleBindState
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.LoveConfigStore
import com.zyc.love_baby.state.MoodStore
import com.zyc.love_baby.state.StatsStore
import com.zyc.love_baby.state.TodoStore
import com.zyc.love_baby.features.home.HomePage
import com.zyc.love_baby.features.mine.MinePage
import com.zyc.love_baby.features.mood.MoodOptionManageOverlay
import com.zyc.love_baby.features.mood.MoodPage
import com.zyc.love_baby.features.todo.TodoPage
import com.zyc.love_baby.state.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainPager — 应用主页面(@Page 注册名 "main"):底部导航五 Tab 容器
 * 用法:打开 pageName=main;KuiklyRenderActivity 默认启动页
 * 依赖:BasePager;五个 features 页面;AppBottomBar;ThemeStore/SessionStore/AppState 各 Store
 * 事件:Tab 切换;回前台(pageDidAppear,排除冷启动首次)→ CoupleStore.notifyForeground(F8):
 *      MainPageImpl 立即 refresh 校准一次 + 轮询循环重启(连败暂停/退避中的轮询立即恢复,§7.4);
 *      聊天角标已随 M3 移除本地假回复而恒空(推送角标留待 §12.4 接入时恢复)
 * 功能:
 *  - willInit 首帧前三初始化:ThemeStore.initFromPageData(主题)+ SessionStore.initFromPageData(会话)
 *    + CoupleStore.initFromPageData(绑定态乐观恢复),均只读 pageData 不走模块,无时序问题;
 *    didInit 的 restore 仅作无参数端的兜底
 *  - 五 Tab 容器 = HorizontalPager(beyondViewportPageCount=4 五页全驻留),支持左右滑切换;
 *    页面状态在全局 Store 中,点切/滑动均不丢
 *  - 切 Tab 收键盘(P1,2026-10-16):targetPage 翻转即 LocalFocusManager.clearFocus() 清页面
 *    焦点树——Kuikly 键盘生命周期绑 TextField 焦点,失焦即 stopInput→原生 blur,一处覆盖五 Tab;
 *    并向 ChatPage 传 pageVisible = selectedTab == 3 驱动进页无条件滚底(P3b)
 *  - 登录门:未登录时 GatePanel 整树条件渲染盖住五 Tab(SessionStore,§7.3)
 *  - 绑定门(M2):已登录未绑定(含启动校准的未知态)时 BindingPanel 整树条件渲染(§7.3 三态状态机);
 *    登录态翻转拉 couple/get 校准 CoupleStore,已绑定后低频轮询感知对方绑定/解绑(§7.4)
 *  - 浮层宿主(F6,§7.7):解绑确认/退出确认/心情字典管理/关于信息卡等浮层挂本页根 Box
 *    且后于底栏组合 → 盖住底栏、遮罩挡触摸,打开期间底栏不可误触;MinePage 只回调上报
 *    不再自持浮层(资料编辑 2026-10-18 收进 profilePage 详情页,根级编辑浮层已拆除)
 *  - 绑定清场(F5,D-11):bindState 翻 UNBOUND(主动解绑/被动感知)即清 Chat/Todo/Mood 三 Store,
 *    重绑后上一段关系的数据与游标不残留、消息流从零开始
 *  - 调试:PROFILE_RECOMPOSITION 置 true 用官方重组分析器定位卡顿
 */
@Page("main", supportInLocal = true)
internal class MainPager : BasePager() {

    /** 是否已消费过冷启动首次出现(pageDidAppear):首次不算「回前台」,不触发 refresh(F8) */
    private var appearedOnce = false

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:从启动参数读(pageData 此时已就绪,不走模块无时序问题),
        // didInit 的 restore 仅作无参数端的兜底
        ThemeStore.initFromPageData(this)
        // 冷启动会话判定同样前置到 willInit(内部幂等,重复调用无副作用):有有效会话时首帧
        // isLoggedIn 即为 true,Gate 根本不进入组合(§7.6①);不放 GatePanel 组合期 remember ——
        // 组合期间写快照状态依赖同帧再组合的失效合并,是未验证假设(ThemeStore 同款先例,零风险等价)
        SessionStore.initFromPageData(this)
        // 绑定态乐观恢复(M2,2026-10-17 真机反馈「每次启动都闪一次绑定页」):宿主经 pageData 注入
        // 绑定提示,bound 直接翻 BOUND——已绑定用户冷启动首帧即主功能,BindingPanel 不进组合;
        // 只恢复不校准,couple/invite 明细仍空,由下方 LaunchedEffect 的 refresh 补齐;乐观 BOUND
        // 对不上(如另一端已解绑)refresh 翻 UNBOUND 走既有清场 effect 自愈
        CoupleStore.initFromPageData(this)
        setContent {
            MainPageImpl(this)
        }
    }

    /**
     * 回前台回调(F8,§7.4 前台拉取触发点):跳过冷启动首次出现后,每次回前台自增
     * CoupleStore.foregroundTick —— MainPageImpl 的前台校准 LaunchedEffect 立即 refresh 一次,
     * 轮询 LaunchedEffect 同 key 重启(连败暂停/退避中立即回常规自适应间隔)。
     * 事件链(Android 已核对 core 源码):KuiklyRenderActivity.onResume → delegator.onResume →
     * core PAGER_EVENT_DID_APPEAR("viewDidAppear") → 本回调;iOS/鸿蒙同机制;
     * H5 未见等价触发(未验证)——退避重试仍兜底,前台即时恢复仅部分端生效
     */
    override fun pageDidAppear() {
        super.pageDidAppear()
        if (appearedOnce) {
            CoupleStore.notifyForeground()
        } else {
            appearedOnce = true
        }
    }

    // 注意:模块在 initModule() 时才注册,而 willInit() 早于 initModule()
    // 所以恢复主题必须放 didInit()(initModule 之后),否则 acquireModule 会异步崩溃
    override fun didInit() {
        super.didInit()
        if (PROFILE_RECOMPOSITION) {
            RecompositionProfiler.configure {
                enableOverlay = true   // 悬浮热点面板,直观看到重组热点
            }
            RecompositionProfiler.start()
        }
        ThemeStore.restore(this)
    }

    companion object {
        /** 置 true 开启重组性能分析:adb logcat -s RCProfiler 看逐帧重组;报告自动写 profiler_report.json */
        private const val PROFILE_RECOMPOSITION = false
    }
}

@Composable
internal fun MainPageImpl(pager: BasePager) {
    // 五 Tab 容器 = HorizontalPager(2026-10-16 由「常驻堆叠+visibility」改造,支持左右滑):
    // beyondViewportPageCount=4 五页全驻留(页面只组合一次,状态不丢),离屏页随内容整体平移出
    // 视口,无旧常驻堆叠的跨页触摸透传问题(visibility 隐藏页真机实证,教训存档于 CLAUDE.md 铁律⑤)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 5 })
    // 当前 Tab = targetPage(将落位页):拖动越过判定阈值即翻转(图标高亮与底栏胶囊同读此值,
    // 同帧翻转/瞬移),静止时恒等于 currentPage;切 Tab 副作用(静默补拉/聊天轮询门)同读
    // 此值,与手势滑动/点切共用一套状态
    val selectedTab = pagerState.targetPage
    // P1 键盘跨页残留收口(2026-10-16):切 Tab(targetPage 翻转:拖拽过阈值/底栏点切)即清页面
    // 焦点树收键盘。Kuikly 软键盘生命周期完全绑在 TextField 的 Compose 焦点上(获焦 startInput→
    // 原生输入视图 focus 弹键盘;失焦 stopInput→原生 blur 收键盘),五页 bVPC=4 全驻留、焦点状态
    // 原样保留,不清焦点则 A 页弹的键盘滑到 B 页一直挂着;clearFocus 沿单 FocusOwner 焦点树失焦
    // 即自动走 stopInput 链路,无焦点时为 no-op。targetPage 静止时恒等 currentPage
    // (structuralEqualityPolicy 不通知),effect 幂等不空转;拖拽过阈值又拖回原页键盘已收
    // (与微信一致)。⚠ 副作用必须放 LaunchedEffect 勿写重组体;与下方切 Tab 补拉的
    // LaunchedEffect(selectedTab) 同 key 双 effect 各自独立协程,合法
    val focusManager = LocalFocusManager.current
    LaunchedEffect(selectedTab) {
        focusManager.clearFocus()
    }
    // 底栏胶囊位置 = targetPage(阈值翻转/点切时瞬移,与图标高亮同帧同步):拖拽中不逐帧追页面
    // ——连续跟随(currentPage+fraction)真机实测拖拽掉帧(2026-10-16):每帧层参数重算都要过与
    // 五页重摆/边界全量重测共享的 Kotlin 队列,页面位移在原生层不受影响、胶囊帧被推迟即肉眼
    // 可见;瞬移单帧一次 transform 写,无动画可掉。remember 缓存 lambda 捕获跨重组同实例的
    // pagerState(与下方 goTo 同款套路);remember 计算块只构造 lambda 不调用它,lambda 内零
    // Composable 调用,合法。SlidingPill 在 graphicsLayer lambda 内读取——Kuikly 对 layerBlock
    // 做快照观察(NodeCoordinator.updateLayerParameters 的 observeReads),翻转帧仅重算胶囊层
    // 参数写原生 transform:零重组/零重测/零动画帧
    val pillPosition: () -> Float = remember { { pagerState.targetPage.toFloat() } }
    // 我的页资料(F6,§7.7 状态提升):MainPager 单一持有,MinePage/首页展示消费此值
    // (资料编辑 2026-10-18 收进 profilePage 详情页,页内自拉自回填,本层不再挂编辑浮层);
    // 拉取失败静默保留现值(只读降级,§11)
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    // 浮层状态(F6):MinePage「解绑」「退出登录」仅回调上报,浮层在本根 Box 渲染;
    // 心情字典管理浮层(M3.5)同款——MoodPage 选择器「＋」回调上报
    var showUnbindConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showMoodManage by remember { mutableStateOf(false) }
    // 「关于我们」信息卡(M18 死入口接轻功能):MinePage 菜单行回调打开,纯展示零网络
    var showMineAbout by remember { mutableStateOf(false) }
    var unbindSubmitting by remember { mutableStateOf(false) }
    var logoutSubmitting by remember { mutableStateOf(false) }
    // M12 聊天页两枚全屏浮层状态提升(2026-10-17 二轮修复):大图预览/撤回确认挂本根 Box 底栏
    // 之后(与本文件其余根级浮层同语言:黑幕盖住底栏、遮罩挡触摸、底栏压暗可见不可点)
    // ——页内挂载在 Pager 子树 z 序盖不住浮岛底栏,首轮「浮层开着底栏不组合」真机反馈生硬弃用。
    // ChatPage 经值+回写双通道读写,提交函数 submitChatRecall 随浮层同迁于此
    var chatImagePreviewUrl by remember { mutableStateOf("") }
    var chatRecallTargetId by remember { mutableStateOf("") }
    var chatRecallSubmitting by remember { mutableStateOf(false) } // POST 在飞(浮层撤回钮防双击)
    // 待办删除确认(M18):Tab 页页内浮层盖不住浮岛底栏(铁律 14),目标 id 提升 MainPager
    // 挂根级确认浮层(ChatRecallConfirmOverlay 同款双通道);deleting 在飞防重复提交
    var todoDeleteTargetId by remember { mutableStateOf("") }
    var todoDeleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 浮层开着时关闭背景滚动(横滑 Pager + 当前页列表):Kuikly 浮层遮罩的 clickable 只吃
    // tap 不吃拖拽,背景原生滚动容器仍收 down/move(2026-10-17 真机实证「浮层开着还能滚
    // 背景/横滑切页」)——LazyColumn/LazyVerticalGrid/HorizontalPager 均有官方 userScrollEnabled
    // 开关,浮层任一开着即全关,是最干净的阻断通道(遮罩拦截方案在 Kuikly 原生视图层不成立)
    val overlayActive =
        showUnbindConfirm || showLogoutConfirm || showMoodManage ||
            chatImagePreviewUrl.isNotEmpty() || chatRecallTargetId.isNotEmpty() ||
            todoDeleteTargetId.isNotEmpty() || showMineAbout
    // Tab 跳转统一走 scrollToPage 瞬时直达(底栏点切/首页快捷入口/心情页「去悄悄话」),胶囊由
    // pillPosition(=targetPage 瞬移)驱动零动画帧,见 AppBottomBar.SlidingPill。⚠ 勿改回
    // animateScrollToPage(2026-10-16 真机「底栏异常
    // 卡」根因,源码实锤):它把任何 spec——含默认 spring 与显式 tween——经 convertAnimationSpec
    // ToSpringAnimation 转成原生 SpringAnimation + setContentOffset(animated=true) 原生插值
    // (PagerState.kt:1502-1532、PagerStateExtensions.kt:250-259,easing 被丢弃),动画窗口内每帧
    // 回灌完整 compose 布局/放置/绘制管线,跨页边界帧还 forceRemeasure 全量重测五棵驻留页面树,
    // 与胶囊/叶子 tween 抢同一条 Kuikly 线程;scrollToPage 单帧落位无动画窗口,点切爆发落在空闲帧。
    // remember 缓存:捕获的 scope/pagerState 均为跨重组同实例;不缓存则每次 MainPageImpl 重组新建
    // lambda,收回调的页面(强跳过模式按引用相等判定)整树重 diff
    val goTo: (Int) -> Unit = remember { { tab: Int -> scope.launch { pagerState.scrollToPage(tab) } } }
    // 各页回调同款 remember(理由同上):翻转帧/浮层开关帧不再级联五页 body 重执行
    val goChat = remember { { goTo(3) } }
    val openUnbindConfirm = remember { { showUnbindConfirm = true } }
    val openLogoutConfirm = remember { { showLogoutConfirm = true } }
    val openMoodManage = remember { { showMoodManage = true } }
    // 双通道回写/开关回调同款 remember(理由同上):值+回写两通道的「回写端」也必须缓存——
    // 否则翻转帧/浮层开关帧新建 lambda 实例,收回调的 TodoPage/ChatPage/MinePage 参数恒不等,
    // 常驻页整树重 diff(2026-10-22 知识库审计补齐的 4 处漏网)
    val setTodoDeleteTarget = remember { { id: String -> todoDeleteTargetId = id } }
    val setChatImagePreviewUrl = remember { { url: String -> chatImagePreviewUrl = url } }
    val setChatRecallTarget = remember { { id: String -> chatRecallTargetId = id } }
    val openMineAbout = remember { { showMineAbout = true } }

    // 资料拉取(§7.7):userId 变化时重拉(冷启动进入/换号防御);此处一次 GET 换 MainPager
    // 单一数据源,五页展示(首页/我的页昵称头像)/解绑浮层伴侣名展示不再跨组件同步
    LaunchedEffect(SessionStore.userId) {
        if (SessionStore.userId.isEmpty()) {
            profile = null
        } else {
            val result = UserApi.getUser(pager)
            if (result is ApiResult.Ok) {
                profile = UserProfile.from(result.data)
            }
            // 恋爱统计(统计真数据化):与 profile 同点首拉,失败静默(只读降级)
            StatsStore.refresh(pager)
            // 业务配置(M23):登录后首拉(客服微信号等 KV),失败静默走内置兜底(只读降级)
            LoveConfigStore.refresh(pager)
        }
    }

    // 解绑动作(F6 收口):成功翻回未绑定引导态 + toast;失败 toast 保留浮层可重试
    fun submitUnbind() {
        if (unbindSubmitting) return
        unbindSubmitting = true
        scope.launch {
            val result = CoupleStore.unbind(pager)
            unbindSubmitting = false
            if (result is ApiResult.Ok) {
                showUnbindConfirm = false
                toastCouple(pager, "已解绑,想 TA 了随时再绑回来")
            } else {
                toastCoupleResult(pager, result)
            }
        }
    }

    // 退出登录(F6 收口,§5.2):先调服务端 logout、容忍失败(结果不分支,失败也继续),再清本地会话;
    // Gate 出现与 Todo/Mood/Chat/Couple 四 Store 清理由下方 LaunchedEffect(SessionStore.isLoggedIn)
    // 翻转驱动的既有链路承接,不另造机制(M1 验收路径「调 logout → 回 Gate → 重启不复活」)
    fun submitLogout() {
        if (logoutSubmitting) return
        logoutSubmitting = true
        scope.launch {
            AuthApi.logout(pager)
            SessionStore.clear(pager)
            logoutSubmitting = false
            showLogoutConfirm = false
        }
    }

    // 头像上传(F6 收口)已随根级编辑浮层拆除迁走:唯一入口=profilePage 详情页「编辑」,
    // 上传链路 uploadAvatarAndUpdate 由 ProfilePage 页级 scope 持有(浮层关闭不取消上传)

    // 待办删除提交(M18,确认浮层回调):成功静默(列表写穿透消失即反馈,与删前口径一致),
    // 失败关浮层 + toast;在飞防重复提交
    fun submitTodoDelete() {
        if (todoDeleting || todoDeleteTargetId.isEmpty()) return
        val id = todoDeleteTargetId
        todoDeleting = true
        scope.launch {
            val result = TodoStore.delete(pager, id)
            todoDeleting = false
            todoDeleteTargetId = ""
            if (result !is ApiResult.Ok) {
                toastCoupleResult(pager, result)
            }
        }
    }

    // M12 撤回提交(浮层状态提升随迁):POST Ok 后墓碑已由 ChatStore.recall 内部 markLocalRecalled
    // 即地置灰,关浮层即见;失败也关浮层 + toast(超窗 1_003_003_001 由服务端权威兜底,不在端上
    // 复制时间窗判定细节)
    fun submitChatRecall() {
        if (chatRecallSubmitting || chatRecallTargetId.isEmpty()) return
        chatRecallSubmitting = true
        scope.launch {
            val result = ChatStore.recall(pager, chatRecallTargetId)
            chatRecallSubmitting = false
            chatRecallTargetId = ""
            if (result !is ApiResult.Ok) {
                toastCoupleResult(pager, result)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ThemeStore.current.bg)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 五页全驻留(=旧常驻堆叠语义):页面仅组合一次;连带约定——数据页(Todo/Mood/Chat)
            // 首拉已改为 CoupleStore.bindState 驱动,五页冷启动即同组合,「首次访问才组合才拉」
            // 的旧时序假设不复存在,新增常驻页面须同款处理(见 CLAUDE.md 铁律⑤)
            beyondViewportPageCount = 4,
            // 浮层开着时禁横滑切 Tab(遮罩吃不了拖拽手势,见上方 overlayActive 注释)
            userScrollEnabled = !overlayActive,
        ) { index ->
            when (index) {
                0 -> HomePage(
                    pager,
                    myNickname = profile?.nickname.orEmpty(),
                    myAvatar = profile?.avatar.orEmpty(),
                    onGoChat = goChat,
                )
                1 -> TodoPage(
                    pager,
                    deleteTargetId = todoDeleteTargetId,
                    onSetDeleteTarget = setTodoDeleteTarget,
                    listScrollEnabled = !overlayActive,
                    pageVisible = selectedTab == 1, // M19:5s 静默轮询门控(常驻 Tab 离页须停)
                )
                // M19:pageVisible 门控 5s 静默轮询(仅当月对账,离页停)
                2 -> MoodPage(pager, onGoChat = goChat, onOpenManage = openMoodManage, listScrollEnabled = !overlayActive, pageVisible = selectedTab == 2)
                3 -> ChatPage(
                    pager,
                    myNickname = profile?.nickname.orEmpty(),
                    myAvatar = profile?.avatar.orEmpty(),
                    pageVisible = selectedTab == 3,
                    imagePreviewUrl = chatImagePreviewUrl,
                    onImagePreviewChange = setChatImagePreviewUrl,
                    recallTargetId = chatRecallTargetId,
                    onRecallTargetChange = setChatRecallTarget,
                )
                else -> MinePage(
                    pager = pager,
                    profile = profile,
                    onUnbindClick = openUnbindConfirm,
                    onLogoutClick = openLogoutConfirm,
                    onAboutClick = openMineAbout,
                    listScrollEnabled = !overlayActive,
                )
            }
        }

        // 悬浮底部导航(后组合压在页面上层;底部避让导航栏/手势条安全距离)
        // 角标(M11):聊天 Tab 未读红点,快照状态驱动(unreadCount 变化仅重组底栏区块)
        // 底栏无条件组合(2026-10-17 二轮修复:首轮「聊天浮层开着不组合底栏」真机反馈底栏直接
        // 消失太生硬,弃用;浮层语义回归全 App 统一的黑幕盖住/压暗/遮罩挡触摸,见下方两浮层挂载)
        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp)
                .padding(bottom = 10.dp + navBarSafePaddingDp()),
        ) {
            AppBottomBar(
                selectedIndex = selectedTab,
                pillPosition = pillPosition,
                // remember 键控缓存:unreadCount 不变的重组帧复用同一 Map 实例,底栏参数按引用相等
                // 直接跳过(每次新建 mapOf 会让底栏内容区恒判「参数变了」而重组,2026-10-22 审计)
                badges = remember(ChatStore.unreadCount) { mapOf(3 to ChatStore.unreadCount) },
                onSelect = goTo,
            )
        }

        // M12 聊天页两枚全屏浮层(状态提升,挂底栏之后=天然盖住:黑幕下底栏压暗可见、遮罩挡
        // 触摸不可点,与本文件其余四枚根级浮层同语言);ChatPage 页内不再挂载。开着时横滑与
        // 聊天列表滚动经 overlayActive 一并关(遮罩只吃 tap 不吃拖拽,F6);切 Tab 由 ChatPage
        // pageVisible effect 回写清空收口
        if (chatImagePreviewUrl.isNotEmpty()) {
            ChatImagePreviewOverlay(url = chatImagePreviewUrl, onDismiss = { chatImagePreviewUrl = "" })
        }
        if (chatRecallTargetId.isNotEmpty()) {
            ChatRecallConfirmOverlay(
                submitting = chatRecallSubmitting,
                onDismiss = { chatRecallTargetId = "" },
                onConfirm = { submitChatRecall() },
            )
        }

        // 待办删除确认浮层(M18):后于底栏组合 → 盖住底栏,遮罩挡触摸;Tab 页删除确认统一根级挂载
        if (todoDeleteTargetId.isNotEmpty()) {
            AppConfirmOverlay(
                title = "删除这条待办?",
                message = "删除后双方都看不到这条待办了。确定要删除吗?",
                busy = todoDeleting,
                onDismiss = { if (!todoDeleting) todoDeleteTargetId = "" },
                onConfirm = { submitTodoDelete() },
            )
        }

        // 解绑二次确认浮层(F6,§7.7):components/AppConfirmOverlay 单源(原 BindUnbindConfirm
        // 私版已删,title/message/confirmText/busyText 逐项对齐原副本文案);后于底栏组合 → 整层
        // 盖住底栏,遮罩挡触摸底栏不可误触;busy 在飞本体拦全部出口,外层 dismiss 守卫保留双保险
        if (showUnbindConfirm) {
            AppConfirmOverlay(
                title = "解除绑定?",
                message = "解绑后你和 ${CoupleStore.couple?.partnerNickname?.trim().orEmpty().ifEmpty { "TA" }} " +
                    "都会回到未绑定状态,历史记录会保留。确定要解绑吗?",
                confirmText = "确认解绑",
                busyText = "解绑中…",
                busy = unbindSubmitting,
                onConfirm = { submitUnbind() },
                onDismiss = { if (!unbindSubmitting) showUnbindConfirm = false },
            )
        }

        // 退出登录二次确认浮层(F6 同款根级挂载,AppConfirmOverlay 单源,原 LogoutConfirmOverlay
        // 私版已删):后于底栏组合盖住底栏、遮罩挡触摸;提交中不关浮层(dismiss 拦截)防重复提交
        if (showLogoutConfirm) {
            AppConfirmOverlay(
                title = "退出登录?",
                message = "退出后需要重新登录才能继续,本机的记录会保留。确定要退出吗?",
                confirmText = "确认退出",
                busyText = "退出中…",
                busy = logoutSubmitting,
                onConfirm = { submitLogout() },
                onDismiss = { if (!logoutSubmitting) showLogoutConfirm = false },
            )
        }

        // 心情字典管理浮层(M3.5 D-14,F6 同款根级挂载):MoodPage 选择器「＋」/空态行打开,
        // couple 私有心情选项增删;增删走 MoodStore 写穿透 + toast 收口,删选中项浮层内自清选中态
        if (showMoodManage) {
            MoodOptionManageOverlay(
                pager = pager,
                onDismiss = { showMoodManage = false },
            )
        }

        // 「关于我们」信息卡(M18 死入口接轻功能):MinePage 菜单行打开,纯展示零网络,
        // 后于底栏组合盖住底栏(根级浮层惯例);卡外点击/知道了关闭
        if (showMineAbout) {
            MineAboutOverlay(onDismiss = { showMineAbout = false })
        }

        // 登录门(INTEGRATION-DESIGN §7.3):未登录/会话不可恢复时整树条件渲染 GatePanel,
        // 后组合画在上层盖住五 Tab;登录/注册成功翻 SessionStore.isLoggedIn,整树随之卸载(预期行为)。
        // Gate 不进 Tab 体系,不动 Pager/底栏;面板切换在其内部条件渲染(禁 Crossfade)
        if (!SessionStore.isLoggedIn) {
            GatePanel(pager)
        }

        // 绑定门(M2,§7.3 三态状态机,接线只增):已登录且绑定态未知/未绑定时整树条件渲染 BindingPanel
        // 盖住五 Tab(不动 Pager/底栏);绑定成功翻 CoupleStore.bindState 整树随之卸载,
        // 解绑/换号后重新出现。与 Gate 互斥(Gate 在前,两者条件互斥不会同时组合)。
        // 冷启动已绑定用户由 willInit 的 initFromPageData 乐观恢复直接 BOUND,不进此门
        // (2026-10-17 真机反馈「启动闪绑定页」);无提示(新装/未桥接端)才落校准中卡
        if (SessionStore.isLoggedIn && CoupleStore.bindState != CoupleBindState.BOUND) {
            BindingPanel(pager)
        }

        // 绑定态校准(M2,§7.4 couple/get 拉取触发点):登录态翻转时拉一次——Gate 消失后(登录成功/冷启动
        // 已登录)校准三态;登出/会话失效清内存态(四个 Store 全清,防换号残留脏数据)。首次组合即执行:
        // 有提示的冷启动(乐观 BOUND/UNBOUND)拉完校准结论;无提示(新装/未桥接端)首帧 UNKNOWN
        // 由 BindingPanel 的校准中卡片承接。校准是事实源:乐观 BOUND 对不上(另一端已解绑)翻
        // UNBOUND 走下方清场 effect 自愈
        LaunchedEffect(SessionStore.isLoggedIn) {
            if (SessionStore.isLoggedIn) {
                CoupleStore.refresh(pager)
            } else {
                CoupleStore.reset()
                TodoStore.reset()
                AnniversaryStore.reset()
                CheckinStore.reset()
                ChatStore.reset(pager)
                MoodStore.reset()
            }
        }

        // 绑定清场(F5,D-11):解绑→重绑(不登出)链路必须清上一段关系的内存数据——否则旧 couple 的
        // 悄悄话/待办/心情以残留态展示给新伴侣,且 ChatStore.initialLoaded 已置位会让首屏覆盖拉被永久
        // 跳过、lastId 属旧 couple(违反 D-11「重绑产生新 couple_id,消息流从零开始」)。主动解绑
        // (submitUnbind→unbind)与被动感知(轮询 refresh 翻 UNBOUND)两条路都汇于 bindState 翻
        // UNBOUND 驱动,与上方登出清场同范式;UNBOUND 态四 Store 必空,重复清幂等无副作用
        LaunchedEffect(CoupleStore.bindState) {
            if (CoupleStore.bindState == CoupleBindState.UNBOUND) {
                ChatStore.reset(pager)
                TodoStore.reset()
                AnniversaryStore.reset()
                CheckinStore.reset()
                MoodStore.reset()
                // 统计随关系清场归零(首页「在一起(天)」回 "--",我的页三项回 0)
                StatsStore.reset()
            }
        }

        // 回前台校准(F8,§7.4):pageDidAppear(排除冷启动首次)打点驱动,立即 refresh 一次;
        // foregroundTick 初始 0 不触发(冷启动校准由上面的 isLoggedIn effect 负责)
        LaunchedEffect(CoupleStore.foregroundTick) {
            if (CoupleStore.foregroundTick > 0 && SessionStore.isLoggedIn) {
                CoupleStore.refresh(pager)
                // 统计同步新(发消息/待办/心情后回前台即可见最新计数)
                StatsStore.refresh(pager)
                // 业务配置同步新(M23):后台改客服微信号等,App 回前台即见(失败静默走兜底)
                LoveConfigStore.refresh(pager)
                // 纪念日整表补拉(M5):首页第 4 列消费 AnniversaryStore,回前台即可见对端增删;
                // 未绑定时服务端回 1_003_004_003,loadList 失败静默保留旧值(解绑清场已清空,无残留)
                if (CoupleStore.bindState == CoupleBindState.BOUND) {
                    AnniversaryStore.loadList(pager)
                    // 聊天增量补拉(M11 顺手修既有缺口:不在聊天页时回前台从不拉消息;红点随 foreground 校准)
                    ChatStore.loadIncrement(pager)
                    // 今日打卡条校准(M18 修复:回前台此前不刷 CheckinStore,上午打过卡下午回前台
                    // 首页仍显未打卡/对方打卡后恒「等 TA 中」;syncToday 只动 flags 不拽打卡页月缓存)
                    CheckinStore.syncToday(pager)
                }
            }
        }

        // WS 触发器连接管理(D-16,§7.4):登录且 BOUND 才连,否则断(登出/解绑翻 key 即断——
        // 覆盖两处清场 effect 的场景,无重复接线);幂等判重在 ChatPushStore.connect 内。
        // 冷启动走 UNKNOWN→校准翻 BOUND 触发;回前台(foregroundTick)重连——后台期连接被
        // OS 冻结自然死,回前台校准恢复;断线自愈由聊天页 5s 轮询循环体顺手 connect 补位。
        // 未桥接端(iOS/ohos/H5)connect 内 getModule=null 静默降级纯轮询
        LaunchedEffect(SessionStore.isLoggedIn, CoupleStore.bindState, CoupleStore.foregroundTick) {
            if (SessionStore.isLoggedIn && CoupleStore.bindState == CoupleBindState.BOUND) {
                ChatPushStore.connect(pager)
            } else {
                ChatPushStore.disconnect(pager)
            }
        }

        // 切 Tab 静默补拉(M3,§7.4 拉取触发点):Pager 常驻五页,「再进入」由本副作用承接——
        // 待办重拉全量、心情重拉当月+选项字典(M3.5,对端增删经此互通)、聊天增量(lastId 游标);
        // 失败静默不打扰(只读降级,§11)。
        // 每 Tab 的首次到达跳过(remember 记录已首拉过的 Tab):首拉由各页 bindState 驱动的 effect
        // 负责(冷启动 UNKNOWN→BOUND 校准翻转即拉),本副作用再拉会同帧并发双拉(M3 评审修正沿用)
        val initialPulledTabs = remember { mutableSetOf<Int>() }
        LaunchedEffect(selectedTab) {
            if (!SessionStore.isLoggedIn) return@LaunchedEffect
            if (selectedTab !in initialPulledTabs) {
                initialPulledTabs.add(selectedTab)
                return@LaunchedEffect
            }
            when (selectedTab) {
                1 -> TodoStore.loadAll(pager)
                2 -> {
                    MoodStore.loadMonth(pager, MoodStore.displayMonth)
                    MoodStore.loadOptions(pager)
                }
                // M12:聊天 Tab 补拉改全量 resync——增量拉拉不到我不在线时对方撤回的老消息变更,
                // 唯全量覆盖能纠偏;50 条量级成本可接受(key 槽位复用无视觉跳动)
                3 -> ChatStore.resync(pager)
            }
            // 统计顺手刷新(切任意 Tab):发消息/建待办后切页即见最新计数,不等回前台
            StatsStore.refresh(pager)
        }

        // 绑定感知轮询(M2 自适应 + M3 退避恢复,§7.4/F8):A 端等 B 绑定回填伴侣信息、对方解绑后本端
        // 切回引导态都靠它;未绑定期 10s 一轮(等待对方输码的感知窗口,及时性优先),已绑定 60s 一轮。
        // 先 delay 再拉(首拉由校准负责,不重复);连败 3 次进退避 60s→120s→300s(封顶),
        // 任一次成功即回常规自适应间隔——轮询永不永久死亡;回前台(foregroundTick 变化)effect 重启,
        // 连败暂停/退避中的节奏立即恢复为常规间隔。bindState 翻转(绑定/解绑)同样重启,间隔随新状态切换;
        // UNKNOWN 是瞬态不轮询
        if (SessionStore.isLoggedIn && CoupleStore.bindState != CoupleBindState.UNKNOWN) {
            LaunchedEffect(CoupleStore.bindState, CoupleStore.foregroundTick) {
                var failures = 0
                var backoffStep = -1 // -1 = 常规自适应间隔;≥0 = POLL_BACKOFF_STEPS_MS 下标
                while (true) {
                    delay(
                        if (backoffStep >= 0) {
                            CoupleStore.POLL_BACKOFF_STEPS_MS[
                                backoffStep.coerceAtMost(CoupleStore.POLL_BACKOFF_STEPS_MS.lastIndex),
                            ]
                        } else if (CoupleStore.bindState == CoupleBindState.UNBOUND) {
                            CoupleStore.POLL_INTERVAL_UNBOUND_MS
                        } else {
                            CoupleStore.POLL_INTERVAL_BOUND_MS
                        },
                    )
                    val result = CoupleStore.refresh(pager)
                    if (result is ApiResult.Ok) {
                        failures = 0
                        backoffStep = -1 // 成功即回常规节奏(F8 退避恢复)
                    } else {
                        failures++
                        if (failures >= CoupleStore.POLL_MAX_CONSECUTIVE_FAILURES) {
                            if (backoffStep < CoupleStore.POLL_BACKOFF_STEPS_MS.lastIndex) backoffStep++
                            failures = 0
                        }
                    }
                }
            }
        }

        // 全局心跳轮询(M3 停留轮询的 M19 全局化,§7.4;F14 退避同构):原仅停留聊天 Tab(3)时
        // 5s 增量拉取;M19 起放宽为登录+BOUND 任意 Tab 常驻——红点(unreadCount)全 Tab 消费、
        // WS 断线退避期不再停摆:循环体内按当前页分派,聊天页走增量拉取(含 markRead 尾钩子),
        // 其他页走 refreshUnread 轻 GET(只刷红点,不打消息列表)。⚠ 分派须循环体内新鲜读
        // pagerState.targetPage——组合期 selectedTab 一次性求值,闭包引用是陈旧值。
        // effect key 不含 selectedTab:切 Tab 不再杀循环/重置退避(顺带改善 F14 节奏);
        // loadIncrement 幂等(lastId 游标 + Store 层 pull 串行 + 追加去重),与切 Tab 首组合拉取
        // 并发亦安全;失败静默(只读降级,§11);连败 3 次进退避 60s→120s→300s(封顶)继续重试
        // 而非暂停退出(轮询永不永久死亡),任一次成功即回 5s 常规间隔;回前台(foregroundTick 入 key)
        // effect 重启立即恢复常规节奏;先 delay 再拉(进页首拉由页面内 effect 负责,不重复)
        if (SessionStore.isLoggedIn && CoupleStore.bindState == CoupleBindState.BOUND) {
            LaunchedEffect(CoupleStore.foregroundTick) {
                var failures = 0
                var backoffStep = -1 // -1 = 5s 常规间隔;≥0 = POLL_BACKOFF_STEPS_MS 下标
                while (true) {
                    delay(
                        if (backoffStep >= 0) {
                            CoupleStore.POLL_BACKOFF_STEPS_MS[
                                backoffStep.coerceAtMost(CoupleStore.POLL_BACKOFF_STEPS_MS.lastIndex),
                            ]
                        } else {
                            ChatStore.POLL_INTERVAL_MS
                        },
                    )
                    // WS 触发器断线自愈(D-16):每轮顺手 ensure(幂等,握手轻量无风暴)——
                    // 网络闪断后 5s 内重连,恢复即时推送;自愈范围从「仅聊天 Tab」扩到任意 Tab
                    ChatPushStore.connect(pager)
                    val result = if (pagerState.targetPage == 3) {
                        ChatStore.loadIncrement(pager)
                    } else {
                        ChatStore.refreshUnread(pager)
                    }
                    if (result is ApiResult.Ok) {
                        failures = 0
                        backoffStep = -1 // 成功即回常规节奏(F8 退避恢复)
                    } else {
                        failures++
                        if (failures >= CoupleStore.POLL_MAX_CONSECUTIVE_FAILURES) {
                            if (backoffStep < CoupleStore.POLL_BACKOFF_STEPS_MS.lastIndex) backoffStep++
                            failures = 0
                        }
                    }
                }
            }
        }
    }
}
