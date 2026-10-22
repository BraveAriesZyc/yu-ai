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
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.text.input.KeyboardType
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.components.AppSegmentedChips
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
    // 登录/绑定→主界面乱滚治理(2026-10-17 真机):提交即清焦。原生 blur 经 setTimeout 下一 tick
    // 落地,趁网络窗口先失焦——否则 Gate 卸载瞬间持焦输入框随面板被移除,Android 把窗口焦点转给
    // 下一个可聚焦视图(五页全驻留,待办页输入框在树中),Pager 原生 RecyclerView 收到子 View 获焦
    // 自动滚动露出该页 + SnapHelper 吸附 + compose/native 对齐来回纠偏 → 进主功能乱滚卡顿;
    // 卸载时视图树无持焦视图则转移无从发生。清焦顺带获得「点登录即收键盘」标准 UX
    val focusManager = LocalFocusManager.current

    AuthCountdownEffect(countdown) { countdown -= 1 }

    fun submit() {
        if (submitting) return
        if (!validateMobileOrToast(mobile, pager)) return
        if (smsMode) {
            if (!validateCodeOrToast(code, pager)) return
        } else {
            if (!validatePasswordOrToast(password, pager)) return
        }
        focusManager.clearFocus()
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
        // 分段选择胶囊(components/AppSegmentedChips 单源,白透底容器变体):chip 圆角由原
        // AuthModeChips 18dp 漂移统一到组件默认 15dp(视觉归一,与绑定域灰底变体同结构)
        AppSegmentedChips(
            options = listOf("密码登录", "验证码登录"),
            selectedIndex = if (smsMode) 1 else 0,
            onSelect = { smsMode = it == 1 },
            containerBg = Color.White.copy(alpha = 0.25f),
            unselectedTextColor = Color.White,
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
