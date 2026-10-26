package com.zyc.love_baby.features.dailyquestion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.base.closeLovePage
import com.zyc.love_baby.base.openLovePage
import com.zyc.love_baby.components.AppInputPill
import com.zyc.love_baby.components.AppLoadFailed
import com.zyc.love_baby.components.AppLoading
import com.zyc.love_baby.components.AppSectionTitle
import com.zyc.love_baby.components.AppSubPageHeader
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.REFRESH_MIN_VISIBLE_MS
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.data.model.DailyQuestionItem
import com.zyc.love_baby.data.model.DailyQuestionToday
import com.zyc.love_baby.features.couple.BindPrimaryButton
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.DailyQuestionApi
import com.zyc.love_baby.state.DailyQuestionStore
import com.zyc.love_baby.state.currentDate
import com.zyc.love_baby.state.nowEpochMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 答案内容长度上限(与服务端 @Length(max=500) 对齐;双保险 take + 渲染层拒超限) */
private const val ANSWER_MAX_LENGTH = 500

/**
 * @Page 注册名(openDailyQuestion 与首页入口共用,ReportPage 先例)
 */
internal const val DAILY_QUESTION_PAGE_NAME = "dailyQuestionPage"

/** 开每日一问独立页(首页「每日一问」条入口;openLovePage 未桥接端 try/catch 吞) */
internal fun openDailyQuestion(pager: Pager) {
    pager.openLovePage(DAILY_QUESTION_PAGE_NAME)
}

/**
 * DailyQuestionPage — 每日一问页(@Page 注册名 "dailyQuestionPage",M32,D-21 答完自动互见)
 * 用法:首页「每日一问」条 `openDailyQuestion(pager)` 打开(base 路由收口);返回键 closePage;
 *      独立二级页(QuotePager 同款壳,不走 MainPager 五 Tab)
 * 依赖:DailyQuestionStore(今日题态,首页条与本题页共用)/DailyQuestionApi(历史列表页内自持,
 *      D-19g——页销毁即弃);components.AppInputPill(答案输入,TodoPage 同型胶囊)/
 *      AppLoading/AppLoadFailed(加载/失败态);couple.BindPrimaryButton(提交钮)/
 *      toastCoupleResult(失败 toast)
 * 事件:返回钮 closePage;提交答案(单飞锁防双击,钮态最少可见 REFRESH_MIN_VISIBLE_MS 防一帧
 *      闪烁,「换一句」同款);已答未揭晓「改答案」展开输入预填;揭晓态零操作(锁定)
 * 功能:
 *  - 今日题:今日区(题目白卡 + 状态文案 + 作答输入/我的答案 + 揭晓态双方答案对比);
 *    今日无题(Store.today == null)显占位卡;partnerAnswer 仅 revealed 非空(服务端隐私口径)
 *  - 历史列表:「往期一问」按当月(question_date asc,含没答的天),页内自持不进 Store;
 *    未揭晓条目只显状态文案(错过没答的天对方答案永不揭晓——只能答当天,D-21)
 *  - 加载/失败态复用 mood 域时间线组件口径(首拉列表页同款;失败重试走首拉)
 */
@Page("dailyQuestionPage", supportInLocal = true)
internal class DailyQuestionPager : BasePager() {

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            DailyQuestionPage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }
}

