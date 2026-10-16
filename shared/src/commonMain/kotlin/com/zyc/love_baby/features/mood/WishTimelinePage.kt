package com.zyc.love_baby.features.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
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
import com.tencent.kuikly.compose.foundation.lazy.itemsIndexed
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.CircularProgressIndicator
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppTag
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.data.model.MessageInfo
import com.zyc.love_baby.features.chat.chatDayIndex
import com.zyc.love_baby.features.chat.chatDayLabelFriendly
import com.zyc.love_baby.features.chat.chatHHmm
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.MessageApi
import com.zyc.love_baby.state.SessionStore
import com.zyc.love_baby.state.appIconFromName
import com.zyc.love_baby.state.colorFromHex
import kotlinx.coroutines.launch

/**
 * WishTimelinePage — 心愿时间线页(@Page 注册名 "wishTimeline"):按日期分组回看已送出的心愿
 * 用法:我的页统计卡「心愿」/心情页心愿墙「已送的心愿 ›」RouterModule.openPage 打开;
 *      返回键/closePage 返回。独立二级页(GalleryPager 同款壳,不走 MainPager 五 Tab)
 * 依赖:MessageApi.wishList(kind=2 只读视图,服务端按 id 倒序、快照四字段全量);
 *      ChatTime(chatDayIndex/chatDayLabelFriendly/chatHHmm);AppState(colorFromHex/appIconFromName);
 *      RouterModule(返回);ThemeStore(独立页主题自持)
 * 事件:返回钮 closePage;失败态「重试」重拉
 * 功能:
 *  - 数据页内自持(remember + LaunchedEffect 拉一次),不进 AppState——单页局部数据无跨页共享,
 *    与 ChatStore/lastId 游标零耦合(聊天流照旧)
 *  - 四态:加载中 / 失败重试 / 空态「还没有送出过心愿」/ 正常时间线
 *  - 正常态:服务端 DESC(最新在前)原样渲染不重排;inline 日期条 = ChatPage 同款胶囊
 *    (chatDayLabelFriendly:今天/昨天/M月d日);createTime<=0 整层静默隐藏(不造假数据)
 *  - 心愿卡 = ChatPage WishBubble 视觉平移:心愿色 8% 洗底软卡 + 34dp 图标块 14% 洗底 +
 *    「我想要/TA 想要」+ 我/TA AppTag + 右缘 HH:mm;快照 color/icon 缺省兜底主题主色/Heart
 */
@Page("wishTimeline", supportInLocal = true)
internal class WishTimelinePager : BasePager() {

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            WishTimelinePage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }
}

@Composable
internal fun WishTimelinePage(pager: Pager) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    val wishes = remember { mutableStateListOf<MessageInfo>() }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            val result = MessageApi.wishList(pager)
            if (result is ApiResult.Ok) {
                wishes.clear()
                wishes.addAll(MessageInfo.listFrom(result.dataArray))
                loadFailed = false
            } else {
                loadFailed = true
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxSize()) {
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
                    AppHeaderTitle("心愿单", "我们许过的每一个小心愿")
                }
            }
            when {
                loading -> item { TimelineLoading() }
                loadFailed -> item { TimelineLoadFailed { load() } }
                wishes.isEmpty() -> item { WishEmptyHint() }
                else -> itemsIndexed(
                    wishes,
                    key = { _, w -> w.id },
                    contentType = { _, _ -> "wish" }, // 同构卡片,提示复用
                ) { idx, wish ->
                    val prev = wishes.getOrNull(idx - 1)
                    // 本条上方是否出日期条(createTime<=0 → 整层静默,ChatPage 同判定)
                    val showDayDivider = wish.createTime > 0L &&
                        (
                            prev == null || prev.createTime <= 0L ||
                                chatDayIndex(prev.createTime) != chatDayIndex(wish.createTime)
                            )
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = AppTheme.PageHPadding)
                            .padding(top = if (showDayDivider) 10.dp else 12.dp),
                    ) {
                        if (showDayDivider) {
                            // 日期条:单节点(背景+文字同节点,无 clickable),整宽居中胶囊(ChatPage 同款)
                            Text(
                                chatDayLabelFriendly(wish.createTime),
                                fontSize = 10.sp,
                                color = AppTheme.TextSecondary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                    .background(AppTheme.TextTertiary.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        WishTimelineCard(wish)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp + navBarSafePaddingDp())) }
        }
    }
}

/**
 * WishTimelineCard — 心愿卡(WishBubble 视觉平移,通栏版):心愿色 8% 洗底单节点软卡(零描边,
 * 层次靠 8%/14% 两级 alpha);图标/文案走快照四字段,color/icon 缺省兜底(主题主色/Heart)。
 * 不做状态角标(love_message 无兑现状态字段,不造假状态)
 */
@Composable
private fun WishTimelineCard(msg: MessageInfo) {
    val theme = ThemeStore.current
    val isMine = msg.senderId == SessionStore.userId
    val wishColor = if (msg.color.isEmpty()) theme.brandStart else colorFromHex(msg.color, theme.brandStart)
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(wishColor.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp)
                    .background(wishColor.copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(appIconFromName(msg.icon), wishColor, Modifier.size(17.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isMine) "我想要" else "TA 想要",
                color = AppTheme.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            AppTag(
                if (isMine) "我" else "TA",
                bgColor = wishColor.copy(alpha = 0.12f),
                textColor = wishColor,
            )
            if (msg.createTime > 0L) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(chatHHmm(msg.createTime), color = AppTheme.TextTertiary, fontSize = 10.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            msg.content.ifEmpty { "小心愿" },
            color = AppTheme.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 空态引导:软色心形圆底 + 两行文案(语气对齐既有页面空态) */
@Composable
private fun WishEmptyHint() {
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
        Text("还没有送出过心愿", color = AppTheme.TextSecondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("去心愿墙挑一个,悄悄送出去吧", color = AppTheme.TextTertiary, fontSize = 11.sp)
    }
}

/** 时间线加载中:居中转圈(两时间线页共用) */
@Composable
internal fun TimelineLoading() {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 72.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = ThemeStore.current.brandStart,
            strokeWidth = 3.dp,
        )
    }
}

/** 时间线加载失败:一句文案 + 重试胶囊(两时间线页共用;ChatLoadError 同语义分家,不造假空态) */
@Composable
internal fun TimelineLoadFailed(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("加载失败了,网络开小差了", color = AppTheme.TextSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier.background(
                    ThemeStore.current.brandStart.copy(alpha = 0.12f),
                    RoundedCornerShape(16.dp),
                )
                .clickable { onRetry() }
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(
                "重试",
                color = ThemeStore.current.brandStart,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
