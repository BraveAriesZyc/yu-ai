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
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.components.AppConfirmOverlay
import com.zyc.love_baby.components.AppDatePickerOverlay
import com.zyc.love_baby.components.AppEmptyHint
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconButton
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppInputField
import com.zyc.love_baby.components.AppLoadFailed
import com.zyc.love_baby.components.AppLoading
import com.zyc.love_baby.components.AppSubPageHeader
import com.zyc.love_baby.components.AppTag
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.components.rememberKeyboardHeight
import com.zyc.love_baby.state.todayDayIndexGmt8
import com.zyc.love_baby.features.couple.BindPrimaryButton
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.data.model.AnniversaryInfo
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.state.AnniversaryStore
import com.zyc.love_baby.state.anniversaryCountdownLabel
import com.zyc.love_baby.state.civilFromDays
import com.zyc.love_baby.state.dateKeyOf
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
 *      TimeKit.todayDayIndexGmt8;couple 组 toast 与 BindPrimaryButton;RouterModule(返回)
 * 事件:返回钮 closePage;失败态「重试」重拉;行删除乐观移除(失败回滚+toast);FAB 开新增浮层
 * 功能:
 *  - 四态:加载中 / 失败重试 / 空态「还没有纪念日」/ 正常列表(展示序 = sortedAnniversaries:
 *    将来倒数升序在前,已过一次性沉底,解析失败最后;每次进入重拉整表覆盖,§7.4)
 *  - AnniversaryRow:38dp emoji 方块(空显 "❤",服务端 DEFAULT '' 口径)+ title + 原样日期 +
 *    「每年/一次性」标签 + 倒数徽标(就是今天高亮品牌色)+ 删除 ✕(TodoRow 同款透明底可点)
 *  - AnniversaryAddOverlay:浮岛式键盘避让(TodoPage 同款共享键盘回调);emoji 预设选一;
 *    日期 = 底部滚轮选择器(AppDatePickerOverlay,初始今天;日历合法性由选择器保证,
 *    提交无格式校验——2026-10-17 用户反馈弃三段手输);提交校验仅标题非空,失败 toast 不关浮层,
 *    成功即收(一次性表单)
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
    // 删除确认目标 id(M18):纪念日删除从 ✕ 一键直删改二次确认——录入成本高于待办,误触即丢
    var deleteTargetId by remember { mutableStateOf("") }
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
                AppSubPageHeader(
                    "纪念日",
                    "重要的日子,都值得记住",
                    onBack = {
                        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                    },
                )
            }
            when {
                loading -> item { AppLoading() }
                loadFailed -> item { AppLoadFailed { load() } }
                sorted.isEmpty() -> item {
                    AppEmptyHint(
                        AppIconType.Heart,
                        "还没有纪念日",
                        modifier = Modifier.padding(top = 64.dp),
                        hint = "恋爱纪念日、生日……点右下角 + 添加一个吧",
                        iconSize = 26.dp,
                    )
                }
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
                            onDelete = { deleteTargetId = item.id },
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
                todayIndex = todayIndex,
                onDismiss = { showAdd = false },
            )
        }

        // 删除确认浮层(M18):后于 FAB/新增浮层组合盖住全部;确认前先清 target 关浮层再删除
        if (deleteTargetId.isNotEmpty()) {
            AppConfirmOverlay(
                title = "删除这个纪念日?",
                message = "删除后双方都看不到这个纪念日了。确定要删除吗?",
                onConfirm = {
                    val id = deleteTargetId
                    deleteTargetId = ""
                    remove(id)
                },
                onDismiss = { deleteTargetId = "" },
            )
        }
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
 * 键盘避让 = TodoPage 浮岛式:输入行共享同一 keyboardHeightChange 回调(聚焦即上报),
 * 键盘起时卡片整体 padding 顶到键盘上方并切 BottomCenter(收起归零回 Center)。
 * 日期行点击先收键盘再弹 AppDatePickerOverlay(挂本根 Box 直下、键盘避让容器之外,
 * 面板贴屏幕底不被键盘 padding 顶起);提交校验失败 toast 不关浮层
 */