@Composable
internal fun DailyQuestionPage(pager: Pager) {
    val theme = ThemeStore.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    // 作答输入(页内自持;已答未揭晓时「改答案」展开并预填)
    var answerInput by remember { mutableStateOf("") }
    var editingAnswer by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    // 历史月列表(页内自持;当月起拉,页销毁即弃)
    var historyItems by remember { mutableStateOf(emptyList<DailyQuestionItem>()) }
    var historyLoading by remember { mutableStateOf(false) }

    // 首拉今日题(Store.today 供首页条共用;成功才清失败态,失败且无旧值才翻失败态)
    suspend fun fetchToday() {
        val result = DailyQuestionStore.loadToday(pager)
        if (result is ApiResult.Ok) {
            loadFailed = false
        } else if (DailyQuestionStore.today == null) {
            loadFailed = true
        }
    }

    // 首拉当月历史:失败保留旧列表 toast 告知(只读降级,CheckinStore.loadMonth 同款口径)
    suspend fun fetchHistory() {
        historyLoading = true
        val result = DailyQuestionApi.list(pager, currentDate(pager).take(7))
        if (result is ApiResult.Ok) {
            historyItems = DailyQuestionItem.listFrom(result.dataArray)
        } else {
            toastCoupleResult(pager, result)
        }
        historyLoading = false
    }

    LaunchedEffect(Unit) {
        fetchToday()
        fetchHistory()
        loading = false
    }

    // 提交答案(单飞锁防双击;钮态最少可见 600ms 防一帧闪烁——QuotePage「换一句」同款)
    fun submitAnswer() {
        val content = answerInput.trim()
        if (content.isEmpty() || submitting) {
            return
        }
        submitting = true
        scope.launch {
            val startedAt = nowEpochMs()
            val result = DailyQuestionStore.submit(pager, content)
            // 请求耗时不足最少可见时长时补齐再落态:提交钮至少完整显示一拍(下拉刷新同款)
            val elapsed = nowEpochMs() - startedAt
            if (elapsed < REFRESH_MIN_VISIBLE_MS) {
                delay(REFRESH_MIN_VISIBLE_MS - elapsed)
            }
            if (result is ApiResult.Ok) {
                answerInput = ""
                editingAnswer = false
            } else {
                // 已揭晓 1_003_016_001 / 未绑定 1_003_004_003 等:统一 toast 服务端 msg
                toastCoupleResult(pager, result)
            }
            submitting = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppSubPageHeader(
                "每日一问",
                "一天一题,答完互见",
                onBack = {
                    pager.closeLovePage()
                },
            )
            when {
                loading -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    AppLoading()
                }
                loadFailed -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    AppLoadFailed(onRetry = {
                        loading = true
                        scope.launch {
                            fetchToday()
                            fetchHistory()
                            loading = false
                        }
                    })
                }
                else -> {
                    val today = DailyQuestionStore.today
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        item(key = "today") {
                            if (today == null) {
                                NoQuestionCard()
                            } else {
                                TodayQuestionCard(
                                    today,
                                    editing = editingAnswer,
                                    onStartEdit = {
                                        // 预填当前答案进输入胶囊(卡外渲染),再答一次即覆盖写
                                        answerInput = today.myAnswer
                                        editingAnswer = true
                                    },
                                )
                                // 可答态:输入胶囊落在 bg 上(白卡对比,AppInputPill CardBg 不与题目卡糊底)
                                if (!today.revealed && today.myAnswer.isEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    AppInputPill(
                                        value = answerInput,
                                        onValueChange = { answerInput = it },
                                        placeholder = "写下你的答案…",
                                        maxLength = ANSWER_MAX_LENGTH,
                                        modifier = Modifier
                                            .padding(horizontal = AppTheme.PageHPadding)
                                            .fillMaxWidth(),
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    BindPrimaryButton(
                                        text = "写 好 了",
                                        submitting = submitting,
                                        modifier = Modifier.padding(horizontal = 32.dp),
                                    ) {
                                        submitAnswer()
                                    }
                                }
                            }
                        }
                        item(key = "historyHeader") {
                            Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
                                Spacer(modifier = Modifier.height(28.dp))
                                AppSectionTitle("往期一问")
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                        if (historyItems.isEmpty()) {
                            item(key = "historyEmpty") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (historyLoading) "加载中…" else "这个月还没有往期一问",
                                        color = AppTheme.TextTertiary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                        items(historyItems, key = { it.questionDate }) { entry ->
                            HistoryItemCard(
                                entry,
                                modifier = Modifier.padding(
                                    horizontal = AppTheme.PageHPadding,
                                ).padding(bottom = 12.dp),
                            )
                        }
                        item(key = "footer") {
                            Spacer(modifier = Modifier.height(20.dp + navBarSafePaddingDp()))
                        }
                    }
                }
            }
        }
    }
}

