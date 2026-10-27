package com.zyc.love_baby.features.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tencent.kuikly.compose.foundation.layout.width
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
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.base.FileModule
import com.zyc.love_baby.base.NotificationModule
import com.zyc.love_baby.base.NotifyPermState
import com.zyc.love_baby.base.LocationModule
import com.zyc.love_baby.base.closeLovePage
import com.zyc.love_baby.base.openLovePage
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** permissionPage 路由名(openPermissionPage 与 @Page 注册名共用,勿散落字面量) */
internal const val PERMISSION_PAGE_NAME = "permissionPage"

/** 宿主直注快照 pageData 键(M29;androidApp KuiklyRenderActivity/PermissionSnapshot
 *  KEY_PERM_SNAPSHOT 同串双源,勿单改一边) */
private const val PERM_SNAPSHOT_KEY = "permSnapshot"

/** 宿主落地快照 prefs 键(M36.1;androidApp PermissionSnapshot KEY_PERM_SNAPSHOT_LIVE
 *  同串双源,勿单改一边):从设置/应用详情返回或应用内弹框出结果时,宿主把新鲜快照写进
 *  KRSharedPreferencesModule 文件,本页轮询经 SharedPreferencesModule.getString 同步桥
 *  读取落态。M36.0 首版走宿主 sendEvent 事件推送,静态链路逐环可证却真机无效——与本页
 *  模块回调同死(初始化后宿主→core 的桥调用在真机上不可达,FIRE_VIEW_EVENT/CREATE 与
 *  同步模块调用不受累),改骑 core→host 同步 KV 这条已证通道 */
private const val PERM_SNAPSHOT_LIVE_KEY = "permSnapshotLive"

/** 宿主落地快照时间戳 prefs 键(M36.1;androidApp PermissionSnapshot
 *  KEY_PERM_SNAPSHOT_LIVE_TS 同串双源,勿单改一边):轮询以时间戳前进为「弹框已出结果/
 *  刚从设置返回」信号——快照内容不变(如弹框点了拒绝)也要落态并复位在飞申请标记 */
private const val PERM_SNAPSHOT_LIVE_TS_KEY = "permSnapshotLiveTs"

/**
 * 路由打开权限管理页(base.openLovePage 收口,try/catch 吞未桥接端在 helper 内统一处理)
 */
internal fun openPermissionPage(pager: Pager) {
    pager.openLovePage(PERMISSION_PAGE_NAME)
}

