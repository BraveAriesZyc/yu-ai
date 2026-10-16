package com.zyc.love_baby.features.auth

import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.ColumnScope
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.KeyboardOptions
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.input.KeyboardType
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.Button
import com.zyc.love_baby.TextField
import com.zyc.love_baby.base.Utils
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
 * 依赖:BasicWidget 同包 TextField/Button(placeholder 为 String,autoFocus 默认 true——
 *      本页一律显式传 false,避免 Gate 一出现就弹键盘);ThemeStore 品牌渐变;Utils.bridgeModule toast
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

/** toast 收口:Utils.bridgeModule(pagerId).toast 先例;模块异常吞掉不影响页面 */
internal fun toastAuth(pager: Pager, message: String) {
    try {
        Utils.bridgeModule(pager.pagerId).toast(message)
    } catch (_: Exception) {
    }
}

/** ApiResult → 面板 toast(§5.1 对照表:业务错误 toast msg;网络异常 toast;不崩) */
internal fun toastAuthResult(pager: Pager, result: ApiResult) {
    when (result) {
        is ApiResult.Ok -> {}
        is ApiResult.BizError -> toastAuth(pager, result.msg.ifEmpty { "操作失败,请稍后再试" })
        ApiResult.Unauthorized -> toastAuth(pager, "登录已失效,请重新登录")
        ApiResult.NetworkError -> toastAuth(pager, "网络异常,请稍后再试")
    }
}

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
 * 输入行:白底圆角行内嵌同包 TextField;可带尾部小件(如「获取验证码」)。
 * autoFocus 一律 false:Gate 出现即自动弹键盘会顶起整屏(§7.3 反馈走 toast,不依赖自动聚焦)
 */
@Composable
internal fun AuthFieldCard(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            modifier = Modifier.weight(1f).height(48.dp),
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            placeholderColor = AppTheme.TextTertiary,
            autoFocus = false,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(color = AppTheme.TextPrimary, fontSize = 15.sp),
        )
        trailing?.invoke()
    }
}

/**
 * 渐变主按钮:BasicWidget.Button(pointerInput 与 background 同节点,命中可靠);
 * submitting 期间只拦点击不改背景(条件换 brush 会在原生层残留旧值,不做视觉禁用)
 */
@Composable
internal fun AuthPrimaryButton(
    text: String,
    submitting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.fillMaxWidth()
            .height(48.dp)
            .background(ThemeStore.current.brandBrush, RoundedCornerShape(24.dp)),
        onClick = { if (!submitting) onClick() },
    ) {
        Text(
            if (submitting) "请稍候…" else text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
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

/**
 * 双模式切换胶囊(密码登录/验证码登录):背景常驻、未选中切透明
 * (铁律:选中态禁用条件增删背景节点,原生属性无 detach 清理会残留)
 */
@Composable
internal fun AuthModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                if (selected) AppTheme.CardBg else Color.Transparent,
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) ThemeStore.current.brandStart else Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** 模式胶囊组:半透明白底容器装两枚 chip */
@Composable
internal fun AuthModeChips(
    firstText: String,
    secondText: String,
    firstSelected: Boolean,
    onSelectFirst: () -> Unit,
    onSelectSecond: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        AuthModeChip(firstText, firstSelected, onSelectFirst, modifier = Modifier.weight(1f))
        AuthModeChip(secondText, !firstSelected, onSelectSecond, modifier = Modifier.weight(1f))
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
