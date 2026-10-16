package com.zyc.love_baby.features.auth

import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.input.KeyboardType
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.AuthApi
import kotlinx.coroutines.launch

/**
 * LoginPanel — 登录面板:密码登录 / 验证码登录双模式(条件渲染切换,INTEGRATION-DESIGN §7.3)
 * 用法:GatePanel 内组合;登录成功 applyAuthSession 落 SessionStore → Gate 整树卸载
 * 依赖:AuthWidgets 各小件;AuthApi.login/smsLogin/sendSmsCode;SessionStore
 * 事件:提交成功翻 SessionStore.isLoggedIn;面板切换经 onGoRegister/onGoReset 回调 Gate
 * 功能:
 *  - 验证码模式走 sms-login(未注册自动注册,首期主路径 §5.2),按钮文案「登录 / 注册」明示
 *  - 校验失败/接口失败 toast;submitting 期间拦重复提交
 */
@Composable
internal fun LoginPanel(
    pager: Pager,
    onGoRegister: () -> Unit,
    onGoReset: () -> Unit,
) {
    var smsMode by remember { mutableStateOf(false) }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    AuthCountdownEffect(countdown) { countdown -= 1 }

    fun submit() {
        if (submitting) return
        if (!validateMobileOrToast(mobile, pager)) return
        if (smsMode) {
            if (!validateCodeOrToast(code, pager)) return
        } else {
            if (!validatePasswordOrToast(password, pager)) return
        }
        submitting = true
        scope.launch {
            val result = if (smsMode) {
                AuthApi.smsLogin(pager, mobile.trim(), code.trim())
            } else {
                AuthApi.login(pager, mobile.trim(), password)
            }
            submitting = false
            if (result is ApiResult.Ok) {
                applyAuthSession(pager, result.data)
            } else {
                toastAuthResult(pager, result)
            }
        }
    }

    AuthCard {
        AuthModeChips(
            firstText = "密码登录",
            secondText = "验证码登录",
            firstSelected = !smsMode,
            onSelectFirst = { smsMode = false },
            onSelectSecond = { smsMode = true },
        )
        Spacer(modifier = Modifier.height(14.dp))
        AuthFieldCard(
            value = mobile,
            onValueChange = { mobile = it },
            placeholder = "手机号",
            keyboardType = KeyboardType.Number,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (smsMode) {
            AuthFieldCard(
                value = code,
                onValueChange = { code = it },
                placeholder = "验证码",
                keyboardType = KeyboardType.Number,
            ) {
                AuthSendCodeButton(countdown) {
                    launchSendSmsCode(scope, pager, mobile) { countdown = SMS_CODE_COUNTDOWN_SECONDS }
                }
            }
        } else {
            AuthFieldCard(
                value = password,
                onValueChange = { password = it },
                placeholder = "密码",
                keyboardType = KeyboardType.Password,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        AuthPrimaryButton(
            text = if (smsMode) "登录 / 注册" else "登 录",
            submitting = submitting,
            onClick = { submit() },
        )
    }
    AuthFooterLinks(
        links = listOf(
            "没有账号?去注册" to onGoRegister,
            "忘记密码?" to onGoReset,
        ),
    )
}