/**
 * PermissionPage — 权限管理页(@Page "permissionPage"):查看权限获取状况 + 一键开启(M22)。
 * 用法:MinePage 设置分组「权限管理」行 openPermissionPage 打开;返回钮 closePage。
 *      独立二级页(CheckinPager 同款壳)
 * 依赖:base.NotificationModule 五方法(checkPermission 六值快照/requestPermission/openSettings/
 *      openChannelSettings/notify,Android 壳 KRNotificationModule 实现;iOS/ohos/H5 未注册
 *      getModule null→「不支持」态渐进降级);base.FileModule 五方法(M29:checkMicPermission/
 *      requestMicPermission/checkMediaPermission/requestMediaPermission/openAppDetails,
 *      Android 壳 KRFileModule 实现,requestCode 2005/2006 静态槽;iOS/ohos/H5 未注册
 *      getModule null→「不支持」);components(AppSubPageHeader/AppMenuGroup/AppMenuRow/
 *      AppTag/AppPrimaryButton);couple 包 toastCouple;Modifier.clickable(状态胶囊透传行热区)
 * 事件:返回钮 closePage;消息通知行/「去开启」requestThenGuide;「悄悄话」渠道行与「悬浮横幅
 *      提醒」行 openChannelSettings(渠道重要性/横幅开关所在页);「前往系统设置」直跳 App 级
 *      通知设置;定位行恒有响应——未开启 requestLocThenGuide(运行时申请,被拒带去应用详情
 *      页),已开启 openLocationSettings(应用详情页「权限 → 位置权限」= 权限获取页);从系统
 *      设置返回/应用内弹框出结果即时刷新——Android 走宿主落地 prefs+本页轮询(M36.1,见
 *      PERM_SNAPSHOT_LIVE_KEY 注),其余端经 pageDidAppear 逐组模块重查(回调可达时同值幂等)
 * 功能:
 *  - 六行结构(2026-10-19 悬浮通知真机反馈轮定稿;M29 权限全面状态化):
 *    ① 消息通知:App 级总开关断言行(已开启/已关闭),已关闭可点=「去开启」全路径;
 *    ② 「悄悄话」渠道:渠道级断言行——正常/已关闭(IMPORTANCE_NONE)/横幅受限(被调低于
 *       HIGH)/跟随系统(API<26 无渠道概念,横幅由通知 PRIORITY 决定),点击跳渠道设置;
 *    ③ 悬浮横幅提醒:**引导行不置断言**——横幅(heads-up)= 渠道≥HIGH 只是必要条件,国产
 *       ROM 另有系统级「悬浮通知/横幅」开关且无标准查询 API(源码级核实),该项恒示
 *       「需手动确认」,点击跳渠道设置人工核对(引擎对「高」的落名:渠道页/系统通知页)
 *    ④ 相册与图片(M29 状态化,修正旧「无需授权」失实):完整授权=已开启/Android14
 *       「仅选中照片」=部分授权/已关闭可申请(M24 十三轮起应用内相册弹层为聊天主路径,
 *       读媒体库运行时权限),已开启/部分授权点击进应用详情页
 *    ⑤ 麦克风(M29 新增,发语音消息 RECORD_AUDIO):已开启点击进应用详情页/已关闭行点击
 *       运行时申请(FileModule requestMicPermission,被拒带去应用详情页)
 *    ⑥ 定位权限(M26):行点击恒有响应,见下方定位权限行条目
 *  - 全行状态化+查询失败兜底(M29):六行状态胶囊恒有形态,进页 4s 仍有任一组未回包 →
 *    queryStuck 置真,全部行落「状态未知」、详情卡改「查询失败」——模块回调静默丢失
 *    (卡「查询中」真机实证)绝不挂死
 *  - 卡死根因修复(同轮):旧版原生回包经 call() 内同步 invoke 不送达=页面卡「查询中」,
 *    原生侧已统一 mainHandler 异步投递+采集异常兜底必达;common 侧六值化(checkPermission
 *    回 NotifyPermState,data class 相等跳重组)
 *  - 「去开启」单按钮覆盖全路径(3-skeptic 验证定稿):app 总开关已开(仅渠道关/横幅受限)
 *    不走 requestPermission(原生会 granted 短路零动作假 toast),直跳渠道设置;总开关关才
 *    发运行时申请,granted=true 仍二段查证(总开关+渠道双开才真已开启),denied 一律带去
 *    系统设置(33+ 拒绝两次后系统不再弹框但仍回调 DENIED,asked 无法区分真假弹框,跳设置
 *    是万能引导)
 *  - 状态详情卡:七态(查询中/不支持/查询失败/已关闭/渠道关/横幅受限/已开启)+ 勿扰/省电
 *    条件提示(两者都会压制横幅,只在做题时显示)+ 权限说明卡(如实告知权限边界)
 *  - 定位权限行(M26):行点击恒有响应——未开启=获取权限(运行时申请,被拒带去应用详情页);
 *    已开启=行尾追加精度位展示已获取的权限(FINE=精确定位/否则 COARSE=大致位置)+跳应用
 *    详情页「权限 → 位置权限」(权限获取页,可查看/改授权)。位置页 M29 起无共享开关,
 *    进页自有权限链
 */
@Page("permissionPage", supportInLocal = true)
internal class PermissionPager : BasePager() {

    /** 状态三件(observable 桥进 compose,回包/回前台重查即刷新):checked=首查完成(完成前
     *  状态位缺省,防「已开启」闪错);supported=桥可用(其他端 getModule null→false 显
     *  「不支持」态);permState=六值快照(null=未查,回包原子落;data class 相等跳重组) */
    internal var checked by observable(false)
    internal var supported by observable(true)
    internal var permState by observable<NotifyPermState?>(null)

    /** 「去开启」在飞(防重复弹框) */
    internal var requesting by observable(false)

    /** 定位权限五件(M26,结构同上通知组):locChecked=首查完成;locSupported=桥可用;
     *  locGranted=FINE/COARSE 任一授权(与 KRLocationModule 口径一致);locFine/locCoarse=
     *  各自授权位(M29:已开启时行尾展示已获取的权限,精确定位/大致位置) */
    internal var locChecked by observable(false)
    internal var locSupported by observable(true)
    internal var locGranted by observable(false)
    internal var locFine by observable(false)
    internal var locCoarse by observable(false)

    /** 定位申请在飞(防重复弹框,与通知 requesting 分立互不干扰) */
    internal var locRequesting by observable(false)

    /** 麦克风权限四件(M29,结构同通知组):micChecked=首查完成;micSupported=桥可用;
     *  micGranted=RECORD_AUDIO 已授权 */
    internal var micChecked by observable(false)
    internal var micSupported by observable(true)
    internal var micGranted by observable(false)

    /** 麦克风申请在飞(防重复弹框) */
    internal var micRequesting by observable(false)

    /** 相册读权限五件(M29,结构同定位组):mediaChecked=首查完成;mediaSupported=桥可用;
     *  mediaGranted=完整读权限(33+ IMAGES+VIDEO 双授权 / 33- 传统存储);mediaPartial=
     *  Android14「仅选中照片」部分授权(granted=false 时才有意义) */
    internal var mediaChecked by observable(false)
    internal var mediaSupported by observable(true)
    internal var mediaGranted by observable(false)
    internal var mediaPartial by observable(false)

    /** 相册读申请在飞(防重复弹框) */
    internal var mediaRequesting by observable(false)

