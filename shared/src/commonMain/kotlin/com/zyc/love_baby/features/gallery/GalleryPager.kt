package com.zyc.love_baby.features.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.ColumnScope
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.components.AppAvatar
import com.zyc.love_baby.components.AppBottomBar
import com.zyc.love_baby.components.AppBrandIntroScreen
import com.zyc.love_baby.components.AppCheckbox
import com.zyc.love_baby.components.AppCheckBadge
import com.zyc.love_baby.components.AppConfirmOverlay
import com.zyc.love_baby.components.AppDateCapsule
import com.zyc.love_baby.components.AppDatePickerOverlay
import com.zyc.love_baby.components.AppEmptyHint
import com.zyc.love_baby.components.AppFeatureCard
import com.zyc.love_baby.components.AppFilterChip
import com.zyc.love_baby.components.AppFilterChipRow
import com.zyc.love_baby.components.AppFeedback
import com.zyc.love_baby.components.AppGhostIconButton
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderBackButton
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconButton
import com.zyc.love_baby.components.AppIconTile
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppInputField
import com.zyc.love_baby.components.AppLoadFailed
import com.zyc.love_baby.components.AppLoading
import com.zyc.love_baby.components.AppMenuDivider
import com.zyc.love_baby.components.AppMenuGroup
import com.zyc.love_baby.components.AppMenuRow
import com.zyc.love_baby.components.AppMonthNav
import com.zyc.love_baby.components.MainTabs
import com.zyc.love_baby.components.AppOverlayCard
import com.zyc.love_baby.components.AppPillChip
import com.zyc.love_baby.components.AppPrimaryButton
import com.zyc.love_baby.components.AppQuickAction
import com.zyc.love_baby.components.AppRingDot
import com.zyc.love_baby.components.AppSectionTitle
import com.zyc.love_baby.components.AppSegmentedChips
import com.zyc.love_baby.components.AppSelectableCard
import com.zyc.love_baby.components.AppStatBar
import com.zyc.love_baby.components.AppStatItem
import com.zyc.love_baby.components.AppSubPageHeader
import com.zyc.love_baby.components.AppTag
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import kotlinx.coroutines.delay

/**
 * 画廊 demo 反馈收口:统一走 components/AppFeedback.toast(可空 getModule 降级不崩,
 * 原 Utils.bridgeModule 即 acquireModule 路径有「异步炸弹」隐患)
 */
private fun demoToast(pager: Pager, message: String) = AppFeedback.toast(pager, message)

/**
 * GalleryPager — 组件列表页(@Page 注册名 "componentList"):项目通用组件 demo 画廊
 * 用法:MinePage「组件列表」入口路由打开;返回键/closePage 返回
 * 依赖:components/ 全部通用组件;GalleryMock 数据;RouterModule(返回)
 * 事件:复选框/分段/筛选/输入/统计项可交互;钮类点击弹 Toast;浮层三件套弹真实浮层
 * 功能:
 *  - 头部即 AppSubPageHeader 活 demo(AppGradientHeader + AppHeaderBackButton + AppHeaderTitle)
 *  - 大类分区(六类,AppSectionTitle 作分区头):基础 / 选择与勾选 / 输入与表单 / 卡片与导航 /
 *    列表与状态 / 浮层与反馈;每类下若干 GallerySection demo 卡(白卡 + 卡内小标题)
 *  - 全屏浮层 demo(AppOverlayCard/AppConfirmOverlay/AppDatePickerOverlay)挂本页根 Box:
 *    独立 @Page 无浮岛底栏,页内根级挂载即可;Tab 页才须提升 MainPager(铁律 14)
 */
@Page("componentList", supportInLocal = true)
internal class GalleryPager : BasePager() {

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            GalleryPage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }
}

