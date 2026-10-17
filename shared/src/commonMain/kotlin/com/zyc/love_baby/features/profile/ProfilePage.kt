package com.zyc.love_baby.features.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
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
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.components.AppAvatar
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppMenuDivider
import com.zyc.love_baby.components.AppMenuGroup
import com.zyc.love_baby.components.AppMenuRow
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppSectionTitle
import com.zyc.love_baby.components.AppStatBar
import com.zyc.love_baby.components.AppStatItem
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.data.model.UserProfile
import com.zyc.love_baby.data.model.WishInfo
import com.zyc.love_baby.features.couple.formatBindTime
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.UserApi
import com.zyc.love_baby.network.WishApi
import com.zyc.love_baby.state.AnniversaryStore
import com.zyc.love_baby.state.CheckinStore
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.SessionStore
import com.zyc.love_baby.state.StatsStore

/** profilePage 路由名与 who 参数取值(openProfilePage 与四处入口共用,勿散落字面量) */
internal const val PROFILE_PAGE_NAME = "profilePage"
internal const val PROFILE_WHO_MINE = "mine"
internal const val PROFILE_WHO_PARTNER = "partner"

/**
 * 路由打开用户资料详情页(who=mine|partner);事件回调里模块已就绪,try/catch 吞未桥接端
 * (HomePage.openAnniversaryList 同款包裹)。带参 openPage 先例 = RouterPage.jumpPage
 */
internal fun openProfilePage(pager: Pager, who: String) {
    try {
        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
            .openPage(PROFILE_PAGE_NAME, JSONObject().put("who", who))
    } catch (_: Exception) {
    }
}

/**
 * ProfilePage — 用户资料详情页(@Page "profilePage"):我/TA 一页两用,纯展示(2026-10-18)
 * 用法:openProfilePage(pager, who) 四入口(聊天页双头像/我的页资料区/情侣空间卡/首页头像);
 *      返回钮 closePage。独立二级页骨架照 WishListPage.kt:122-139(WishListPager 同款壳)
 * 依赖:StatsStore/CoupleStore/AnniversaryStore/CheckinStore(全局 Store 只读);
 *      UserApi.getUser(mine 自拉权威真值)/WishApi.list(贡献数端上聚合,页内自持不进 AppState
 *      D-19g 同款,页销毁即弃);features.couple.formatBindTime(宿主 dateFormatter);
 *      components(AppGradientHeader/AppAvatar/AppStatBar/AppMenuGroup/AppMenuRow/AppSectionTitle)
 * 入参:pageData.params.who = "mine"|"partner"(非法/缺失兜底 mine,willInit 读入 Pager 字段)
 * 事件:仅返回钮 closePage(纯展示页,无行点击无编辑入口——编辑留在我的页原链路)
 * 功能:
 *  - 渐变头:返回行 + 96dp 双层渐变环大头像(照 BindingPanel 46dp 环放大)+ 昵称 + 副标题
 *    (mine=「在一起的第 N 天」/partner=「绑定于 X」,bindTime≤0 显「时间未知」)
 *  - 共享统计 5 列:在一起/心愿/心情/待办/纪念日(couple 共享账本,StatsStore+AnniversaryStore)
 *  - 打卡条 3 列:共同连续 + 我/TA 累计(summary null 显 "--",CheckinStore 注释自带口径)
 *  - 个人贡献「足迹」三行:许下的心愿/点亮的心愿/创建的纪念日——端上聚合零服务端改动
 *    (wish.userId=许愿人/wish.doneBy=点亮人[发布者不能点亮自己 D-19]/anniversary.ownerId=创建者)
 */
@Page("profilePage", supportInLocal = true)
internal class ProfilePager : BasePager() {

    /** who 参数(willInit 定,组合体只读):true=伴侣页;非 "partner" 一律 mine */
    internal var isPartnerMode = false

    override fun willInit() {
        super.willInit()
        isPartnerMode = try {
            pageData.params.optString("who") == PROFILE_WHO_PARTNER
        } catch (_: Exception) {
            false
        }
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            ProfilePage(this, isPartnerMode)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }
}