    /** 查询失败兜底(M29):进页 4s 后仍有任一组未回包 → 置真,全部行落「状态未知」、详情卡
     *  改「查询失败」——绝不挂「查询中」(模块回调静默丢失真机实证,宁可诚实示「未知」,
     *  不给假的已开启/已关闭;后续回包先到则本位无效果) */
    internal var queryStuck by observable(false)

    override fun willInit() {
        super.willInit()
        // 首帧前定主题:宿主经 pageData 注入(KuiklyRenderActivity.createPageData)
        ThemeStore.initFromPageData(this)
        // 首帧前播种权限状态(M29):模块回调在本页静默丢失真机实证(两轮 mainHandler 投递
        // 修复未根治),首帧真实状态由宿主直注绕开模块桥——createPageData 对 permissionPage
        // 注快照,四组查询与模块 check 回包同一原生 companion 单源;模块 refresh 仍在(回调
        // 若通,到包同值幂等刷),无快照端(iOS/ohos/H5)保持逐组查询+超时兜底链
        seedFromPageData()
        // 回前台刷新(M36.1):见 PERM_SNAPSHOT_LIVE_KEY 注——宿主落 prefs,本页组合内每秒
        // 轮询同步读(观察 LaunchedEffect 轮询块);M36.0 的 sendEvent 事件推送通道已废
        setContent {
            PermissionPage(this)
        }
    }

    /** 解析宿主快照并播种四组状态(pageData 首帧播种/回前台推送共用;各 checked 即置真,
     *  首帧不闪「查询中」也不吃超时兜底;解析失败/空串静默,走常规查询链) */
    private fun applyPermSnapshot(raw: String) {
        if (raw.isEmpty()) return
        try {
            val snapshot = JSONObject(raw)
            permState = NotifyPermState(
                appGranted = snapshot.optBoolean("appGranted", false),
                channelExists = snapshot.optBoolean("channelExists", false),
                channelBlocked = snapshot.optBoolean("channelBlocked", false),
                channelHigh = snapshot.optBoolean("channelHigh", false),
                dndOn = snapshot.optBoolean("dndOn", false),
                powerSaveOn = snapshot.optBoolean("powerSaveOn", false),
            )
            checked = true
            supported = true
            locGranted = snapshot.optBoolean("granted", false)
            locFine = snapshot.optBoolean("fine", false)
            locCoarse = snapshot.optBoolean("coarse", false)
            locChecked = true
            micGranted = snapshot.optBoolean("micGranted", false)
            micChecked = true
            mediaGranted = snapshot.optBoolean("mediaGranted", false)
            mediaPartial = snapshot.optBoolean("mediaPartial", false)
            mediaChecked = true
        } catch (_: Exception) {
        }
    }

    private fun seedFromPageData() {
        try {
            applyPermSnapshot(pageData.params.optString(PERM_SNAPSHOT_KEY))
        } catch (_: Exception) {
        }
    }

    /** 已落态的宿主快照时间戳(轮询去重:时间戳只在宿主 onResume/弹框结果两处前进,
     *  与之相等=无新快照,跳过;冷启动首读即宿主 onCreate 后 onResume 所写,与 pageData
     *  播种同源同值,幂等落态无感) */
    private var lastAppliedLiveTs = 0L