@Composable
internal fun GalleryPage(pager: Pager) {
    val theme = ThemeStore.current
    // 浮层 demo 状态(值提升到页根,浮层在 LazyColumn 之后组合 = 天然盖住列表)
    var showDemoOverlayCard by remember { mutableStateOf(false) }
    var showDemoConfirm by remember { mutableStateOf(false) }
    var showDemoDatePicker by remember { mutableStateOf(false) }
    var pickedDateText by remember { mutableStateOf("还没选过") }

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                // 头部 = AppSubPageHeader 活 demo(返回钮 + 标题列一键拼装,原手写骨架直替)
                AppSubPageHeader(
                    title = "组件列表",
                    subtitle = "项目通用组件 · 按类分区 · 可点击交互",
                    onBack = {
                        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                    },
                )
            }

            // ---------- 基础 ----------
            item { GalleryCategory("基础组件", "图标 · 标签 · 日期胶囊") }
            item { IconSection() }
            item { TagSection() }
            item { DateCapsuleSection() }
            item { PillChipSection(pager) }

            // ---------- 选择与勾选 ----------
            item { GalleryCategory("选择与勾选", "勾选框 · 筛选 · 分段") }
            item { CheckSection() }
            item { FilterChipSection() }
            item { SegmentedChipsSection() }

            // ---------- 输入与表单 ----------
            item { GalleryCategory("输入与表单", "输入行 · 主钮 · 圆钮") }
            item { InputSection() }
            item { PrimaryButtonSection() }
            item { IconButtonsSection(pager) }

            // ---------- 卡片与导航 ----------
            item { GalleryCategory("卡片与导航", "卡片 · 菜单 · 子页头 · 月导航 · 底栏") }
            item { CardSection() }
            item { MenuSection(pager) }
            item { SubHeaderSection(pager) }
            item { MonthNavSection() }
            item { BottomBarSection() }

            // ---------- 列表与状态 ----------
            item { GalleryCategory("列表与状态", "统计条 · 加载 · 失败 · 空态") }
            item { StatSection(pager) }
            item { StatusSection() }

            // ---------- 浮层与反馈 ----------
            item { GalleryCategory("浮层与反馈", "全屏浮层 · 引导屏 · 下拉刷新") }
            item {
                OverlaySection(
                    pickedDateText = pickedDateText,
                    onOpenOverlayCard = { showDemoOverlayCard = true },
                    onOpenConfirm = { showDemoConfirm = true },
                    onOpenDatePicker = { showDemoDatePicker = true },
                )
            }
            item { BrandIntroSection() }
            item { PullRefreshNoteSection() }
            item { InfrastructureSection(pager) }

            item { Spacer(modifier = Modifier.height(40.dp + navBarSafePaddingDp())) }
        }

        // 浮层 demo 三件套(挂页根 Box,后组合者在上;独立 @Page 页内根级挂载即可)
        if (showDemoOverlayCard) {
            AppOverlayCard(onDismiss = { showDemoOverlayCard = false }) {
                Text("AppOverlayCard", color = AppTheme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "通用浮层骨架:全屏灰幕 + 居中白卡,卡内容 ColumnScope 自排;灰幕点击或下方按钮收起。",
                    color = AppTheme.TextSecondary,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AppPillChip("关闭浮层", bg = ThemeStore.current.brandStart) {
                        showDemoOverlayCard = false
                    }
                }
            }
        }
        if (showDemoConfirm) {
            AppConfirmOverlay(
                title = "演示确认框",
                message = "这是 AppConfirmOverlay 的真实弹窗:遮罩/「取消」关闭,「确认」弹 Toast;busy=true 期间全部出口拦截。",
                confirmText = "确认",
                onConfirm = {
                    demoToast(pager, "确认了演示框")
                    showDemoConfirm = false
                },
                onDismiss = { showDemoConfirm = false },
            )
        }
        if (showDemoDatePicker) {
            AppDatePickerOverlay(
                initialYear = 2026,
                initialMonth = 10,
                initialDay = 18,
                onConfirm = { year, month, day ->
                    pickedDateText = "${year}年${month}月${day}日"
                    demoToast(pager, "选好了:$pickedDateText")
                    showDemoDatePicker = false
                },
                onDismiss = { showDemoDatePicker = false },
            )
        }
    }
}

