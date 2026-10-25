package com.zyc.love_baby.features.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.ModalBottomSheet
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.toArgb
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable
import com.zyc.love_baby.base.AMapNativeMap
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.base.LocationModule
import com.zyc.love_baby.base.openLovePage
import com.zyc.love_baby.components.AppFeedback
import com.zyc.love_baby.components.AppPillChip
import com.zyc.love_baby.components.AppPrimaryButton
import com.zyc.love_baby.components.AppSegmentedChips
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.KuiklyTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.components.statusBarPaddingDp
import com.zyc.love_baby.data.model.CoupleInfo
import com.zyc.love_baby.data.model.LocationPlaceInfo
import com.zyc.love_baby.data.model.LocationPointInfo
import com.zyc.love_baby.data.model.UserProfile
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.LocationApi
import com.zyc.love_baby.network.UserApi
import com.zyc.love_baby.state.CoupleStore
import com.zyc.love_baby.state.LocationStore
import com.zyc.love_baby.state.nowEpochMs
import com.zyc.love_baby.state.relativeTimeLabel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.round
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** locationPage 路由名(openLocationPage 与 @Page 注册名共用,勿散落字面量) */
internal const val LOCATION_PAGE_NAME = "locationPage"

/** 路由打开位置页(base.openLovePage 收口,try/catch 吞未桥接端在 helper 内统一处理) */
internal fun openLocationPage(pager: Pager) {
    pager.openLovePage(LOCATION_PAGE_NAME)
}

/**
 * 页内会话节奏(M27 会话期,对标微信共享实时位置观感):5s 一拍(仅上报自己,原生单飞
 * 在飞即弃);每 6 拍(30s)拉一次双方 latest 兜底——主路径是 WS 实时帧
 * (love-location-new,TA 上报秒推),latest 只负责纠偏(TA 关共享/帧丢失)
 */
private const val LOCATION_POLL_INTERVAL_MS = 5_000L
private const val LOCATION_LATEST_EVERY_BEATS = 6

/** 轨迹目标(trail 接口 target 参数口径) */
private const val TARGET_SELF = "self"
private const val TARGET_PARTNER = "partner"

/**
 * LocationPage — 位置共享页(M27 全屏原生动态地图版;M26 静态图卡片布局退役;M29 会话共享改版)
 * 用法:HomePage 快捷入口「位置轨迹」/ PermissionPage「定位权限」行(已授权)openLocationPage
 *      打开;左上返回钮 closePage
 * 依赖:base/AMapNativeMap(Compose 侧原生地图声明,Android 壳 KRAMapView 消费,头像
 *      Marker)+ state/LocationStore(快照单例)+base/LocationModule(定位与权限,未桥接端
 *      getModule null→整页降级)+ network/UserApi(我的资料,marker 头像)+ state/CoupleStore
 *      (TA 昵称头像);regeo 在 LocationStore 内经 AMapApi(Web 服务 key)
 * 事件:返回钮 closePage;进页权限链「查权限→未授权运行时申请→拒绝引导 openSettings」,
 *      遮罩卡「去开启」重试同链;「回到视野」fitTrigger++ 触发相机包络;「行动轨迹」弹
 *      ModalBottomSheet 半层(dismissOnDrag=false 铁律)
 * 功能:
 *  - 会话共享(M29 去开关化,对标微信共享实时位置):页内不再有共享开关——进页权限就绪即
 *    updateShare(true) 进入共享会话,回主页(MainPager 前台钩子 closeShareSession)即关;
 *    强隐私 §12.4 由「会话」承载,不在会话中时服务端同闸拒收上报
 *  - 进页权限链:「一进来没权限就让给」——查权限,未授权直接出系统运行时弹框;拒绝(含
 *    两次后系统静默拒绝)统一 openSettings+toast,页内显遮罩卡(说明+「去开启」重试);
 *    从系统设置回来(refreshTick)被动重查,开了即时接管,不再主动弹框
 *  - 全屏地图双头像 marker(我=主题渐变圆,TA=品牌洗底圆;有头像 URL 走 Glide 圆裁):
 *    组合期组 JSON 下发,点位变化(5s 上报回点/WS 实时帧)自动重组即时落 marker;
 *    TA 关共享 → 列表缺席 → 原生移除 marker(消失即语义)
 *  - 顶部悬浮胶囊:实时态圆点+我的地址(regeo >100m 才重查)+相对时间;左上返回钮
 *  - 会话节奏:权限就绪后 5s 上报一拍(enable 失败随拍重试)+每 6 拍(30s)latest 兜底
 *    与地址;双方都开页 = 秒级互相刷新(TA 上报→服务端 WS 推→我落 marker)
 *  - 底部悬浮条:「行动轨迹」/「回到视野」两枚等宽大按钮(原「共享开关行+双按钮行」两行
 *    结构随开关退役,M29 收为单行);TA 未开启共享时条上一行琥珀轻提示
 *  - 行动轨迹半层(M28 地点列表,M28.1 真机定案纯列表化):今天/昨天/近7天 + 我/TA chips +
 *    地点记录列表(280dp 定高,POI/省市区+到达时间,30min 粒度+换地 ≥300m 服务端落库);
 *    TA 页签「提醒 TA」pill(WS 催对方上报);点条目关闭半层 → 跳 trailPlacePage 独立
 *    回看页(原生地图 focusPoint 品牌圆点+飞 16 级)。M28 的「半层静态轨迹图 + 主地图
 *    回看」退役:静态图 trailPoints==null 无失败分支恒卡「加载中」(真机实锤),主地图
 *    回看又被半层遮挡观感割裂——列表 + 独立回看页直达
 * 重组域(2026-10-25 流畅度定稿):本页每 5s 落新点,顶层只持有低频状态——点位/地址/
 * 权限态分别下沉到 LiveMapLayer/AddressCapsule/PartnerHintBar/底部按钮条/PermissionHintCard
 * 各自直读 store 或页态,点变化只重组地图层与顶栏,不整页重 diff(铁律 10② 精神:状态在叶子读)
 */
