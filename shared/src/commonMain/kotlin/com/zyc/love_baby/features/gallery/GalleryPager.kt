package com.zyc.love_baby.features.gallery

import androidx.compose.runtime.Composable
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
import com.zyc.love_baby.base.Utils
import com.zyc.love_baby.components.AppCheckbox
import com.zyc.love_baby.components.AppCheckBadge
import com.zyc.love_baby.components.AppFeatureCard
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconButton
import com.zyc.love_baby.components.AppIconTile
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppMenuDivider
import com.zyc.love_baby.components.AppMenuGroup
import com.zyc.love_baby.components.AppMenuRow
import com.zyc.love_baby.components.AppQuickAction
import com.zyc.love_baby.components.AppSectionTitle
import com.zyc.love_baby.components.AppSelectableCard
import com.zyc.love_baby.components.AppStatBar
import com.zyc.love_baby.components.AppStatItem
import com.zyc.love_baby.components.AppTag
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.AppAvatar
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp

/**
 * GalleryPager — 组件列表页(@Page 注册名 "componentList"):项目通用组件 demo 画廊
 * 用法:MinePage「组件列表」入口路由打开;返回键/closePage 返回
 * 依赖:components/ 全部通用组件;GalleryMock 数据;RouterModule(返回与 Toast)
 * 事件:复选框/选中卡可交互;菜单与按钮点击弹 Toast
 * 功能:
 *  - 本页头部即 AppGradientHeader 活 demo(标题列 AppHeaderTitle + 返回钮)
 *  - 分组展示:图标/勾选/标签头像/统计条/菜单/卡片/圆钮,均取自项目五页抽取的通用组件
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
    LazyColumn(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        item {
            // 本身就是 AppGradientHeader + AppHeaderTitle 的活 demo
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
                AppHeaderTitle("组件列表", "项目通用组件 · 可点击交互")
            }
        }
        item { IconSection() }
        item { CheckSection() }
        item { TagAvatarSection() }
        item { StatSection(pager) }
        item { MenuSection(pager) }
        item { CardSection() }
        item { ButtonSection(pager) }
        item { Spacer(modifier = Modifier.height(40.dp + navBarSafePaddingDp())) }
    }
}

/**
 * GallerySection — 画廊分组:区块标题(带尾部说明) + 白色 demo 卡
 */
@Composable
private fun GallerySection(
    title: String,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
        Spacer(modifier = Modifier.height(24.dp))
        AppSectionTitle(
            title = title,
            trailing = {
                if (note != null) {
                    Text(note, color = AppTheme.TextTertiary, fontSize = 11.sp)
                }
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                .padding(14.dp),
        ) {
            content()
        }
    }
}

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

@Composable
private fun TagAvatarSection() {
    GallerySection(title = "AppTag / AppAvatar", note = "标签与文字头像") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GalleryMock.tags.forEach { (text, color) ->
                AppTag(text, bgColor = color.copy(alpha = 0.12f), textColor = color)
            }
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
                    onClick = { Utils.bridgeModule(pager.pagerId).toast("点了可点项 1") },
                ),
                AppStatItem("34", "不可点"),
                AppStatItem(
                    "56",
                    "可点项",
                    onClick = { Utils.bridgeModule(pager.pagerId).toast("点了可点项 2") },
                ),
            ),
        )
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
                    onClick = { Utils.bridgeModule(pager.pagerId).toast("点击了「${item.title}」") },
                )
            }
        }
    }
}

@Composable
private fun CardSection() {
    var selected by remember { mutableStateOf(false) }
    val demoBrush = remember { Brush.linearGradient(GalleryMock.featureColors) }
    val theme = ThemeStore.current
    GallerySection(title = "AppSelectableCard / 卡片组件", note = "点击卡片试试") {
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
private fun ButtonSection(pager: Pager) {
    GallerySection(title = "AppIconButton", note = "品牌渐变圆钮") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(AppIconType.Plus, onClick = { Utils.bridgeModule(pager.pagerId).toast("添加") })
            Spacer(modifier = Modifier.width(12.dp))
            AppIconButton(AppIconType.Send, onClick = { Utils.bridgeModule(pager.pagerId).toast("发送") })
            Spacer(modifier = Modifier.width(12.dp))
            AppIconButton(
                AppIconType.Check,
                onClick = { Utils.bridgeModule(pager.pagerId).toast("完成") },
                size = 36.dp,
                iconSize = 18.dp,
            )
        }
    }
}