@Composable
private fun AnniversaryAddOverlay(pager: Pager, todayIndex: Long, onDismiss: () -> Unit) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var title by remember { mutableStateOf("") }
    // 日期 = 滚轮选择器产物(dateKeyOf 格式,初始今天):选择器保证日历合法性,提交无格式校验
    val (initY, initM, initD) = remember { civilFromDays(todayIndex) }
    var selectedDate by remember { mutableStateOf(dateKeyOf(initY, initM, initD)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var emoji by remember { mutableStateOf(annivEmojiPresets.first()) }
    var repeatYearly by remember { mutableStateOf(true) }
    var adding by remember { mutableStateOf(false) }
    val (keyboardHeight, onKeyboardHeightChange) = rememberKeyboardHeight()

    fun submit() {
        if (adding) return
        val name = title.trim()
        if (name.isEmpty()) {
            toastCouple(pager, "先给纪念日起个名字吧")
            return
        }
        adding = true
        scope.launch {
            val result = AnniversaryStore.add(
                pager, name, selectedDate,
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

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize()
                // 键盘起时整体顶到键盘上方(原生 KeyboardParams.height 不含导航栏区域,TodoPage 输入条
                // 同款几何),收起归零回正中;卡片紧凑,小屏 + 全键盘也放得下
                .padding(
                    bottom = if (keyboardHeight.value > 0f) {
                        keyboardHeight.value.dp + navBarSafePaddingDp() + 12.dp
                    } else {
                        0.dp
                    },
                ),
            contentAlignment = if (keyboardHeight.value > 0f) Alignment.BottomCenter else Alignment.Center,
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
            AppInputField(
                value = title,
                onValueChange = { title = it.filterNot { ch -> ch == '\n' }.take(TITLE_MAX_LENGTH) },
                placeholder = "纪念日名称(如:领证纪念日)",
                maxLines = Int.MAX_VALUE, // 原 AnnivInputRow 走 TextField 缺省可折行,长标题换行展示保真
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
            // 日期选择行(点击弹底部滚轮选择器):左侧标签 + 右侧已选日期 + 箭头;
            // 点击先收键盘再开面板(键盘动画与面板出现并行,收完面板完整露出)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0xFFF5F6FA), RoundedCornerShape(14.dp))
                    .clickable {
                        focusManager.clearFocus()
                        showDatePicker = true
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("日期", color = AppTheme.TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    annivDateLabel(selectedDate),
                    color = AppTheme.TextPrimary,
                    fontSize = 15.sp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                AppIcon(AppIconType.ChevronRight, Color(0xFFC8CCD8), Modifier.size(16.dp))
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

        // 日期滚轮选择器(挂根 Box 直下,在键盘避让容器之外:面板 BottomCenter 贴屏幕底,
        // 不随输入行的键盘 padding 顶起;遮罩/取消可关,确定回吐选中的年月日)
        if (showDatePicker) {
            val cur = selectedDate.split("-")
            AppDatePickerOverlay(
                initialYear = cur.getOrNull(0)?.toIntOrNull() ?: initY,
                initialMonth = cur.getOrNull(1)?.toIntOrNull() ?: initM,
                initialDay = cur.getOrNull(2)?.toIntOrNull() ?: initD,
                onConfirm = { y, m, d ->
                    selectedDate = dateKeyOf(y, m, d)
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false },
            )
        }
    }
}

/** dateKey("yyyy-MM-dd") → 「2024年8月8日」展示串(解析失败原样回显兜底) */
private fun annivDateLabel(key: String): String {
    val parts = key.split("-")
    val year = parts.getOrNull(0) ?: return key
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return key
    val day = parts.getOrNull(2)?.toIntOrNull() ?: return key
    return "${year}年${month}月${day}日"
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
