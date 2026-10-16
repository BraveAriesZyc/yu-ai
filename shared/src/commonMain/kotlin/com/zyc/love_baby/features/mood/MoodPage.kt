package com.zyc.love_baby.features.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyRow
import com.tencent.kuikly.compose.foundation.lazy.grid.GridCells
import com.tencent.kuikly.compose.foundation.lazy.grid.GridItemSpan
import com.tencent.kuikly.compose.foundation.lazy.grid.LazyVerticalGrid
import com.tencent.kuikly.compose.foundation.lazy.grid.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.components.AppCheckbox
import com.zyc.love_baby.components.AppCheckBadge
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconTile
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppSectionTitle
import com.zyc.love_baby.components.AppSelectableCard
import com.zyc.love_baby.components.AppTag
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.state.ChatStore
import com.zyc.love_baby.state.CoupleBindState
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.MoodStore
import com.zyc.love_baby.state.MoodOption
import com.zyc.love_baby.state.Wish
import com.zyc.love_baby.state.allWishes
import com.zyc.love_baby.state.currentDate
import com.zyc.love_baby.state.dateKeyOf
import com.zyc.love_baby.state.daysInMonthYear
import com.zyc.love_baby.state.firstWeekdayOfMondayFirst
import com.zyc.love_baby.state.moodCategories
import com.zyc.love_baby.state.moodRecordColor
import com.zyc.love_baby.state.monthParts
import com.zyc.love_baby.state.monthShift
import kotlinx.coroutines.launch

/**
 * 分类胶囊未选底:同色渐变(视觉=纯白)。⚠ 与选中态 brandBrush 同走 background(brush=)
 * 重载(铁律⑦)——原 .then(if...) 两分支混用 color/brush 重载,写两个不同原生 prop
 * (backgroundColor/backgroundImage)→LayerDrawable 两独立图层且层序首建冻结:选中过
 * 一次的胶囊渐变层冻结在白层上方,取消选中后白层在下层原位更新遮不掉,胶囊「幽灵
 * 选中」常亮渐变(聊天页 Q2 同源,2026-10-16 心情页排查时发现顺手修)
 */
private val chipIdleBrush = Brush.linearGradient(listOf(AppTheme.CardBg, AppTheme.CardBg))

/**
 * MoodPage — 心情页:每日心情月历(M3)+ 按情绪分类挑选小心愿,多选后一键"送出"到聊天页给 TA
 * 用法:MoodPage(pager, onGoChat, onOpenManage),选择状态在 MoodStore(全局);月历缓存 moodRecords(双方记录)
 * 依赖:MoodStore/ChatStore/ThemeStore/AppIcons;MoodApi/MessageApi 经 Store 走透
 * 事件:日历切月 → MoodStore.loadMonth;记录心情 → MoodStore.recordMood(乐观+失败回滚,toast 收口);
 *      点卡片切换选中;送出 → ChatStore.sendWishes(逐条 POST 首错即停,成功撤选、部分失败报已发条数)
 *      + 全部成功才 onGoChat() 跳聊天页;点选择器「＋」→ onOpenManage 开字典管理浮层(MainPager 挂载)
 * 功能:月份切换(‹ 2026年10月 ›)+ 7 列日历格(双方心情:我=实心情色点 / TA=同色空心环,无记录=空缺;
 *      取色走记录快照列,删选项历史不变灰,D-14)+
 *      心情选择器(M3.5 D-14:couple 私有字典 MoodStore.moodOptions 服务端下发,LazyRow 横排+
 *      尾「＋」管理芯片;空字典整行空态提示)+ 记录按钮;
 *      心愿墙情绪分类筛选、多选、送出心意(toast 反馈);
 *      整页合一单滚动容器(2026-10-16):月历卡/分类/标题作 span 跨双列表头进心愿墙同一
 *      LazyVerticalGrid,拖动把月历滑走、心愿墙抬满全屏(原月历 ~350dp 固定压底,列表
 *      困在屏底一小条「抬不起来」);勿改回「固定月历+内层网格」同向嵌套(Kuikly 手势
 *      仲裁风险+内层死高度,见 inline 注释)
 */
