package com.zyc.love_baby.features.couple

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.ColumnScope
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.input.KeyboardType
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.views.KeyboardParams
import com.zyc.love_baby.components.AppFeedback
import com.zyc.love_baby.components.AppInputField
import com.zyc.love_baby.components.AppPrimaryButton
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.network.ApiResult

/**
 * BindWidgets — 绑定域(引导层 BindingPanel / 情侣空间卡 BoundPartnerCard)共用小件与反馈收口
 * 用法:BindCard(白卡容器)/ BindInputField(输入行)/ BindPrimaryButton(渐变主钮)/
 *      BindInviteCodeCard(邀请码展示+复制);toastCouple / toastCoupleResult(反馈收口)/
 *      copyInviteCode / formatBindTime
 *      (双模式胶囊已收口 components/AppSegmentedChips、解绑二次确认浮层已收口
 *      components/AppConfirmOverlay,私版删除,调用方 BindingPanel/MainPager 直调通用件)
 * 依赖:输入行/主钮/toast 系已收口到 components(AppInputField/AppPrimaryButton/AppFeedback,
 *      本文件保留同名薄壳委托,既有调用方 import 零改动);ThemeStore.current.brandBrush
 *      (懒缓存渐变,铁律⑧)
 * 事件:各组件回调透传
 * 功能:
 *  - 命中铁律:可点击小件一律 background(常驻,含透明)与 clickable 同节点(⑤);渐变一律
 *    ThemeStore 懒缓存 Brush 不新建(⑧)
 *  - toast 收口:业务错误 toast 服务端 msg,网络异常 toast 不崩,登录失效提示(§5.1 对照表)
 *    ——映射本体在 AppFeedback.toastResult
 */

internal const val INVITE_CODE_LENGTH = 8

/** 昵称上限(服务端 love_user.nickname varchar(32)) */
internal const val NICKNAME_MAX_LENGTH = 32

// ---------- 反馈收口(AuthWidgets 同款语义,couple 域本地化命名保持 features 解耦;
// 本体在 components/AppFeedback,薄壳委托保持调用方 import 稳定) ----------

internal fun toastCouple(pager: Pager, message: String) = AppFeedback.toast(pager, message)

/** ApiResult → toast(§5.1 对照表:业务错误 toast msg;网络异常 toast;不崩) */
internal fun toastCoupleResult(pager: Pager, result: ApiResult) = AppFeedback.toastResult(pager, result)

/** 复制文本到剪贴板(宿主 BridgeModule;可空 getModule 降级,失败静默不崩)。邀请码/客服微信号等共用 */
internal fun copyToPasteboard(pager: Pager, text: String, onCopied: () -> Unit) =
    AppFeedback.copyToPasteboard(pager, text, onCopied)

/**
 * epoch 毫秒 → "yyyy-MM-dd"(宿主 dateFormatter,设备时区;可空 getModule 降级)。
 * 时间未回传/模块不可用时给占位文案
 */
internal fun formatBindTime(pager: Pager, epochMs: Long): String = AppFeedback.formatBindTime(pager, epochMs)

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
 * 输入行:浅灰底圆角行内嵌 TextField;薄壳委托 components/AppInputField(「容器定高 48dp +
 * 内容居中」铁律与原生 gravity workaround 注释随本体单源化);keyboardHeightChange 透传保留
 */
@Composable
internal fun BindInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    keyboardHeightChange: (KeyboardParams) -> Unit = {}, // 透传键盘高度回调(默认空,存量调用方零改动)
    maxLines: Int = Int.MAX_VALUE, // 源 TextField 缺省可折行,薄壳保真透传(存量调用方行为逐字节不变)
    maxLength: Int = Int.MAX_VALUE, // 透传 AppInputField(渲染层拒超限输入)
) {
    AppInputField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        keyboardType = keyboardType,
        keyboardHeightChange = keyboardHeightChange,
        maxLines = maxLines,
        maxLength = maxLength,
    )
}

/**
 * 渐变主按钮:薄壳委托 components/AppPrimaryButton(submitting 期间只拦点击与换文案、
 * 不改背景的约定随本体单源化);onClick 收尾参数保持,调用方尾随 lambda 零改动
 */
@Composable
internal fun BindPrimaryButton(
    text: String,
    submitting: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppPrimaryButton(
        text = text,
        submitting = submitting,
        modifier = modifier,
        onClick = onClick,
    )
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
