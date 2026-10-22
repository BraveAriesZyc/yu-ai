package com.zyc.love_baby.features.profile

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
import com.zyc.love_baby.components.AppDatePickerOverlay
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderBackButton
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
import com.zyc.love_baby.data.model.zodiacSignOf
import com.zyc.love_baby.features.couple.NICKNAME_MAX_LENGTH
import com.zyc.love_baby.features.couple.formatBindTime
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.UserApi
import com.zyc.love_baby.network.WishApi
import com.zyc.love_baby.state.AnniversaryStore
import com.zyc.love_baby.state.CheckinStore
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.SessionStore
import com.zyc.love_baby.state.StatsStore
import kotlinx.coroutines.launch

/** profilePage 路由名与 who 参数取值(openProfilePage 与四处入口共用,勿散落字面量) */
internal const val PROFILE_PAGE_NAME = "profilePage"
internal const val PROFILE_WHO_MINE = "mine"
internal const val PROFILE_WHO_PARTNER = "partner"

/**
 * 路由打开用户资料详情页(who=mine|partner);事件回调里模块已就绪,try/catch 吞未桥接端
 * (base.Pager.openLovePage 统一包裹)。带参 openPage 先例 = RouterPage.jumpPage
 */
internal fun openProfilePage(pager: Pager, who: String) {
    try {
        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
            .openPage(PROFILE_PAGE_NAME, JSONObject().put("who", who))
    } catch (_: Exception) {
    }
}

