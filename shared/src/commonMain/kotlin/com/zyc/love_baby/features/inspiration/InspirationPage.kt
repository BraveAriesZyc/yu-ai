package com.zyc.love_baby.features.inspiration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
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
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.rememberPullToRefreshState
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.components.AppFilterChipRow
import com.zyc.love_baby.components.AppLoadFailed
import com.zyc.love_baby.components.AppLoading
import com.zyc.love_baby.components.AppPillChip
import com.zyc.love_baby.components.AppPullRefreshItem
import com.zyc.love_baby.components.AppSubPageHeader
import com.zyc.love_baby.components.AppTag
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.data.model.InspirationInfo
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.InspirationApi
import kotlinx.coroutines.launch

/**
 * InspirationPage — 约会灵感页(@Page 注册名 "inspirationPage",M17):分类 chips + 灵感卡列表
 * 用法:首页「恋爱灵感·约会灵感」卡 RouterModule.openPage 打开(HomePage.openInspirationPage);
 *      返回键 closePage;独立二级页(WishListPager 同款壳,不走 MainPager 五 Tab)
 * 依赖:InspirationApi/InspirationInfo(页内自持不进 AppState,D-19g——页销毁即弃);
 *      AppFilterChipRow(分类 chips,components 通用件,M17 本页需求同源抽离);
 *      AppPullRefreshItem(下拉刷新收口,components 三页单源);AppTag(卡片分类标签);
 *      components.AppLoading/AppLoadFailed;RouterModule
 * 事件:返回钮 closePage;chips onSelect 切分类(id 序);头部「换一换」本地随机打乱当前分类;
 *      下拉刷新重拉全量(官方 pullToRefreshItem 须为首个 item,MomentPage 先例)
 * 功能:
 *  - list 全量拉取(kind=2 启用行,id asc 稳定序,几十条量级无分页):服务端序入库,
 *    展示列表 display 单独收口 recomputeDisplay(shuffle)——切分类保持 id 序,「换一换」
 *    对当前分类 filtered.shuffled();分类动态聚合(distinct 首现序),首枚「全部」
 *  - 加载/失败/空态复用 mood 域组件(fetchFirst 口径同 WishListPage:loadFailed 仅空表才置,
 *    刷新失败保留原列表;最短驻留收口在 AppPullRefreshItem,三页同值);失败 toast
 *  - 卡片:标题 Bold + 分类标签(brand 12% 透底)+ 描述;title 空(运营未填)整卡只渲染描述
 */
@Page("inspirationPage", supportInLocal = true)
internal class InspirationPager : BasePager() {

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            InspirationPage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }
}

