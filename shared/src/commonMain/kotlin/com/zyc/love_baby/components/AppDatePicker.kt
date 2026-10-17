package com.zyc.love_baby.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 年份可选范围(恋爱纪念/生日场景,1970 起够用;终点给未来 30+ 年余量) */
private const val PICKER_MIN_YEAR = 1970
private const val PICKER_MAX_YEAR = 2060

/** 滚轮行高与可见行数(5 行 = 选中行上下各 2 行,占位 pad 与此联动) */
private val WHEEL_ITEM_HEIGHT = 40.dp
private const val WHEEL_VISIBLE_COUNT = 5

/**
 * 当月天数(公历算术,闰年规则完整)。与 state.daysInMonthYear 同构——components 层
 * 不依赖 state(分层方向:state/features 依赖 components),纯函数各持一份,注释互引防漂移
 */
private fun daysInMonthOf(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}

/**
 * AppDatePickerOverlay — 底部滚轮式日期选择器(全屏遮罩 + 底部白卡三列滚轮,M17 自
 * features.anniversary.AnnivDatePickerOverlay 迁入通用化:M5 纪念日页与 M16 资料编辑浮层
 * 已两域在用,components 层归位)
 * 用法:AppDatePickerOverlay(initialYear, initialMonth, initialDay, onConfirm(y,m,d), onDismiss)
 *      挂在调用方根 Box 最后(自带全屏遮罩,后组合者在上),确认回吐 1 基年月日(日期合法性由
 *      本组件保证:日列随年月联动收窄,selection 不会落在不存在的日子上)
 * 依赖:Kuikly compose LazyColumn(滚轮列)+ LazyListState.isScrollInProgress(停止探测)+
 *      scrollToItem(半格校正);ThemeStore 品牌色;navBarSafePaddingDp(面板底部安全距离)
 * 事件:取消/遮罩点击 onDismiss;确定 onConfirm(selYear, selMonth, effDay);
 *      滚轮拖动停止(settle)才回调选中值,拖动中不改父级选中
 * 功能:
 *  - 三列独立滚轮(年/月/日),行高 40dp 可见 5 行,头尾各垫 2 行空占位使选中项居中
 *  - 手动 snap:Kuikly compose 未导出 rememberSnapFlingBehavior(SnapFlingBehavior.kt 内整体注释),
 *    惯性停止后按 firstVisibleItemIndex+offset 四舍五入对齐半格(scrollToItem 瞬时落位,勿用
 *    animateScrollToItem——原生 setContentOffset animated 通道在 Pager 域有真机卡顿实锤,同源风险)
 *  - 日列 items 随 (年,月) 联动:月大 31/月小 30/2 月闰年 29;年月切换收窄日数时父级
 *    min(selDay, maxDay) 钳制后经 selectedIndex 回传,非拖动中自动校正滚回
 *  - 上下渐隐装饰层无 clickable 不命中(触摸穿透到滚轮),中间选中带为上下两条 1dp 灰线
 *    (core DatePickerView 同视觉);无转场动画(铁律④禁 Crossfade/AnimatedVisibility)
 */

