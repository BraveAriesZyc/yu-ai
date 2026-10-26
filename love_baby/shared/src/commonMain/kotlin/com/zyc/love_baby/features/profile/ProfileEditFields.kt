package com.zyc.love_baby.features.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.FileModule
import com.zyc.love_baby.components.AppDialogButtonRow
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppOverlayCard
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.components.rememberKeyboardHeight
import com.zyc.love_baby.components.runAfterBlur
import com.zyc.love_baby.features.couple.BindInputField
import com.zyc.love_baby.features.couple.toastCouple
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.network.ApiConfig
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.UserApi
import com.zyc.love_baby.state.SessionStore
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * ProfileEditFields — QQ 式行内编辑控件集(2026-10-18 用户裁决「详情页改成和 QQ 一样」:
 * 编辑不再走大弹窗,详情页每行点击直接编辑),替代原 ProfileEditOverlay 大编辑浮层——
 *  - SexPickerOverlay:性别底部选择器(QQ 式 bottom sheet:女/男两行,当前值 Check 高亮,
 *    点行即保存;保存中拦点击)
 *  - FieldInputOverlay:单字段输入卡(昵称/个性签名共用,预填现值+字数计数+取消/确认;
 *    居中偏上放置——Kuikly 窗口不 resize 键盘纯遮挡(轮16 锤实模型),贴底会被键盘盖住)
 *  - uploadAvatarAndUpdate/isValidBirthday:自 ProfileEditOverlay.kt 原样迁入(文件随大
 *    浮层废除删除,同包零 import 变化)
 * 共同约定:dismiss 收口照铁律 16(components.runAfterBlur 单源——先清焦再迟一拍卸载,blur
 * 批处理与 action 同队列按入队序执行,持焦输入框不会带焦被移除);保存语义与 UserUpdateReq
 * 守卫对齐(昵称空串拦截/签名 trim 含空串=清空/全等不打请求);sex 选中≠现值才传,
 * 选中后不可取消
 */

/**
 * SexPickerOverlay — 性别底部选择器(QQ 式)
 * 用法:挂 ProfilePage 根(LazyColumn 兄弟之后=后组合者在上);onPick 回调选择值,
 *      调用方判断与现值相同直接关、不同走 updateUser(sex) 成功后关
 * 依赖:AppTheme(brandStart/Divider/CardBg/TextSecondary);无输入框无键盘参与
 * 事件:灰幕点击 → onDismiss(saving 拦);行点击 → onPick(0=女/1=男)
 * 功能:当前值行右侧 brand 色 Check 高亮;白卡底部贴屏幕(top-only 四参圆角+
 *      navBarSafePaddingDp+12dp 底垫,AppDatePickerOverlay 同款结构)
 */
@Composable
internal fun SexPickerOverlay(
    current: Int?,
    saving: Boolean,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AppOverlayCard(
        onDismiss = { if (!saving) onDismiss() },
        busy = saving, // 保存中拦灰幕(语义保真;行点击的 enabled=!saving 闸仍在行内)
        contentAlignment = Alignment.BottomCenter,
        cardOuterPadding = PaddingValues(horizontal = 0.dp),
        // top-only 四参圆角(AppDatePickerOverlay 生产先例,Kuikly 不裁屏幕边缘——
        // 单参全圆角底部缺口会透灰幕)
        cardShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 0.dp, bottomStart = 0.dp),
        // navBarSafe 底垫防手势区压行(原两段 padding 合一)
        cardPadding = PaddingValues(
            start = 20.dp,
            top = 14.dp,
            end = 20.dp,
            bottom = navBarSafePaddingDp() + 12.dp,
        ),
    ) {
            Text(
                "选择性别",
                color = AppTheme.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            SexPickerRow("女", selected = current == 0, enabled = !saving) { onPick(0) }
            Box(
                modifier = Modifier.fillMaxWidth()
                    .height(1.dp)
                    .background(AppTheme.Divider),
            )
            SexPickerRow("男", selected = current == 1, enabled = !saving) { onPick(1) }
    }
}

/** 性别选择器单行:56dp 高,左侧 15sp 文字,当前值右侧 brand 色 Check;整行可点 */
@Composable
private fun SexPickerRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .height(56.dp)
            .clickable { if (enabled) onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppTheme.TextPrimary, fontSize = 15.sp)
        Spacer(modifier = Modifier.weight(1f))
        if (selected) {
            AppIcon(AppIconType.Check, ThemeStore.current.brandStart, Modifier.size(16.dp))
        }
    }
}

/**
 * FieldInputOverlay — 单字段输入卡(昵称/个性签名共用)
 * 用法:挂 ProfilePage 根;onSave 回调 trim 后草稿,调用方校验+updateUser+回填+关浮层
 * 依赖:features.couple.BindInputField(48dp 容器定高+内容居中,全仓输入框统一模式);
 *      components.runAfterBlur(dismiss 收口)
 * 事件:灰幕/取消 → dismiss(saving 拦);确认 → onSave(draft.trim())
 * 功能:预填现值+右下字数计数 x/maxLength;垂直居中略偏上(Center:键盘收 bottom=140 静态
 *      补偿,键盘起 bottom=键盘高+navBar+12 动态顶升躲键盘遮挡——纯居中/静态 140 在矮屏
 *      会压键盘顶缘);字数上限 take 截断
 */
