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
import com.zyc.love_baby.data.model.AuthSession
import com.zyc.love_baby.network.ApiConfig
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.AuthApi
import com.zyc.love_baby.state.SessionStore
import kotlinx.coroutines.launch

/**
 * RegisterPanel — 注册面板:手机号 + 验证码 + 密码(密码注册,短信码校验 §5.2)
 * 用法:GatePanel 内组合(「去注册」进入);成功落 SessionStore 回主功能,响应无会话字段则退回登录面板
 * 依赖:AuthWidgets 各小件;AuthApi.register/sendSmsCode;SessionStore
 * 事件:onBackToLogin 回登录面板;注册成功翻 SessionStore.isLoggedIn → Gate 整树卸载
 * 功能:
 *  - 服务端注册成功若直接返回登录契约五字段(与 yudao 行为一致)则自动登录;
 *    否则 toast 后回登录面板(响应形态两种都兼容,§5.2 未约定注册回包)
 *  - 校验失败/接口失败 toast(手机号已注册 1_003_005_000 直接展示服务端 msg);submitting 期间拦重复提交
 */
@Composable
internal fun RegisterPanel(
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
            val result = AuthApi.register(pager, mobile.trim(), password, code.trim())
            submitting = false
            if (result is ApiResult.Ok) {
                val session = AuthSession.from(result.data, fallbackClientId = ApiConfig.CLIENT_ID)
                if (session != null) {
                    // 注册回包即登录态:直接进主功能
                    SessionStore.update(pager, session)
                } else {
                    // 回包无会话字段:退回登录面板
                    toastAuth(pager, "注册成功,请登录")
                    onBackToLogin()
                }
            } else {
                toastAuthResult(pager, result)
            }
        }
    }

    AuthCard {
        Column {
            Text(
                "创建账号",
                color = AppTheme.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "只为你们两个人",
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
            placeholder = "设置密码(至少 $PASSWORD_MIN_LENGTH 位)",
            keyboardType = KeyboardType.Password,
        )
        Spacer(modifier = Modifier.height(18.dp))
        AuthPrimaryButton(
            text = "注 册",
            submitting = submitting,
            onClick = { submit() },
        )
    }
    AuthFooterLinks(
        links = listOf("已有账号?去登录" to onBackToLogin),
    )
}
