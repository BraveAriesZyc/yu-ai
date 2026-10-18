package com.zyc.love_baby.features.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.base.NotificationModule
import com.zyc.love_baby.components.AppIconType
import com.zyc.love_baby.components.AppMenuDivider
import com.zyc.love_baby.components.AppMenuGroup
import com.zyc.love_baby.components.AppMenuRow
import com.zyc.love_baby.components.AppPrimaryButton
import com.zyc.love_baby.components.AppSubPageHeader
import com.zyc.love_baby.components.AppTag
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.features.couple.toastCouple

/** permissionPage 路由名(openPermissionPage 与 @Page 注册名共用,勿散落字面量) */
internal const val PERMISSION_PAGE_NAME = "permissionPage"

/**
 * 路由打开权限管理页;事件回调里模块已就绪,try/catch 吞未桥接端(openProfilePage 同款包裹)
 */
internal fun openPermissionPage(pager: Pager) {
    try {
        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
            .openPage(PERMISSION_PAGE_NAME, JSONObject())
    } catch (_: Exception) {
    }
}

/**
 * PermissionPage — 权限管理页(@Page "permissionPage"):查看权限获取状况 + 一键开启(M22)
 * 用法:MinePage 设置分组「权限管理」行 openPermissionPage 打开;返回钮 closePage。
 *      独立二级页(CheckinPager 同款壳)
 * 依赖:base.NotificationModule 三方法(checkPermission/requestPermission/openSettings,
 *      Android 壳 KRNotificationModule 实现;iOS/ohos/H5 未注册 getModule null→「不支持」态
 *      渐进降级);components(AppSubPageHeader/AppMenuGroup/AppMenuRow/AppTag/AppPrimaryButton);
 *      couple 包 toastCouple;Modifier.clickable(已关闭胶囊透传行热区)
 * 事件:返回钮 closePage;通知行/「去开启」requestThenGuide(总开关关先弹授权框,拒绝或
 *      仅渠道关一律带去系统设置——单按钮覆盖全路径);「前往系统设置」直跳;从系统设置
 *      返回经 pageDidAppear 重查状态即时刷新
 * 功能:
 *  - 消息通知行:App 级总开关+「悄悄话」渠道级开关双值合成状态(已开启/已关闭,两者分离
 *    可区分「总开关关」与「渠道被单独关」两种关闭形态);未首查前状态位缺省防「已开启」闪错
 *    (行内胶囊与状态详情卡同一守卫)
 *  - 「去开启」单按钮覆盖全路径(3-skeptic 验证定稿):app 总开关已开(仅渠道关)直跳系统
 *    设置,不走 requestPermission(原生会 granted 短路零动作假 toast);总开关关才发运行时
 *    申请,granted=true 仍二段查证双开才 toast「已开启」,denied 一律带去系统设置(33+
 *    拒绝两次后系统不再弹框但仍回调 DENIED,asked 无法区分真假弹框,跳设置是万能引导)
 *  - 状态详情卡 + 权限说明卡:只申请通知权限一项,选图走系统相册选择器免授权(如实告知)
 */
@Page("permissionPage", supportInLocal = true)
internal class PermissionPager : BasePager() {

    /** 状态四件(observable 桥进 compose,回包/回前台重查即刷新):checked=首查完成
     *  (完成前状态位缺省,防「已开启」闪错);supported=桥可用(其他端 getModule null→
     *  false 显「不支持」态);appGranted=App 级通知总开关;channelOn=「悄悄话」渠道未被单独关 */
    internal var checked by observable(false)
    internal var supported by observable(true)
    internal var appGranted by observable(true)
    internal var channelOn by observable(true)

    /** 「去开启」在飞(防重复弹框) */
    internal var requesting by observable(false)

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        setContent {
            PermissionPage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }

    /** 从系统设置返回即重触发(一页一 Activity:设置页压顶,返回本页 onResume→didAppear) */
    override fun pageDidAppear() {
        super.pageDidAppear()
        refresh()
    }

    /** 重查通知权限状态(进页首查 + pageDidAppear + 授权回调后刷) */
    internal fun refresh() {
        val module = getModule<NotificationModule>(NotificationModule.MODULE_NAME)
        if (module == null) {
            supported = false
            checked = true
            return
        }
        supported = true
        module.checkPermission { app, channel ->
            appGranted = app
            channelOn = channel
            checked = true
        }
    }

    /** 「去开启」单按钮覆盖全路径(3-skeptic 验证定稿,推翻初版 asked 分支设计):
     *  ① app 总开关已开(仅「悄悄话」渠道被单独关)不走 requestPermission——原生会
     *     granted 短路零动作假 toast,直跳系统设置,渠道项就在页内;
     *  ② 总开关关才发运行时申请;granted=true 仍须二段查证(总开关+渠道双开才真已开启,
     *     appGranted=false&&channelOn=false 场景授权后渠道仍关,toast 会与状态卡矛盾);
     *  ③ denied 一律跳系统设置:33+ 拒绝两次后系统不再弹框但 requestPermissions 仍立即
     *     回调 DENIED——asked 无法区分「真实弹框」与「静默拒绝」,跳设置对首次拒绝
     *     (还能再弹一次)与永久拒绝都是合理引导,不再依赖 asked 区分 */
    internal fun requestThenGuide() {
        if (requesting) return
        val module = getModule<NotificationModule>(NotificationModule.MODULE_NAME) ?: run {
            toastCouple(this, "当前平台暂不支持")
            return
        }
        // 读 observable 快照:按钮/行可点前提是首查完成(checked),值为真;状态在页外被改
        // 的过期误差=多跳一次设置,无害(pageDidAppear 已即时重查)
        if (appGranted) {
            module.openSettings()
            return
        }
        requesting = true
        module.requestPermission { granted, _ ->
            requesting = false
            refresh()
            if (granted) {
                // 授权框放行只代表 app 总开关;渠道状态须重新查证(checkPermission 回包
                // 读到的是授权生效后的最新状态)
                module.checkPermission { app, channel ->
                    if (app && channel) toastCouple(this, "通知权限已开启") else module.openSettings()
                }
            } else {
                module.openSettings()
            }
        }
    }