@Composable
internal fun ProfilePage(pager: Pager, isPartner: Boolean) {
    val theme = ThemeStore.current
    // mine 页自拉资料:独立页是另一个 Pager,MainPager 的组合态 profile 不可达,也不宜经 pageData
    // 传快照(开页时刻的值,编辑后即陈旧)——一次 GET 换权威真值;partner 页不消费此值
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    // 贡献聚合源:恋爱清单全量(页内自持,与 WishListPage 同款不进全局状态)
    val wishes = remember { mutableStateListOf<WishInfo>() }

    // 独立 @Page 组合在即活动在(MomentPage/WishListPage 先例),每次打开新 Pager 组合即重拉
    LaunchedEffect(Unit) {
        StatsStore.refresh(pager) // 共享统计保新鲜(轻量 GET,失败静默保留旧值)
        if (isPartner) {
            CoupleStore.refresh(pager) // 60s 轮询外的进页兜底,TA 换头像立即可见
        } else {
            UserApi.getUser(pager).let { result ->
                if (result is ApiResult.Ok) profile = UserProfile.from(result.data)
            }
        }
        WishApi.list(pager).let { result ->
            if (result is ApiResult.Ok) wishes.addAll(WishInfo.listFrom(result.dataArray))
        } // 失败静默:贡献行显 0(只读展示,下个周期无轮询但进页重拉自愈)
        if (AnniversaryStore.anniversaries.isEmpty()) AnniversaryStore.loadList(pager)
        if (CheckinStore.summary == null) CheckinStore.loadSummary(pager)
    }

    // 派生值(组合期纯计算,全局 Store 可读状态直接依赖)
    val couple = CoupleStore.couple
    val myId = SessionStore.userId
    val subjectId = if (isPartner) couple?.partnerId.orEmpty() else myId
    val wishCreated = wishes.count { it.userId == subjectId }
    val wishLit = wishes.count { it.doneBy == subjectId }
    val annivCreated = AnniversaryStore.anniversaries.count { it.ownerId == subjectId }

    // 头像/昵称/副标题(按 who 切数据源;空值兜底全走既有先例口径)
    val avatarUrl = if (isPartner) couple?.partnerAvatar.orEmpty() else profile?.avatar.orEmpty()
    val nickname = if (isPartner) {
        couple?.partnerNickname?.trim().orEmpty().ifEmpty { "TA" }
    } else {
        profile?.nickname?.trim().orEmpty().ifEmpty { "宝" }
    }
    val bindTimeText = if (isPartner) {
        val bindTime = couple?.bindTime ?: 0L
        if (bindTime > 0) remember(bindTime) { formatBindTime(pager, bindTime) } else "时间未知"
    } else {
        "在一起的第 ${StatsStore.bindDays} 天,依然心动"
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        item {
            ProfileHeader(pager, isPartner, avatarUrl, nickname, bindTimeText)
        }
        item {
            // 共享统计 5 列(couple 共享账本:统计端点按 couple 归属计数,双方同值)
            Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
                Spacer(modifier = Modifier.height(14.dp))
                AppStatBar(
                    items = listOf(
                        AppStatItem("${StatsStore.bindDays}", "在一起"),
                        AppStatItem("${StatsStore.wishCount}", "心愿"),
                        AppStatItem("${StatsStore.moodCount}", "心情"),
                        AppStatItem("${StatsStore.todoCount}", "待办"),
                        AppStatItem("${AnniversaryStore.anniversaries.size}", "纪念日"),
                    ),
                    valueSize = 18.sp,
                    labelSize = 11.sp,
                    dividerHeight = 26.dp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                // 打卡条 3 列:共同连续 + 我/TA 累计(服务端聚合直给,端上零算术);
                // summary 未拉到显 "--"(CheckinStore 注释自带口径),拉到后 0 是合法真值
                val summary = CheckinStore.summary
                val myTotal = summary?.myTotal?.toString() ?: "--"
                val partnerTotal = summary?.partnerTotal?.toString() ?: "--"
                AppStatBar(
                    items = listOf(
                        AppStatItem(summary?.commonStreak?.toString() ?: "--", "共同连续(天)"),
                        if (isPartner) {
                            AppStatItem(partnerTotal, "TA 累计(天)")
                        } else {
                            AppStatItem(myTotal, "我累计(天)")
                        },
                        if (isPartner) {
                            AppStatItem(myTotal, "我累计(天)")
                        } else {
                            AppStatItem(partnerTotal, "TA 累计(天)")
                        },
                    ),
                    valueSize = 18.sp,
                    labelSize = 11.sp,
                    dividerHeight = 26.dp,
                )
            }
        }
        item {
            // 个人贡献区:图标配色与首页快捷入口四件套同源(HomePage AppQuickAction);
            // trailing 覆写为计数替代默认箭头 → 展示行不暗示可跳(onClick 空)
            Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
                Spacer(modifier = Modifier.height(20.dp))
                AppSectionTitle(if (isPartner) "TA 的足迹" else "我的足迹")
                Spacer(modifier = Modifier.height(10.dp))
                AppMenuGroup {
                    AppMenuRow(AppIconType.Flag, Color(0xFFF7A44A), Color(0xFFFFF3E6), "许下的心愿") {
                        Text("$wishCreated 条", color = AppTheme.TextSecondary, fontSize = 13.sp)
                    }
                    AppMenuDivider()
                    AppMenuRow(AppIconType.Heart, Color(0xFFFF5A8C), Color(0xFFFFEAF1), "点亮的心愿") {
                        Text("$wishLit 个", color = AppTheme.TextSecondary, fontSize = 13.sp)
                    }
                    AppMenuDivider()
                    AppMenuRow(AppIconType.Star, Color(0xFFFF9A56), Color(0xFFFFF1E8), "创建的纪念日") {
                        Text("$annivCreated 个", color = AppTheme.TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(24.dp + navBarSafePaddingDp()))
        }
    }
}

/**
 * 渐变头:返回钮行 + 居中舞台(96dp 双层渐变环大头像 + 昵称 + 副标题)。
 * 返回钮 WishListPage.kt:364-373 逐字同款;渐变环照 BindingPanel 46dp 环放大;
 * 舞台层 Column fillMaxWidth 让头像/昵称相对整行居中(返回钮独占一行,无横向重叠)
 */
@Composable
private fun ProfileHeader(
    pager: Pager,
    isPartner: Boolean,
    avatarUrl: String,
    nickname: String,
    subtitle: String,
) {
    val theme = ThemeStore.current
    AppGradientHeader(bottomSpacing = 20.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(38.dp)
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(19.dp))
                        .clickable {
                            pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(
                        AppIconType.ChevronLeft,
                        Color.White,
                        Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // 大头像舞台:品牌渐变外环 + 白底内圆(AppAvatar 圆角裁切,加载中/失败内圆底透出)
            Box(
                modifier = Modifier.size(96.dp)
                    .background(theme.brandBrush, RoundedCornerShape(48.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppAvatar(
                    nickname.take(1),
                    size = 88.dp,
                    bgColor = AppTheme.CardBg,
                    textColor = theme.brandStart,
                    fontSize = 30.sp,
                    avatarUrl = avatarUrl,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(nickname, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
            )
        }
    }
}