@Page("locationPage", supportInLocal = true)
internal class LocationPager : BasePager() {

    /** 回前台补拉 tick(pageDidAppear 单 Activity 一页模型下=App 回前台;0=冷启动首现跳过) */
    internal var refreshTick by observable(0)

    override fun willInit() {
        super.willInit()
        ThemeStore.initFromPageData(this)
        setContent {
            LocationPage(this)
        }
    }

    override fun didInit() {
        super.didInit()
        ThemeStore.restore(this)
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        refreshTick++
    }
}

@Composable
internal fun LocationPage(pager: LocationPager) {
    val theme = ThemeStore.current
    var supported by remember { mutableStateOf(true) }
    // 定位权限三态(null=首查中,true=已授权,false=已拒绝[含引导系统设置后仍未开];
    // 驱动遮罩卡与会话循环,false 时地图仍铺底但无点可落)
    var permGranted by remember { mutableStateOf<Boolean?>(null) }
    // 「去开启」重试在飞(防重复弹框)
    var requesting by remember { mutableStateOf(false) }
    var trailSheetOn by remember { mutableStateOf(false) }
    // 我的资料(marker 头像/首字;进页拉一次,失败空串走文字圆兜底)
    var myProfile by remember { mutableStateOf<UserProfile?>(null) }
    // 相机包络触发序号(「回到视野」手动累加;与图层内自动 fit 序号求和单调递增)
    var fitTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // 进页:拉我的资料 + 权限链(查权限→未授权直接出系统运行时弹框→拒绝引导系统设置)。
    // 「一进来没权限就让给」即此链——不再有确认浮层与共享开关(M29 会话共享)
    LaunchedEffect(Unit) {
        val module = pager.getModule<LocationModule>(LocationModule.MODULE_NAME)
        if (module == null) {
            supported = false
            return@LaunchedEffect
        }
        val result = UserApi.getUser(pager)
        if (result is ApiResult.Ok) {
            myProfile = UserProfile.from(result.data)
        }
        var granted = checkPermission(module)
        if (!granted) granted = requestPermission(module)
        if (!granted) {
            module.openSettings()
            AppFeedback.toast(pager, "需要定位权限,请前往系统设置开启")
        }
        permGranted = granted
    }
    // 回前台/回页补拉(refreshTick>0):从系统设置回来时被动重查权限(设置里开了即时接管,
    // 不主动再弹框);latest/地址补拉与 doReport 并发由 store 单飞去重
    LaunchedEffect(pager.refreshTick) {
        if (pager.refreshTick > 0 && supported) {
            if (permGranted == false) {
                val module = pager.getModule<LocationModule>(LocationModule.MODULE_NAME)
                if (module != null && checkPermission(module)) {
                    permGranted = true
                }
            }
            LocationStore.refresh(pager)
            LocationStore.myPoint?.let { LocationStore.resolveAddress(pager, it.lat, it.lng) }
        }
    }
    // 会话循环(权限就绪后启):进页即开共享(M29 会话语义,enable 失败随拍重试);5s 上报
    // 一拍(原生单飞在飞即弃),每 6 拍(30s)latest 兜底+地址——主路径是 WS 实时帧
    // (love-location-new,TA 上报秒推),latest 只负责纠偏(TA 关共享/帧丢失)
    LaunchedEffect(permGranted) {
        if (permGranted != true) return@LaunchedEffect
        var beat = 0
        while (true) {
            if (!LocationStore.locationShareEnabled) {
                LocationStore.updateShare(pager, true)
            }
            LocationStore.doReport(pager)
            if (beat % LOCATION_LATEST_EVERY_BEATS == 0) {
                LocationStore.refreshLatest(pager)
                LocationStore.myPoint?.let { LocationStore.resolveAddress(pager, it.lat, it.lng) }
            }
            beat++
            delay(LOCATION_POLL_INTERVAL_MS)
        }
    }

    // 遮罩卡「去开启」:重走申请链——首次拒绝后系统还能再弹;两次拒绝后系统静默 DENIED,
    // asked 无法区分真假弹框,统一带去系统设置(万能引导)
    fun retryPermission() {
        if (requesting) return
        val module = pager.getModule<LocationModule>(LocationModule.MODULE_NAME) ?: return
        scope.launch {
            requesting = true
            val granted = requestPermission(module)
            requesting = false
            if (granted) {
                permGranted = true
            } else {
                module.openSettings()
                AppFeedback.toast(pager, "需要定位权限,请前往系统设置开启")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
        if (!supported) {
            // 未桥接端(iOS/ohos/H5)整页降级:居中卡 + 返回钮
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                ) {
                    Column {
                        Text(
                            "当前设备暂不支持位置共享",
                            color = AppTheme.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "位置功能即将支持更多机型,敬请期待",
                            color = AppTheme.TextTertiary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            BackFloatingButton {
                pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
            }
        } else {
            // ---- 全屏原生动态地图(头像 marker+精度圈;Android key 未配时瓦片空白,功能不崩) ----
            LiveMapLayer(
                myProfile = myProfile,
                fitTrigger = fitTrigger,
                modifier = Modifier.fillMaxSize(),
            )

            // 顶部:返回钮 + 我的地址/实时态胶囊
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = statusBarPaddingDp() + 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackFloatingButton {
                    pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                }
                Spacer(modifier = Modifier.width(8.dp))
                AddressCapsule(modifier = Modifier.weight(1f))
            }

            // 底部:TA 未开启提示 + 按钮条(原「共享开关行+双按钮行」两行结构随开关退役,
            // M29 收为单行两枚等宽大按钮)
            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = navBarSafePaddingDp() + 16.dp),
            ) {
                PartnerHintBar()
                Row {
                    ActionButton(
                        text = "行动轨迹",
                        modifier = Modifier.weight(1f),
                        bgBrush = theme.brandBrush,
                        textColor = Color.White,
                        onClick = { trailSheetOn = true },
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    ActionButton(
                        text = "回到视野",
                        modifier = Modifier.weight(1f),
                        bg = theme.brandStart.copy(alpha = 0.12f),
                        textColor = theme.brandStart,
                        onClick = { fitTrigger++ },
                    )
                }
            }

            // 行动轨迹半层(ModalBottomSheet;⚠ dismissOnDrag 必须保持 false——
            // 官方 true 时 detectVerticalDragGestures 挂整个 Surface 与内容滚动抢事件,
            // MediaPickerSheet 十五轮真机实锤;状态全部内聚在 TrailSheet,关层即弃;
            // M28.1 点地点条目 = 关半层 + 跳 trailPlacePage 独立回看页)
            if (trailSheetOn) {
                TrailSheet(
                    pager = pager,
                    onDismiss = { trailSheetOn = false },
                    onPickPlace = { place ->
                        trailSheetOn = false
                        openTrailPlacePage(
                            pager,
                            place.lat,
                            place.lng,
                            placePrimaryLabel(place),
                            placeSecondaryLabel(place),
                        )
                    },
                )
            }

            // 权限未就绪遮罩卡(地图铺底但无点可落;左上返回钮不受影响)
            if (permGranted == false) {
                PermissionHintCard(requesting = requesting, onRequest = { retryPermission() })
            }
        }
    }
}

/**
 * 地图层:全屏原生动态地图,点位数据唯一消费者。直读 store 双方点/共享态并组装
 * marker JSON(每 5s 拍随点位重组重建,小串直组代价可忽略,正确性优先);自动 fit 与
 * 手动「回到视野」两个触发序号求和单调递增下发(每次 +1 恰好触发一次相机包络);
 * 回看标注(M28 focus 通道)自 M28.1 起由 trailPlacePage 独立页承担,本层不再透传
 */
@Composable
private fun LiveMapLayer(
    myProfile: UserProfile?,
    fitTrigger: Int,
    modifier: Modifier,
) {
    val theme = ThemeStore.current
    val myPoint = LocationStore.myPoint
    // TA 关共享 → 列表缺席 → 原生移除其 marker(消失即语义)
    val partnerPoint = if (LocationStore.partnerShareEnabled) LocationStore.partnerPoint else null
    val couple = CoupleStore.couple
    val pointsJson = buildPointsJson(myPoint, partnerPoint, myProfile, couple, theme)
    // 自动 fit:首点/对方首点出现各一次(后续位移只动 marker 不拉相机,防 5s 拖拽视野)
    var autoFitSeq by remember { mutableStateOf(0) }
    LaunchedEffect(myPoint != null, partnerPoint != null) {
        if (myPoint != null || partnerPoint != null) {
            autoFitSeq++
        }
    }
    AMapNativeMap(
        pointsJson = pointsJson,
        fitTrigger = fitTrigger + autoFitSeq,
        modifier = modifier,
    )
}

/** 左上返回钮(全屏地图无页头,白圆 ‹;降级态与地图态共用;trailPlacePage 复用,M28.1) */
@Composable
internal fun BackFloatingButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp)
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
            .clickable { onBack() },
        contentAlignment = Alignment.Center,
    ) {
        Text("‹", color = AppTheme.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 我的地址胶囊:实时态圆点(绿=有定位,灰=定位中)+ regeo 地址(>100m 才重查,缓存于
 * store)+ 「实时 · 相对时间」;无地址降级显坐标。点位/地址直读 store,整页不为其重组合
 */
@Composable
private fun AddressCapsule(modifier: Modifier = Modifier) {
    val myPoint = LocationStore.myPoint
    val address = LocationStore.myAddress
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(6.dp)
                    .background(
                        if (myPoint != null) Color(0xFF34C77B) else Color(0xFFC9CDD6),
                        RoundedCornerShape(3.dp),
                    ),
            )
            Spacer(modifier = Modifier.width(7.dp))
            Column {
                Text(
                    when {
                        myPoint == null -> "定位中…"
                        address.isNotEmpty() -> address
                        else -> "${round4(myPoint.lat)}, ${round4(myPoint.lng)}"
                    },
                    color = AppTheme.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (myPoint != null) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        "实时 · " + relativeTimeLabel(myPoint.reportTime).ifEmpty { "刚刚" },
                        color = AppTheme.TextTertiary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

/**
 * TA 未开启共享轻提示(琥珀暖底+圆点;轮询 shareEnabled=false 且 latest 成功过才现身)。
 * 状态直读 store,条件不满足时零组合
 */
@Composable
private fun PartnerHintBar() {
    val partnerShareEnabled = LocationStore.partnerShareEnabled
    val latestLoaded = LocationStore.latestLoaded
    if (partnerShareEnabled || !latestLoaded) return
    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFFFFF4E5), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(6.dp)
                    .background(Color(0xFFFFB37A), RoundedCornerShape(3.dp)),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "TA 未开启位置共享,开启后这里能看到 TA 的实时位置",
                color = Color(0xFFB25E09),
                fontSize = 11.sp,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * 权限未就绪遮罩卡(permGranted=false 时居中;「未桥接端」降级卡同语言):说明 + 「去开启」
 * 重试(重走申请链,再拒→系统设置)。会话共享的强隐私说明在卡内如实告知:退出本页即停
 */
@Composable
private fun PermissionHintCard(requesting: Boolean, onRequest: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Column {
                Text(
                    "需要定位权限",
                    color = AppTheme.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "开启后才能上报你的位置,与 TA 互看实时位置与行动轨迹;退出本页共享即停。",
                    color = AppTheme.TextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppPrimaryButton(
                    text = "去开启",
                    submitting = requesting,
                    busyText = "请求中…",
                ) { onRequest() }
            }
        }
    }
}

/**
 * 等宽大按钮(40dp 高圆角 12dp):渐变底走 brush 重载、纯色底走 color 重载——同一节点
 * 两重载不跨状态切换(铁律 7:背景分支必须同参类型),每枚按钮的形态在生命周期内固定不互换
 */
@Composable
private fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    bg: Color = Color.Transparent,
    bgBrush: Brush? = null,
    textColor: Color,
    onClick: () -> Unit,
) {
    val base = modifier.height(40.dp)
    val shaped = if (bgBrush != null) {
        base.background(bgBrush, RoundedCornerShape(12.dp))
    } else {
        base.background(bg, RoundedCornerShape(12.dp))
    }
    Box(
        modifier = shaped.clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 行动轨迹半层(M28 地点列表,M28.1 真机定案纯列表化——静态轨迹图退役):今天/昨天/近7天
 * + 我/TA chips + 地点记录列表。状态全部内聚于此,关层即弃,页面 scope 不背列表状态;
 * 区间/目标选择即拉地点(缓存命中零网络),刷新 force 旁路缓存;TA 页签多一枚「提醒 TA」
 * pill(WS 信号催对方上报一次);点地点条目 → onPickPlace(关闭半层 + 跳 trailPlacePage)
 */
@Composable
private fun TrailSheet(
    pager: LocationPager,
    onDismiss: () -> Unit,
    onPickPlace: (LocationPlaceInfo) -> Unit,
) {
    val theme = ThemeStore.current
    // 轨迹选择:rangeIndex 0=今天 1=昨天 2=近7天;targetIndex 0=自己 1=TA
    var rangeIndex by remember { mutableStateOf(0) }
    var targetIndex by remember { mutableStateOf(0) }
    var places by remember { mutableStateOf<List<LocationPlaceInfo>?>(null) }
    var placeTruncated by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // chip 切换即拉(缓存命中时零网络)
    LaunchedEffect(rangeIndex, targetIndex) {
        val (startTs, endTs) = trailRange(rangeIndex)
        val target = if (targetIndex == 0) TARGET_SELF else TARGET_PARTNER
        LocationStore.loadPlaceList(pager, target, startTs, endTs)?.let { (list, truncated) ->
            places = list
            placeTruncated = truncated
        }
    }

    fun refreshNow() {
        if (refreshing) return
        scope.launch {
            refreshing = true
            val (startTs, endTs) = trailRange(rangeIndex)
            val target = if (targetIndex == 0) TARGET_SELF else TARGET_PARTNER
            LocationStore.loadPlaceList(pager, target, startTs, endTs, force = true)?.let { (list, truncated) ->
                places = list
                placeTruncated = truncated
            }
            refreshing = false
        }
    }

    // 提醒 TA 更新位置(M28):WS 信号恒 success(对方前台+开共享才回点,回点走既有
    // love-location-new 帧秒落 marker)——toast 用「已提醒」语义,不承诺已更新
    fun remindPartner() {
        scope.launch {
            val result = LocationApi.pull(pager)
            AppFeedback.toast(
                pager,
                if (result is ApiResult.Ok) "已提醒 TA 更新位置" else "提醒发送失败,请稍后再试",
            )
        }
    }

    ModalBottomSheet(
        visible = true,
        onDismissRequest = onDismiss,
        containerColor = AppTheme.CardBg,
        dismissOnDrag = false,
        modifier = Modifier.clip(RoundedCornerShape(20.dp, 20.dp)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "行动轨迹",
                    color = AppTheme.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (targetIndex == 1) {
                    AppPillChip(
                        "提醒 TA",
                        bg = theme.brandStart.copy(alpha = 0.12f),
                        textColor = theme.brandStart,
                    ) {
                        remindPartner()
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                AppPillChip(
                    if (refreshing) "刷新中…" else "刷新",
                    bg = theme.brandStart.copy(alpha = 0.12f),
                    textColor = theme.brandStart,
                ) {
                    refreshNow()
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            AppSegmentedChips(
                options = listOf("今天", "昨天", "近7天"),
                selectedIndex = rangeIndex,
                onSelect = { rangeIndex = it },
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppSegmentedChips(
                options = listOf("我的轨迹", "TA 的轨迹"),
                selectedIndex = targetIndex,
                onSelect = { targetIndex = it },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PlaceListContent(
                targetIndex = targetIndex,
                partnerShareEnabled = LocationStore.partnerShareEnabled,
                places = places,
                brandColor = theme.brandStart,
                onPick = onPickPlace,
            )
            if (placeTruncated) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "仅展示最近 100 条地点",
                    color = AppTheme.TextTertiary,
                    fontSize = 11.sp,
                )
            }
            Spacer(modifier = Modifier.height(16.dp + navBarSafePaddingDp()))
        }
    }
}

/**
 * 地点记录列表(M28 行动轨迹核心新内容):表头(cadence 说明)+ 280dp 定高 LazyColumn——
 * ⚠ 未定高 lazy 容器在 ModalBottomSheet 内会无限高崩溃(MediaPickerSheet 定高先例);
 * 空态按目标与共享态细分,加载中/暂无记录各占定高盒
 */
@Composable
private fun PlaceListContent(
    targetIndex: Int,
    partnerShareEnabled: Boolean,
    places: List<LocationPlaceInfo>?,
    brandColor: Color,
    onPick: (LocationPlaceInfo) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "地点记录",
                color = AppTheme.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "约 30 分钟记一条,换地方立即记",
                color = AppTheme.TextTertiary,
                fontSize = 10.sp,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        when {
            targetIndex == 1 && !partnerShareEnabled -> {
                SheetEmptyBox { Text("TA 未开启位置共享", color = AppTheme.TextTertiary, fontSize = 13.sp) }
            }
            places == null -> {
                SheetEmptyBox { Text("地点加载中…", color = AppTheme.TextTertiary, fontSize = 13.sp) }
            }
            places.isEmpty() -> {
                SheetEmptyBox {
                    Text(
                        "该时段暂无地点记录",
                        color = AppTheme.TextTertiary,
                        fontSize = 13.sp,
                    )
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                    items(places) { place ->
                        PlaceRow(place, brandColor) { onPick(place) }
                    }
                }
            }
        }
    }
}

/** 空态/加载态定高盒(地点列表未出数据时占位,避免表头下突跳) */
@Composable
private fun SheetEmptyBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * 地点条目:品牌洗底圆点 + 主行(POI>地址>坐标 三级降级)+ 副行(省 市 区 去重 · 到达时间,
 * relativeTimeLabel 复用);点击回看(关闭半层,跳 trailPlacePage 独立回看页)
 */
@Composable
private fun PlaceRow(place: LocationPlaceInfo, brandColor: Color, onClick: () -> Unit) {
    val primary = placePrimaryLabel(place)
    val secondary = placeSecondaryLabel(place)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(8.dp)
                .background(brandColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                primary,
                color = AppTheme.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            if (secondary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    secondary,
                    color = AppTheme.TextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 组装 marker 数据 JSON(KRAMapView.updatePoints 契约):双方点+头像+配色,我方附
 * accuracy(原生精度圈半径);TA 未开共享/无点不出现在列表(原生按缺席移除 marker)
 */
private fun buildPointsJson(
    myPoint: LocationPointInfo?,
    partnerPoint: LocationPointInfo?,
    profile: UserProfile?,
    couple: CoupleInfo?,
    theme: KuiklyTheme,
): String {
    val points = JSONArray()
    myPoint?.let { pt ->
        points.put(
            JSONObject()
                .put("role", "self")
                .put("lat", pt.lat)
                .put("lng", pt.lng)
                .put("accuracy", pt.accuracy)
                .put("avatarUrl", profile?.avatar ?: "")
                .put("initial", (profile?.nickname ?: "").trim().take(1).ifEmpty { "我" })
                .put("colorStart", toHex(theme.brandStart))
                .put("colorEnd", toHex(theme.brandEnd))
                .put("textColor", "#FFFFFFFF"),
        )
    }
    partnerPoint?.let { pt ->
        // TA 文字圆 = 品牌洗底 + 品牌字(ChatPage 文字头像同构;地图上白圈托底可读)
        val wash = theme.brandStart.copy(alpha = 0.18f)
        points.put(
            JSONObject()
                .put("role", "partner")
                .put("lat", pt.lat)
                .put("lng", pt.lng)
                .put("avatarUrl", couple?.partnerAvatar ?: "")
                .put("initial", (couple?.partnerNickname ?: "").trim().take(1).ifEmpty { "TA" })
                .put("colorStart", toHex(wash))
                .put("colorEnd", toHex(wash))
                .put("textColor", toHex(theme.brandStart)),
        )
    }
    return JSONObject().put("points", points).toString()
}

/** Compose Color → "#AARRGGBB"(原生 Canvas 十六进制串;TA 洗底带 alpha 故 8 位,commonMain 禁 String.format 手拼;trailPlacePage 回看标注同用,M28.1) */
internal fun toHex(color: Color): String {
    val argb = color.toArgb()
    val chars = "0123456789ABCDEF"
    fun hex2(v: Int): String = "${chars[(v shr 4) and 0xF]}${chars[v and 0xF]}"
    return "#" + hex2((argb shr 24) and 0xFF) + hex2((argb shr 16) and 0xFF) +
        hex2((argb shr 8) and 0xFF) + hex2(argb and 0xFF)
}

/** 查定位权限(suspend 包装回调;进页链与回前台被动重查共用;精度位仅权限管理页消费) */
private suspend fun checkPermission(module: LocationModule): Boolean =
    suspendCoroutine { cont -> module.checkLocationPermission { granted, _, _ -> cont.resume(granted) } }

/** 运行时申请定位权限(suspend 包装;denied 语义与「asked 不可辨」见 LocationModule 注释) */
private suspend fun requestPermission(module: LocationModule): Boolean =
    suspendCoroutine { cont -> module.requestLocationPermission { granted, _ -> cont.resume(granted) } }

/** 地点条目主行文案(POI>地址>坐标 三级降级;trailPlacePage 回看页标题同源,M28.1) */
private fun placePrimaryLabel(place: LocationPlaceInfo): String = when {
    place.poi.isNotEmpty() -> place.poi
    place.address.isNotEmpty() -> place.address
    else -> "${round4(place.lat)}, ${round4(place.lng)}"
}

/**
 * 地点条目副行文案(省 市 区 distinct 去重 · 到达时间;trailPlacePage 回看页副题同源)。
 * 直辖市 province==city 去重:「上海市 黄浦区」而非「上海市 上海市 黄浦区」
 */
private fun placeSecondaryLabel(place: LocationPlaceInfo): String {
    val region = listOf(place.province, place.city, place.district)
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" ")
    val time = relativeTimeLabel(place.reportTime)
    return listOf(region, time).filter { it.isNotEmpty() }.joinToString(" · ")
}

/**
 * 轨迹时间区间(GMT+8 日界,产品口径):0=今天 1=昨天 2=近7天 → (startTs, endTs)。
 * epoch 加 8h 后按天取整得 GMT+8 日序,再减回 8h 还原真实毫秒边界
 */
private fun trailRange(index: Int): Pair<Long, Long> {
    val now = nowEpochMs()
    val todayIndex = (now + 8 * 3_600_000L) / 86_400_000L
    val dayStart: (Long) -> Long = { daysAgo ->
        (todayIndex - daysAgo) * 86_400_000L - 8 * 3_600_000L
    }
    return when (index) {
        0 -> Pair(dayStart(0), now)
        1 -> Pair(dayStart(1), dayStart(0))
        else -> Pair(dayStart(6), now)
    }
}

/** 坐标 4 位小数圆整(约 11m;地点无地址时的降级文案用,commonMain 禁 String.format 故手写) */
private fun round4(v: Double): Double = round(v * 10000.0) / 10000.0
