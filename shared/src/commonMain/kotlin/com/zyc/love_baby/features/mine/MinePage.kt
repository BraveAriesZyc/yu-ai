package com.zyc.love_baby.features.mine

import androidx.compose.runtime.Composable
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
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.components.AppAvatar
import com.zyc.love_baby.components.AppGradientHeader
import com.zyc.love_baby.components.AppHeaderTitle
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppMenuDivider
import com.zyc.love_baby.components.AppMenuGroup
import com.zyc.love_baby.components.AppMenuRow
import com.zyc.love_baby.components.AppStatBar
import com.zyc.love_baby.components.AppStatItem
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.data.model.UserProfile
import com.zyc.love_baby.features.couple.BindInputField
import com.zyc.love_baby.features.couple.BindPrimaryButton
import com.zyc.love_baby.features.couple.BoundPartnerCard
import com.zyc.love_baby.features.couple.NICKNAME_MAX_LENGTH
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.UserApi
import com.zyc.love_baby.state.CoupleBindState
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.StatsStore
import kotlinx.coroutines.launch

/**
 * MinePage — 我的页:主题渐变头部 + 悬浮统计(恋爱数据) + 情侣空间卡(M2) + 主题外观切换 + 设置分组
 * 用法:MinePage(pager, profile, onEditProfile, onUnbindClick, onLogoutClick)。M3(F6,§7.7)起本页无浮层/无拉取:
 *      profile 由 MainPager 提升持有并拉取(user/get),「编辑」「解绑」「退出登录」仅回调上报,浮层统一挂在
 *      MainPageImpl 根 Box(后于底栏组合,盖住底栏且遮罩挡触摸)——浮层陷入本页 LazyColumn 时
 *      滚动项仍可穿透点击,且被底栏压住,均需根级承载
 * 依赖:ThemeStore/AppIcons/components 通用组件;RouterModule(打开组件列表页);
 *      UserProfile(MainPager 回填);CoupleStore/BoundPartnerCard(情侣空间,M2)
 * 事件:主题圆点点击 → ThemeStore.switch + persist;「组件列表」→ openPage("componentList");
 *      「编辑」→ onEditProfile();「解绑」→ onUnbindClick();「退出登录」→ onLogoutClick()(动作由 MainPager 收口)
 * 功能:
 *  - 资料区(§7.7):昵称/头像展示由 profile 参数给定,编辑浮层在 MainPager(user/update 写穿透)
 *  - 情侣空间卡:已绑定时展示伴侣昵称/绑定时间 + 解绑入口(二次确认浮层在 MainPager);
 *    未绑定时本页被 BindingGuide 整屏覆盖不可达,故不设未绑定分支
 */
