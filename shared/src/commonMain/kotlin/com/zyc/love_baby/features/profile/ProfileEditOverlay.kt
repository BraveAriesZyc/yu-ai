package com.zyc.love_baby.features.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalFocusManager
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.FileModule
import com.zyc.love_baby.base.setTimeout
import com.zyc.love_baby.components.AppAvatar
import com.zyc.love_baby.components.AppIcon
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.AppDatePickerOverlay
import com.zyc.love_baby.features.couple.BindInputField
import com.zyc.love_baby.features.couple.BindPrimaryButton
import com.zyc.love_baby.features.couple.NICKNAME_MAX_LENGTH
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
 * ProfileEditOverlay — 完整资料编辑浮层(M16 真机反馈合并轮,2026-10-18):
 * 头像 + 昵称 + 性别 + 生日(滚轮) + 个性签名 **一处编辑**;唯一挂载点=ProfilePage
 * (独立 @Page 页内自持挂载,数据源=页内自拉 profile)。原 MainPager 我的页「编辑」
 * 根级挂载(F6)随 2026-10-18「右上角改详情」裁决拆除——我的页编辑入口收进详情页,
 * MainPager 不再持有编辑浮层(替代原 NicknameEditOverlay 的合并语义不变)。
 * 用法:挂调用方根 Box 最后(灰幕盖底栏语义);上传通道 onAvatarClick 上报 + uploading
 *      外部传入,全链路走 uploadAvatarAndUpdate(须在浮层外的页面级 scope 调,浮层关闭
 *      不取消上传);字段回填经 onSaved(昵称/性别/生日/签名)回调,头像回填由调用方
 *      在 onAvatarClick 的 pageScope 协程内 profile.copy(avatar=URL) 完成
 * 依赖:features.couple.BindInputField/BindPrimaryButton/toastCouple/toastCoupleResult;
 *      components.AppDatePickerOverlay(生产在用的三列滚轮日期选择器,
 *      挂本浮层灰幕 Box 内最后=后组合者在上,覆盖编辑卡);
 *      base.FileModule(未桥接端 getModule=null 降级);network.UserApi/ApiConfig
 * 事件:遮罩点击/保存成功 dismiss(保存中拦);生日行点击开滚轮,确定回写 draft
 * 功能:
 *  - 提交语义(与 UserUpdateReq 守卫对齐):昵称空串拦截「昵称不能为空」;性别选中≠现值
 *    才传(选中后不可取消);生日 trim 非空且≠现值才传(滚轮保证格式,isValidBirthday 仅
 *    兜底);签名 trim≠现值才传——**含空串=清空**;四值全等直接 dismiss 不打请求
 *  - 关浮层先清焦(浮层卸载持焦输入框被移除 → Android 焦点转给待办页输入框 → Pager
 *    乱滚,M16 前多轮真机实锤)
 *  - 美化轮(2026-10-18 二轮,真机反馈):标题行右上 Close 关闭钮+性别改双卡(SexCard:
 *    AppIcons 新增 GenderFemale/GenderMale Canvas 图标,选中 brand 渐变白字白图标+白圆
 *    Check 角标,未选灰底)+生日行右侧 ChevronRight 箭头+头像徽章「+」换 Plus 图标
 *  - 美化三轮(同日,用户再提美观度):删「性别/生日/个性签名」三个独立 label——Column
 *    CenterHorizontally 下短 label 居中与拉满控件错位,且与昵称 placeholder 模式两套混表单;
 *    统一走自解释(性别双卡图标+文字/生日空值「点击设置生日」/签名 placeholder),净省约
 *    68dp 缓解键盘遮挡下半屏;卡片边距 32→28(卡更宽)+圆角 20→24+头像 72→80dp,
 *    间距节奏统一 12(控件间)/18(保存前)
 */

