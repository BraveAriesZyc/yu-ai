package com.zyc.love_baby.features.anniversary

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
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.KeyboardOptions
import com.tencent.kuikly.compose.foundation.text.maxLength
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.input.KeyboardType
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.views.KeyboardParams
import com.zyc.love_baby.TextField
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconButton
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppTag
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.features.chat.todayDayIndexGmt8
import com.zyc.love_baby.features.couple.BindPrimaryButton
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.features.mood.TimelineLoadFailed
import com.zyc.love_baby.features.mood.TimelineLoading
import com.zyc.love_baby.data.model.AnniversaryInfo
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.state.AnniversaryStore
import com.zyc.love_baby.state.anniversaryCountdownLabel
import com.zyc.love_baby.state.dateKeyOf
import com.zyc.love_baby.state.daysInMonthYear
import com.zyc.love_baby.state.sortedAnniversaries
import kotlinx.coroutines.launch

/** title 与服务端 @Length(32) 对齐;emoji 预设全 Unicode 11 及以前(MoodOptionManageOverlay 同纪律) */
private const val TITLE_MAX_LENGTH = 32
private val annivEmojiPresets = listOf("❤️", "🎂", "💍", "💌", "🌸", "✈️", "🏠", "⭐")

/**
 * AnniversaryPage — 纪念日页(@Page 注册名 "anniversaryList"):情侣共同纪念日清单 + 新增/删除
 * 用法:首页统计卡第 4 列「纪念日(天)」/ 我的页「纪念日提醒」菜单 RouterModule.openPage 打开;
 *      返回键/closePage 返回。独立二级页(WishTimelinePager 同款壳,不走 MainPager 五 Tab)
 * 依赖:AnniversaryStore(全局清单,首页第 4 列共消费,此处增删即时反映回首页);
 *      AppState 倒数纯函数(anniversaryCountdownLabel/sortedAnniversaries,跨天只重算不重拉);
 *      ChatTime.todayDayIndexGmt8;couple 组 toast 与 BindPrimaryButton;RouterModule(返回)
 * 事件:返回钮 closePage;失败态「重试」重拉;行删除乐观移除(失败回滚+toast);FAB 开新增浮层
 * 功能:
 *  - 四态:加载中 / 失败重试 / 空态「还没有纪念日」/ 正常列表(展示序 = sortedAnniversaries:
 *    将来倒数升序在前,已过一次性沉底,解析失败最后;每次进入重拉整表覆盖,§7.4)
 *  - AnniversaryRow:38dp emoji 方块(空显 "❤",服务端 DEFAULT '' 口径)+ title + 原样日期 +
 *    「每年/一次性」标签 + 倒数徽标(就是今天高亮品牌色)+ 删除 ✕(TodoRow 同款透明底可点)
 *  - AnniversaryAddOverlay:浮岛式键盘避让(TodoPage 同款共享键盘回调);emoji 预设选一;
 *    日期三段输入不自动跳格(FocusRequester 无失焦 API,退格回跳边界复杂——明示决策,逐格手点);
 *    提交端上校验(标题非空/年 4 位/月日日历合法)失败 toast 不关浮层,成功即收(一次性表单)
 *  - 浮层打开期间 LazyColumn userScrollEnabled=false(浮层触控穿透铁律);浮层最后组合盖 FAB
 */
@Page("anniversaryList", supportInLocal = true)
internal class AnniversaryPager : BasePager() {

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            AnniversaryPage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }
}

@Composable
internal fun AnniversaryPage(pager: Pager) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    // 今天的天序号 remember 一次:本页短驻留内跨天不重排(回前台/重进重拉校准,§7.4)
    val todayIndex = remember { todayDayIndexGmt8() }
    val anniversaries = AnniversaryStore.anniversaries
    val sorted = sortedAnniversaries(anniversaries, todayIndex)

    fun load() {
        scope.launch {
            loading = true
            val result = AnniversaryStore.loadList(pager)
            if (result is ApiResult.Ok) loadFailed = false else loadFailed = true
            loading = false
        }
    }

    fun remove(id: String) {
        scope.launch {
            val result = AnniversaryStore.delete(pager, id)
            if (result !is ApiResult.Ok) toastCoupleResult(pager, result)
        }
    }

    // 独立页每次打开都是新 Pager 组合,LaunchedEffect(Unit) 即每次进入都重拉
    // (数据在 AnniversaryStore,与首页第 4 列共享,拉取后两处同步刷新)
    LaunchedEffect(Unit) { load() }

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 浮层打开期间禁列表滚动(触控穿透铁律:浮层各节点自成命中区,底下列表须显式关滚动)
            userScrollEnabled = !showAdd,
        ) {
            item {
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
                    AppHeaderTitle("纪念日", "重要的日子,都值得记住")
                }
            }
            when {
                loading -> item { TimelineLoading() }
                loadFailed -> item { TimelineLoadFailed { load() } }
                sorted.isEmpty() -> item { AnniversaryEmptyHint() }
                else -> items(
                    sorted,
                    key = { it.id },
                    contentType = { "anniv" }, // 同构卡片,提示复用
                ) { item ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = AppTheme.PageHPadding)
                            .padding(top = 12.dp),
                    ) {
                        AnniversaryRow(
                            item = item,
                            todayIndex = todayIndex,
                            onDelete = { remove(item.id) },
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp + navBarSafePaddingDp())) }
        }

        // 右下 FAB 新增(浮层打开时被最后组合的 AnniversaryAddOverlay 覆盖,不可误触)
        AppIconButton(
            AppIconType.Plus,
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp + navBarSafePaddingDp()),
        )

        if (showAdd) {
            AnniversaryAddOverlay(
                pager = pager,
                onDismiss = { showAdd = false },
            )
        }
    }
}