/**
 * ProfilePage — 用户资料详情页(@Page "profilePage"):我/TA 一页两用(QQ 式行内编辑,2026-10-18)
 * 用法:openProfilePage(pager, who) 五入口(聊天页双头像/我的页资料区+右上角「详情」/情侣空间卡/首页头像);
 *      返回钮 closePage。独立二级页骨架照 WishListPage.kt:122-139(WishListPager 同款壳)
 * 依赖:StatsStore/CoupleStore/AnniversaryStore/CheckinStore(全局 Store 只读);
 *      UserApi.getUser(mine 自拉权威真值)/WishApi.list(贡献数端上聚合,页内自持不进 AppState
 *      D-19g 同款,页销毁即弃);features.profile 同包 ProfileEditFields(SexPickerOverlay
 *      性别底部选择器/FieldInputOverlay 单字段输入卡/uploadAvatarAndUpdate/isValidBirthday)
 *      + components.AppDatePickerOverlay(生日三列滚轮);features.couple.formatBindTime
 *      与 NICKNAME_MAX_LENGTH;
 *      components(AppGradientHeader/AppAvatar/AppStatBar/AppMenuGroup/AppMenuRow/AppSectionTitle)
 * 入参:pageData.params.who = "mine"|"partner"(非法/缺失兜底 mine,willInit 读入 Pager 字段)
 * 事件:返回钮 closePage;mine 页 QQ 式行内编辑(2026-10-18 用户裁决「详情页改成和 QQ 一样」,
 *      原「编辑」胶囊与 ProfileEditOverlay 大浮层废除):头像点击直接选图上传(右下角图片
 *      icon+灰磨砂圆角标);资料卡四行点击各弹对应控件,确定即保存
 * 功能:
 *  - 渐变头:返回行 + 96dp 双层渐变环大头像(照 BindingPanel 46dp 环放大)+ 昵称 + 副标题
 *    (mine=「在一起的第 N 天」/partner=「绑定于 X」,bindTime≤0 显「时间未知」);mine 大头像
 *    整块可点选图上传+右下角 28dp 灰磨砂圆(Image icon 白,半透明黑 35% 模拟毛玻璃)角标,
 *    上传中蒙黑 28%;partner 页只读不组合
 *  - 资料卡(M16 四行):昵称/性别/生日(星座)/个性签名 AppMenuRow,mine 行点击各弹控件
 *    (昵称/签名→FieldInputOverlay;性别→SexPickerOverlay;生日→AppDatePickerOverlay),
 *    partner 页只读;未设置占位 TextTertiary
 *  - 保存语义(UserUpdateReq 守卫对齐):昵称空串拦截「昵称不能为空」;签名 trim 含空串=清空;
 *    与现值全等不打请求直接关;性别选中≠现值才传(选中后不可取消);每字段独立单字段提交,
 *    成功原位回填+toast「已保存」,失败 toast 保留浮层可重试(fieldSaving 在飞拦点击)
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

    // 资料三字段(M16):mine=自拉 profile,partner=couple/get 回包 partner 三字段
    val profileSex = if (isPartner) couple?.partnerSex else profile?.sex
    val profileBirthday = if (isPartner) couple?.partnerBirthday.orEmpty() else profile?.birthday.orEmpty()
    val profileSignature = if (isPartner) couple?.partnerSignature.orEmpty() else profile?.signature.orEmpty()

    // QQ 式行内编辑浮层(仅 mine 页可开,partner 页纯展示):昵称/性别/生日/签名四浮层
    // 同一时刻至多一个;fieldSaving 各字段保存在飞闸共用的浮层(互斥开,天然不并发)。
    // 头像上传走页面级 scope(浮层外持有,上传中途关浮层照常完成)
    var showNicknameInput by remember { mutableStateOf(false) }
    var showSexPicker by remember { mutableStateOf(false) }
    var showBirthdayPicker by remember { mutableStateOf(false) }
    var showSignatureInput by remember { mutableStateOf(false) }
    var fieldSaving by remember { mutableStateOf(false) }
    var avatarUploading by remember { mutableStateOf(false) }
    val pageScope = rememberCoroutineScope()

    // profile=null(getUser 失败进页)时兜底构造空壳:五字段(头像/昵称/签名/性别/生日)
    // 保存成功都能原位回填,防「服务端已落库、本地态静默不回填」(重开页重拉自愈是第二道)
    fun ensureProfile(): UserProfile = profile ?: UserProfile(SessionStore.userId, "", "", "")

    // 头像上传(mine):选图→压缩→直传→user/update 写穿透,成功原位回填(原编辑浮层
    // onAvatarClick 回调链路收直为本页函数,QQ 式点击大头像直接发起)
    fun onAvatarUpload() {
        if (avatarUploading) return
        avatarUploading = true
        pageScope.launch {
            val fileUrl = uploadAvatarAndUpdate(pager)
            avatarUploading = false
            if (fileUrl != null) profile = ensureProfile().copy(avatar = fileUrl)
        }
    }

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

    // 浮层开着禁背景滚动(M16 真机反馈):灰幕 clickable 只吞 tap 不吞 move,原生 ScrollerView
    // 照滚 → 官方 userScrollEnabled 门控(六页先例:MinePage/MomentPage/ChatPage/AnniversaryPage/
    // WishListPage/MoodPage 的 listScrollEnabled 同款)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(theme.bg),
        userScrollEnabled = !(showNicknameInput || showSexPicker || showBirthdayPicker || showSignatureInput),
    ) {
        item {
            ProfileHeader(
                pager,
                isPartner,
                avatarUrl,
                nickname,
                bindTimeText,
                if (!isPartner) {
                    { onAvatarUpload() }
                } else {
                    null
                },
                avatarUploading,
            )
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
            // 资料卡(M16→QQ 式四行):昵称/性别/生日(星座)/签名,trailing 覆写为值替代默认箭头;
            // mine 页各行点击弹对应编辑控件(2026-10-18 QQ 式行内编辑),partner 页只读传空;
            // 图标凑现有库,空值显「未设置」
            Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
                Spacer(modifier = Modifier.height(20.dp))
                AppSectionTitle(if (isPartner) "TA 的资料" else "我的资料")
                Spacer(modifier = Modifier.height(10.dp))
                // 四行各自独立入口(mine 页;partner 页只读传空 lambda)
                val openNickname: () -> Unit = if (isPartner) ({}) else ({ showNicknameInput = true })
                val openSex: () -> Unit = if (isPartner) ({}) else ({ showSexPicker = true })
                val openBirthday: () -> Unit = if (isPartner) ({}) else ({ showBirthdayPicker = true })
                val openSignature: () -> Unit = if (isPartner) ({}) else ({ showSignatureInput = true })
                AppMenuGroup {
                    AppMenuRow(AppIconType.Mine, Color(0xFF6C7BFF), Color(0xFFEDEFFF), "昵称", onClick = openNickname) {
                        Text(
                            nickname,
                            color = AppTheme.TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                    AppMenuDivider()
                    AppMenuRow(AppIconType.GenderMale, Color(0xFF6C7BFF), Color(0xFFEDEFFF), "性别", onClick = openSex) {
                        Text(
                            when (profileSex) {
                                0 -> "女"
                                1 -> "男"
                                else -> "未设置"
                            },
                            color = if (profileSex == null) AppTheme.TextTertiary else AppTheme.TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                    AppMenuDivider()
                    AppMenuRow(AppIconType.Star, Color(0xFFFF9A56), Color(0xFFFFF1E8), "生日", onClick = openBirthday) {
                        val zodiac = zodiacSignOf(profileBirthday)
                        Text(
                            when {
                                profileBirthday.isEmpty() -> "未设置"
                                zodiac.isNotEmpty() -> "$profileBirthday（$zodiac）"
                                else -> profileBirthday
                            },
                            color = if (profileBirthday.isEmpty()) AppTheme.TextTertiary else AppTheme.TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                    AppMenuDivider()
                    AppMenuRow(AppIconType.Info, Color(0xFF34C77B), Color(0xFFE8F9F0), "个性签名", onClick = openSignature) {
                        Text(
                            profileSignature.ifEmpty { "未设置" },
                            color = if (profileSignature.isEmpty()) AppTheme.TextTertiary else AppTheme.TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                }
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

    // QQ 式行内编辑浮层挂载区(LazyColumn 兄弟之后=后组合者在上;独立 @Page 无浮岛底栏,
    // 不存在 M12「页内浮层盖不住底栏」问题)。每字段独立保存:成功原位回填+toast+关浮层,
    // 失败 toast 保留浮层可重试(fieldSaving 在飞拦点击);profile 为 null(getUser 失败
    // 进页)时兜底构造防服务端已落库本地却不回填
    if (showNicknameInput) {
        FieldInputOverlay(
            pager = pager,
            title = "修改昵称",
            initialValue = profile?.nickname.orEmpty(),
            placeholder = "昵称",
            maxLength = NICKNAME_MAX_LENGTH,
            saving = fieldSaving,
            onSave = { next ->
                if (next.isEmpty()) {
                    toastCouple(pager, "昵称不能为空")
                } else if (next == profile?.nickname.orEmpty()) {
                    showNicknameInput = false // 全等不打请求直接关
                } else {
                    fieldSaving = true
                    pageScope.launch {
                        val result = UserApi.updateUser(pager, nickname = next)
                        fieldSaving = false
                        if (result is ApiResult.Ok) {
                            profile = ensureProfile().copy(nickname = next)
                            showNicknameInput = false
                            toastCouple(pager, "已保存")
                        } else {
                            toastCoupleResult(pager, result)
                        }
                    }
                }
            },
            onDismiss = { showNicknameInput = false },
        )
    }
    if (showSignatureInput) {
        FieldInputOverlay(
            pager = pager,
            title = "个性签名",
            initialValue = profile?.signature.orEmpty(),
            placeholder = "一句话介绍自己",
            maxLength = 100,
            saving = fieldSaving,
            onSave = { next ->
                if (next == profile?.signature.orEmpty()) {
                    showSignatureInput = false // 全等不打请求直接关
                } else {
                    // 含空串=清空(M16 定稿语义:trim 后非 null 即传)
                    fieldSaving = true
                    pageScope.launch {
                        val result = UserApi.updateUser(pager, signature = next)
                        fieldSaving = false
                        if (result is ApiResult.Ok) {
                            profile = ensureProfile().copy(signature = next)
                            showSignatureInput = false
                            toastCouple(pager, "已保存")
                        } else {
                            toastCoupleResult(pager, result)
                        }
                    }
                }
            },
            onDismiss = { showSignatureInput = false },
        )
    }
    if (showSexPicker) {
        SexPickerOverlay(
            current = profile?.sex,
            saving = fieldSaving,
            onPick = { sex ->
                if (sex == profile?.sex) {
                    showSexPicker = false // 点已选行直接关(性别选中后不可取消)
                } else {
                    fieldSaving = true
                    pageScope.launch {
                        val result = UserApi.updateUser(pager, sex = sex)
                        fieldSaving = false
                        if (result is ApiResult.Ok) {
                            profile = ensureProfile().copy(sex = sex)
                            showSexPicker = false
                            toastCouple(pager, "已保存")
                        } else {
                            toastCoupleResult(pager, result)
                        }
                    }
                }
            },
            onDismiss = { showSexPicker = false },
        )
    }
    if (showBirthdayPicker) {
        val parts = profileBirthday.trim().split("-")
        AppDatePickerOverlay(
            initialYear = parts.getOrNull(0)?.toIntOrNull() ?: 2000,
            initialMonth = parts.getOrNull(1)?.toIntOrNull() ?: 1,
            initialDay = parts.getOrNull(2)?.toIntOrNull() ?: 1,
            onConfirm = { y, m, d ->
                // commonMain 无 String.format,padStart 拼 yyyy-MM-dd;滚轮保证格式,
                // isValidBirthday 仅内部状态异常兜底
                val date = "$y".padStart(4, '0') + "-" + "$m".padStart(2, '0') + "-" + "$d".padStart(2, '0')
                if (!isValidBirthday(date) || date == profileBirthday.trim()) {
                    showBirthdayPicker = false
                    return@AppDatePickerOverlay
                }
                fieldSaving = true
                pageScope.launch {
                    val result = UserApi.updateUser(pager, birthday = date)
                    fieldSaving = false
                    showBirthdayPicker = false // 滚轮无重试态,失败 toast 后关(重开即重试)
                    if (result is ApiResult.Ok) {
                        profile = ensureProfile().copy(birthday = date)
                        toastCouple(pager, "已保存")
                    } else {
                        toastCoupleResult(pager, result)
                    }
                }
            },
            onDismiss = { showBirthdayPicker = false },
        )
    }
}

/**
 * 渐变头:返回钮行 + 居中舞台(96dp 双层渐变环大头像 + 昵称 + 副标题)。
 * 返回钮 WishListPage.kt:364-373 逐字同款;渐变环照 BindingPanel 46dp 环放大;
 * 舞台层 Column fillMaxWidth 让头像/昵称相对整行居中(返回钮独占一行,无横向重叠)。
 * onAvatarClick 仅 mine 页传(QQ 式点头像直接选图上传,2026-10-18 原「编辑」胶囊废除):
 * 大头像右下角 28dp 灰磨砂圆(半透明黑 35% 模拟毛玻璃,Kuikly 无真 blur)+ 白色图片
 * icon 角标;上传中(avatarUploading)蒙黑 28% 拦重复发起;partner 页 null 只读不组合
 */
