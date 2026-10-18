package com.zyc.love_baby.features.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.tencent.kuikly.compose.animation.animateColorAsState
import com.tencent.kuikly.compose.animation.core.FastOutSlowInEasing
import com.tencent.kuikly.compose.animation.core.tween
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
import com.tencent.kuikly.compose.foundation.lazy.LazyRow
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.maxLength
import com.tencent.kuikly.compose.material3.Text
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
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.views.KeyboardParams
import com.zyc.love_baby.TextField
import com.zyc.love_baby.components.AppCheckbox
import com.zyc.love_baby.components.AppFilterChip
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconButton
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppTag
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.features.chat.chatDayLabelFriendly
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.state.CoupleBindState
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.TODO_DEFAULT_COLOR_HEX
import com.zyc.love_baby.state.colorFromHex
import com.zyc.love_baby.state.TodoItem
import com.zyc.love_baby.state.TodoStore
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 文本长度上限(与服务端 AppTodoCreateReqVO @Length(max=64) 对齐;maxLength 原生层拒绝 + take 兜底) */
private const val TODO_INPUT_MAX_LENGTH = 64

/** 静默轮询间隔(M19):与 ChatStore.POLL_INTERVAL_MS/wish/moment 同款 5s 对账节奏 */
private const val TODO_POLL_INTERVAL_MS = 5_000L

/**
 * 五标签值域与配色(M19 标签选择 UI):值域照 LoveTodoDO.java:65 注释定稿(家庭/恋爱/健康/生活/随手记),
 * server 只存不校验(客户端字典,铁律⑥);色值端上定(纯视觉零迁移)——家庭橙/恋爱粉/健康绿/生活蓝紫/
 * 随手记粉(默认,= TODO_DEFAULT_COLOR_HEX 对齐);web 管理后台 tag 列已展示文本,零配套改动
 */
private val TODO_TAGS = listOf("家庭", "恋爱", "健康", "生活", "随手记")
private val TODO_TAG_COLORS = mapOf(
    "家庭" to "#F97316",
    "恋爱" to "#FF6B81",
    "健康" to "#34C77B",
    "生活" to "#6C7BFF",
    "随手记" to "#FF8FA3",
)

/**
 * TodoPage — 待办页:主题渐变头部 + 分段过滤 + 清单卡片 + 底部添加输入条(M3 服务端同步版)
 * 用法:TodoPage(pager, deleteTargetId, onSetDeleteTarget),数据与过滤状态在 TodoStore(全局,
 *      切 Tab 不丢);写操作经 pager 走 app-api;删除确认状态提升 MainPager(M18:本页是 Tab 页,
 *      页内浮层盖不住浮岛底栏,铁律 14——ChatRecallConfirmOverlay 同款双通道接线)
 * 依赖:TodoStore/ThemeStore/AppIcons/BasicWidget.TextField(String placeholder, keyboardHeightChange)
 * 事件:勾选完成/添加 → TodoStore 写穿透(乐观更新失败回滚,失败 toastCoupleResult);删除 →
 *      onSetDeleteTarget 置 id 弹 MainPager 根级确认浮层,确认后 MainPager 调 TodoStore.delete;
 *      进入首组合 loadAll 全量拉取,再进本页由 MainPager 切 Tab 触发补拉(§7.4 拉取触发点);
 *      添加成功 → 滚到列表尾新条目(TodoStore.add 恒追加,长列表不滚 = 「加了没反应」)
 * 功能:全部/进行中/已完成 三态过滤;空列表引导文案;键盘三件套(2026-10-16 对齐聊天页):
 *  - 软键盘避让(聊天页 F2/F7 同款):keyboardHeightChange 顶起悬浮输入条 + 列表尾 Spacer
 *    同步长高,键盘开着新增也能完整看到新条目落在顶起的输入条上方(几何见各 inline 注释)
 *  - 拖列表收键盘(聊天页 Q3 同款):键盘开着竖向拖列表越 touchSlop 即 clearFocus,
 *    列表容器被动观察器不消费事件,不扰原生滚动/行点击勾选/切过滤(铁律13④)
 *  - 键盘跨页残留由 MainPager 切 Tab clearFocus 全局兜底(上轮已做,本页零改动)
 *  - 输入框高度(2026-10-16 F15 对齐聊天页):单行≈44dp 与添加钮等高(22dp 胶囊圆角=高之半),
 *    maxLines=3 长文本封顶自然长高,64 字上限与服务端 @Length(max=64) 对齐
 */