/** 滚轮单列:items 展示行,selectedIndex 受控选中(联动钳制用),settle 后回吐数据 index */
@Composable
private fun DatePickerWheelColumn(
    items: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val pad = WHEEL_VISIBLE_COUNT / 2
    val density = LocalDensity.current
    val itemHpx = with(density) { WHEEL_ITEM_HEIGHT.toPx() }
    val listState = rememberLazyListState()
    // 首帧门:settle effect 首跑发生在初始定位(effect2)之前,此刻 firstVisible=0,
    // 直接回调会把父级选中错误重置到第 0 项(年/月/日全中)——首次空跑只标记不回调
    var settledOnce by remember { mutableStateOf(false) }

    // 滚动停止(含初始定位落定)→ 四舍五入对齐半格 + 回调;listState 头尾垫占位后,
    // 「视口顶 index」即数据选中 index(顶 index + pad = 中心行 = pad + 选中 idx)
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            if (!settledOnce) {
                settledOnce = true
                return@LaunchedEffect
            }
            val last = items.lastIndex
            if (last >= 0) {
                val idx = (listState.firstVisibleItemIndex +
                    (listState.firstVisibleItemScrollOffset / itemHpx).roundToInt())
                    .coerceIn(0, last)
                if (listState.firstVisibleItemIndex != idx || listState.firstVisibleItemScrollOffset != 0) {
                    listState.scrollToItem(idx)
                }
                onSelect(idx)
            }
        }
    }
    // 外部钳制/联动校正(日列随年月收窄等):仅非拖动中滚回受控选中;settle 回调值等于
    // selectedIndex 时 key 不变不重跑,与 settle 对齐无抖动闭环
    LaunchedEffect(selectedIndex, items.size) {
        if (items.isEmpty() || listState.isScrollInProgress) return@LaunchedEffect
        val target = selectedIndex.coerceIn(0, items.lastIndex)
        if (listState.firstVisibleItemIndex != target) {
            listState.scrollToItem(target)
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(WHEEL_ITEM_HEIGHT * WHEEL_VISIBLE_COUNT),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(pad + items.size + pad) { index ->
                val dataIdx = index - pad
                Box(
                    modifier = Modifier.fillMaxWidth().height(WHEEL_ITEM_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    if (dataIdx in items.indices) {
                        Text(items[dataIdx], color = AppTheme.TextPrimary, fontSize = 16.sp)
                    }
                }
            }
        }
        // 上下渐隐(与面板同底色的白雾;装饰层无 clickable,不命中不挡滚轮手势)
        Box(
            modifier = Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT * pad)
                .background(
                    Brush.verticalGradient(listOf(AppTheme.CardBg, Color.Transparent)),
                ),
        )
        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT * pad)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, AppTheme.CardBg)),
                ),
        )
        // 中间选中带:上下两条 1dp 灰线夹住选中行(core DatePickerView 同款视觉)
        Box(
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(top = WHEEL_ITEM_HEIGHT * pad)
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT),
        ) {
            Box(
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFE6E6E6)),
            )
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFE6E6E6)),
            )
        }
    }
}

/**
 * 底部日期选择面板(全屏遮罩 + 底部白卡):取消/标题/确定 + 三列滚轮。
 * 确定回吐 (selYear, selMonth, effDay)——effDay = min(selDay, 当月天数),年月切换把
 * 「1月31日 → 2月」钳成 2 月最后一天,市面 picker 同语义
 */
@Composable
fun AppDatePickerOverlay(
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    onConfirm: (Int, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = ThemeStore.current
    val years = remember { (PICKER_MIN_YEAR..PICKER_MAX_YEAR).map { "${it}年" } }
    val months = remember { (1..12).map { "${it}月" } }
    var selYear by remember { mutableStateOf(initialYear.coerceIn(PICKER_MIN_YEAR, PICKER_MAX_YEAR)) }
    var selMonth by remember { mutableStateOf(initialMonth.coerceIn(1, 12)) }
    var selDay by remember { mutableStateOf(initialDay.coerceIn(1, 31)) }
    val maxDay = daysInMonthOf(selYear, selMonth)
    // 钳制后生效日(重组中不写 state,确定/回传统一用 effDay)
    val effDay = minOf(selDay, maxDay)
    val days = remember(maxDay) { (1..maxDay).map { "${it}日" } }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onDismiss() },
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    AppTheme.CardBg,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 0.dp, bottomStart = 0.dp),
                )
                .clickable { } // 吞点击防透传到遮罩(铁律⑤:background 与 clickable 同节点)
                .padding(horizontal = 18.dp)
                .padding(top = 14.dp, bottom = navBarSafePaddingDp() + 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "取消",
                    color = AppTheme.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onDismiss() },
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "选择日期",
                    color = AppTheme.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "确定",
                    color = theme.brandStart,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onConfirm(selYear, selMonth, effDay) },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerWheelColumn(
                    items = years,
                    selectedIndex = selYear - PICKER_MIN_YEAR,
                    modifier = Modifier.weight(1f),
                ) { selYear = PICKER_MIN_YEAR + it }
                DatePickerWheelColumn(
                    items = months,
                    selectedIndex = selMonth - 1,
                    modifier = Modifier.weight(1f),
                ) { selMonth = it + 1 }
                DatePickerWheelColumn(
                    items = days,
                    selectedIndex = effDay - 1,
                    modifier = Modifier.weight(1f),
                ) { selDay = it + 1 }
            }
        }
    }
}