    /** 轮询宿主落地快照(M36.1,每秒由组合内 LaunchedEffect 驱动):SharedPreferencesModule
     *  同步桥读取(真机已证方向——主题持久化同走此桥;同步返回不经过本页已死的回调通道),
     *  时间戳前进即落态+复位四组在飞申请标记(回调丢失场景 requesting 永真焊死按钮,10s
     *  保险仍在,这里提前到 ≤1s;快照内容不变如弹框点了拒绝,靠时间戳也能推进)。
     *  宿主只在权限页 onResume/弹框结果时写键,无键端(iOS/ohos/H5)空串早退零成本 */
    internal fun pollHostSnapshot() {
        val sp = getModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME) ?: return
        val ts = sp.getString(PERM_SNAPSHOT_LIVE_TS_KEY)
        val raw = sp.getString(PERM_SNAPSHOT_LIVE_KEY)
        val tsVal = if (ts.isEmpty() || ts == "null") 0L else ts.toLongOrNull() ?: return
        if (raw.isEmpty() || raw == "null") return
        if (tsVal == lastAppliedLiveTs) return
        lastAppliedLiveTs = tsVal
        applyPermSnapshot(raw)
        requesting = false
        locRequesting = false
        micRequesting = false
        mediaRequesting = false
    }

    override fun didInit() {
        super.didInit()
        // 无参数端(iOS/鸿蒙/H5)兜底
        ThemeStore.restore(this)
    }

    /** 从系统设置返回即重触发(一页一 Activity:设置页压顶,返回本页 onResume→didAppear)。
     *  Android 真值由宿主落 prefs+本页轮询承载(M36.1:本页模块回调静默丢失[M29 实证],
     *  这四次逐组重查的回调到不了包),此处保留为回调可达端(iOS/ohos/H5)的重查通道 */
    override fun pageDidAppear() {
        super.pageDidAppear()
        refresh()
        refreshLocation()
        refreshMic()
        refreshMedia()
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
        module.checkPermission { state ->
            permState = state
            checked = true
        }
    }

    /** 「去开启」单按钮覆盖全路径(3-skeptic 验证定稿,推翻初版 asked 分支设计):
     *  ① app 总开关已开(仅「悄悄话」渠道被单独关/横幅受限)不走 requestPermission——原生
     *     会 granted 短路零动作假 toast,直跳渠道设置(渠道重要性就在页内,横幅开关也在
     *     渠道页/系统通知页);
     *  ② 总开关关才发运行时申请;granted=true 仍须二段查证(总开关+渠道双开才真已开启,
     *     授权后渠道仍关的场景 toast 会与状态卡矛盾——改带去渠道设置);
     *  ③ denied 一律跳系统设置:33+ 拒绝两次后系统不再弹框但 requestPermissions 仍立即
     *     回调 DENIED——asked 无法区分「真实弹框」与「静默拒绝」,跳设置对首次拒绝
     *     (还能再弹一次)与永久拒绝都是合理引导,不再依赖 asked 区分 */
    internal fun requestThenGuide() {
        if (requesting) return
        val module = getModule<NotificationModule>(NotificationModule.MODULE_NAME) ?: run {
            toastCouple(this, "当前平台暂不支持")
            return
        }
        // 读 observable 快照:按钮/行可点前提是首查完成(checked),permState 非空;状态在页外
        // 被改的过期误差=多跳一次设置,无害(pageDidAppear 已即时重查)
        val state = permState
        if (state != null && state.appGranted) {
            module.openChannelSettings()
            return
        }
        requesting = true
        module.requestPermission { granted, _ ->
            requesting = false
            refresh()
            if (granted) {
                // 授权框放行只代表 app 总开关;渠道状态须重新查证(checkPermission 回包
                // 读到的是授权生效后的最新状态)
                module.checkPermission { fresh ->
                    when {
                        fresh.appGranted && !fresh.channelBlocked ->
                            toastCouple(this, "通知权限已开启")
                        // 总开关开了但渠道仍被单独关:渠道页才是修它的地方
                        fresh.appGranted -> module.openChannelSettings()
                        else -> module.openSettings()
                    }
                }
            } else {
                module.openSettings()
            }
        }
    }

    /** 「前往系统设置」直跳 App 级通知设置(总开关关的万能引导,渠道项也在页内) */
    internal fun openSystemSettings() {
        getModule<NotificationModule>(NotificationModule.MODULE_NAME)?.openSettings()
    }

    /** 重查定位权限(M26:进页首查 + pageDidAppear + 授权回调后刷;结构照 refresh) */
    internal fun refreshLocation() {
        val module = getModule<LocationModule>(LocationModule.MODULE_NAME)
        if (module == null) {
            locSupported = false
            locChecked = true
            return
        }
        locSupported = true
        module.checkLocationPermission { granted, fine, coarse ->
            locGranted = granted
            locFine = fine
            locCoarse = coarse
            locChecked = true
        }
    }

    /** 重查麦克风权限(M29:进页首查 + pageDidAppear + 授权回调后刷;结构照 refreshLocation,
     *  FileModule 桥实现) */
    internal fun refreshMic() {
        val module = getModule<FileModule>(FileModule.MODULE_NAME)
        if (module == null) {
            micSupported = false
            micChecked = true
            return
        }
        micSupported = true
        module.checkMicPermission { granted ->
            micGranted = granted
            micChecked = true
        }
    }

    /** 重查相册读权限(M29:进页首查 + pageDidAppear + 授权回调后刷;结构照 refreshMic) */
    internal fun refreshMedia() {
        val module = getModule<FileModule>(FileModule.MODULE_NAME)
        if (module == null) {
            mediaSupported = false
            mediaChecked = true
            return
        }
        mediaSupported = true
        module.checkMediaPermission { granted, partial ->
            mediaGranted = granted
            mediaPartial = partial
            mediaChecked = true
        }
    }

    /** 「去开启」定位(通知行 3-skeptic 定稿同款简化):denied 一律跳系统设置(拒绝两次后
     *  系统静默拒绝仍回调 DENIED,asked 无法区分真假弹框,跳设置是万能引导);granted 重查刷状态 */
    internal fun requestLocThenGuide() {
        if (locRequesting) return
        val module = getModule<LocationModule>(LocationModule.MODULE_NAME) ?: run {
            toastCouple(this, "当前平台暂不支持")
            return
        }
        locRequesting = true
        module.requestLocationPermission { granted, _ ->
            locRequesting = false
            refreshLocation()
            if (granted) {
                toastCouple(this, "定位权限已开启")
            } else {
                module.openSettings()
            }
        }
    }

    /** 「去开启」麦克风(M29,通知/定位同款简化):denied 一律跳应用详情页(拒绝两次后系统
     *  静默拒绝仍回调 DENIED,asked 区分不了真假弹框,跳设置是万能引导);granted 重查刷状态 */
    internal fun requestMicThenGuide() {
        if (micRequesting) return
        val module = getModule<FileModule>(FileModule.MODULE_NAME) ?: run {
            toastCouple(this, "当前平台暂不支持")
            return
        }
        micRequesting = true
        module.requestMicPermission { granted, _ ->
            micRequesting = false
            refreshMic()
            if (granted) {
                toastCouple(this, "麦克风权限已开启")
            } else {
                module.openAppDetails()
            }
        }
    }

    /** 「去开启」相册读权限(M29,同款简化):denied 一律跳应用详情页;granted/partial 重查
     *  刷状态(partial=Android14「仅选中照片」,行尾展示部分授权态) */
    internal fun requestMediaThenGuide() {
        if (mediaRequesting) return
        val module = getModule<FileModule>(FileModule.MODULE_NAME) ?: run {
            toastCouple(this, "当前平台暂不支持")
            return
        }
        mediaRequesting = true
        module.requestMediaPermission { granted, _, _ ->
            mediaRequesting = false
            refreshMedia()
            if (granted) {
                toastCouple(this, "相册权限已开启")
            } else {
                module.openAppDetails()
            }
        }
    }

    /** 跳「悄悄话」渠道级设置(渠道行/悬浮横幅引导行的落点;未桥接端 getModule null 静默降级) */
    internal fun openChannelSettings() {
        getModule<NotificationModule>(NotificationModule.MODULE_NAME)?.openChannelSettings()
    }

    /** 跳应用详情页(定位权限的权限获取页:「权限 → 位置权限」所在;已开启态行点击落这里,
     *  可查看/改授权;未桥接端 getModule null 静默降级) */
    internal fun openLocationSettings() {
        getModule<LocationModule>(LocationModule.MODULE_NAME)?.openSettings()
    }

    /** 跳应用详情页(麦克风/相册读权限的权限获取页:「权限 → 麦克风/照片和视频」所在;
     *  已开启/部分授权态行点击落这里,可查看/改授权;未桥接端 getModule null 静默降级) */
    internal fun openAppDetails() {
        getModule<FileModule>(FileModule.MODULE_NAME)?.openAppDetails()
    }
}