/** 空态:软色 Heart 圆底 + 引导文案(照 WishEmptyHint 结构) */
@Composable
private fun AnniversaryEmptyHint() {
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
        Text("还没有纪念日", color = AppTheme.TextSecondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("恋爱纪念日、生日……点右下角 + 添加一个吧", color = AppTheme.TextTertiary, fontSize = 11.sp)
    }
}

/** 单行卡片:emoji 方块 + 名称/日期 + 重复类型标签 + 倒数徽标 + 删除 ✕ */
@Composable
private fun AnniversaryRow(item: AnniversaryInfo, todayIndex: Long, onDelete: () -> Unit) {
    val theme = ThemeStore.current
    val countdown = anniversaryCountdownLabel(item, todayIndex)
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // emoji 方块(空按 ❤ 兜底,服务端 DEFAULT '' 口径)
        Box(
            modifier = Modifier.size(38.dp)
                .background(theme.brandStart.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.emoji.ifEmpty { "❤" }, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                color = AppTheme.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.annivDate, color = AppTheme.TextTertiary, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(8.dp))
                AppTag(
                    if (item.repeatType == 1) "每年" else "一次性",
                    bgColor = theme.brandStart.copy(alpha = 0.10f),
                    textColor = theme.brandStart,
                )
            }
        }
        if (countdown.isNotEmpty()) {
            // 倒数徽标:就是今天高亮品牌色,其余中性灰
            val isToday = countdown == "就是今天"
            AppTag(
                countdown,
                bgColor = if (isToday) theme.brandStart.copy(alpha = 0.14f) else Color(0xFFF3F4F8),
                textColor = if (isToday) theme.brandStart else AppTheme.TextSecondary,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        // 删除 ✕(透明 background 与 clickable 同节点,铁律⑤;TodoRow L449-456 同款)
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

/**
 * AnniversaryAddOverlay — 新增纪念日浮层(独立页内根级挂载,一次性表单:成功即收)。
 * 键盘避让 = TodoPage 浮岛式:四行输入共享同一 keyboardHeightChange 回调(任一聚焦都上报),
 * 键盘起时卡片整体 padding 顶到键盘上方并切 BottomCenter(收起归零回 Center)。
 * 日期三段不自动跳格(见页注释);提交校验失败 toast 不关浮层
 */
@Composable
private fun AnniversaryAddOverlay(pager: Pager, onDismiss: () -> Unit) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var title by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(annivEmojiPresets.first()) }
    var repeatYearly by remember { mutableStateOf(true) }
    var adding by remember { mutableStateOf(false) }
    var keyboardHeight by remember { mutableStateOf(0f) }
    val onKeyboardHeightChange: (KeyboardParams) -> Unit = remember {
        { params: KeyboardParams -> keyboardHeight = params.height }
    }

    fun submit() {
        if (adding) return
        val name = title.trim()
        if (name.isEmpty()) {
            toastCouple(pager, "先给纪念日起个名字吧")
            return
        }
        val y = year.toIntOrNull() ?: 0
        val m = month.toIntOrNull() ?: 0
        val d = day.toIntOrNull() ?: 0
        if (y !in 1000..9999 || m !in 1..12 || d !in 1..daysInMonthYear(y, m)) {
            toastCouple(pager, if (year.length != 4) "年份请输 4 位数字" else "日期不完整或不存在")
            return
        }
        adding = true
        scope.launch {
            val result = AnniversaryStore.add(
                pager, name, dateKeyOf(y, m, d),
                if (repeatYearly) 1 else 2, emoji,
            )
            adding = false
            if (result is ApiResult.Ok) {
                toastCouple(pager, "已添加")
                focusManager.clearFocus()
                onDismiss()
            } else {
                toastCoupleResult(pager, result)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
            // 键盘起时整体顶到键盘上方(原生 KeyboardParams.height 不含导航栏区域,TodoPage 输入条
            // 同款几何),收起归零回正中;卡片紧凑,小屏 + 全键盘也放得下
            .padding(
                bottom = if (keyboardHeight > 0f) {
                    keyboardHeight.dp + navBarSafePaddingDp() + 12.dp
                } else {
                    0.dp
                },
            ),
        contentAlignment = if (keyboardHeight > 0f) Alignment.BottomCenter else Alignment.Center,
    ) {
        // 灰幕:收键盘 + 关浮层一体(TodoPage 浮层无灰幕是输入条场景,本浮层键盘由输入行触发)
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
                "新增纪念日",
                color = AppTheme.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(14.dp))
            AnnivInputRow(
                value = title,
                onValueChange = { title = it.filterNot { ch -> ch == '\n' }.take(TITLE_MAX_LENGTH) },
                placeholder = "纪念日名称(如:领证纪念日)",
                keyboardHeightChange = onKeyboardHeightChange,
            )
            Spacer(modifier = Modifier.height(10.dp))
            // emoji 预设(横向单层滚动;选中换底色不改结构,铁律③)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(annivEmojiPresets.size) { index ->
                    val selected = annivEmojiPresets[index] == emoji
                    Box(
                        modifier = Modifier.size(34.dp)
                            .background(
                                if (selected) theme.brandStart.copy(alpha = 0.14f) else Color(0xFFF5F6FA),
                                RoundedCornerShape(17.dp),
                            )
                            .clickable { emoji = annivEmojiPresets[index] },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(annivEmojiPresets[index], fontSize = 16.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // 日期三段(数字键盘;全部 autoFocus=false 防开浮层即弹键盘):
            // 单位字「年/月/日」常驻后缀 + 示例数字 hint,客户一眼可读(替代隐晦的 yyyy/MM/dd)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnnivDateField(
                    value = year,
                    onValueChange = { year = it.filter(Char::isDigit).take(4) },
                    hint = "2024",
                    width = 58.dp,
                    maxLen = 4,
                    keyboardHeightChange = onKeyboardHeightChange,
                )
                DateUnit("年")
                AnnivDateField(
                    value = month,
                    onValueChange = { month = it.filter(Char::isDigit).take(2) },
                    hint = "8",
                    width = 40.dp,
                    maxLen = 2,
                    keyboardHeightChange = onKeyboardHeightChange,
                )
                DateUnit("月")
                AnnivDateField(
                    value = day,
                    onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    hint = "8",
                    width = 40.dp,
                    maxLen = 2,
                    keyboardHeightChange = onKeyboardHeightChange,
                )
                DateUnit("日")
            }
            Spacer(modifier = Modifier.height(10.dp))
            // 重复类型胶囊(BindModeChips 同视觉,本域私有不复用 couple 组件)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xFFF3F4F8), RoundedCornerShape(18.dp))
                    .padding(3.dp),
            ) {
                AnnivChip("每年", repeatYearly, Modifier.weight(1f)) { repeatYearly = true }
                AnnivChip("一次性", !repeatYearly, Modifier.weight(1f)) { repeatYearly = false }
            }
            Spacer(modifier = Modifier.height(16.dp))
            BindPrimaryButton(text = "添 加", submitting = adding) { submit() }
        }
    }
}