    /** 「前往系统设置」直跳(总开关关/渠道关都去通知设置页,渠道项就在页内) */
    internal fun openSystemSettings() {
        getModule<NotificationModule>(NotificationModule.MODULE_NAME)?.openSettings()
    }
}

@Composable
internal fun PermissionPage(pager: PermissionPager) {
    val theme = ThemeStore.current
    // 进页首查(独立 @Page 组合在即活动在;从设置返回由 pageDidAppear 兜)
    LaunchedEffect(Unit) { pager.refresh() }

    val enabled = pager.appGranted && pager.channelOn

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                AppSubPageHeader(
                    title = "权限管理",
                    subtitle = "查看与开启应用权限",
                    onBack = {
                        pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                    },
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AppMenuGroup {
                        AppMenuRow(
                            icon = AppIconType.Bell,
                            iconTint = Color(0xFFFF4D6A),
                            iconBg = Color(0xFFFFE9ED),
                            title = "消息通知",
                            // 已关闭时整行可点=同「去开启」;已开启时行点击无动作
                            onClick = { if (!enabled) pager.requestThenGuide() },
                        ) {
                            when {
                                !pager.checked -> {}
                                !pager.supported -> AppTag(
                                    "不支持",
                                    bgColor = Color(0xFFF0F1F5),
                                    textColor = Color(0xFF9AA0AE),
                                )
                                enabled -> AppTag(
                                    "已开启",
                                    bgColor = Color(0xFF34C77B).copy(alpha = 0.12f),
                                    textColor = Color(0xFF34C77B),
                                )
                                // 已关闭胶囊:带背景的原生视图吃 tap(命中测试铁律),不透传会
                                // 在「已关闭」态把行点击最需要的热区堵死——包一层 clickable 转发行动作
                                else -> Box(
                                    modifier = Modifier.clickable { pager.requestThenGuide() },
                                ) {
                                    AppTag(
                                        "已关闭",
                                        bgColor = AppTheme.Danger.copy(alpha = 0.12f),
                                        textColor = AppTheme.Danger,
                                    )
                                }
                            }
                        }
                        AppMenuDivider()
                        AppMenuRow(
                            icon = AppIconType.Image,
                            iconTint = Color(0xFF6C7BFF),
                            iconBg = Color(0xFFEDEFFF),
                            title = "相册与图片",
                            onClick = {},
                        ) {
                            AppTag(
                                "无需授权",
                                bgColor = Color(0xFF34C77B).copy(alpha = 0.12f),
                                textColor = Color(0xFF34C77B),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    // 状态详情卡:按当前形态给一句人话
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                    ) {
                        Column {
                            Text(
                                statusTitle(pager),
                                color = AppTheme.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(statusDesc(pager), color = AppTheme.TextSecondary, fontSize = 12.sp)
                        }
                    }
                    // 操作区:仅已关闭(且桥可用)时出现
                    if (pager.supported && pager.checked && !enabled) {
                        Spacer(modifier = Modifier.height(18.dp))
                        AppPrimaryButton(
                            text = "去开启",
                            submitting = pager.requesting,
                            busyText = "请求中…",
                        ) { pager.requestThenGuide() }
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(AppTheme.CardBg, RoundedCornerShape(24.dp))
                                .clickable { pager.openSystemSettings() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "前往系统设置",
                                color = AppTheme.TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    // 权限说明卡:如实告知权限边界(只申请通知一项)
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                    ) {
                        Column {
                            Text(
                                "权限说明",
                                color = AppTheme.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "消息通知：仅用于接收对方发来悄悄话时的后台提醒（通知栏与横幅）。",
                                color = AppTheme.TextSecondary,
                                fontSize = 12.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "相册选图使用系统相册选择器，无需存储或相机权限；应用不申请定位、通讯录、麦克风等其他敏感权限。",
                                color = AppTheme.TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp + navBarSafePaddingDp()))
                }
            }
        }
    }
}

/** 状态详情卡标题(与行内状态胶囊同一套判定,组合期纯函数;!checked 前置防首查回包前
 *  按缺省 true 闪「已开启」——行内胶囊有同款守卫,卡片不能漏,3-skeptic minor) */
private fun statusTitle(pager: PermissionPager): String = when {
    !pager.supported -> "当前平台暂不支持"
    !pager.checked -> "正在查询通知状态…"
    pager.appGranted && pager.channelOn -> "通知已开启"
    !pager.appGranted -> "通知已关闭"
    else -> "「悄悄话」渠道已关闭"
}

/** 状态详情卡正文 */
private fun statusDesc(pager: PermissionPager): String = when {
    !pager.supported -> "权限查询与授权仅 Android 端提供；其他端留意应用内红点提醒即可。"
    !pager.checked -> " "
    pager.appGranted && pager.channelOn -> "对方发来悄悄话时，会在通知栏与横幅提醒你。"
    !pager.appGranted -> "收不到对方消息的后台提醒。点「去开启」重新授权；若系统不再弹框，请到系统设置手动开启。"
    else -> "应用通知已开启，但「悄悄话」渠道在系统设置中被单独关闭，去设置里重新打开它。"
}
