package com.zyc.love_baby.features.couple

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppMenuGroup
import com.zyc.love_baby.components.AppSectionTitle
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.components.statusBarPaddingDp
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.state.CoupleBindState
import com.zyc.love_baby.state.CoupleStore
import kotlinx.coroutines.launch

/**
 * BindingPanel — 绑定域 UI(INTEGRATION-DESIGN §7.3 三态状态机的 couple 侧)
 * 用法:
 *  - MainPager:已登录且 CoupleStore.bindState != BOUND(未知校准态/未绑定)时整树条件渲染本面板,
 *    后组合画在上层盖住五 Tab(与 Gate 同款手法,不动常驻堆叠);绑定成功翻 CoupleStore.bindState 整树卸载
 *  - MinePage:已绑定时组合 BoundPartnerCard(伴侣信息 + 解绑入口;解绑二次确认浮层 BindUnbindConfirm
 *    由 MinePage 顶层持有,避免浮层陷入 LazyColumn 滚动项)
 * 依赖:CoupleStore(三态/邀请码/伴侣信息);BindWidgets 各小件;components 通用库
 * 事件:生成/复制/绑定/解绑走 CoupleStore 与宿主 BridgeModule;绑定/解绑成功由状态翻转驱动层挂卸
 * 功能:
 *  - UNKNOWN:校准中卡片(失败给「重试」,CoupleStore.refreshFailed);UNBOUND:双模式引导
 *    (我的邀请码:一键生成/复制,过期可重新生成;输码绑定:8 位校验 + 提交防重)+ 底部绑定说明文案
 *  - BOUND:BoundPartnerCard(伴侣昵称/文字头像/绑定时间 + 解绑入口,解绑必须二次确认)
 *  - 头像一律文字首字头像:头像上传与远程图片展示接 infra 文件服务属后续里程碑(M2 不做)
 */
@Composable
internal fun BindingPanel(pager: Pager) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(ThemeStore.current.brandBrush)
            .clickable { },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(statusBarPaddingDp() + 40.dp))
            BindHeader()
            Spacer(modifier = Modifier.height(26.dp))
            // 三态条件渲染(禁 Crossfade/AnimatedVisibility,铁律④;子视图整体销毁无残留)
            when (CoupleStore.bindState) {
                CoupleBindState.UNKNOWN -> CheckingBody(pager)
                CoupleBindState.UNBOUND -> GuideBody(pager)
                // BOUND 时本层已被 MainPager 卸载(§7.3「已绑定 → 主功能」);防御性空分支
                CoupleBindState.BOUND -> {}
            }
            Spacer(modifier = Modifier.height(24.dp + navBarSafePaddingDp()))
        }
    }
}

@Composable
private fun BindHeader() {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = AppTheme.PageHPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 半透明白底圆 + 心形白 icon(与 GateHeader 同款画法)
        Box(
            modifier = Modifier.size(64.dp)
                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconType.Heart, Color.White, Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "绑定另一半",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "一张邀请码,把两颗心连在一起",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
        )
    }
}