/** 输入行(浅灰底圆角,BindInputField 同视觉;键盘回调透传——浮层避让靠任一聚焦行上报)。固定高挂行容器、TextField 自适应行高居中(原生 gravity 恒 TOP|START) */
@Composable
private fun AnnivInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardHeightChange: (KeyboardParams) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFFF5F6FA), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            placeholderColor = AppTheme.TextTertiary,
            autoFocus = false,
            textStyle = TextStyle(color = AppTheme.TextPrimary, fontSize = 15.sp),
            keyboardHeightChange = keyboardHeightChange,
        )
    }
}

/**
 * 日期小段输入(数字键盘;maxLength 原生层拒绝超限输入 + take 截断双保险,BindInputField
 * 无键盘回调参数故本域自建;background 挂在 Box 包装层,TextField 透底)。固定高 44dp 挂
 * 外层 Box + contentAlignment=Center 居中(TextField 自带固定高度会被原生 TOP|START gravity
 * 顶到上缘),数字水平居中走 TextStyle.textAlign(CoreTextField 会映射到原生 textAlign)
 */
@Composable
private fun AnnivDateField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    width: Dp,
    maxLen: Int,
    keyboardHeightChange: (KeyboardParams) -> Unit,
) {
    Box(
        modifier = Modifier.width(width)
            .height(44.dp)
            .background(Color(0xFFF5F6FA), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        TextField(
            modifier = Modifier.fillMaxWidth().maxLength(maxLen),
            value = value,
            onValueChange = onValueChange,
            placeholder = hint,
            placeholderColor = AppTheme.TextTertiary,
            autoFocus = false,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(
                color = AppTheme.TextPrimary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            ),
            keyboardHeightChange = keyboardHeightChange,
        )
    }
}

/** 日期段单位后缀(「年/月/日」常驻可读,替代隐晦的 yyyy/MM/dd;示例数字见各段 hint) */
@Composable
private fun DateUnit(text: String) {
    Text(
        text,
        color = AppTheme.TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** 重复类型选中胶囊(BindModeChip 同视觉;选中换底色不改结构,铁律③) */
@Composable
private fun AnnivChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (selected) AppTheme.CardBg else Color.Transparent,
                RoundedCornerShape(15.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) ThemeStore.current.brandStart else AppTheme.TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