@Composable
internal fun MinePage(
    pager: Pager,
    profile: UserProfile?,
    onEditProfile: () -> Unit,
    onUnbindClick: () -> Unit,
    onLogoutClick: () -> Unit,
    listScrollEnabled: Boolean = true,
) {
    // 根容器收背景(浮层在 MainPager 根 Box,需盖住整页;背景与 LazyColumn 原本同色,视觉无变化)
    Box(modifier = Modifier.fillMaxSize().background(ThemeStore.current.bg)) {
        // userScrollEnabled:浮层(编辑/解绑/退出,MainPager 根级)开着时关本页列表滚动——
        // Kuikly 遮罩的 clickable 只吃 tap 不吃拖拽,背景 LazyColumn 原生滚动仍收事件
        // (2026-10-17 真机实证「浮层开着还能滚背景」),官方开关是最干净的阻断通道
        LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = listScrollEnabled) {
            item {
                HeaderWithStats(
                    pager = pager,
                    profile = profile,
                    onEditClick = onEditProfile,
                )
            }
            item {
                Spacer(modifier = Modifier.height(38.dp))
                // 情侣空间卡(M2):伴侣信息 + 解绑入口;解绑确认浮层挂在 MainPager 根 Box
                if (CoupleStore.bindState == CoupleBindState.BOUND) {
                    BoundPartnerCard(pager, onUnbindClick = onUnbindClick)
                    Spacer(modifier = Modifier.height(14.dp))
                }
                ThemePickerCard(pager)
                Spacer(modifier = Modifier.height(14.dp))
                // 设置分组
                AppMenuGroup(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
                    AppMenuRow(
                        icon = AppIconType.Star,
                        iconTint = Color(0xFFFF9A56),
                        iconBg = Color(0xFFFFF1E8),
                        title = "纪念日提醒",
                        onClick = { openAnniversaryList(pager) }, // M5:接纪念日独立页(原死菜单项)
                    )
                    AppMenuDivider()
                    AppMenuRow(
                        icon = AppIconType.Todo,
                        iconTint = Color(0xFF6C7BFF),
                        iconBg = Color(0xFFEDEFFF),
                        title = "每日打卡",
                        onClick = { openCheckinPage(pager) }, // M8:接打卡独立页(月历 + 共同连续)
                    )
                    AppMenuDivider()
                    AppMenuRow(
                        icon = AppIconType.Heart,
                        iconTint = Color(0xFFFF5A8C),
                        iconBg = Color(0xFFFFEAF1),
                        title = "我们的相册",
                        onClick = { openMomentPage(pager) }, // M9:接相册页(双列瞬间墙 + 发布/预览)
                    )
                    AppMenuDivider()
                    AppMenuRow(
                        icon = AppIconType.Service,
                        iconTint = Color(0xFF34C77B),
                        iconBg = Color(0xFFE6F8EF),
                        title = "帮助与客服",
                    )
                    AppMenuDivider()
                    AppMenuRow(
                        icon = AppIconType.Info,
                        iconTint = Color(0xFF6C7BFF),
                        iconBg = Color(0xFFEDEFFF),
                        title = "关于我们",
                    )
                    AppMenuDivider()
                    // 组件列表:通用组件 demo 画廊
                    AppMenuRow(
                        icon = AppIconType.Components,
                        iconTint = Color(0xFFFF7A45),
                        iconBg = Color(0xFFFFF3ED),
                        title = "组件列表",
                        onClick = { openComponentGallery(pager) },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                // 退出登录:仅回调上报,确认浮层与 logout/clear 动作由 MainPager 根级收口(F6,§5.2)
                Box(
                    modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)
                        .fillMaxWidth()
                        .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                        .clickable { onLogoutClick() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("退出登录", color = AppTheme.Danger, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(100.dp + navBarSafePaddingDp())) // 底部导航避让
            }
        }
    }
}

/**
 * openComponentGallery — 路由打开组件列表页(新 Activity 压栈,closePage/系统返回即回)
 * 事件回调里模块已就绪(didInit 之后),acquireModule 安全
 */
private fun openComponentGallery(pager: Pager) {
    try {
        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
            .openPage("componentList", JSONObject())
    } catch (_: Exception) {
    }
}

/**
 * openWishTimeline — 路由打开心愿时间线页(新 Activity 压栈,closePage/系统返回即回)
 * 事件回调里模块已就绪(didInit 之后),acquireModule 安全(openComponentGallery 同款)
 */
private fun openWishTimeline(pager: Pager) {
    try {
        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
            .openPage("wishTimeline", JSONObject())
    } catch (_: Exception) {
    }
}

/**
 * openAnniversaryList — 路由打开纪念日页(新 Activity 压栈,closePage/系统返回即回)
 * 事件回调里模块已就绪(didInit 之后),acquireModule 安全(openWishTimeline 同款)
 */
private fun openAnniversaryList(pager: Pager) {
    try {
        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
            .openPage("anniversaryList", JSONObject())
    } catch (_: Exception) {
    }
}

/**
 * openCheckinPage — 路由打开每日打卡页(M8;openAnniversaryList 同款新 Activity 压栈)
 */
private fun openCheckinPage(pager: Pager) {
    try {
        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
            .openPage("checkinPage", JSONObject())
    } catch (_: Exception) {
    }
}

/**
 * openMomentPage — 路由打开我们的相册页(M9;openCheckinPage 同款新 Activity 压栈)
 */
private fun openMomentPage(pager: Pager) {
    try {
        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
            .openPage("momentPage", JSONObject())
    } catch (_: Exception) {
    }
}

/** 头部:资料区(§7.7)读 profile 参数回填昵称与首字头像;「编辑」回调 onEditClick;
 *  统计卡「心愿」列可点进心愿时间线页(需 pager 路由,未绑定时点击不响应) */
@Composable
private fun HeaderWithStats(pager: Pager, profile: UserProfile?, onEditClick: () -> Unit) {
    val nickname = profile?.nickname?.trim().orEmpty()
    Box(modifier = Modifier.fillMaxWidth()) {
        // 渐变头部
        AppGradientHeader(height = 138.dp, topSpacing = 20.dp) {
            // 头像(双层圆环):avatar 非空走远程图(AppAvatar 圆角裁切,加载中半透白底透出
            // 充当占位),空走首字(文件服务里程碑,上传链路见 MainPager.submitAvatarUpload)
            Box(
                modifier = Modifier.size(58.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(29.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppAvatar(
                    if (nickname.isEmpty()) "宝" else nickname.take(1),
                    size = 50.dp,
                    bgColor = Color.White.copy(alpha = 0.35f),
                    fontSize = 20.sp,
                    avatarUrl = profile?.avatar.orEmpty(),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            AppHeaderTitle(
                if (nickname.isEmpty()) "宝贝 & 我" else nickname,
                "在一起的第 520 天,依然心动",
                titleSize = 19.sp,
                subtitleSize = 12.sp,
            )
            // 编辑入口
            Box(
                modifier = Modifier.background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .clickable { onEditClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("编辑", color = Color.White, fontSize = 12.sp)
            }
        }
        // 悬浮统计卡(统计真数据化:三项全真,StatsStore 由 MainPager 拉取,数值变化写穿透后刷新)。
        // 「心愿」列可点进时间线页(messageCount 口径保留在接口,端上第一卡按产品口径改心愿);
        // 未绑定不响应(wish-list 需 couple 上下文,点了也只是失败态)
        AppStatBar(
            items = listOf(
                AppStatItem(
                    "${StatsStore.wishCount}",
                    "心愿",
                    onClick = {
                        if (CoupleStore.bindState == CoupleBindState.BOUND) openWishTimeline(pager)
                    },
                ),
                AppStatItem("${StatsStore.todoCount}", "待办"),
                AppStatItem("${StatsStore.moodCount}", "心情"),
            ),
            valueSize = 18.sp,
            dividerHeight = 26.dp,
            modifier = Modifier.align(Alignment.BottomCenter)
                .offset(y = 22.dp)
                .padding(horizontal = AppTheme.PageHPadding),
        )
    }
}

/**
 * NicknameEditOverlay — 资料编辑浮层(M2 §7.7):user/update 写穿透,成功才回填 onSaved,
 * 失败 toast 保留原值(§7.4 失败即回滚语义,不做乐观更新)。头像区(文件服务里程碑美化):
 * 72dp 大头像(渐变底/远程图)+ 右下「+」更换徽章 + 居中提示文案,整块可点上报 onAvatarClick,
 * 上传中头像蒙半透明黑 + 文案切换且拦点击;上传流程由 MainPager 收口(F6 同款,本浮层不自持)。
 * M3(F6,§7.7)起改 internal 由 MainPager 根 Box 挂载(本页滚动项不吞浮层触摸)
 */
@Composable
internal fun NicknameEditOverlay(
    pager: Pager,
    currentNickname: String,
    currentAvatar: String = "",
    uploading: Boolean = false,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    onAvatarClick: () -> Unit = {},
) {
    var draft by remember { mutableStateOf(currentNickname) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save() {
        if (saving) return
        val next = draft.trim()
        if (next.isEmpty()) {
            toastCouple(pager, "昵称不能为空")
            return
        }
        if (next == currentNickname) {
            onDismiss()
            return
        }
        saving = true
        scope.launch {
            val result = UserApi.updateUser(pager, nickname = next)
            saving = false
            if (result is ApiResult.Ok) {
                onSaved(next)
                toastCouple(pager, "已保存")
                onDismiss()
            } else {
                toastCoupleResult(pager, result)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { if (!saving) onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp)
                .fillMaxWidth()
                .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                .clickable { }
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("编辑资料", color = AppTheme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(18.dp))
            // 头像区(文件服务里程碑美化):72dp 大头像 + 右下「+」更换徽章 + 居中提示,
            // 整块可点上报 onAvatarClick(选图/压缩/直传/写回全在 MainPager 收口);
            // 上传中蒙半透明黑 + 文案切换 + 拦点击防重复发起
            Column(
                modifier = Modifier.clickable { if (!uploading) onAvatarClick() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.size(72.dp)) {
                    AppAvatar(
                        currentNickname.trim().take(1).ifEmpty { "爱" },
                        size = 72.dp,
                        bgBrush = ThemeStore.current.brandBrush,
                        fontSize = 24.sp,
                        avatarUrl = currentAvatar,
                    )
                    if (uploading) {
                        // 上传中蒙层:圆内半透明黑,盖住头像暗示「处理中」
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(36.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("…", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // 右下「+」更换徽章:白底圆 + 主题色加号(压在头像右下缘,对比来自头像本身)
                        Box(
                            modifier = Modifier.align(Alignment.BottomEnd)
                                .size(24.dp)
                                .background(AppTheme.CardBg, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "+",
                                color = ThemeStore.current.brandStart,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (uploading) "头像上传中…" else "点击更换头像",
                    color = if (uploading) AppTheme.TextTertiary else AppTheme.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            BindInputField(
                value = draft,
                onValueChange = { draft = it.take(NICKNAME_MAX_LENGTH) },
                placeholder = "昵称",
            )
            Spacer(modifier = Modifier.height(18.dp))
            BindPrimaryButton(text = if (saving) "保存中…" else "保 存", submitting = saving) { save() }
        }
    }
}

/**
 * LogoutConfirmOverlay — 退出登录二次确认浮层(全屏灰幕 + 居中白卡,BindUnbindConfirm 同款结构):
 * 灰幕点击取消,卡片 background+clickable 同节点吞点击防透传(铁律⑤);submitting 期间所有出口拦点击。
 * M3(F6)起 internal 由 MainPager 根 Box 挂载(同 NicknameEditOverlay,本页滚动项不吞浮层触摸);
 * 确认动作 = 先调 AuthApi.logout(容忍失败,§5.2)再 SessionStore.clear 回 Gate,由 MainPager 收口
 */
@Composable
internal fun LogoutConfirmOverlay(
    submitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { if (!submitting) onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 36.dp)
                .fillMaxWidth()
                .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                .clickable { }
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Text("退出登录?", color = AppTheme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "退出后需要重新登录才能继续,本机的记录会保留。确定要退出吗?",
                color = AppTheme.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row {
                Box(
                    modifier = Modifier.weight(1f)
                        .height(42.dp)
                        .background(Color(0xFFF3F4F8), RoundedCornerShape(21.dp))
                        .clickable { if (!submitting) onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消", color = AppTheme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier.weight(1f)
                        .height(42.dp)
                        .background(AppTheme.Danger, RoundedCornerShape(21.dp))
                        .clickable { if (!submitting) onConfirm() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (submitting) "退出中…" else "确认退出",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * ThemePickerCard — 主题外观:5 个主题圆点,点击即时切换并持久化
 */
@Composable
private fun ThemePickerCard(pager: Pager) {
    val theme = ThemeStore.current
    Column(
        modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)
            .fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("主题外观", color = AppTheme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Text("· ${theme.name}", color = AppTheme.TextSecondary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ThemeStore.all.forEach { t ->
                val selected = t.id == theme.id
                // 选中环:嵌套视图画环 + 背景常驻未选中切透明
                // (环/填充各占一个原生视图,同链双 background 互相覆盖;
                //  条件增删背景节点会残留上一次的方色块——原生属性无 detach 清理)
                Box(
                    modifier = Modifier.size(32.dp)
                        .background(
                            if (selected) t.brandStart else Color.Transparent,
                            RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(if (selected) 28.dp else 26.dp)
                            .background(
                                if (selected) AppTheme.CardBg else Color.Transparent,
                                RoundedCornerShape(14.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier.size(if (selected) 22.dp else 26.dp)
                                .background(t.brandBrush, RoundedCornerShape(if (selected) 11.dp else 13.dp))
                                .clickable {
                                    ThemeStore.switch(t)
                                    ThemeStore.persist(pager)
                                },
                        )
                    }
                }
            }
        }
    }
}