/** 今日无题占位(题库空/当日题未生成;💬 方块 + 引导文案,TodayCheckinEntry 方块同视觉) */
@Composable
private fun NoQuestionCard() {
    Column(
        modifier = Modifier
            .padding(horizontal = AppTheme.PageHPadding)
            .padding(top = 16.dp)
            .fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(48.dp)
                .background(
                    ThemeStore.current.brandStart.copy(alpha = 0.10f),
                    RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("💬", fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "今日无题",
            color = AppTheme.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "题库休息中,明天再来",
            color = AppTheme.TextTertiary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 今日题卡:分类 chip + 日期 + 题目 + 状态/答案区。
 * 状态机(revealed / 我答 / TA 答)决定区体:揭晓=双方答案对比(锁定);我已答=答案卡+改答案;
 * 未答=状态文案(输入胶囊与提交钮在卡外 bg 上,见 DailyQuestionPage)
 */
@Composable
private fun TodayQuestionCard(
    today: DailyQuestionToday,
    editing: Boolean,
    onStartEdit: () -> Unit,
) {
    val theme = ThemeStore.current
    Column(
        modifier = Modifier
            .padding(horizontal = AppTheme.PageHPadding)
            .padding(top = 16.dp)
            .fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (today.category.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .background(theme.brandStart.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(today.category, color = theme.brandStart, fontSize = 10.sp)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                today.questionDate.take(10),
                color = AppTheme.TextTertiary,
                fontSize = 11.sp,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            today.content,
            color = AppTheme.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(14.dp))
        when {
            // 已揭晓:双方答案对比(锁定,零操作)
            today.revealed -> {
                AnswerBlock("我的答案", today.myAnswer, theme.brandStart)
                Spacer(modifier = Modifier.height(10.dp))
                AnswerBlock("TA 的答案", today.partnerAnswer, theme.brandEnd)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "💕 已揭晓 · 答案已锁定",
                    color = theme.brandStart,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            // 我已答未揭晓:答案卡 + 改答案入口(展开输入并预填;揭晓即锁,能改就趁早)
            today.myAnswer.isNotEmpty() -> {
                AnswerBlock("我的答案", today.myAnswer, theme.brandStart)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    if (today.partnerAnswered) "TA 也答好了,马上揭晓…" else "我已答,等 TA 中",
                    color = AppTheme.TextTertiary,
                    fontSize = 12.sp,
                )
                if (!today.partnerAnswered && !editing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "改答案",
                        color = theme.brandStart,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onStartEdit() },
                    )
                }
            }
            // 未答:状态文案(输入胶囊与提交钮在卡外,由页体按同条件渲染)
            else -> Text(
                if (today.partnerAnswered) "TA 已经答了,就差你啦" else "还没人回答,写下你的想法",
                color = AppTheme.TextTertiary,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * 答案块(揭晓对比/我的答案共用):浅彩底圆角块,左上角标签 + 正文。
 * 背景常驻+纯色 copy alpha(铁律 7:同节点单 background,无条件增删节点)
 */
@Composable
private fun AnswerBlock(label: String, content: String, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(content, color = AppTheme.TextPrimary, fontSize = 14.sp)
    }
}

/** 历史条目卡:日期 + 题目 + 揭晓态双方答案对比或未揭晓状态文案(错过没答的天答案永不揭晓) */
@Composable
private fun HistoryItemCard(item: DailyQuestionItem, modifier: Modifier = Modifier) {
    val theme = ThemeStore.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(theme.brandStart.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    item.questionDate.take(10),
                    color = theme.brandStart,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (item.category.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(item.category, color = AppTheme.TextTertiary, fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (item.revealed) {
                Text("💕", fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            item.content,
            color = AppTheme.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (item.revealed) {
            AnswerBlock("我的答案", item.myAnswer, theme.brandStart)
            Spacer(modifier = Modifier.height(8.dp))
            AnswerBlock("TA 的答案", item.partnerAnswer, theme.brandEnd)
        } else {
            Text(
                when {
                    item.myAnswer.isNotEmpty() -> "我已答,等 TA 中"
                    item.partnerAnswered -> "TA 已答,就差你——只能答当天,TA 的答案保密着"
                    else -> "都没答,这个问题留在了那天"
                },
                color = AppTheme.TextTertiary,
                fontSize = 12.sp,
            )
        }
    }
}