/** 上传进行中/保存中都不放走遮罩点击,防丢视觉反馈;生日子滚轮开着时编辑遮罩点击被其全屏层截获 */
@Composable
internal fun ProfileEditOverlay(
    pager: Pager,
    currentNickname: String,
    currentAvatar: String,
    currentSex: Int?,
    currentBirthday: String,
    currentSignature: String,
    uploading: Boolean,
    onDismiss: () -> Unit,
    onSaved: (String, Int?, String, String) -> Unit,
    onAvatarClick: () -> Unit = {},
) {
    var draftNickname by remember { mutableStateOf(currentNickname) }
    var draftSex by remember { mutableStateOf(currentSex) }
    var draftBirthday by remember { mutableStateOf(currentBirthday) }
    var draftSignature by remember { mutableStateOf(currentSignature) }
    var showBirthdayPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun dismiss() {
        // 先清焦,卸载迟一拍:compose clearFocus 只同步清 compose 焦点,原生 blur 经
        // SoftwareKeyboardController 的 setTimeout(0) 批处理异步落地;onDismiss 若同回合执行,
        // 浮层在下一帧卸载时与 blur 竞速——持焦 EditText(KRTextFieldView 是全树唯一
        // focusableInTouchMode 叶子)一旦带焦点随视图树被移除,Android ViewRootImpl 自动把焦点
        // 转给五页驻留树中的待办页输入框→Pager 原生 KRRecyclerView requestChildRectangleOnScreen
        // 横滚露出它+SnapHelper 吸附=关闭后页面自滚(1570d07 同源;M12 修的 submit 路径有网络
        // 窗口护住 blur 落地,遮罩/Close 即时路径无窗口,2026-10-18 真机复现)。
        // 落序保证(对抗验证锤实):blur 批处理与 onDismiss 均经 SET_TIMEOUT 桥接落入同一原生
        // HRContextQueueHandlerThread 的 MessageQueue,同 delay=0 按 AOSP enqueueMessage 插入序
        // 执行——clearFocus 的 blur 批同步先入队,本 onDismiss 后入队,blur 必然先落地:卸载时
        // 树中已无持焦视图,焦点转移无从发生(原生主队列排水亦按序 FIFO,blur 的原生清焦严格
        // 先于 removeView——落序是确定性而非竞速)。
        // ⚠ 维护红线:本收口依赖 clearFocus 真正清焦——输入框祖先链不得加 focusProperties exit
        // 拦截(那会使 clearFocus 变 no-op、blur 批不入队,延迟卸载独自挡不住转移);且仅适用于
        // 静态布局——LazyList 复用窗口存在原生先行触焦致 activeView 失同步、blur 不入队的通道
        // (CoreTextField 复用窗口防御注释),抄写本模式前先核对
        focusManager.clearFocus()
        pager.setTimeout(0) { onDismiss() }
    }

    fun save() {
        if (saving) return
        val next = draftNickname.trim()
        if (next.isEmpty()) {
            toastCouple(pager, "昵称不能为空")
            return
        }
        val trimmedBirthday = draftBirthday.trim()
        if (trimmedBirthday.isNotEmpty() && !isValidBirthday(trimmedBirthday)) {
            toastCouple(pager, "生日格式应为 yyyy-MM-dd")
            return
        }
        val trimmedSignature = draftSignature.trim()
        val nicknameChanged = next != currentNickname
        val sexChanged = draftSex != currentSex
        val birthdayChanged = trimmedBirthday.isNotEmpty() && trimmedBirthday != currentBirthday
        val signatureChanged = trimmedSignature != currentSignature
        if (!nicknameChanged && !sexChanged && !birthdayChanged && !signatureChanged) {
            dismiss()
            return
        }
        saving = true
        focusManager.clearFocus()
        scope.launch {
            val result = UserApi.updateUser(
                pager,
                nickname = if (nicknameChanged) next else null,
                sex = if (sexChanged) draftSex else null,
                birthday = if (birthdayChanged) trimmedBirthday else null,
                signature = if (signatureChanged) trimmedSignature else null,
            )
            saving = false
            if (result is ApiResult.Ok) {
                onSaved(
                    next,
                    if (sexChanged) draftSex else currentSex,
                    if (birthdayChanged) trimmedBirthday else currentBirthday,
                    if (signatureChanged) trimmedSignature else currentSignature,
                )
                toastCouple(pager, "已保存")
                dismiss()
            } else {
                toastCoupleResult(pager, result)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { if (!saving) dismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp)
                .fillMaxWidth()
                .background(AppTheme.CardBg, RoundedCornerShape(24.dp))
                .clickable { } // 空 clickable 吞点击防透传
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 标题行:居中标题 + 右上关闭钮(26dp 圆灰底 Close 图标;保存中拦点击防丢态)
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "编辑资料",
                    color = AppTheme.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )
                // 双热区:外层 38dp 透明热区(26dp 视觉圆是全仓最小独立可点控件,真机误触率高),
                // 内层 26dp 视觉圆载底色(Divider 令牌,与 ChatPage 贴纸胶囊同源)
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .size(38.dp)
                        .clickable { if (!saving) dismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(26.dp)
                            .background(AppTheme.Divider, RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIconType.Close, AppTheme.TextSecondary, Modifier.size(11.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // 头像区(自 NicknameEditOverlay 迁入,美化三轮 72→80dp):大头像 + 右下「+」徽章
            // + 居中提示,整块可点上报 onAvatarClick(选图/压缩/直传/写回全在 uploadAvatarAndUpdate);
            // 上传中蒙半透明黑 + 文案切换 + 拦点击防重复发起
            Column(
                modifier = Modifier.clickable { if (!uploading) onAvatarClick() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.size(80.dp)) {
                    AppAvatar(
                        currentNickname.trim().take(1).ifEmpty { "爱" },
                        size = 80.dp,
                        bgBrush = ThemeStore.current.brandBrush,
                        fontSize = 26.sp,
                        avatarUrl = currentAvatar,
                    )
                    if (uploading) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(40.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("…", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            modifier = Modifier.align(Alignment.BottomEnd)
                                .size(24.dp)
                                .background(AppTheme.CardBg, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(AppIconType.Plus, ThemeStore.current.brandStart, Modifier.size(12.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (uploading) "头像上传中…" else "点击更换头像",
                    color = if (uploading) AppTheme.TextTertiary else AppTheme.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            BindInputField(
                value = draftNickname,
                onValueChange = { draftNickname = it.take(NICKNAME_MAX_LENGTH) },
                placeholder = "昵称",
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SexCard("女", AppIconType.GenderFemale, draftSex == 0, Modifier.weight(1f)) { draftSex = 0 }
                Spacer(modifier = Modifier.width(10.dp))
                SexCard("男", AppIconType.GenderMale, draftSex == 1, Modifier.weight(1f)) { draftSex = 1 }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 生日行:只读点击行(视觉对齐 BindInputField 灰底 48dp),点击弹三列滚轮——
            // M16 真机反馈弃 yyyy 文本输入,复用纪念日页生产在用、已抽到 components 的 AppDatePickerOverlay
            Row(
                modifier = Modifier.fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0xFFF5F6FA), RoundedCornerShape(14.dp))
                    // 点击先收键盘再开滚轮(AnniversaryPage 日期行同款):键盘开着时滚轮底部
                    // 面板会被纯遮挡的键盘盖住(窗口不 resize,轮16 锤实模型)
                    .clickable {
                        focusManager.clearFocus()
                        showBirthdayPicker = true
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    draftBirthday.trim().ifEmpty { "点击设置生日" },
                    color = if (draftBirthday.trim().isEmpty()) AppTheme.TextTertiary else AppTheme.TextPrimary,
                    fontSize = 15.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                AppIcon(AppIconType.ChevronRight, Color(0xFFC8CCD8), Modifier.size(15.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            BindInputField(
                value = draftSignature,
                onValueChange = { draftSignature = it.take(100) },
                placeholder = "一句话介绍自己",
            )
            Spacer(modifier = Modifier.height(18.dp))
            // submitting 文案由 BindPrimaryButton 自管(内置「请稍候…」,调用方传值会被覆盖)
            BindPrimaryButton(text = "保 存", submitting = saving) { save() }
        }
        // 生日滚轮子浮层:挂编辑浮层灰幕 Box 内最后(后组合者在上,覆盖编辑卡);
        // 自带全屏遮罩点击 dismiss,初值空缺省 2000-01-01
        if (showBirthdayPicker) {
            val parts = draftBirthday.trim().split("-")
            AppDatePickerOverlay(
                initialYear = parts.getOrNull(0)?.toIntOrNull() ?: 2000,
                initialMonth = parts.getOrNull(1)?.toIntOrNull() ?: 1,
                initialDay = parts.getOrNull(2)?.toIntOrNull() ?: 1,
                onConfirm = { y, m, d ->
                    // commonMain 无 String.format,padStart 拼 yyyy-MM-dd
                    draftBirthday = "$y".padStart(4, '0') + "-" + "$m".padStart(2, '0') + "-" + "$d".padStart(2, '0')
                    showBirthdayPicker = false
                },
                onDismiss = { showBirthdayPicker = false },
            )
        }
    }
}

/** SexCard 未选底(0xFFF5F6FA 同色渐变):文件级常量避免每次重组新建 Brush 实例——重组 diff
 *  /掉帧根因(AppTheme.kt 注释明载);选中分支 brandBrush 本就是 lazy 缓存稳定实例 */
private val SexCardIdleBrush = Brush.linearGradient(listOf(Color(0xFFF5F6FA), Color(0xFFF5F6FA)))

/**
 * SexCard — 性别选择双卡之一(美化轮,替代原小胶囊):半宽 68dp 定高卡,竖排 性别图标+文字。
 * 选中 brandBrush 渐变白字白图标(白字主操作铁律:禁 brandEnd 浅端单色)+右上白圆 Check 角标;
 * 未选灰底(0xFFF5F6FA 与 BindInputField 同底)灰图标。点已选卡 no-op(性别选中后不可取消,
 * 与 UserUpdateReq null 语义对齐)。modifier 外部传 RowScope.weight(1f)。
 */
@Composable
private fun SexCard(
    label: String,
    icon: AppIconType,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val selectedBrush = ThemeStore.current.brandBrush
    // color/brush 重载禁混用(铁律):三元两分支必须同型——未选纯色转同色 linearGradient 走 brush 重载
    Box(
        modifier = modifier
            .height(68.dp)
            .background(if (selected) selectedBrush else SexCardIdleBrush, RoundedCornerShape(14.dp))
            .clickable { onClick() },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIcon(icon, if (selected) Color.White else AppTheme.TextSecondary, Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                label,
                color = if (selected) Color.White else AppTheme.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
        if (selected) {
            // 选中角标:右上白圆 + brandStart Check(渐变底上对比来自白圆)
            Box(
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
                    .background(Color.White, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIconType.Check, ThemeStore.current.brandStart, Modifier.size(9.dp))
            }
        }
    }
}

/** 生日粗校验兜底:yyyy-MM-dd 格式 + 月 1-12 日 1-31(滚轮已保证格式,此防线防内部状态异常) */
private fun isValidBirthday(value: String): Boolean {
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value)) return false
    val month = value.substring(5, 7).toIntOrNull() ?: return false
    val day = value.substring(8, 10).toIntOrNull() ?: return false
    return month in 1..12 && day in 1..31
}

/**
 * uploadAvatarAndUpdate — 头像上传全链路(自 MainPager.submitAvatarUpload 收口迁移,合并轮起
 * ProfilePage 域同款复用):① getUser 热身(原生直传不经 HttpClient 无 401 单飞刷新兜底,失败
 * 不拦流程);② FileModule.pickAndUpload 一次性回调(选图→压缩→multipart 直传 /infra/file/upload
 * 全在原生;未桥接端 getModule=null → toast 降级提示);③ user/update 写穿透 avatar。
 * 成功已 toast「头像已更新」并回传 URL 供调用方回填本地态;失败 toast 已完成回传 null。
 * ⚠ 须在浮层外的页面级 scope 调用(浮层关闭不取消上传,Upload 中途关浮层照常完成)
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
