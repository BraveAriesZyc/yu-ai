package com.zyc.love_baby.features.couple

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.ColumnScope
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.KeyboardOptions
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.input.KeyboardType
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.views.KeyboardParams
import com.zyc.love_baby.Button
import com.zyc.love_baby.TextField
import com.zyc.love_baby.base.BridgeModule
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.network.ApiResult

/**
 * BindWidgets — 绑定域(引导层 BindingPanel / 情侣空间卡 BoundPartnerCard)共用小件与反馈收口
 * 用法:BindCard(白卡容器)/ BindModeChips(双模式胶囊)/ BindInputField(输入行)/
 *      BindPrimaryButton(渐变主钮)/ BindInviteCodeCard(邀请码展示+复制)/ BindUnbindConfirm(解绑二次确认浮层);
 *      toastCouple / toastCoupleResult(反馈收口)/ copyInviteCode / formatBindTime
 * 依赖:BasicWidget 同包 TextField/Button(placeholder 为 String,autoFocus 一律显式 false,防弹窗即弹键盘);
 *      ThemeStore.current.brandBrush(懒缓存渐变,铁律⑧);BridgeModule(toast/copyToPasteboard/dateFormatter,
 *      一律可空 getModule 降级,禁 acquireModule 双抛——铁律⑦)
 * 事件:各组件回调透传;BindUnbindConfirm 的 onConfirm 由调用方发 CoupleStore.unbind
 * 功能:
 *  - 命中铁律:可点击小件一律 background(常驻,含透明)与 clickable 同节点(⑤);选中态胶囊背景常驻切透明(③);
 *    不用 Crossfade/AnimatedVisibility(④);渐变一律 ThemeStore 懒缓存 Brush 不新建(⑧)
 *  - toast 收口:业务错误 toast 服务端 msg,网络异常 toast 不崩,登录失效提示(§5.1 对照表)
 */

internal const val INVITE_CODE_LENGTH = 8

/** 昵称上限(服务端 love_user.nickname varchar(32)) */
internal const val NICKNAME_MAX_LENGTH = 32

// ---------- 反馈收口(AuthWidgets 同款语义,couple 域本地化命名保持 features 解耦) ----------

internal fun toastCouple(pager: Pager, message: String) {
    try {
        pager.getModule<BridgeModule>(BridgeModule.MODULE_NAME)?.toast(message)
    } catch (_: Exception) {
    }
}

/** ApiResult → toast(§5.1 对照表:业务错误 toast msg;网络异常 toast;不崩) */
internal fun toastCoupleResult(pager: Pager, result: ApiResult) {
    when (result) {
        is ApiResult.Ok -> {}
        is ApiResult.BizError -> toastCouple(pager, result.msg.ifEmpty { "操作失败,请稍后再试" })
        ApiResult.Unauthorized -> toastCouple(pager, "登录已失效,请重新登录")
        ApiResult.NetworkError -> toastCouple(pager, "网络异常,请稍后再试")
    }
}

/** 复制文本到剪贴板(宿主 BridgeModule;可空 getModule 降级,失败静默不崩)。邀请码/客服微信号等共用 */
internal fun copyToPasteboard(pager: Pager, text: String, onCopied: () -> Unit) {
    try {
        pager.getModule<BridgeModule>(BridgeModule.MODULE_NAME)?.copyToPasteboard(text)
        onCopied()
    } catch (_: Exception) {
    }
}

/**
 * epoch 毫秒 → "yyyy-MM-dd"(宿主 dateFormatter,设备时区;可空 getModule 降级)。
 * 时间未回传/模块不可用时给占位文案
 */
internal fun formatBindTime(pager: Pager, epochMs: Long): String {
    if (epochMs <= 0) return "时间未知"
    return try {
        pager.getModule<BridgeModule>(BridgeModule.MODULE_NAME)
            ?.dateFormatter(epochMs, "yyyy-MM-dd")
            ?.takeIf { it.isNotEmpty() } ?: "时间未知"
    } catch (_: Exception) {
        "时间未知"
    }
}

// ---------- 小件 ----------

/** 白卡容器:引导层/表单统一外壳(横向外边距收敛在此,调用方不用再套 padding) */
@Composable
internal fun BindCard(content: @Composable ColumnScope.() -> Unit) {
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
 * 双模式切换胶囊(我的邀请码/输码绑定):白卡内使用灰底容器,
 * 选中 chip 白底常驻 + 未选中切透明(铁律③:禁条件增删背景节点,原生属性无 detach 清理会残留)
 */
@Composable
internal fun BindModeChips(
    firstText: String,
    secondText: String,
    firstSelected: Boolean,
    onSelectFirst: () -> Unit,
    onSelectSecond: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFFF3F4F8), RoundedCornerShape(18.dp))
            .padding(3.dp),
    ) {
        BindModeChip(firstText, firstSelected, onSelectFirst, modifier = Modifier.weight(1f))
        BindModeChip(secondText, !firstSelected, onSelectSecond, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BindModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                if (selected) AppTheme.CardBg else Color.Transparent,
                RoundedCornerShape(15.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) ThemeStore.current.brandStart else AppTheme.TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * 输入行:浅灰底圆角行内嵌同包 TextField(与白卡区分);autoFocus 一律 false,防面板一出现就弹键盘。
 * 固定高 48dp 挂在行容器上、TextField 自适应内容行高由 CenterVertically 居中——原生 EditText/TextArea
 * gravity 恒 TOP|START(KRTextAreaView 硬编码 setGravity(51),textAlign 属性只改水平位),固定高度
 * 直接压在 TextField 上会把文字/placeholder 顶到上缘(全仓输入框统一此「容器定高 + 内容居中」模式)
 */
@Composable
internal fun BindInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    keyboardHeightChange: (KeyboardParams) -> Unit = {}, // 透传键盘高度回调(默认空,存量调用方零改动)
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFFF5F6FA), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            placeholderColor = AppTheme.TextTertiary,
            autoFocus = false,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(color = AppTheme.TextPrimary, fontSize = 15.sp),
            keyboardHeightChange = keyboardHeightChange,
        )
    }
}

/**
 * 渐变主按钮:BasicWidget.Button(pointerInput 与 background 同节点,命中可靠);
 * submitting 期间只拦点击与换文案,不改背景(条件换 brush 会在原生层残留旧值,AuthPrimaryButton 同款)。
 * onClick 放最后一位:调用方可用尾随 lambda(modifier 带默认值须在函数参数前)
 */
@Composable
internal fun BindPrimaryButton(
    text: String,
    submitting: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
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

/** 邀请码展示卡:大号间隔字母 + 有效期 + 品牌渐变复制钮(复制经宿主剪贴板) */
@Composable
internal fun BindInviteCodeCard(
    code: String,
    expireText: String,
    onCopy: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("我的邀请码", color = AppTheme.TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            code.toCharArray().joinToString(" "),
            color = AppTheme.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(expireText, color = AppTheme.TextTertiary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier.background(ThemeStore.current.brandBrush, RoundedCornerShape(16.dp))
                .clickable(onClick = onCopy)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            Text("复制邀请码", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 解绑二次确认浮层(全屏灰幕 + 居中白卡):灰幕点击取消,卡片 background+clickable 同节点吞点击防透传(铁律⑤);
 * submitting 期间所有出口拦点击,防重复提交/误关
 */
@Composable
internal fun BindUnbindConfirm(
    partnerName: String,
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
            Text("解除绑定?", color = AppTheme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "解绑后你和 $partnerName 都会回到未绑定状态,历史记录会保留。确定要解绑吗?",
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
                        if (submitting) "解绑中…" else "确认解绑",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