@Composable
internal fun FieldInputOverlay(
    pager: Pager,
    title: String,
    initialValue: String,
    placeholder: String,
    maxLength: Int,
    saving: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initialValue) }
    val (keyboardHeight, onKeyboardHeightChange) = rememberKeyboardHeight()
    val focusManager = LocalFocusManager.current

    // dismiss 收口(铁律 16):components.runAfterBlur 单源(先清焦再推后一拍卸载,机制详见
    // 该件 KDoc——blur 批处理必然先于卸载,持焦输入框不会带焦被移除,1570d07 根因)。
    // ⚠ 输入框祖先链不得加 focusProperties exit 拦截
    fun dismiss() {
        pager.runAfterBlur(focusManager) { onDismiss() }
    }

    // 垂直居中略偏上:键盘收时 bottom=140 静态补偿(视觉中心比屏中心高 70dp,半强换算
    // bottom/2);键盘纯遮挡模型窗口不 resize,top 92dp 顶置被真机否决「没有居中」,
    // 静态 140 在 ≤720dp 矮屏上键盘弹起会盖住按钮行(对抗验证 major)——键盘起切动态
    // bottom=键盘高+navBar+12,卡片整体居中于键盘上方可见区(800dp 屏净空+149/640dp +69)
    val bottomPad = if (keyboardHeight.value > 0f) {
        keyboardHeight.value.dp + navBarSafePaddingDp() + 12.dp
    } else {
        140.dp
    }
    AppOverlayCard(
        onDismiss = { if (!saving) dismiss() },
        busy = saving, // 保存中拦灰幕(语义保真;取消/确认的 !saving 闸仍在回调侧)
        cardOuterPadding = PaddingValues(start = 28.dp, top = 0.dp, end = 28.dp, bottom = bottomPad),
        cardCorner = 24.dp,
    ) {
        Text(title, color = AppTheme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))
        BindInputField(
            value = draft,
            onValueChange = { draft = it.take(maxLength) },
            placeholder = placeholder,
            keyboardHeightChange = onKeyboardHeightChange,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${draft.length}/$maxLength",
            color = AppTheme.TextTertiary,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
        Spacer(modifier = Modifier.height(12.dp))
        AppDialogButtonRow(
            leftText = "取消",
            rightText = if (saving) "保存中…" else "确认",
            onLeft = { if (!saving) dismiss() },
            onRight = {
                if (!saving) {
                    // 确认即清焦(覆盖 onSave 三路:保存成功卸载/全等直关/失败保留浮层)——
                    // 输入框不带焦被移除,本控件复用到 MainPager 域(多输入框+横向 Pager)
                    // 不复现 1570d07 关浮层自滚
                    focusManager.clearFocus()
                    onSave(draft.trim())
                }
            },
            rightBrush = ThemeStore.current.brandBrush,
            rightFontWeight = FontWeight.Bold,
        )
    }
}

/** 生日粗校验兜底:yyyy-MM-dd 格式 + 月 1-12 日 1-31(滚轮已保证格式,此防线防内部状态异常) */
internal fun isValidBirthday(value: String): Boolean {
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value)) return false
    val month = value.substring(5, 7).toIntOrNull() ?: return false
    val day = value.substring(8, 10).toIntOrNull() ?: return false
    return month in 1..12 && day in 1..31
}

/**
 * uploadAvatarAndUpdate — 头像上传全链路(自 MainPager 收口迁移,M16 合并轮起 ProfilePage
 * 域使用;原 ProfileEditOverlay.kt 随大浮层废除删除后迁驻本文件):① getUser 热身(原生
 * 直传不经 HttpClient 无 401 单飞刷新兜底,失败不拦流程);② FileModule.pickAndUpload
 * 一次性回调(选图→压缩→multipart 直传 /infra/file/upload 全在原生;未桥接端
 * getModule=null → toast 降级提示);③ user/update 写穿透 avatar。
 * 成功已 toast「头像已更新」并回传 URL 供调用方回填本地态;失败 toast 已完成回传 null。
 * ⚠ 须在浮层外的页面级 scope 调用(浮层关闭不取消上传,上传中途切页照常完成)
 */
internal suspend fun uploadAvatarAndUpdate(pager: Pager): String? {
    UserApi.getUser(pager)
    var failMsg = "上传失败,请重试"
    val fileUrl = try {
        val module = pager.getModule<FileModule>(FileModule.MODULE_NAME)
        if (module == null) {
            failMsg = "当前端暂不支持上传头像"
            null
        } else {
            suspendCoroutine<String?> { cont ->
                module.pickAndUpload(
                    ApiConfig.url(pager, "/infra/file/upload"),
                    SessionStore.accessToken,
                    directory = FileModule.DIR_AVATAR,
                ) { result ->
                    val code = result?.optInt("code", -1) ?: -1
                    val data = result?.optString("data").orEmpty()
                    if (code == 0 && data.isNotEmpty()) {
                        cont.resume(data)
                    } else {
                        result?.optString("msg")?.takeIf { it.isNotEmpty() }?.let { failMsg = it }
                        cont.resume(null)
                    }
                }
            }
        }
    } catch (_: Exception) {
        null
    }
    if (fileUrl.isNullOrEmpty()) {
        toastCouple(pager, failMsg)
        return null
    }
    val result = UserApi.updateUser(pager, avatar = fileUrl)
    return if (result is ApiResult.Ok) {
        toastCouple(pager, "头像已更新")
        fileUrl
    } else {
        toastCoupleResult(pager, result)
        null
    }
}