@Composable
private fun ProfileHeader(
    pager: Pager,
    isPartner: Boolean,
    avatarUrl: String,
    nickname: String,
    subtitle: String,
    onAvatarClick: (() -> Unit)? = null,
    avatarUploading: Boolean = false,
) {
    val theme = ThemeStore.current
    AppGradientHeader(bottomSpacing = 20.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 返回钮(components/AppHeaderBackButton 单源:38dp 白 25% 圆 + ChevronLeft 20dp,
                // 与原手写件逐值同构);仅换返回钮,本头部 SpaceBetween 自绘结构不动
                AppHeaderBackButton {
                    pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // 大头像舞台:品牌渐变外环 + 白底内圆(AppAvatar 圆角裁切,加载中/失败内圆底透出)。
            // mine 页(onAvatarClick != null):透明全尺寸热区层点头像任意位置即发起选图上传
            // (QQ 式;无背景纯 clickable 热区,BoundPartnerCard Row 链插同款),组合序
            // 外环 → AppAvatar → 热区 → 角标 → 上传蒙层(后者在上);角标=灰磨砂圆+白图片 icon
            // ⚠ 舞台本体无背景:QQ 式角标骑在头像圆边上,若住进外环的圆角背景容器,
            // 圆外部分会被原生轮廓裁掉(2026-10-18 真机反馈,M15 三轮透明圆角同源)——
            // 外环圆独立成无子节点背景层,AppAvatar/角标作舞台直接子节点
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(96.dp)
                        .background(theme.brandBrush, RoundedCornerShape(48.dp)),
                )
                AppAvatar(
                    nickname.take(1),
                    size = 88.dp,
                    bgColor = AppTheme.CardBg,
                    textColor = theme.brandStart,
                    fontSize = 30.sp,
                    avatarUrl = avatarUrl,
                )
                if (onAvatarClick != null) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .clickable { onAvatarClick() },
                    )
                    // 角标(三层:外定位内背景 icon 子节点,铁律 14/15):28dp 灰磨砂圆
                    // (半透明黑 35% 模拟毛玻璃质感)+ 14dp 白图片 icon。⚠ 自带 clickable:
                    // 有背景视图盖在无背景热区之上会吞掉落点(铁律 7 命中测试以背景为准,
                    // AppCheckbox 环内孔同款),角标是头像上最像按钮的区域必须挂同一回调
                    // (嵌套 clickable 内层消费,不会双触发)
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .clickable { onAvatarClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIconType.Image, Color.White, Modifier.size(14.dp))
                    }
                    if (avatarUploading) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(48.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("…", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