/**
 * GalleryCategory — 大类分区头(AppSectionTitle 活 demo):品牌渐变竖条 + 加粗类名 + 尾部说明
 */
@Composable
private fun GalleryCategory(title: String, note: String?) {
    Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
        Spacer(modifier = Modifier.height(26.dp))
        AppSectionTitle(
            title = title,
            trailing = {
                if (note != null) {
                    Text(note, color = AppTheme.TextTertiary, fontSize = 11.sp)
                }
            },
        )
    }
}

/**
 * GallerySection — 画廊 demo 卡:白卡容器 + 卡内小标题行(组件名 + 尾部说明) + demo 内容。
 * 大类分区后标题降为 13sp 卡内行,页面层级让给 GalleryCategory(AppSectionTitle 分区头)
 */
@Composable
private fun GallerySection(
    title: String,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
        Spacer(modifier = Modifier.height(14.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = AppTheme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                if (note != null) {
                    Text(note, color = AppTheme.TextTertiary, fontSize = 10.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

// ---------- 基础组件 ----------

@Composable
private fun IconSection() {
    GallerySection(title = "AppIcon / AppIconTile", note = "Path 矢量自绘") {
        // 全量图标一览,5 个一行
        AppIconType.entries.chunked(5).forEach { rowIcons ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                rowIcons.forEach { iconType ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(iconType, AppTheme.TextSecondary, Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(iconType.name, color = AppTheme.TextTertiary, fontSize = 9.sp)
                    }
                }
                repeat(5 - rowIcons.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIconTile(AppIconType.Heart, bgColor = Color(0xFFFFEAF1), iconTint = Color(0xFFFF5A8C))
            AppIconTile(AppIconType.Star, bgColor = Color(0xFFFFF1E8), iconTint = Color(0xFFFF9A56))
            AppIconTile(
                AppIconType.Bell,
                size = 46.dp,
                bgBrush = ThemeStore.current.brandBrush,
                iconSize = 22.dp,
            )
        }
    }
}

/** AppTag(含 fontWeight demo)+ AppAvatar(白透底/纯色底/渐变底三形态) */
@Composable
private fun TagSection() {
    GallerySection(title = "AppTag / AppAvatar", note = "标签与文字头像") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GalleryMock.tags.forEach { (text, color) ->
                AppTag(text, bgColor = color.copy(alpha = 0.12f), textColor = color)
            }
            // fontWeight demo:AppTag 默认 null 不加粗,显式 FontWeight.Bold 对比
            AppTag(
                "加粗",
                bgColor = GalleryMock.tags[1].second.copy(alpha = 0.12f),
                textColor = GalleryMock.tags[1].second,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppAvatar("宝")
            Spacer(modifier = Modifier.width(12.dp))
            AppAvatar("TA", bgColor = Color(0xFFFFE3EC), textColor = Color(0xFFFF5A8C), fontSize = 13.sp)
            Spacer(modifier = Modifier.width(12.dp))
            AppAvatar("我", size = 34.dp, bgBrush = ThemeStore.current.brandBrush, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("AppAvatar", color = AppTheme.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text("白透底/纯色底/渐变底", color = AppTheme.TextTertiary, fontSize = 10.sp)
            }
        }
    }
}

/** AppDateCapsule — 弱灰日期胶囊:默认形态 + 墓碑条变体(参数覆写,消息撤回灰条同款) */
@Composable
private fun DateCapsuleSection() {
    GallerySection(title = "AppDateCapsule", note = "弱灰日期胶囊") {
        AppDateCapsule("今天 14:30")
        Spacer(modifier = Modifier.height(6.dp))
        // 墓碑条变体:更低透明底 + 更小字号弱化 + 更多竖向内距
        AppDateCapsule(
            "2026年10月18日",
            bgAlpha = 0.10f,
            fontSize = 11.sp,
            textColor = AppTheme.TextTertiary,
            verticalPadding = 4.dp,
        )
    }
}

/** AppPillChip — 轻量文字小钮:浅灰底 / 品牌浅底 / 品牌实底三形态,点击弹 Toast */
@Composable
private fun PillChipSection(pager: Pager) {
    GallerySection(title = "AppPillChip", note = "点击弹 Toast") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppPillChip("浅灰底", bg = Color(0xFFF3F4F8), textColor = AppTheme.TextSecondary) {
                demoToast(pager, "点了浅灰胶囊")
            }
            AppPillChip(
                "品牌浅底",
                bg = ThemeStore.current.brandStart.copy(alpha = 0.12f),
                textColor = ThemeStore.current.brandStart,
                fontWeight = FontWeight.Medium,
            ) {
                demoToast(pager, "点了品牌浅底胶囊")
            }
            AppPillChip("品牌实底", bg = ThemeStore.current.brandStart) {
                demoToast(pager, "点了品牌实底胶囊")
            }
        }
    }
}

// ---------- 选择与勾选 ----------

@Composable
private fun CheckSection() {
    val states = remember { mutableStateListOf(true, false, false) }
    val labels = listOf("一起看日落", "周末吃火锅", "给你带早餐")
    GallerySection(title = "AppCheckbox / AppCheckBadge", note = "点击切换") {
        labels.forEachIndexed { index, label ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppCheckbox(
                    checked = states[index],
                    onCheckedChange = { states[index] = !states[index] },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    label,
                    color = if (states[index]) AppTheme.TextTertiary else AppTheme.TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                AppCheckBadge(visible = states[index])
            }
        }
    }
}

/** AppFilterChipRow / AppFilterChip — 横向滚动筛选胶囊(动态选中态,灵感页分类同构) */
@Composable
private fun FilterChipSection() {
    var selected by remember { mutableStateOf(0) }
    GallerySection(title = "AppFilterChipRow / AppFilterChip", note = "分类筛选胶囊(M17)") {
        AppFilterChipRow(
            labels = listOf("全部", "室内", "户外", "美食", "观影", "手作"),
            selectedIndex = selected,
            onSelect = { selected = it },
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFilterChip("单枚·选中", selected = true, onClick = {})
            AppFilterChip("单枚·未选", selected = false, onClick = {})
        }
    }
}

/** AppSegmentedChips — 分段选择胶囊:灰底默认变体 + 品牌渐变上的白透底变体(auth 域形态) */
@Composable
private fun SegmentedChipsSection() {
    var grayIndex by remember { mutableStateOf(0) }
    var onBrandIndex by remember { mutableStateOf(0) }
    GallerySection(title = "AppSegmentedChips", note = "分段选择 · 两种容器变体") {
        // 灰底变体(绑定域双模式同款,组件默认参数即生产视觉)
        AppSegmentedChips(
            options = GalleryMock.segmentedOptions,
            selectedIndex = grayIndex,
            onSelect = { grayIndex = it },
        )
        Spacer(modifier = Modifier.height(12.dp))
        // 白透底变体:品牌渐变条上传 containerBg=White 25% + unselectedTextColor=White(登录门形态)
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(ThemeStore.current.brandBrush, RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            AppSegmentedChips(
                options = GalleryMock.segmentedOptionsOnBrand,
                selectedIndex = onBrandIndex,
                onSelect = { onBrandIndex = it },
                containerBg = Color.White.copy(alpha = 0.25f),
                unselectedTextColor = Color.White,
            )
        }
    }
}

// ---------- 输入与表单 ----------

/** AppInputField — 浅灰底输入行:基础形态 + trailing 槽(「清空」小钮),输入实时回显 */
@Composable
private fun InputSection() {
    var demoInput by remember { mutableStateOf("") }
    GallerySection(title = "AppInputField", note = "输入行 + trailing 槽") {
        AppInputField(
            value = demoInput,
            onValueChange = { demoInput = it },
            placeholder = "输入点啥试试(48dp 定高居中)",
        )
        if (demoInput.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text("你输入了:$demoInput", color = AppTheme.TextSecondary, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        // trailing 槽:行尾小钮(登录面板「获取验证码」同位);⚠ trailing 是具名参数,
        // 尾随 lambda 会绑去末参 keyboardHeightChange
        AppInputField(
            value = demoInput,
            onValueChange = { demoInput = it },
            placeholder = "带尾部小钮的输入行",
            trailing = {
                AppPillChip("清空", bg = Color(0xFFF3F4F8), textColor = AppTheme.TextSecondary) {
                    demoInput = ""
                }
            },
        )
    }
}

/** AppPrimaryButton — 品牌渐变主钮:submitting 态 demo(点击进入在飞 1.6s 自复位) */
@Composable
private fun PrimaryButtonSection() {
    var submitting by remember { mutableStateOf(false) }
    // demo 在飞自收口:1.6s 后复位(生产中由网络回包置 false)
    LaunchedEffect(submitting) {
        if (submitting) {
            delay(1600)
            submitting = false
        }
    }
    GallerySection(title = "AppPrimaryButton", note = "点一下看 submitting 态") {
        AppPrimaryButton(text = "提交表单", submitting = submitting, busyText = "提交中…") {
            submitting = true
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "submitting=true 期间只拦点击与换 busy 文案,背景不重写(条件换 brush 原生层会残留)",
            color = AppTheme.TextTertiary,
            fontSize = 10.sp,
        )
    }
}

/** AppGhostIconButton / AppIconButton — 透明/浅底幽灵小圆钮 + 品牌渐变圆钮 */
@Composable
private fun IconButtonsSection(pager: Pager) {
    GallerySection(title = "AppGhostIconButton / AppIconButton", note = "点击弹 Toast") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppGhostIconButton(
                AppIconType.ChevronLeft,
                onClick = { demoToast(pager, "幽灵钮(透明底)") },
            )
            Spacer(modifier = Modifier.width(12.dp))
            AppGhostIconButton(
                AppIconType.Search,
                bg = Color(0xFFF3F4F8),
                onClick = { demoToast(pager, "幽灵钮(浅灰底)") },
            )
            Spacer(modifier = Modifier.width(12.dp))
            AppGhostIconButton(
                AppIconType.Close,
                tint = AppTheme.Danger,
                onClick = { demoToast(pager, "幽灵钮(危险色)") },
            )
            Spacer(modifier = Modifier.width(20.dp))
            AppIconButton(AppIconType.Plus, onClick = { demoToast(pager, "添加") })
            Spacer(modifier = Modifier.width(12.dp))
            AppIconButton(AppIconType.Send, onClick = { demoToast(pager, "发送") })
            Spacer(modifier = Modifier.width(12.dp))
            AppIconButton(
                AppIconType.Check,
                onClick = { demoToast(pager, "完成") },
                size = 36.dp,
                iconSize = 18.dp,
            )
        }
    }
}

// ---------- 卡片与导航 ----------

@Composable
private fun CardSection() {
    var selected by remember { mutableStateOf(false) }
    val demoBrush = remember { Brush.linearGradient(GalleryMock.featureColors) }
    val theme = ThemeStore.current
    GallerySection(title = "AppSelectableCard / AppQuickAction / AppFeatureCard", note = "点击卡片试试") {
        AppSelectableCard(selected = selected, corner = 16.dp, onClick = { selected = !selected }) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 图标底座是背景视图会吞点击,透传卡片回调
                AppIconTile(
                    AppIconType.Heart,
                    size = 46.dp,
                    bgBrush = demoBrush,
                    iconSize = 22.dp,
                    onClick = { selected = !selected },
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "今日情话",
                        color = if (selected) ThemeStore.current.brandStart else AppTheme.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("每天一句小甜蜜", color = AppTheme.TextSecondary, fontSize = 11.sp)
                }
                AppCheckbox(checked = selected, size = 18.dp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppQuickAction("去待办", AppIconType.Todo, Color(0xFF6C7BFF), Color(0xFFEDEFFF), Modifier.weight(1f))
            AppQuickAction("送心意", AppIconType.Heart, Color(0xFFFF5A8C), Color(0xFFFFEAF1), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppFeatureCard(
                "今日情话",
                "每天一句小甜蜜",
                AppIconType.Heart,
                Modifier.weight(1f),
                tileColors = GalleryMock.featureColors,
            )
            AppFeatureCard(
                "约会灵感",
                "周末去哪儿玩",
                AppIconType.Star,
                Modifier.weight(1f),
                tileColors = listOf(theme.brandStart, theme.brandEnd),
            )
        }
    }
}

@Composable
private fun MenuSection(pager: Pager) {
    GallerySection(title = "AppMenuGroup / AppMenuRow", note = "点击弹 Toast") {
        AppMenuGroup {
            GalleryMock.menuItems.forEachIndexed { index, item ->
                if (index > 0) {
                    AppMenuDivider()
                }
                AppMenuRow(
                    icon = item.icon,
                    iconTint = item.tint,
                    iconBg = item.bg,
                    title = item.title,
                    onClick = { demoToast(pager, "点击了「${item.title}」") },
                )
            }
        }
    }
}

/**
 * AppHeaderBackButton / AppSubPageHeader — 子页头部件缩略 demo:渐变头内放返回钮 + 标题列 +
 * 尾部槽(返回钮点击弹 Toast,生产中 onBack = closePage);本页头部本身即 AppSubPageHeader 活 demo
 */
@Composable
private fun SubHeaderSection(pager: Pager) {
    GallerySection(title = "AppHeaderBackButton / AppSubPageHeader", note = "子页头缩略") {
        AppGradientHeader {
            AppHeaderBackButton {
                demoToast(pager, "返回钮 demo(生产中 onBack = closePage)")
            }
            Spacer(modifier = Modifier.width(10.dp))
            AppHeaderTitle("子页标题", "返回钮 + 标题列 + 尾部槽")
            AppPillChip(
                "尾部槽",
                bg = Color.White.copy(alpha = 0.25f),
                onClick = { demoToast(pager, "点了尾部槽") },
            )
        }
    }
}

/** AppMonthNav / AppRingDot — 月导航行(‹ 年月 › 可翻动)+ 空心环点(外圆+内芯嵌套画法) */
@Composable
private fun MonthNavSection() {
    var monthIndex by remember { mutableStateOf(GalleryMock.monthLabels.lastIndex) }
    GallerySection(title = "AppMonthNav / AppRingDot", note = "月导航可点 · 环点展示") {
        AppMonthNav(
            label = GalleryMock.monthLabels[monthIndex],
            onPrev = { monthIndex = (monthIndex - 1).coerceAtLeast(0) },
            onNext = { monthIndex = (monthIndex + 1).coerceAtMost(GalleryMock.monthLabels.lastIndex) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppRingDot(ThemeStore.current.brandStart)
            Spacer(modifier = Modifier.width(6.dp))
            Text("TA 空心环(嵌套画法)", color = AppTheme.TextTertiary, fontSize = 10.sp)
            Spacer(modifier = Modifier.weight(1f))
            AppRingDot(Color(0xFF34C77B))
            Spacer(modifier = Modifier.width(6.dp))
            Text("异色环(innerColor 默认 CardBg)", color = AppTheme.TextTertiary, fontSize = 10.sp)
        }
    }
}

// ---------- 列表与状态 ----------

@Composable
private fun StatSection(pager: Pager) {
    GallerySection(title = "AppStatBar", note = "两种规格 + 可点统计项") {
        AppStatBar(GalleryMock.stats)
        Spacer(modifier = Modifier.height(10.dp))
        AppStatBar(GalleryMock.stats, valueSize = 18.sp, dividerHeight = 26.dp)
        Spacer(modifier = Modifier.height(10.dp))
        // onClick 形态(2026-10-17 三域查看):可点列整列热区(我的页统计卡「心愿」进时间线同款)
        AppStatBar(
            listOf(
                AppStatItem(
                    "12",
                    "可点项",
                    onClick = { demoToast(pager, "点了可点项 1") },
                ),
                AppStatItem("34", "不可点"),
                AppStatItem(
                    "56",
                    "可点项",
                    onClick = { demoToast(pager, "点了可点项 2") },
                ),
            ),
        )
    }
}

/**
 * AppLoading / AppLoadFailed / AppEmptyHint — 列表三态可交互:
 * 「触发加载」→ AppLoading 1.5s → AppLoadFailed(点「重试」再转)→「回空态」看 AppEmptyHint 两形态
 */
@Composable
private fun StatusSection() {
    // loading → (1.5s demo 时延) → failed → 重试 → loading…;empty 展示两形态空态
    var status by remember { mutableStateOf("empty") } // "loading" | "failed" | "empty"
    LaunchedEffect(status) {
        if (status == "loading") {
            delay(1500)
            status = "failed"
        }
    }
    GallerySection(title = "AppLoading / AppLoadFailed / AppEmptyHint", note = "三态可交互") {
        when (status) {
            "loading" -> AppLoading()
            "failed" -> AppLoadFailed(onRetry = { status = "loading" })
            else -> Column(modifier = Modifier.fillMaxWidth()) {
                // circled 形态(圆底 icon + 标题 + 副文案,MomentPage/AnniversaryPage 空态同款)
                AppEmptyHint(AppIconType.Heart, "还没有瞬间", hint = "点右下角 + 晒一张今天的照片吧")
                Spacer(modifier = Modifier.height(18.dp))
                // 裸 icon 形态(circled=false,iconSize 惯例大号 44dp,WishListPage 空态同款)
                AppEmptyHint(AppIconType.Star, "还没有心动清单", circled = false, iconSize = 44.dp)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AppPillChip(
                if (status == "empty") "触发加载" else "回空态",
                bg = ThemeStore.current.brandStart.copy(alpha = 0.12f),
                textColor = ThemeStore.current.brandStart,
            ) {
                status = if (status == "empty") "loading" else "empty"
            }
        }
    }
}

// ---------- 浮层与反馈 ----------

/**
 * 浮层三件套入口(AppOverlayCard / AppConfirmOverlay / AppDatePickerOverlay):点钮弹真实浮层,
 * 浮层本体挂 GalleryPage 根 Box(独立 @Page 页内根级挂载即可,后组合者在上)
 */
@Composable
private fun OverlaySection(
    pickedDateText: String,
    onOpenOverlayCard: () -> Unit,
    onOpenConfirm: () -> Unit,
    onOpenDatePicker: () -> Unit,
) {
    GallerySection(title = "AppOverlayCard / AppConfirmOverlay / AppDatePickerOverlay", note = "点钮弹真实浮层") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppPillChip("通用浮层壳", bg = Color(0xFFF3F4F8), textColor = AppTheme.TextSecondary, onClick = onOpenOverlayCard)
            AppPillChip("确认框", bg = AppTheme.Danger.copy(alpha = 0.12f), textColor = AppTheme.Danger, onClick = onOpenConfirm)
            AppPillChip(
                "日期滚轮",
                bg = ThemeStore.current.brandStart.copy(alpha = 0.12f),
                textColor = ThemeStore.current.brandStart,
                onClick = onOpenDatePicker,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text("日期滚轮回吐:$pickedDateText", color = AppTheme.TextSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "确认框 busy=true 拦全部出口(遮罩/取消/确认);两浮层挂本页根 Box——独立 @Page 页内挂即可,Tab 页须提升 MainPager(铁律 14)",
            color = AppTheme.TextTertiary,
            fontSize = 10.sp,
        )
    }
}

/**
 * AppBrandIntroScreen — 品牌引导整屏骨架:全屏件不真开(避免接管画廊页面),
 * 卡内放缩小形态静态示意(brandBrush 底 + hero 头等比缩小)+ 说明条;
 * 生产调用方 = GatePanel(登录门)/ BindingPanel(绑定引导)
 */
@Composable
private fun BrandIntroSection() {
    GallerySection(title = "AppBrandIntroScreen", note = "整屏件 · 缩小示意") {
        Box(
            modifier = Modifier.fillMaxWidth()
                .height(150.dp)
                .background(ThemeStore.current.brandBrush, RoundedCornerShape(16.dp))
                .clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(36.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIconType.Heart, Color.White, Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("宝贝 & 我", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(3.dp))
                Text("登录后开启你们的小世界", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "整屏骨架:brandBrush 全屏底 + hero 头(64dp 白 30% 圆 + Heart + 26sp 标题 + 13sp 白 85% 副标题)+ statusBar/导航栏避让;content 槽装表单。上图为缩小静态示意,生产中登录门/绑定引导整屏使用。",
            color = AppTheme.TextTertiary,
            fontSize = 10.sp,
        )
    }
}

/**
 * AppPullRefreshItem — LazyListScope 扩展件(非独立 Composable,不可在卡内展示):
 * 不在画廊真开下拉(会劫持本页列表下拉手势),文字说明 + 指路真实调用方
 */
@Composable
private fun PullRefreshNoteSection() {
    GallerySection(title = "AppPullRefreshItem", note = "LazyListScope 件") {
        Text(
            "下拉刷新通用件:挂 LazyListScope(官方 pullToRefreshItem 封装),最短驻留 600ms 让「刷新中…」完整显示一拍 + 失败 toast 收口。本页不真开(避免劫持画廊列表手势),真实刷新见相册页 / 恋爱清单页 / 灵感页。",
            color = AppTheme.TextSecondary,
            fontSize = 12.sp,
        )
    }
}

/**
 * AppBottomBar — 浮岛底栏活 demo:本地 state 点切,胶囊经 pillPosition(targetPage 瞬移语义)
 * 停在选中项;badges 演示聊天 Tab 红点
 */
@Composable
private fun BottomBarSection() {
    var demoTab by remember { mutableStateOf(0) }
    GallerySection(title = "AppBottomBar", note = "点切选中 · 胶囊瞬移") {
        AppBottomBar(
            selectedIndex = demoTab,
            // 生产中 = pagerState.targetPage:点切即翻转瞬移,静态值同语义(零动画帧)
            pillPosition = { demoTab.toFloat() },
            badges = mapOf(3 to 2),
            onSelect = { demoTab = it },
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "64dp 浮岛挂 MainPager 底部,胶囊只随 targetPage 翻转瞬移(零动画帧,逐帧跟随=拖拽掉帧根因);badges 键=tab 序号、值=未读数(红点全量收口在 ChatStore)。",
            color = AppTheme.TextTertiary,
            fontSize = 10.sp,
        )
    }
}

/**
 * 基建工具(AppGestures / AppKeyboard / AppFeedback)— 非视觉件:
 * AppFeedback 真交互(toast/剪贴板走宿主桥),另两件文字说明(手势修饰符/键盘监听无视觉形态)
 */
@Composable
private fun InfrastructureSection(pager: Pager) {
    GallerySection(title = "AppGestures / AppKeyboard / AppFeedback", note = "基建工具") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppPillChip("弹个 Toast", bg = ThemeStore.current.brandStart) {
                demoToast(pager, "AppFeedback.toast 收口")
            }
            AppPillChip(
                "复制到剪贴板",
                bg = Color(0xFFF3F4F8),
                textColor = AppTheme.TextSecondary,
                onClick = {
                    AppFeedback.copyToPasteboard(pager, "来自组件列表的复制 demo") {
                        demoToast(pager, "已复制")
                    }
                },
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "AppGestures.dragDismissKeyboard():列表拖拽收键盘修饰符(聊天/待办/清单同款);AppKeyboard.rememberKeyboardHeight():键盘高度状态对 + 避让公式收口(keyboardLiftBottomDp/inputBarBottomPaddingDp);AppFeedback:toast/剪贴板/日期格式化宿主桥单源,可空 getModule 降级不崩。",
            color = AppTheme.TextSecondary,
            fontSize = 12.sp,
        )
    }
}
