package com.zyc.love_baby.features.auth

import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.setTimeout
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.components.statusBarPaddingDp
import com.zyc.love_baby.state.SessionStore

/**
 * GatePanel — 登录门:未登录/会话不可恢复时整屏盖住五 Tab(INTEGRATION-DESIGN §7.3)
 * 用法:MainPageImpl 根容器末尾 if (!SessionStore.isLoggedIn) GatePanel(pager)
 *      (后组合画在上层;登录/注册成功翻 SessionStore.isLoggedIn → 本树整棵卸载,预期行为)
 * 依赖:SessionStore(会话恢复兜底);ThemeStore.current.brandBrush(懒缓存渐变,铁律⑧);
 *      Login/Register/Reset 三面板(纯条件渲染,禁 Crossfade/AnimatedVisibility——铁律④)
 * 事件:面板切换走局部 panel 状态;登录成功在面板内落 SessionStore,本组件不额外回调
 * 功能:
 *  - 冷启动会话判定前置在 MainPager.willInit 的 SessionStore.initFromPageData(§7.6①,ThemeStore
 *    同款先例):有有效会话时本组件根本不进入组合;本组件只保留 prefs 兜底 restore
 *    (LaunchedEffect,无参数端 iOS/鸿蒙/H5 晚一帧,§10 m-2 可接受)
 *  - 全屏拦截层:brandBrush 背景 + clickable 同节点(铁律⑤)吞掉整屏点击,
 *    盖住底下仍组合着的五 Tab(§7.3:Tab 内容区由 Gate 全屏覆盖,不改各页内部逻辑)
 *  - 顶部 statusBarPaddingDp()、底部按钮避让 + navBarSafePaddingDp()(铁律⑨)
 */
internal enum class AuthPanel { LOGIN, REGISTER, RESET }

@Composable
internal fun GatePanel(pager: Pager) {
    // 无参数端(iOS/鸿蒙/H5)SharedPreferences 兜底(ThemeStore.restore 同款可空 getModule 降级):
    // 组合后执行,晚一帧可接受;已登录时跳过,避免覆盖刚登录的内存会话
    // (pageData 首帧判定在 MainPager.willInit,不在此处组合期写状态)
    LaunchedEffect(pager) {
        if (!SessionStore.isLoggedIn) SessionStore.restore(pager)
    }

    var panel by remember { mutableStateOf(AuthPanel.LOGIN) }
    // 面板切换即清焦(同 LoginPanel.submit 焦点转移问题):旧面板持焦输入框随切换被移除,
    // Android 把焦点转给待办页输入框,Pager 在 Gate 底下滚走待办页——登录成功卸载 Gate 时
    // 便直接亮在错误 Tab。切换统一走 switchPanel:清焦后目标面板迟一拍再上(机制详见
    // ProfileEditOverlay.dismiss)——旧写法清焦与状态翻转同回合,旧面板卸载与 blur 竞速,
    // 只是赌 blur 赢(2026-10-18 ProfileEditOverlay 真机复现证明会输);推迟一拍入同一
    // MessageQueue 后落序钉死,blur 必然先行
    val focusManager = LocalFocusManager.current
    fun switchPanel(target: AuthPanel) {
        focusManager.clearFocus()
        pager.setTimeout(0) { panel = target }
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(ThemeStore.current.brandBrush)
            .clickable { },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(statusBarPaddingDp() + 40.dp))
            GateHeader()
            Spacer(modifier = Modifier.height(26.dp))
            // 三面板纯条件渲染切换(子视图整体销毁,无残留;铁律④禁 Crossfade)
            when (panel) {
                AuthPanel.LOGIN -> LoginPanel(
                    pager = pager,
                    onGoRegister = { switchPanel(AuthPanel.REGISTER) },
                    onGoReset = { switchPanel(AuthPanel.RESET) },
                )
                AuthPanel.REGISTER -> RegisterPanel(
                    pager = pager,
                    onBackToLogin = { switchPanel(AuthPanel.LOGIN) },
                )
                AuthPanel.RESET -> ResetPanel(
                    pager = pager,
                    onBackToLogin = { switchPanel(AuthPanel.LOGIN) },
                )
            }
            Spacer(modifier = Modifier.height(24.dp + navBarSafePaddingDp()))
        }
    }
}

@Composable
private fun GateHeader() {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = AppTheme.PageHPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 半透明白底圆 + 心形白 icon(与 MinePage 头像同款画法)
        Box(
            modifier = Modifier.size(64.dp)
                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconType.Heart, Color.White, Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "宝贝 & 我",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "登录后开启你们的小世界",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
        )
    }
}
