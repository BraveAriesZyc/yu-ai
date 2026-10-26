package com.zyc.love_baby.features.inspiration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.base.closeLovePage
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppLoadFailed
import com.zyc.love_baby.components.AppLoading
import com.zyc.love_baby.components.AppSubPageHeader
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.data.model.InspirationQuote
import com.zyc.love_baby.features.couple.BindPrimaryButton
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.InspirationApi
import kotlinx.coroutines.launch

/**
 * QuotePage — 今日情话页(@Page 注册名 "quotePage",M17):服务端按日轮换的一句话 + 随机换一句
 * 用法:首页「恋爱灵感·今日情话」卡 `pager.openLovePage("quotePage")` 打开(base 路由收口);
 *      返回键 closePage;独立二级页(WishListPager 同款壳,不走 MainPager 五 Tab)
 * 依赖:InspirationApi/InspirationQuote(页内自持不进 AppState,D-19g——页销毁即弃);
 *      components.AppLoading/AppLoadFailed(加载/失败态);couple.BindPrimaryButton(换一句钮);
 *      closeLovePage(返回)
 * 事件:返回钮 closePage;「换一句」→ quote-random(服务端排除今日句,池 >1 必换),单飞锁防双击
 * 功能:
 *  - today 进页拉取:data 单对象 InspirationQuote.from(content);池空服务端回兜底文案,
 *    本页零错误码分支——失败仅网络/鉴权通道(普通 BizError)
 *  - 居中大卡展示(白卡圆角 24 + Heart 图标圆底 + 引号装饰 + 正文居中 + 「今日份心动」落款),
 *    「换一句」brandBrush 渐变主钮(白字主操作铁律,禁 brandEnd 浅端单色)
 *  - 加载/失败态复用 mood 域时间线组件(首拉列表页同口径;失败重试走首拉)
 */
@Page("quotePage", supportInLocal = true)
internal class QuotePager : BasePager() {

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            QuotePage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }
}

@Composable
internal fun QuotePage(pager: Pager) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }

    // 首拉 today:Ok 才落 content;失败仅在还没有内容时置 loadFailed(换一句失败保留原句不翻失败态)
    suspend fun fetchToday() {
        val result = InspirationApi.today(pager)
        if (result is ApiResult.Ok) {
            InspirationQuote.from(result.data)?.let { content = it.content }
            loadFailed = false
        } else if (content.isEmpty()) {
            loadFailed = true
        }
    }

    LaunchedEffect(Unit) {
        fetchToday()
        loading = false
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppSubPageHeader(
                "今日情话",
                "每天一句小甜蜜",
                onBack = {
                    pager.closeLovePage()
                },
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> AppLoading()
                    loadFailed -> AppLoadFailed(onRetry = {
                        loading = true
                        scope.launch { fetchToday(); loading = false }
                    })
                    else -> QuoteCard(content, modifier = Modifier.padding(horizontal = 28.dp))
                }
            }
            // 换一句(服务端排除今日句,池 >1 必换;单飞锁防双击并发——switching 期间拦截点击)
            BindPrimaryButton(
                text = "换 一 句",
                submitting = switching,
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                switching = true
                scope.launch {
                    val result = InspirationApi.quoteRandom(pager)
                    if (result is ApiResult.Ok) {
                        InspirationQuote.from(result.data)?.let { content = it.content }
                    } else {
                        // 失败不再静默(M18 收口):保留原句,toast 告知(网络/服务端原因区分文案)
                        toastCoupleResult(pager, result)
                    }
                    switching = false
                }
            }
            Spacer(modifier = Modifier.height(20.dp + navBarSafePaddingDp()))
        }
    }
}

/** 今日情话大卡:Heart 图标圆底 + 引号装饰 + 正文居中 + 落款;白卡圆角 24 与全仓浮层卡同源 */
@Composable
private fun QuoteCard(content: String, modifier: Modifier = Modifier) {
    val theme = ThemeStore.current
    Column(
        modifier = modifier.fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(48.dp)
                .background(theme.brandStart.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconType.Heart, theme.brandStart, Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "“",
            color = theme.brandStart,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            content,
            color = AppTheme.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier.size(width = 36.dp, height = 3.dp)
                .background(theme.brandStart.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("今日份心动", color = AppTheme.TextTertiary, fontSize = 12.sp)
    }
}