/** UNKNOWN:校准中卡片;失败(refreshFailed)给「重试」入口,成功由状态翻转自动切换 */
@Composable
private fun CheckingBody(pager: Pager) {
    val scope = rememberCoroutineScope()
    BindCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (CoupleStore.refreshFailed) "网络开小差了,没确认到绑定状态" else "正在确认你们的绑定状态…",
                color = AppTheme.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("确认后才能进入你们的空间", color = AppTheme.TextSecondary, fontSize = 12.sp)
            if (CoupleStore.refreshFailed) {
                Spacer(modifier = Modifier.height(16.dp))
                BindPrimaryButton(text = "重 试", submitting = false) {
                    scope.launch { CoupleStore.refresh(pager) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** UNBOUND:双模式引导(我的邀请码 / 输码绑定)+ 底部绑定说明文案(§7.3) */
@Composable
private fun GuideBody(pager: Pager) {
    var mode by remember { mutableIntStateOf(0) } // 0 我的邀请码 / 1 输码绑定
    BindCard {
        BindModeChips(
            firstText = "我的邀请码",
            secondText = "输码绑定",
            firstSelected = mode == 0,
            onSelectFirst = { mode = 0 },
            onSelectSecond = { mode = 1 },
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (mode == 0) {
            InviteCodePane(pager)
        } else {
            BindByCodePane(pager)
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        "绑定后你们将共享待办清单、悄悄话与心情日历\n邀请码 7 天内有效,过期后可重新生成",
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = AppTheme.PageHPadding + 8.dp),
    )
}

/** 我的邀请码模式:未生成/已过期给生成按钮;有效则展示 + 复制(§7.3「邀请码展示/一键生成」) */
@Composable
private fun InviteCodePane(pager: Pager) {
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    val invite = CoupleStore.invite
    if (invite == null || CoupleStore.isInviteExpired) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("生成你的专属邀请码,发给 TA 完成配对", color = AppTheme.TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
            BindPrimaryButton(
                text = if (invite == null) "生成我的邀请码" else "邀请码已过期,重新生成",
                submitting = creating,
            ) {
                creating = true
                scope.launch {
                    val result = CoupleStore.createInvite(pager)
                    creating = false
                    if (result is ApiResult.Ok) {
                        toastCouple(pager, "已生成,7 天内有效")
                    } else {
                        toastCoupleResult(pager, result)
                    }
                }
            }
        }
    } else {
        // remember 计算展示文案(格式化走宿主 dateFormatter;remember 内不可组合调用)
        val expireText = remember(invite.expireTime) {
            "有效期至 ${formatBindTime(pager, invite.expireTime)}"
        }
        BindInviteCodeCard(
            code = invite.inviteCode,
            expireText = expireText,
            onCopy = {
                copyInviteCode(pager, invite.inviteCode) {
                    toastCouple(pager, "已复制,去发给 TA 吧")
                }
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "TA 在「输码绑定」中填入这串号码,你们就绑定成功啦",
            color = AppTheme.TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 输码绑定模式:8 位校验(§7.3)+ 提交防重;成功翻 BOUND 引导层自动卸载 */
@Composable
private fun BindByCodePane(pager: Pager) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    fun submit() {
        if (submitting) return
        val trimmed = code.trim()
        if (trimmed.length != INVITE_CODE_LENGTH) {
            toastCouple(pager, "请输入 $INVITE_CODE_LENGTH 位邀请码")
            return
        }
        submitting = true
        scope.launch {
            val result = CoupleStore.bind(pager, trimmed)
            submitting = false
            if (result is ApiResult.Ok) {
                toastCouple(pager, "绑定成功,开始你们的甜蜜之旅吧")
            } else {
                toastCoupleResult(pager, result)
            }
        }
    }

    BindInputField(
        value = code,
        onValueChange = { code = it.take(INVITE_CODE_LENGTH) },
        placeholder = "输入 TA 的 $INVITE_CODE_LENGTH 位邀请码",
    )
    Spacer(modifier = Modifier.height(16.dp))
    BindPrimaryButton(text = "绑 定", submitting = submitting) { submit() }
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        "邀请码由对方在其「我的邀请码」中生成",
        color = AppTheme.TextTertiary,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * BoundPartnerCard — 已绑定态卡(§7.3 三态 UI 的 BOUND 面):伴侣昵称/文字头像/绑定时间 + 解绑入口。
 * 供 MinePage 组合(§7.3「已绑定 → 主功能」,引导层已卸载);解绑浮层由 MinePage 顶层持有
 */
@Composable
internal fun BoundPartnerCard(pager: Pager, onUnbindClick: () -> Unit) {
    val couple = CoupleStore.couple ?: return
    val partnerName = couple.partnerNickname.trim().ifEmpty { "TA" }
    // 绑定时间格式化走宿主 dateFormatter,remember 缓存防重组重复跨桥
    val bindTimeText = remember(couple.bindTime) { formatBindTime(pager, couple.bindTime) }
    Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
        AppSectionTitle("情侣空间")
        Spacer(modifier = Modifier.height(10.dp))
        AppMenuGroup {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 双层环文字头像(品牌渐变环 + 白底首字);头像图展示/上传接文件服务属后续里程碑
                Box(
                    modifier = Modifier.size(46.dp)
                        .background(ThemeStore.current.brandBrush, RoundedCornerShape(23.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(40.dp)
                            .background(Color.White, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            partnerName.take(1),
                            color = ThemeStore.current.brandStart,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(partnerName, color = AppTheme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("绑定于 $bindTimeText", color = AppTheme.TextSecondary, fontSize = 12.sp)
                }
                // 解绑入口:background(常驻)与 clickable 同节点(铁律⑤),确认浮层由调用方拉起
                Box(
                    modifier = Modifier.background(Color(0xFFFFEDEF), RoundedCornerShape(14.dp))
                        .clickable(onClick = onUnbindClick)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("解绑", color = AppTheme.Danger, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