@Composable
internal fun PermissionPage(pager: PermissionPager) {
    val theme = ThemeStore.current
    // 进页首查(独立 @Page 组合在即活动在;从设置返回/弹框结果 Android 由宿主 prefs+轮询兜、
    // 余端 pageDidAppear 模块重查兜);通知/定位/麦克风/相册读四组并行,随后常驻轮询宿主
    // 落地快照(M36.1):每秒同步读一次 prefs,时间戳前进即落态——设置返回 onResume 与弹框
    // 出结果 onRequestPermissionsResult 宿主都会推进时间戳,两条路径统一覆盖(≤1s 延迟);
    // 组合即页面在(一页一 Activity),无需挂起条件,页销组合撤轮询随亡
    LaunchedEffect(Unit) {
        pager.refresh()
        pager.refreshLocation()
        pager.refreshMic()
        pager.refreshMedia()
        while (isActive) {
            delay(1_000)
            pager.pollHostSnapshot()
        }
    }
    // 查询失败兜底(M29):模块回调静默丢失真机实证(通知/定位首查双双不回=页面永挂「查询中」),
    // 4s 后仍有任一组未回包 → 全部行落「状态未知」诚实降级,绝不挂死;回包先到则置真无效果
    LaunchedEffect(Unit) {
        delay(4000)
        if (!pager.checked || !pager.locChecked || !pager.micChecked || !pager.mediaChecked) {
            pager.queryStuck = true
        }
    }
    // 申请在飞保险(M29):回调丢失场景 requesting 永真会焊死「去开启」按钮与行点击,10s 复位
    // 自愈(结果真到达仍走各自回包收口刷新,双到达无害);只解交互锁,不做状态断言
    LaunchedEffect(Unit) {
        delay(10000)
        pager.requesting = false
        pager.locRequesting = false
        pager.micRequesting = false
        pager.mediaRequesting = false
    }

    // 派生态:总开关开 + 渠道未被单独关 = 消息通知可用(checked 前不置真,防「已开启」闪错)
    val st = pager.permState
    val enabled = pager.checked && (st?.appGranted ?: false) && !(st?.channelBlocked ?: false)

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                AppSubPageHeader(
                    title = "权限管理",
                    subtitle = "查看与开启应用权限",
                    onBack = {
                        pager.closeLovePage()
                    },
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AppMenuGroup {
                        // ① 消息通知:App 级总开关断言行
                        AppMenuRow(
                            icon = AppIconType.Bell,
                            iconTint = Color(0xFFFF4D6A),
                            iconBg = Color(0xFFFFE9ED),
                            title = "消息通知",
                            // 已关闭时整行可点=同「去开启」;已开启时行点击无动作
                            onClick = { if (pager.checked && !enabled) pager.requestThenGuide() },
                        ) {
                            when {
                                // 查询失败兜底:4s 未回包落「状态未知」,绝不挂空白(命中测试铁律:
                                // 胶囊包 clickable 转发行动作,未知态同样可点=同「去开启」)
                                !pager.checked && pager.queryStuck -> Box(
                                    modifier = Modifier.clickable { pager.requestThenGuide() },
                                ) {
                                    AppTag(
                                        "状态未知",
                                        bgColor = AppTheme.Divider,
                                        textColor = AppTheme.TextDisabled,
                                    )
                                }
                                !pager.checked -> {}
                                !pager.supported -> AppTag(
                                    "不支持",
                                    bgColor = AppTheme.Divider,
                                    textColor = AppTheme.TextDisabled,
                                )
                                enabled -> AppTag(
                                    "已开启",
                                    bgColor = AppTheme.Success.copy(alpha = 0.12f),
                                    textColor = AppTheme.Success,
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
                        // ② 「悄悄话」渠道:渠道级断言行(重要性被调低=横幅受限)
                        AppMenuRow(
                            icon = AppIconType.Chat,
                            iconTint = Color(0xFF7C5CF0),
                            iconBg = Color(0xFFF0ECFF),
                            title = "「悄悄话」渠道",
                            // 点击跳渠道设置:正常态也值得去核对(渠道页就是横幅开关所在页)
                            onClick = { pager.openChannelSettings() },
                        ) {
                            when {
                                // 查询失败兜底(与通知行同一回包源,同落「状态未知」;点击仍是渠道设置)
                                !pager.checked && pager.queryStuck -> AppTag(
                                    "状态未知",
                                    bgColor = AppTheme.Divider,
                                    textColor = AppTheme.TextDisabled,
                                )
                                !pager.checked -> {}
                                !pager.supported -> AppTag(
                                    "不支持",
                                    bgColor = AppTheme.Divider,
                                    textColor = AppTheme.TextDisabled,
                                )
                                st == null -> {}
                                !st.channelExists -> AppTag(
                                    // API<26 无渠道概念:横幅由通知 PRIORITY 决定,随系统总开关
                                    "跟随系统",
                                    bgColor = AppTheme.Divider,
                                    textColor = AppTheme.TextDisabled,
                                )
                                st.channelBlocked -> channelTag(pager) {
                                    AppTag(
                                        "已关闭",
                                        bgColor = AppTheme.Danger.copy(alpha = 0.12f),
                                        textColor = AppTheme.Danger,
                                    )
                                }
                                !st.channelHigh -> channelTag(pager) {
                                    AppTag(
                                        "横幅受限",
                                        bgColor = AppTheme.Warning.copy(alpha = 0.12f),
                                        textColor = AppTheme.Warning,
                                    )
                                }
                                else -> channelTag(pager) {
                                    AppTag(
                                        "正常",
                                        bgColor = AppTheme.Success.copy(alpha = 0.12f),
                                        textColor = AppTheme.Success,
                                    )
                                }
                            }
                        }
                        AppMenuDivider()
                        // ③ 悬浮横幅提醒:引导行恒不置断言——横幅=渠道≥HIGH 只是必要条件,
                        // 国产 ROM 另有系统级「悬浮通知」开关且无标准查询 API,恒示「需手动确认」
                        AppMenuRow(
                            icon = AppIconType.Info,
                            iconTint = AppTheme.Warning,
                            iconBg = Color(0xFFFFF4E5),
                            title = "悬浮横幅提醒",
                            onClick = { pager.openChannelSettings() },
                        ) {
                            when {
                                // 查询失败兜底(与通知行同一回包源;点击仍是渠道设置人工核对)
                                !pager.checked && pager.queryStuck -> AppTag(
                                    "状态未知",
                                    bgColor = AppTheme.Divider,
                                    textColor = AppTheme.TextDisabled,
                                )
                                !pager.checked -> {}
                                !pager.supported -> AppTag(
                                    "不支持",
                                    bgColor = AppTheme.Divider,
                                    textColor = AppTheme.TextDisabled,
                                )
                                // 胶囊带背景吃 tap(命中测试铁律),包 clickable 转发行动作
                                else -> Box(
                                    modifier = Modifier.clickable { pager.openChannelSettings() },
                                ) {
                                    AppTag(
                                        "需手动确认",
                                        bgColor = AppTheme.Divider,
                                        textColor = AppTheme.TextDisabled,
                                    )
                                }
                            }
                        }
                        AppMenuDivider()
                        // ④ 相册与图片(M29 状态化,修正旧「无需授权」失实——M24 十三轮起聊天主
                        //  路径为应用内相册弹层,读媒体库运行时权限):完整授权=已开启 / Android14
                        //  「仅选中照片」=部分授权 / 未授权=已关闭可申请
                        AppMenuRow(
                            icon = AppIconType.Image,
                            iconTint = Color(0xFF6C7BFF),
                            iconBg = Color(0xFFEDEFFF),
                            title = "相册与图片",
                            onClick = {
                                if (pager.mediaChecked && pager.mediaSupported) {
                                    if (pager.mediaGranted || pager.mediaPartial) {
                                        pager.openAppDetails()
                                    } else {
                                        pager.requestMediaThenGuide()
                                    }
                                }
                            },
                        ) {
                            when {
                                !pager.mediaChecked && pager.queryStuck -> unknownTag()
                                !pager.mediaChecked -> {}
                                !pager.mediaSupported -> AppTag(
                                    "不支持",
                                    bgColor = AppTheme.Divider,
                                    textColor = AppTheme.TextDisabled,
                                )
                                // 已开启/部分授权:点击进应用详情页(权限获取页,可查看/改授权;
                                // 部分授权的「选择更多照片」升级入口也在那里)。胶囊带背景原生视图
                                // 吃 tap(命中测试铁律),包 clickable 转发行动作
                                pager.mediaGranted -> Box(
                                    modifier = Modifier.clickable { pager.openAppDetails() },
                                ) {
                                    AppTag(
                                        "已开启",
                                        bgColor = AppTheme.Success.copy(alpha = 0.12f),
                                        textColor = AppTheme.Success,
                                    )
                                }
                                pager.mediaPartial -> Box(
                                    modifier = Modifier.clickable { pager.openAppDetails() },
                                ) {
                                    AppTag(
                                        "部分授权",
                                        bgColor = AppTheme.Warning.copy(alpha = 0.12f),
                                        textColor = AppTheme.Warning,
                                    )
                                }
                                else -> Box(
                                    modifier = Modifier.clickable { pager.requestMediaThenGuide() },
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
                        // ⑤ 麦克风(M29 新增,发语音消息用 RECORD_AUDIO):已开启=点击进应用详情页;
                        // 已关闭=行点击运行时申请,被拒带去应用详情页
                        AppMenuRow(
                            icon = AppIconType.Mic,
                            iconTint = Color(0xFFE0559B),
                            iconBg = Color(0xFFFBE8F1),
                            title = "麦克风",
                            onClick = {
                                if (pager.micChecked && pager.micSupported) {
                                    if (pager.micGranted) {
                                        pager.openAppDetails()
                                    } else {
                                        pager.requestMicThenGuide()
                                    }
                                }
                            },
                        ) {
                            when {
                                !pager.micChecked && pager.queryStuck -> unknownTag()
                                !pager.micChecked -> {}
                                !pager.micSupported -> AppTag(
                                    "不支持",
                                    bgColor = AppTheme.Divider,
                                    textColor = AppTheme.TextDisabled,
                                )
                                pager.micGranted -> Box(
                                    modifier = Modifier.clickable { pager.openAppDetails() },
                                ) {
                                    AppTag(
                                        "已开启",
                                        bgColor = AppTheme.Success.copy(alpha = 0.12f),
                                        textColor = AppTheme.Success,
                                    )
                                }
                                else -> Box(
                                    modifier = Modifier.clickable { pager.requestMicThenGuide() },
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
                        // ⑥ 定位权限(M26):位置页前置(FINE/COARSE 任一授权即放行)。行点击恒有响应——
                        // 未开启=获取权限(运行时申请;被拒[含 33+ 两次后系统静默拒绝,弹框不再出]
                        // 一律带去应用详情页);已开启=行尾追加精度位展示已获取的权限+跳应用详情页
                        // 「权限 → 位置权限」(权限获取页,可查看/改授权;系统无直达权限子页的
                        // 公开 API,应用详情页即落点)
                        AppMenuRow(
                            icon = AppIconType.Location,
                            iconTint = Color(0xFF3AA6DC),
                            iconBg = Color(0xFFE7F5FC),
                            title = "定位权限",
                            onClick = {
                                if (pager.locChecked && pager.locSupported) {
                                    if (pager.locGranted) {
                                        pager.openLocationSettings()
                                    } else {
                                        pager.requestLocThenGuide()
                                    }
                                }
                            },
                        ) {
                            when {
                                !pager.locChecked && pager.queryStuck -> unknownTag()
                                !pager.locChecked -> {}
                                !pager.locSupported -> AppTag(
                                    "不支持",
                                    bgColor = AppTheme.Divider,
                                    textColor = AppTheme.TextDisabled,
                                )
                                // 已开启:行尾追加已获取的权限(FINE=精确定位,否则 COARSE=大致位置);
                                // 两枚胶囊带背景原生视图吃 tap(命中测试铁律),各包 clickable 转发
                                pager.locGranted -> Row {
                                    Box(
                                        modifier = Modifier.clickable { pager.openLocationSettings() },
                                    ) {
                                        AppTag(
                                            "已开启",
                                            bgColor = AppTheme.Success.copy(alpha = 0.12f),
                                            textColor = AppTheme.Success,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier.clickable { pager.openLocationSettings() },
                                    ) {
                                        AppTag(
                                            if (pager.locFine) "精确定位" else "大致位置",
                                            bgColor = AppTheme.Divider,
                                            textColor = AppTheme.TextDisabled,
                                        )
                                    }
                                }
                                // 已关闭胶囊:带背景原生视图吃 tap(命中测试铁律),包 clickable 转发
                                else -> Box(
                                    modifier = Modifier.clickable { pager.requestLocThenGuide() },
                                ) {
                                    AppTag(
                                        "已关闭",
                                        bgColor = AppTheme.Danger.copy(alpha = 0.12f),
                                        textColor = AppTheme.Danger,
                                    )
                                }
                            }
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
                            // 标题+正文同一条件梯子出对(statusCopy 单源,防两份 when 漂移)
                            val (statusLine, statusDetail) = statusCopy(pager)
                            Text(
                                statusLine,
                                color = AppTheme.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(statusDetail, color = AppTheme.TextSecondary, fontSize = 12.sp)
                            // 勿扰/省电条件提示:两者都压制横幅,只在激活时显示
                            if (pager.supported && pager.checked && st?.dndOn == true) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "勿扰模式开启中：横幅与提示音会被系统屏蔽，通知栏仍会收到。",
                                    color = AppTheme.TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                            if (pager.supported && pager.checked && st?.powerSaveOn == true) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "省电模式开启中：部分手机会临时关闭横幅提醒。",
                                    color = AppTheme.TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
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
                                "悬浮横幅（横幅通知）还受手机系统「悬浮通知」开关影响；若通知栏有提醒但屏幕顶部无横幅，点上方「悬浮横幅提醒」前往系统通知设置确认。",
                                color = AppTheme.TextSecondary,
                                fontSize = 12.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "相册选图在应用内相册页完成，需要「照片和视频」读取权限（Android 14 及以上可选择仅授权部分照片）；麦克风仅在你发语音消息时申请；定位权限仅在位置页会话期间使用：进入位置页自动共享、退出位置页即停；点上方「定位权限」行可去授权，已开启时行尾展示已获取的精度（精确定位/大致位置），点击可进系统权限页查看或修改；不申请通讯录等其他敏感权限。",
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

/** 渠道行可点胶囊壳(命中测试铁律:带背景胶囊吃 tap,包 clickable 转发渠道行动作) */
@Composable
private fun channelTag(pager: PermissionPager, tag: @Composable () -> Unit) {
    Box(modifier = Modifier.clickable { pager.openChannelSettings() }) {
        tag()
    }
}

/** 「状态未知」胶囊(M29 查询失败兜底,各行共用):4s 未回包的诚实降级,中性灰不给假状态 */
@Composable
private fun unknownTag() {
    AppTag(
        "状态未知",
        bgColor = AppTheme.Divider,
        textColor = AppTheme.TextDisabled,
    )
}

/** 状态详情卡「标题+正文」映射(单一条件梯子同出对文案,防标题/正文两份 when 漂移;组合期
 *  纯函数;!checked 前置防首查回包前按缺省闪「已开启」——行内胶囊有同款守卫,卡片不能漏) */
private fun statusCopy(pager: PermissionPager): Pair<String, String> {
    val st = pager.permState
    return when {
        !pager.supported ->
            "当前平台暂不支持" to "权限查询与授权仅 Android 端提供；其他端留意应用内红点提醒即可。"
        // 查询失败兜底(M29):「正在查询」永不超时是缺陷,4s 后改口「查询失败」
        !pager.checked && pager.queryStuck ->
            "状态查询失败" to
                "权限状态没能取回（页面数据通道异常）。退出本页重新进入可重试；若反复出现请反馈开发者。"
        !pager.checked -> "正在查询通知状态…" to " "
        // 不可达兜底(checked 置真时 permState 必已落),防御式走乐观文案
        st == null -> "通知已开启" to "对方发来悄悄话时，会在通知栏与横幅提醒你。"
        !st.appGranted ->
            "通知已关闭" to
                "收不到对方消息的后台提醒。点「去开启」重新授权；若系统不再弹框，请到系统设置手动开启。"
        st.channelBlocked ->
            "「悄悄话」渠道已关闭" to
                "应用通知已开启，但「悄悄话」渠道在系统设置中被单独关闭，点上方「悄悄话」渠道行去重新打开。"
        st.channelExists && !st.channelHigh ->
            "横幅可能不显示" to
                "「悄悄话」渠道的重要性被调低，横幅（悬浮通知）不会弹出。点上方渠道行把重要性设为「高」。"
        else ->
            "通知已开启" to
                "对方发来悄悄话时，会在通知栏与横幅提醒你。悬浮横幅还取决于手机系统的「悬浮通知」开关，若未弹横幅，点上方「悬浮横幅提醒」去确认。"
    }
}