@Composable
fun MoodPage(
    pager: Pager,
    onGoChat: () -> Unit,
    onOpenManage: () -> Unit,
    listScrollEnabled: Boolean = true,
) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var today by remember { mutableStateOf("") }

    // 进入触发(§7.4 拉取触发点):定当月 + 按月拉双方记录 + 拉选项字典(M3.5);失败静默
    //(空月历/旧字典可见,不崩);常驻堆叠下本页只组合一次,「再进入」由 MainPager 切 Tab
    // 副作用补拉当月与字典
    // 首拉绑定态驱动(Pager 化改造,语义同 TodoPage):五页冷启动即组合,未绑定不拉,
    // 校准/绑定翻 BOUND 时重拉;解绑清场后重绑同键重拉
    LaunchedEffect(CoupleStore.bindState) {
        if (CoupleStore.bindState != CoupleBindState.BOUND) return@LaunchedEffect
        MoodStore.ensureDisplayMonth(pager)
        MoodStore.loadMonth(pager, MoodStore.displayMonth)
        MoodStore.loadOptions(pager)
        today = currentDate(pager)
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 渐变头部
            AppGradientHeader {
                AppHeaderTitle("心情", "把想要的,说给TA听")
                // 选中数量提示
                if (MoodStore.selectedIds.isNotEmpty()) {
                    AppTag(
                        "已选 ${MoodStore.selectedIds.size} 个",
                        bgColor = Color.White.copy(alpha = 0.25f),
                        textColor = Color.White,
                        fontSize = 11.sp,
                        corner = 12.dp,
                        hPadding = 10.dp,
                        vPadding = 4.dp,
                    )
                }
            }

            // 整页合一(2026-10-16):月历卡/分类/标题全部作为 span 跨双列表头进同一
            // LazyVerticalGrid,与心愿墙共用一条滚动容器——拖动即可把月历滑走、心愿墙
            // 抬满全屏(原月历 ~350dp 固定压底,列表困在屏底一小条「抬不起来」)。不用
            // 「外层滚动套内层网格」的同向嵌套:Kuikly 同向嵌套有手势仲裁风险且内层网格
            // 必须死高度;span 表头是单容器官方形态(GridItemSpan/maxLineSpan 2.29 全
            // 实现非死 API,LazyGridMeasure 真消费 spanLayoutProvider,反编译核实)。
            // 残存嵌套仅分类 LazyRow——横向 in 竖向,跨轴,MainPager 横滑先例同型
            val visible = if (MoodStore.categoryIndex == 0) {
                allWishes
            } else {
                allWishes.filter { it.category == moodCategories[MoodStore.categoryIndex] }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                // 字典管理浮层(MainPager 根级)开着时关滚动:遮罩 clickable 不吃拖拽,
                // 背景 grid 原生滚动仍收事件(同 MinePage,2026-10-17 真机实证)
                userScrollEnabled = listScrollEnabled,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 104.dp + navBarSafePaddingDp(),
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 月历卡(跨双列表头;横向 16dp 页边由 contentPadding 提供,卡内勿再补)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MoodCalendarCard(
                        pager = pager,
                        selectedMood = MoodStore.selectedOptionId,
                        onSelectMood = { MoodStore.selectedOptionId = it },
                        recording = recording,
                        today = today,
                        onOpenManage = onOpenManage,
                        onRecord = {
                            if (MoodStore.selectedOptionId.isEmpty()) {
                                toastCouple(pager, "先选一个今天的心情吧")
                            } else if (!recording) {
                                recording = true
                                scope.launch {
                                    val result = MoodStore.recordMood(pager, MoodStore.selectedOptionId)
                                    recording = false
                                    if (result is ApiResult.Ok) {
                                        toastCouple(pager, "已记录,TA 也能看到")
                                    } else {
                                        toastCoupleResult(pager, result)
                                    }
                                }
                            }
                        },
                    )
                }
                // 情绪分类(跨双列表头):16dp 页边由 contentPadding 提供,内部 spacer 全撤
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(moodCategories.size) { index ->
                            val selected = index == MoodStore.categoryIndex
                            Box(
                                // 胶囊背景两分支统一 brush 重载(铁律⑦):原 .then(if...) 混用
                                // color/brush 重载,图层首建冻结后取消选中会「幽灵选中」
                                // 常亮渐变(见文件头 chipIdleBrush 注释)
                                modifier = Modifier
                                    .background(
                                        brush = if (selected) theme.brandBrush else chipIdleBrush,
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .clickable { MoodStore.categoryIndex = index }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            ) {
                                Text(
                                    moodCategories[index],
                                    color = if (selected) Color.White else AppTheme.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
                // 区块标题(跨双列表头)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AppSectionTitle(
                        title = "心愿墙",
                        titleSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        leading = {
                            AppIcon(AppIconType.Heart, theme.brandStart, Modifier.size(15.dp))
                        },
                        trailing = {
                            // 「已送的心愿」入口(心愿时间线页):可点文案,clickable 与透明背景
                            // 同节点(铁律⑤);原「点卡片许愿」说明让位——卡片手势本身已是许愿引导
                            Box(
                                modifier = Modifier.background(Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { openWishTimeline(pager) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Text("已送的心愿 ›", color = theme.brandStart, fontSize = 11.sp)
                            }
                        },
                    )
                }
                items(
                    visible,
                    key = { it.id },
                    contentType = { "wish" }, // 同构卡片,提示复用
                ) { wish ->
                    WishCard(wish = wish)
                }
            }
        }

        // 悬浮送出条(送出 = 逐条 POST 成功即撤选;全部成功后选择集清空,本条随 selectedIds 空自动消失)
        if (MoodStore.selectedIds.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 84.dp + navBarSafePaddingDp())
                    .fillMaxWidth()
                    .background(AppTheme.CardBg, RoundedCornerShape(26.dp))
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "已选 ${MoodStore.selectedIds.size} 个心愿",
                        color = AppTheme.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("送出去,让TA安排", color = AppTheme.TextSecondary, fontSize = 10.sp)
                }
                Box(
                    modifier = Modifier.background(theme.brandBrush, RoundedCornerShape(20.dp))
                        .clickable {
                            if (!sending) {
                                sending = true
                                scope.launch {
                                    val wishes = MoodStore.selectedWishes
                                    val result = ChatStore.sendWishes(pager, wishes)
                                    sending = false
                                    if (result is ApiResult.Ok) {
                                        toastCouple(pager, "心意已送达")
                                        onGoChat()
                                    } else {
                                        // 首错即停:已成功发送的不回滚不重发(成功一条撤选一条),
                                        // 报已发条数,剩余选中可重试补发;一条未发按原样报错
                                        val sent = wishes.size - MoodStore.selectedIds.size
                                        if (sent > 0) {
                                            toastCouple(pager, "已送出 $sent 个心意,剩余的稍后再送")
                                        } else {
                                            toastCoupleResult(pager, result)
                                        }
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(AppIconType.Send, Color.White, Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("送出", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** openWishTimeline — 路由打开心愿时间线页(独立二级页压栈,closePage/系统返回即回) */
private fun openWishTimeline(pager: Pager) {
    try {
        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
            .openPage("wishTimeline", JSONObject())
    } catch (_: Exception) {
    }
}

/** openMoodTimeline — 路由打开心情时间线页(独立二级页压栈,closePage/系统返回即回) */
private fun openMoodTimeline(pager: Pager) {
    try {
        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
            .openPage("moodTimeline", JSONObject())
    } catch (_: Exception) {
    }
}

/**
 * MoodCalendarCard — 每日心情月历卡:标题+月份切换 / 星期表头 / 日历格 / 图例 / 选项选择器 / 记录按钮。
 * 日历格按展示月实际周数渲染;双方心情:我=实心情色点、TA=同色空心环,某日无记录=空缺渲染(§5.2);
 * 取色走记录快照列(moodRecordColor,删选项历史不变灰,D-14)。
 * 铁律对齐:月份钮 clickable 与透明背景同节点(⑤);环=嵌套容器画法(原生 border 方角);
 * 点/日号空缺用子级 if/else 换整个容器(③允许,无背景属性残留);无 Crossfade,切月直接重组;
 * 选择器 LazyRow 横向 in 竖向 = 跨轴嵌套(铁律⑦,分类行同型先例)
 */
@Composable
private fun MoodCalendarCard(
    pager: Pager,
    selectedMood: String,
    onSelectMood: (String) -> Unit,
    recording: Boolean,
    today: String,
    onOpenManage: () -> Unit,
    onRecord: () -> Unit,
) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    val (year, month) = monthParts(MoodStore.displayMonth)

    fun switchMonth(delta: Int) {
        val next = monthShift(MoodStore.displayMonth, delta)
        if (next.isNotEmpty()) {
            // 失败静默:保留现月缓存,不打断浏览(§7.4 只读降级)
            scope.launch { MoodStore.loadMonth(pager, next) }
        }
    }

    Column(
        // 横向 16dp 页边由统一 grid 的 contentPadding 提供(整页合一改造,卡内自补会双倍)
        modifier = Modifier.padding(top = 10.dp)
            .fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        // 标题行 + 月份切换
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIconType.Heart, theme.brandStart, Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("每日心情", color = AppTheme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            MonthNavButton(AppIconType.ChevronLeft) { switchMonth(-1) }
            Text(
                if (year == 0) "—" else "${year}年${month}月",
                color = AppTheme.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            MonthNavButton(AppIconType.ChevronRight) { switchMonth(1) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        // 星期表头(周一..周日,与日历格首列对齐)
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                Text(
                    weekday,
                    color = AppTheme.TextTertiary,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        // 日历格(仅渲染实际周数;首列周一;末周补空位对齐 7 列)
        if (year != 0) {
            val leading = firstWeekdayOfMondayFirst(year, month)
            val cells: List<Int?> = List(leading) { null } + (1..daysInMonthYear(year, month)).toList()
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val dateKey = if (day == null) "" else dateKeyOf(year, month, day)
                        MoodDayCell(
                            day = day,
                            dateKey = dateKey,
                            today = today,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        // 图例:我=实心点 / TA=空心环;圆点颜色=当天心情(§5.2 色点/emoji 区分双方)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(7.dp)
                    .background(theme.brandStart, RoundedCornerShape(3.5.dp)),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("我", color = AppTheme.TextTertiary, fontSize = 10.sp)
            Spacer(modifier = Modifier.width(12.dp))
            MoodRingDot(theme.brandStart)
            Spacer(modifier = Modifier.width(4.dp))
            Text("TA", color = AppTheme.TextTertiary, fontSize = 10.sp)
            Spacer(modifier = Modifier.weight(1f))
            // 「心情时间线」入口(独立二级页):可点文案,clickable 与透明背景同节点(铁律⑤);
            // 原「圆点颜色 = 当天心情」说明让位(图例本身已表意)
            Box(
                modifier = Modifier.background(Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { openMoodTimeline(pager) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text("心情时间线 ›", color = theme.brandStart, fontSize = 10.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // 心情选项选择器(M3.5 D-14:couple 私有字典 MoodStore.moodOptions 服务端 sort asc 下发;
        // 选中背景常驻切色——铁律③禁条件增删背景节点)。字典可增删不固定 5 档,
        // Row+weight(1f) 均分不再成立 → LazyRow 按内容宽滚动(横向 in 竖向=跨轴,铁律⑦分类行同型),
        // 尾挂「＋」管理芯片进字典管理浮层
        if (MoodStore.moodOptions.isEmpty()) {
            // 空字典空态(模板空/全被删):整行提示,点按即开管理浮层(空态自愈入口)
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xFFF5F6FA), RoundedCornerShape(12.dp))
                    .clickable { onOpenManage() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("点 + 添加心情", color = AppTheme.TextTertiary, fontSize = 11.sp)
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MoodStore.moodOptions.size) { index ->
                    val option = MoodStore.moodOptions[index]
                    MoodOptionChip(
                        option = option,
                        selected = option.id == selectedMood,
                        onSelect = { onSelectMood(option.id) },
                    )
                }
                item { ManageMoodChip(onClick = onOpenManage) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // 记录按钮(recordMood 乐观+失败回滚,toast 由回调收口;同日重复记=服务端覆盖写,§4)
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(theme.brandBrush, RoundedCornerShape(22.dp))
                .clickable { onRecord() }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (recording) "记录中…" else "记录今天的心情",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 月份切换圆钮:clickable 与透明背景同节点(铁律⑤,命中测试以背景视图为准) */
@Composable
private fun MonthNavButton(icon: AppIconType, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(26.dp)
            .background(Color.Transparent, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, AppTheme.TextSecondary, Modifier.size(14.dp))
    }
}

/**
 * 单日格:日号(今日=主题色加粗)+ 心情点行;日号/点空缺用子级 if/else 换整个容器,
 * 槽位固定高保持行距整齐(某日无记录=空缺渲染,§5.2)
 */
@Composable
private fun MoodDayCell(day: Int?, dateKey: String, today: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.height(15.dp), contentAlignment = Alignment.Center) {
            if (day != null) {
                val isToday = dateKey == today
                Text(
                    day.toString(),
                    color = if (isToday) ThemeStore.current.brandStart else AppTheme.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 我:实心情色点(取色走记录快照列,删选项后历史不变灰,D-14)
            val mine = if (day == null) null else MoodStore.myMoodOn(dateKey)
            Box(modifier = Modifier.size(9.dp), contentAlignment = Alignment.Center) {
                if (mine != null) {
                    val color = moodRecordColor(mine, AppTheme.TextTertiary)
                    Box(
                        modifier = Modifier.size(7.dp)
                            .background(color, RoundedCornerShape(3.5.dp)),
                    )
                }
            }
            Spacer(modifier = Modifier.width(3.dp))
            // TA:同色空心环(同样走快照列)
            val partner = if (day == null) null else MoodStore.partnerMoodOn(dateKey)
            Box(modifier = Modifier.size(11.dp), contentAlignment = Alignment.Center) {
                if (partner != null) {
                    val color = moodRecordColor(partner, AppTheme.TextTertiary)
                    MoodRingDot(color)
                }
            }
        }
    }
}

/** 空心环圆点(TA 标识):嵌套容器画环(原生 border 描边不跟随圆角,环/填充分属两个原生视图) */
@Composable
private fun MoodRingDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(9.dp)
            .background(color, RoundedCornerShape(4.5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .padding(2.dp)
                .background(AppTheme.CardBg, RoundedCornerShape(2.5.dp)),
        )
    }
}

/**
 * 心情选项芯片:emoji + label 胶囊;选中=主题渐变主色底白字,未选=浅灰底(背景常驻切色,铁律③)。
 * M3.5 起选项不固定 5 档:由 LazyRow 按内容宽排布(原 Row+weight 均分不再成立),
 * 横向内边距让相邻芯片不贴死
 */
@Composable
private fun MoodOptionChip(
    option: MoodOption,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                if (selected) ThemeStore.current.brandStart else Color(0xFFF5F6FA),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(option.emoji, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            option.label,
            color = if (selected) Color.White else AppTheme.TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * 「＋」字典管理芯片(选择器尾):与选项芯片同构双行(高一致,排版齐),点按开管理浮层。
 * 勿引「Plus」图标资源——AppIcons 无此字形,文本"＋"三端字体直出零成本
 */
@Composable
private fun ManageMoodChip(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .background(Color(0xFFF5F6FA), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("＋", fontSize = 15.sp, color = AppTheme.TextSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text("管理", color = AppTheme.TextTertiary, fontSize = 10.sp)
    }
}

/**
 * WishCard — 心愿贴纸卡:渐变图位(装饰泡泡+磨砂图标底座) + 标题/副标题 + 分类点 + 状态圈
 * 依赖:MoodStore(选中态)/ThemeStore/AppIcons
 */
@Composable
private fun WishCard(wish: Wish) {
    val theme = ThemeStore.current
    val selected = wish.id in MoodStore.selectedIds
    // 渐变按 wish 缓存,避免每次重组新建 Brush 实例引发无效 diff
    val tileBrush = remember(wish.id) { Brush.linearGradient(wish.colors) }
    // 选中描边环由 AppSelectableCard 统一处理(环宽常驻占位,选中不抖动)
    // 卡片内所有背景子视图都要透传点击(Kuikly 命中测试以背景视图为准,见 AppCheckbox 注释)
    AppSelectableCard(
        selected = selected,
        modifier = Modifier.fillMaxWidth(),
        corner = 20.dp,
        onClick = { MoodStore.toggle(wish.id) },
    ) {
        // 贴纸图位:渐变底 + 磨砂图标底座
        // (每张卡 = 一棵原生视图树,尽量精简节点数,滚动组合才不卡)
        Box(
            modifier = Modifier.fillMaxWidth().height(106.dp)
                .background(tileBrush, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .clickable { MoodStore.toggle(wish.id) },
        ) {
            // 图标磨砂底座(背景视图,须透传点击)
            AppIconTile(
                wish.icon,
                modifier = Modifier.align(Alignment.Center),
                size = 46.dp,
                bgColor = Color.White.copy(alpha = 0.28f),
                corner = 16.dp,
                iconSize = 24.dp,
                onClick = { MoodStore.toggle(wish.id) },
            )
            // 选中角标(背景视图,须透传点击)
            AppCheckBadge(
                visible = selected,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                onClick = { MoodStore.toggle(wish.id) },
            )
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp).padding(top = 10.dp, bottom = 12.dp)) {
            Text(
                wish.title,
                color = if (selected) theme.brandStart else AppTheme.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(wish.subtitle, color = AppTheme.TextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(wish.category, color = AppTheme.TextTertiary, fontSize = 10.sp)
                Spacer(modifier = Modifier.weight(1f))
                // 右侧状态圈(选中=实心勾,未选=空心环)
                AppCheckbox(
                    checked = selected,
                    size = 18.dp,
                    onCheckedChange = { MoodStore.toggle(wish.id) },
                )
            }
        }
    }
}