@Composable
fun TodoPage(
    pager: Pager,
    deleteTargetId: String = "",
    onSetDeleteTarget: (String) -> Unit = {},
    listScrollEnabled: Boolean = true,
    pageVisible: Boolean = false, // M19:5s 静默轮询门控(MainPager 下传;常驻 Tab 离页须停)
) {
    val theme = ThemeStore.current
    var inputText by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    // 选中标签下标(M19,默认「随手记」= lastIndex):添加时传 tag+色,TodoStore.add 默认参兜底
    var selectedTagIndex by remember { mutableStateOf(TODO_TAGS.lastIndex) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // 软键盘高度(dp,KeyboardParams.height),0 = 收起;驱动输入条 bottom 与列表尾 Spacer 顶起
    var keyboardHeight by remember { mutableStateOf(0f) }
    // Q3 拖拽收键盘(2026-10-16,同聊天页):键盘开着时手指拖列表 → clearFocus 收键盘
    // (微信语义)。观察器挂列表容器(见 LazyColumn modifier),被动不消费事件;程序化滚动
    // (新增滚底 scrollToItem 只写原生偏移)零指针事件,天然不误伤(铁律13)
    val focusManager = LocalFocusManager.current
    // 键盘高度回调(BasicWidget 内部 rememberUpdatedState 透传;remember 化满足铁律⑩)。
    // 不需要聊天页的 kbFollow 贴底快照链:本页输入条是悬浮浮岛,列表视口不随键盘收缩,
    // 键盘弹起期间列表不重锚;「新增后滚到新条目」已覆盖键盘开着看新待办的诉求
    val onKeyboardHeightChange: (KeyboardParams) -> Unit = remember {
        { params: KeyboardParams -> keyboardHeight = params.height }
    }
    val todos = TodoStore.todos
    val filtered = visibleTodos()
    // 日期分组条目(2026-10-17 三域查看):变天插组头,三态过滤视图统一生效
    val entries = buildTodoEntries(filtered)

    // 首组合全量拉取(§7.4):失败静默——只读降级,旧数据可见不崩(§11);
    // 常驻堆叠下本页只组合一次,「再进入」由 MainPager 切 Tab 副作用补拉
    // 首拉绑定态驱动(Pager 化改造):HorizontalPager 五页冷启动即全组合,原「首次访问才组合
    // 才拉」的隐含时序已不存在——未登录/未绑定(UNKNOWN/UNBOUND)时静默跳过,校准/绑定翻
    // BOUND 时重拉(解绑清场后重绑同键重拉,与 D-11 清场闭环;冷启动已绑定 = UNKNOWN→BOUND 拉一次)
    LaunchedEffect(CoupleStore.bindState) {
        if (CoupleStore.bindState != CoupleBindState.BOUND) return@LaunchedEffect
        TodoStore.loadAll(pager)
    }

    // 5s 静默轮询(M19,§7.4 对账先例 wish/moment):本页是 HorizontalPager 常驻 Tab(冷启动即
    // 全组合),轮询必须 pageVisible 门控——离页 LaunchedEffect 取消,不像独立 @Page 天然
    // 「组合即活动」。先 delay 再拉(首拉由上方 bindState effect 负责,不重复);Store 层
    // fetchSilent 自带写在飞守卫与相等跳过,失败静默(§11)
    LaunchedEffect(pageVisible, CoupleStore.bindState) {
        if (!pageVisible || CoupleStore.bindState != CoupleBindState.BOUND) return@LaunchedEffect
        while (true) {
            delay(TODO_POLL_INTERVAL_MS)
            TodoStore.fetchSilent(pager)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 渐变头部
            AppGradientHeader {
                AppHeaderTitle("待办清单")
                AppTag(
                    "${TodoStore.doneCount}/${todos.size} 已完成",
                    bgColor = Color.White.copy(alpha = 0.25f),
                    textColor = Color.White,
                    fontSize = 11.sp,
                    corner = 12.dp,
                    hPadding = 10.dp,
                    vPadding = 4.dp,
                )
            }

            // 分段过滤器
            FilterBar(
                filter = TodoStore.filter,
                onFilter = { TodoStore.filter = it },
                modifier = Modifier.padding(horizontal = AppTheme.PageHPadding, vertical = 14.dp),
            )

            // 清单
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    // Q3 拖拽收键盘:被动手势观察器(同聊天页,三个不可妥协点见铁律13④):
                    // ①全程不调 change.consume()——Compose 侧消费移动才会 preventTouch
                    // 阻断原生触摸,不消费 = 原生滚动/惯性/行点击勾选/切过滤全部无感;
                    // ②pass 一律 Initial + requireUnconsumed=false,位移读
                    // positionChangeIgnoreConsumed()——原生层接管滚动后事件带预消费标记,
                    // 普通 positionChange() 对已消费事件返回 Zero 会检测失灵;③竖向
                    // 位移越 touchSlop 且竖向占优(dy>dx)才收——横滑翻页由 MainPager 切
                    // Tab 的 clearFocus 承接,轻点行勾选/切过滤不越 slop 不收。
                    // key=focusManager(页内稳定实例;勿用逐帧变化值做 key,重组会重启
                    // 手势协程)。手势 handler 内勿用 withTimeoutOrNull(2.29 no-op 存根)
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
                // 删除确认浮层(M18)开着时关本页滚动:遮罩只吃 tap 不吃拖拽,背景列表仍收
                // down/move(真机实证),Mood/Mine/Chat 三页同款门控,本页此前是唯一缺口
                userScrollEnabled = listScrollEnabled,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (filtered.isEmpty()) {
                    item {
                        TodoEmptyHint(
                            text = when {
                                todos.isEmpty() -> "还没有待办,添加一条吧"
                                TodoStore.filter == 1 -> "都做完啦,好好休息一下吧"
                                else -> "还没有完成的待办"
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        )
                    }
                }
                items(
                    entries,
                    key = { it.key }, // Header = "h-"+label;RowVal = 服务端 id(均全域唯一)
                    contentType = { if (it is TodoEntry.Header) "todo-header" else "todo" },
                ) { entry ->
                    when (entry) {
                        is TodoEntry.Header -> TodoDayHeader(entry.label)
                        is TodoEntry.RowVal -> TodoRow(
                            todo = entry.todo,
                            onToggle = {
                                scope.launch {
                                    val result = TodoStore.toggle(pager, entry.todo.id)
                                    if (result !is ApiResult.Ok) toastCoupleResult(pager, result)
                                }
                            },
                            onDelete = {
                                // M18:删除改二次确认——目标 id 提升给 MainPager 弹确认浮层,
                                // 确认回调在 MainPager 侧执行 TodoStore.delete(本页零网络动作)
                                onSetDeleteTarget(entry.todo.id)
                            },
                        )
                    }
                }
                // 尾部 Spacer:给悬浮输入条让位(闭键常数 188 = 输入条上缘 86+44 再 +20 呼吸
                // + M19 chips 带约 38dp[chip 高 ~29+间距 8];原 150 恰让位不足,新条目下缘
                // 落输入条带内被遮一截)。键盘弹起同步长高(键盘高+navBar+108 = 顶起输入条
                // 上缘 56+14 呼吸+chips 带 38),滚底的新条目完整落在键盘/输入条上方
                // (2026-10-16 对齐聊天页避让几何,M19 随 chips 行同步 +38;收起归零自动回落,
                // lazy 滚动 clamp 自动保持贴底;真机校准点)
                item {
                    Spacer(
                        modifier = Modifier.height(
                            max(
                                188.dp + navBarSafePaddingDp(),
                                keyboardHeight.dp + navBarSafePaddingDp() + 108.dp,
                            ),
                        ),
                    )
                }
            }
        }

        // 底部添加条(悬浮在底栏上方;软键盘避让 2026-10-16 对齐聊天页 F2/F7)。⚠ 原生
        // Android11PlusKeyboardWatcher 的 height = imeInsets.bottom - navigationBars.bottom
        // (聊天页反编译核实同款)——不含导航栏区域,键盘实际顶边比它高出一条 navBar,
        // 须再叠加 navBarSafePaddingDp() 补齐,否则输入框下缘被导航栏区域上方的键盘盖住
        // 一条;弹起时 bottom 切到「键盘高 + navBar + 12dp」顶到键盘上方,收起回调归零
        // 恢复浮岛常数(悬浮浮岛与聊天页流内顶起等观感)。
        // M19:外包 Column 加标签 chips 行(LazyRow——5 枚 3 字 ≈347dp 超窄屏可用宽,横滑兜底);
        // bottom 浮岛常数搬 Column 整体,输入 Row 几何原样内嵌
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = AppTheme.PageHPadding)
                .padding(
                    bottom = max(
                        86.dp + navBarSafePaddingDp(),
                        keyboardHeight.dp + navBarSafePaddingDp() + 12.dp,
                    ),
                )
                .fillMaxWidth(),
        ) {
            // 五标签选择行(M19):默认「随手记」(= TODO_DEFAULT_TAG,既有数据零意外);
            // 选中 chip 底色用该标签专属色(AppFilterChip.selectedColor 覆盖),所见即所得
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TODO_TAGS.size) { i ->
                    AppFilterChip(
                        TODO_TAGS[i],
                        selected = i == selectedTagIndex,
                        onClick = { selectedTagIndex = i },
                        selectedColor = colorFromHex(TODO_TAG_COLORS[TODO_TAGS[i]] ?: TODO_DEFAULT_COLOR_HEX),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                        .background(AppTheme.CardBg, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it.take(TODO_INPUT_MAX_LENGTH) },
                        placeholder = "想做点什么?",
                        autoFocus = false,
                        placeholderColor = AppTheme.TextTertiary,
                        textStyle = TextStyle(color = AppTheme.TextPrimary, fontSize = 13.sp),
                        maxLines = 3, // 长文本限 3 行竖向生长,不在 22dp 胶囊里无限撑高(2026-10-16 F15 对齐聊天页)
                        keyboardHeightChange = onKeyboardHeightChange, // 写键盘高度:输入条顶起 + 尾 Spacer 长高
                        modifier = Modifier.fillMaxWidth()
                            // 单行≈44dp 与添加钮等高(22dp 胶囊圆角恰为高之半,原 6dp 压成 ~30dp
                            // 矮墩圆角被钳制)+ 触达舒适;3 行时自然长高,底部锚定向上生长
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                            .maxLength(TODO_INPUT_MAX_LENGTH), // 原生层拒绝超限输入,与 take 兜底双保险
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                // 添加按钮(POST 成功才清输入:失败保留文案可重试,无脏数据 §7.4)
                AppIconButton(
                    AppIconType.Plus,
                    onClick = {
                        val text = inputText.trim()
                        if (text.isNotEmpty() && !adding) {
                            adding = true
                            scope.launch {
                                val result = TodoStore.add(
                                    pager,
                                    text,
                                    TODO_TAGS[selectedTagIndex],
                                    TODO_TAG_COLORS[TODO_TAGS[selectedTagIndex]] ?: TODO_DEFAULT_COLOR_HEX,
                                )
                                adding = false
                                if (result is ApiResult.Ok) {
                                    inputText = ""
                                    // 新待办滚到可见(2026-10-16,对齐聊天页 P3a 同源):
                                    // TodoStore.add 恒追加列表尾(服务端 create_time asc 同序),
                                    // 长列表下不滚 = 「加了没反应」。「已完成」过滤不含新
                                    // (未完成)条目,滚了也看不到,跳过。先 awaitMeasured 等
                                    // 含新条目的 measure 落地再 snap 滚到尾 Spacer 下标(铁律13:
                                    // 过早 scrollToItem 撞 stale totalItemsCount 越界守卫被静默
                                    // 吞;尾部天然 clamp 贴底,键盘开着时新条目完整露出)
                                    if (TodoStore.filter != 2) {
                                        // 日期分组后 item 数 = 行数 + 组头数:滚底目标改数 entries
                                        // (尾 Spacer 下标 = entries.size;只数行会少算新条目触发的组头)
                                        val itemCount = buildTodoEntries(visibleTodos()).size
                                        listState.awaitMeasured(itemCount + 1)
                                        listState.scrollToItem(itemCount)
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
}

/** 时间线条目:日期分组头 或 待办行(LazyColumn 混排,ChatPage 日期条同思路) */
private sealed interface TodoEntry {
    val key: String

    /** 分组头:key = "h-" + label(label 全域唯一:今天/昨天/M月d日/yyyy年M月d日/更早) */
    data class Header(val label: String) : TodoEntry {
        override val key: String = "h-$label"
    }

    /** 待办行:key = 服务端 id(整表覆盖/增量 appendDedupe 均唯一) */
    data class RowVal(val todo: TodoItem) : TodoEntry {
        override val key: String = todo.id
    }
}

/**
 * buildTodoEntries — 有序待办 → 分组条目:按 createTime(GMT+8)变天插「今天/昨天/M月d日」头,
 * createTime<=0(旧包/未回传)归「更早」组兜底;组序 = 传入顺序(TodoStore 与服务端
 * create_time asc 同序),新组头随新数据自然落尾,与「新增滚底」语义一致
 */
private fun buildTodoEntries(todos: List<TodoItem>): List<TodoEntry> {
    val entries = ArrayList<TodoEntry>(todos.size * 2)
    var lastLabel: String? = null
    todos.forEach { todo ->
        val label = if (todo.createTime > 0L) chatDayLabelFriendly(todo.createTime) else "更早"
        if (label != lastLabel) {
            entries.add(TodoEntry.Header(label))
            lastLabel = label
        }
        entries.add(TodoEntry.RowVal(todo))
    }
    return entries
}

/** 日期分组头:左对齐小字(单文本节点,无背景无点击,与 ChatPage 居中胶囊刻意区分层级) */
@Composable
private fun TodoDayHeader(label: String) {
    Text(
        label,
        color = AppTheme.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

/** 空态引导:软色图标圆底 + 一句文案(语气对齐既有页面) */
@Composable
private fun TodoEmptyHint(text: String, modifier: Modifier = Modifier) {
    val theme = ThemeStore.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(56.dp)
                .background(theme.brandStart.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconType.Todo, theme.brandStart, Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text, color = AppTheme.TextTertiary, fontSize = 13.sp)
    }
}

@Composable
private fun FilterBar(filter: Int, onFilter: (Int) -> Unit, modifier: Modifier = Modifier) {
    val theme = ThemeStore.current
    val tabs = listOf("全部", "进行中", "已完成")
    Row(
        modifier = modifier.fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == filter
            // 短 tween 压帧数;preferNative 原生路径接管背景色后与主题切换冲突,实测异常,暂不启用
            val bg by animateColorAsState(
                targetValue = if (selected) theme.bg else AppTheme.CardBg,
                animationSpec = tween<Color>(durationMillis = 150, easing = FastOutSlowInEasing),
                label = "filterBg",
            )
            Box(
                modifier = Modifier.weight(1f)
                    .background(bg, RoundedCornerShape(11.dp))
                    .clickable { onFilter(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) AppTheme.TextPrimary else AppTheme.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun TodoRow(todo: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)
            .fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(16.dp))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 圆形勾选框(通用组件)
        AppCheckbox(
            checked = todo.done,
            onCheckedChange = { onToggle() },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                todo.title,
                color = if (todo.done) AppTheme.TextTertiary else AppTheme.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (todo.done) FontWeight.Normal else FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(5.dp))
            AppTag(
                todo.tag,
                bgColor = todo.tagColor.copy(alpha = 0.12f),
                textColor = todo.tagColor,
            )
        }
        // 删除(clickable 与透明 background 同节点,铁律⑤:命中测试以带 background 的原生视图为准,
        // 无背景 clickable 的点击会被行级背景子视图整个吞掉)
        Box(
            modifier = Modifier.size(28.dp)
                .background(Color.Transparent, RoundedCornerShape(14.dp))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconType.Close, Color(0xFFC8CCD8), Modifier.size(16.dp))
        }
    }
}

/** 当前过滤视图的可见待办(页首 filtered 与新增滚底协程共用;协程内新鲜读 Store,防捕获重组期旧列表) */
private fun visibleTodos(): List<TodoItem> = when (TodoStore.filter) {
    1 -> TodoStore.todos.filter { !it.done }
    2 -> TodoStore.todos.filter { it.done }
    else -> TodoStore.todos
}

/**
 * 等列表对「包含目标条数」的那次 measure 真正落地再放行(同 ChatPage.awaitMeasured,
 * 铁律13):Kuikly 的 layoutInfo 只反映上一次 measure,新增待办后立刻 scrollToItem 会
 * 撞陈旧 totalItemsCount 越界守卫被静默吞(snapToItemIndexInternal 首行 index 越界直接
 * return);snapshotFlow 对 neverEqualPolicy 的 layoutInfoState 每次 measure 必发新值,
 * 以「totalItemsCount ≥ itemTotal」为落地信号。500ms 兜底超时(丢弃本次滚动优于错误滚动;
 * 与 ChatPage 同款实现,各自随页面演进,勿强行合并去重)
 */
private suspend fun LazyListState.awaitMeasured(itemTotal: Int) {
    withTimeoutOrNull(500) {
        snapshotFlow { layoutInfo.totalItemsCount }.first { it >= itemTotal }
    }
}
