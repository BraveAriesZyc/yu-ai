package com.zyc.love_baby.features.auth

import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.ColumnScope
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.input.KeyboardType
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.components.AppFeedback
import com.zyc.love_baby.components.AppInputField
import com.zyc.love_baby.components.AppPrimaryButton
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.data.model.AuthSession
import com.zyc.love_baby.network.ApiConfig
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.AuthApi
import com.zyc.love_baby.state.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AuthWidgets — Gate 三面板(登录/注册/重置)共用小件与校验
 * 用法:AuthFieldCard(输入行) / AuthPrimaryButton(渐变主按钮) / AuthLinkButton(白字链);
 *      isValidMobile / toastAuthResult(错误转 toast);applyAuthSession(登录响应落 SessionStore)
 * 依赖:BasicWidget 同包 TextField(placeholder 为 String,autoFocus 默认 true——
 *      本页一律显式传 false,避免 Gate 一出现就弹键盘);ThemeStore 品牌渐变;toast 系本体
 *      在 components/AppFeedback(toastAuth/toastAuthResult 薄壳委托,通道可空 getModule 降级)
 * 事件:各组件回调透传;applyAuthSession 翻 SessionStore.isLoggedIn → Gate 整树卸载
 * 功能:
 *  - 表单校验规则(§7.3):手机号 11 位数字 / 密码长度 >= 6 / 验证码非空
 *  - 命中铁律:可点击小件一律 background(常驻,含透明)与 clickable 同节点
 *  - 业务错误 → toast 服务端 msg;网络异常 → toast 不崩(§5.1 对照表)
 */

internal const val PASSWORD_MIN_LENGTH = 6
internal const val SMS_CODE_COUNTDOWN_SECONDS = 60

/** 手机号 11 位数字(§7.3 校验规则) */
internal fun isValidMobile(mobile: String): Boolean =
    mobile.length == 11 && mobile.all { it.isDigit() }

/** toast 收口:薄壳委托 components/AppFeedback.toast(可空 getModule 降级;原 Utils.bridgeModule
 *  即 acquireModule 路径有「异步炸弹」隐患,并入后统一消掉,语义不变) */
internal fun toastAuth(pager: Pager, message: String) = AppFeedback.toast(pager, message)

/** ApiResult → 面板 toast(§5.1 对照表:业务错误 toast msg;网络异常 toast;不崩) */
internal fun toastAuthResult(pager: Pager, result: ApiResult) = AppFeedback.toastResult(pager, result)

/**
 * 登录/注册成功统一收口:响应 data 落成 SessionStore(整树 Gate 随之卸载)。
 * data 缺 accessToken 判响应异常,提示后留在 Gate
 */
internal fun applyAuthSession(pager: Pager, data: JSONObject) {
    val session = AuthSession.from(data, fallbackClientId = ApiConfig.CLIENT_ID)
    if (session == null) {
        toastAuth(pager, "登录响应异常,请稍后再试")
    } else {
        SessionStore.update(pager, session)
    }
}

/** 验证码倒计时心跳:每秒 -1,到 0 自停(各面板自带 countdown 状态) */
@Composable
internal fun AuthCountdownEffect(countdown: Int, onTick: () -> Unit) {
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            onTick()
        }
    }
}

// ---------- 表单校验(§7.3:手机号 11 位 / 密码长度 / 验证码非空;失败 toast) ----------

internal fun validateMobileOrToast(mobile: String, pager: Pager): Boolean =
    if (isValidMobile(mobile)) {
        true
    } else {
        toastAuth(pager, "请输入 11 位手机号")
        false
    }

internal fun validatePasswordOrToast(password: String, pager: Pager): Boolean =
    if (password.length >= PASSWORD_MIN_LENGTH) {
        true
    } else {
        toastAuth(pager, "密码至少 $PASSWORD_MIN_LENGTH 位")
        false
    }

internal fun validateCodeOrToast(code: String, pager: Pager): Boolean =
    if (code.isNotBlank()) {
        true
    } else {
        toastAuth(pager, "请输入验证码")
        false
    }

/** 发验证码统一路径:校验手机号 → sendSmsCode → 成功 toast + 启动倒计时;失败 toast */
internal fun launchSendSmsCode(
    scope: CoroutineScope,
    pager: Pager,
    mobile: String,
    onSent: () -> Unit,
) {
    if (!validateMobileOrToast(mobile, pager)) return
    scope.launch {
        val result = AuthApi.sendSmsCode(pager, mobile.trim())
        if (result is ApiResult.Ok) {
            toastAuth(pager, "验证码已发送")
            onSent()
        } else {
            toastAuthResult(pager, result)
        }
    }
}

/** 白卡容器:三面板的输入区统一外壳 */
@Composable
internal fun AuthCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = AppTheme.PageHPadding)
            .background(AppTheme.CardBg, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        content()
    }
}

/**
 * 输入行:白底圆角行内嵌输入行;薄壳委托 components/AppInputField(「容器定高 48dp +
 * 内容居中」铁律与原生 gravity workaround 注释随本体单源化;autoFocus 一律 false——
 * Gate 出现即自动弹键盘会顶起整屏,§7.3 反馈走 toast 不依赖自动聚焦,该约定由本体保证)。
 * containerColor 对齐原白卡视觉:AppInputField 默认浅灰底(0xFFF5F6FA,BindInputField 形态),
 * 本件覆写 CardBg(白)——Gate 品牌渐变上的白卡(AuthCard)内输入行沿用原白底
 */
@Composable
internal fun AuthFieldCard(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLines: Int = Int.MAX_VALUE, // 源 TextField 缺省可折行,薄壳保真透传(勿排 trailing 后,尾随 lambda 绑末参)
    trailing: (@Composable () -> Unit)? = null,
) {
    AppInputField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        keyboardType = keyboardType,
        containerColor = AppTheme.CardBg,
        trailing = trailing,
        maxLines = maxLines,
    )
}

/**
 * 渐变主按钮:薄壳委托 components/AppPrimaryButton(submitting 期间只拦点击不改背景的
 * 约定随本体单源化);onClick 收尾参数保持,调用方尾随 lambda 零改动
 */
@Composable
internal fun AuthPrimaryButton(
    text: String,
    submitting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPrimaryButton(
        text = text,
        submitting = submitting,
        onClick = onClick,
        modifier = modifier,
    )
}

/** 白字链(注册/忘记密码):背景常驻透明与 clickable 同节点(铁律⑤) */
@Composable
internal fun AuthLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

/** 「获取验证码」小钮:倒计时中只显示秒数并拦点击(onClick 收尾参数,支持尾随 lambda) */
@Composable
internal fun AuthSendCodeButton(
    countdown: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cooling = countdown > 0
    Box(
        modifier = modifier
            .background(Color.Transparent, RoundedCornerShape(14.dp))
            .clickable { if (!cooling) onClick() }
            .padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Text(
            if (cooling) "${countdown}s" else "获取验证码",
            color = if (cooling) AppTheme.TextTertiary else ThemeStore.current.brandStart,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 面板底部整行链接(注册/忘记密码):居中排布 */
@Composable
internal fun AuthFooterLinks(
    links: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = AppTheme.PageHPadding)
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        links.forEachIndexed { index, (text, onClick) ->
            if (index > 0) Spacer(modifier = Modifier.width(4.dp))
            AuthLinkButton(text, onClick)
        }
    }
}
