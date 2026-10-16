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
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.input.KeyboardType
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.AuthApi
import kotlinx.coroutines.launch

/**
 * ResetPanel — 重置密码面板:手机号 + 验证码 + 新密码(复用 SmsCodeApi scene=1 §5.2)
 * 用法:GatePanel 内组合(「忘记密码?」进入);成功 toast 后退回登录面板用新密码登录
 * 依赖:AuthWidgets 各小件;AuthApi.resetPassword/sendSmsCode
 * 事件:onBackToLogin 回登录面板;重置不产生登录态(不动 SessionStore)
 * 功能:校验失败/接口失败 toast;submitting 期间拦重复提交
 */
@Composable
internal fun ResetPanel(
    pager: Pager,
    onBackToLogin: () -> Unit,
) {
    var mobile by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    AuthCountdownEffect(countdown) { countdown -= 1 }

    fun submit() {
        if (submitting) return
        if (!validateMobileOrToast(mobile, pager)) return
        if (!validateCodeOrToast(code, pager)) return
        if (!validatePasswordOrToast(password, pager)) return
        submitting = true
        scope.launch {
            val result = AuthApi.resetPassword(pager, mobile.trim(), code.trim(), password)
            submitting = false
            if (result is ApiResult.Ok) {
                toastAuth(pager, "密码已重置,请用新密码登录")
                onBackToLogin()
            } else {
                toastAuthResult(pager, result)
            }
        }
    }

    AuthCard {
        Column {
            Text(
                "重置密码",
                color = AppTheme.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "用手机验证码设置新密码",
                color = AppTheme.TextTertiary,
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        AuthFieldCard(
            value = mobile,
            onValueChange = { mobile = it },
            placeholder = "手机号",
            keyboardType = KeyboardType.Number,
        )
        Spacer(modifier = Modifier.height(12.dp))
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
        Spacer(modifier = Modifier.height(12.dp))
        AuthFieldCard(
            value = password,
            onValueChange = { password = it },
            placeholder = "新密码(至少 $PASSWORD_MIN_LENGTH 位)",
            keyboardType = KeyboardType.Password,
        )
        Spacer(modifier = Modifier.height(18.dp))
        AuthPrimaryButton(
            text = "重置密码",
            submitting = submitting,
            onClick = { submit() },
        )
    }
    AuthFooterLinks(
        links = listOf("返回登录" to onBackToLogin),
    )
}