@Composable
internal fun InspirationPage(pager: Pager) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") } // "" = 全部
    // 页内自持(D-19g):服务端序全量(id asc);展示列表单独收口(换一换打乱只动 display)
    val inspirations = remember { mutableStateListOf<InspirationInfo>() }
    val display = remember { mutableStateListOf<InspirationInfo>() }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState(isRefreshing = refreshing)

    // 分类 chips:动态聚合(首现序)+ 首枚「全部」;运营加新分类无需端上改枚举
    val categories = remember(inspirations.size) { inspirations.map { it.category }.distinct() }
    val chipLabels = remember(categories) { listOf("全部") + categories }
    val selectedChipIndex = if (selectedCategory.isEmpty()) 0 else chipLabels.indexOf(selectedCategory)

    // 展示列表收口:切分类(保持 id 序)/换一换(当前分类随机打乱)两入口共用
    fun recomputeDisplay(shuffle: Boolean) {
        val filtered = if (selectedCategory.isEmpty()) {
            inspirations.toList()
        } else {
            inspirations.filter { it.category == selectedCategory }
        }
        display.clear()
        display.addAll(if (shuffle) filtered.shuffled() else filtered)
    }

    // 拉全量:进页首拉 / 下拉刷新 / 失败重试共用;成功清空重填并按当前分类重排展示。
    // loadFailed 仅在列表为空时才置——已有内容时刷新失败保留原列表(WishListPage fetchFirst 同款口径)
    suspend fun fetchFirst(): Boolean {
        val result = InspirationApi.list(pager)
        val ok = result is ApiResult.Ok
        if (ok) {
            inspirations.clear()
            inspirations.addAll(InspirationInfo.listFrom((result as ApiResult.Ok).dataArray))
            recomputeDisplay(shuffle = false)
        }
        loadFailed = !ok && inspirations.isEmpty()
        return ok
    }

    LaunchedEffect(Unit) { fetchFirst(); loading = false }

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppSubPageHeader(
                "约会灵感",
                "周末去哪儿玩",
                onBack = {
                    pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                },
            ) {
                // 「换一换」(本地随机打乱当前分类,灵感页专属趣味;空表无意义隐藏)
                if (display.isNotEmpty()) {
                    AppPillChip(
                        "换一换",
                        bg = Color.White.copy(alpha = 0.25f),
                        corner = 12.dp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                    ) { recomputeDisplay(shuffle = true) }
                }
            }
            // 分类 chips 固定区(M20 粘顶:原 LazyColumn 内 item 随内容滚走,挪出列表=滚动恒定粘顶;
            // 显示条件与列表内数据分支一致——加载/失败/空态不显示)
            if (!loading && !loadFailed && inspirations.isNotEmpty()) {
                AppFilterChipRow(
                    labels = chipLabels,
                    selectedIndex = selectedChipIndex,
                    onSelect = { i ->
                        selectedCategory = if (i == 0) "" else chipLabels[i]
                        recomputeDisplay(shuffle = false)
                    },
                    modifier = Modifier.padding(
                        horizontal = AppTheme.PageHPadding,
                        vertical = 12.dp,
                    ),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
            ) {
                // 下拉刷新(官方 pullToRefreshItem 须为首个 item,MomentPage 先例):重拉全量。
                // 最短驻留 + 失败 toast 已收口在 AppPullRefreshItem(components,三页单源,
                // REFRESH_MIN_VISIBLE_MS=600L 同常数;成功不 toast,数据自己会更新,肉眼可辨)
                AppPullRefreshItem(
                    pullState = pullState,
                    listState = listState,
                    scope = scope,
                    setRefreshing = { refreshing = it },
                    pager = pager,
                    onRefresh = { fetchFirst() },
                )
                when {
                    loading -> item { AppLoading() }
                    loadFailed -> item { AppLoadFailed(onRetry = {
                        loading = true
                        scope.launch { fetchFirst(); loading = false }
                    }) }
                    inspirations.isEmpty() -> item {
                        Text(
                            "还没有灵感，等运营官补货~",
                            color = AppTheme.TextTertiary,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> {
                        items(display, key = { it.id }, contentType = { "inspiration-card" }) { item ->
                            InspirationCard(
                                info = item,
                                // 卡间距 10dp(与 Todo/Wish 卡列表 spacedBy(10) 同值);逐卡 bottom
                                // padding 而非列表 verticalArrangement——首 item 是下拉刷新指示器,
                                // spacedBy 会在 chips 与首卡间凭空加缝,MoodPage 链式 padding 同款
                                modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)
                                    .padding(bottom = 10.dp),
                            )
                        }
                        item(key = "tail", contentType = "inspiration-tail") {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 灵感卡:标题 Bold + 分类标签 + 描述;title 空(运营未填)只渲染描述;白卡圆角 16 全仓列表卡同源 */
@Composable
private fun InspirationCard(info: InspirationInfo, modifier: Modifier = Modifier) {
    val theme = ThemeStore.current
    Column(
        modifier = modifier.fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (info.title.isNotEmpty()) {
                Text(
                    info.title,
                    color = AppTheme.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (info.category.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                AppTag(
                    info.category,
                    bgColor = theme.brandStart.copy(alpha = 0.12f),
                    textColor = theme.brandStart,
                    fontSize = 10.sp,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            info.content,
            color = if (info.title.isEmpty()) AppTheme.TextPrimary else AppTheme.TextSecondary,
            fontSize = if (info.title.isEmpty()) 14.sp else 13.sp,
        )
    }
}
