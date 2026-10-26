package com.zyc.love_baby.features.battery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
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
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable
import com.zyc.love_baby.base.BasePager
import com.zyc.love_baby.base.BatteryModule
import com.zyc.love_baby.base.closeLovePage
import com.zyc.love_baby.base.openLovePage
import com.zyc.love_baby.components.AppFeedback
import com.zyc.love_baby.components.AppPillChip
import com.zyc.love_baby.components.AppSubPageHeader
import com.zyc.love_baby.components.AppSwitch
import com.zyc.love_baby.components.AppTag
import com.zyc.love_baby.components.AppTheme
import com.zyc.love_baby.components.ThemeStore
import com.zyc.love_baby.components.navBarSafePaddingDp
import com.zyc.love_baby.features.couple.toastCoupleResult
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.state.BatteryStore
import com.zyc.love_baby.state.ChatStore
import com.zyc.love_baby.state.nowEpochMs
import com.zyc.love_baby.state.relativeTimeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** batteryPage 路由名(openBatteryPage 与 @Page 注册名共用,勿散落字面量) */
internal const val BATTERY_PAGE_NAME = "batteryPage"

/** 路由打开电量页(base.openLovePage 收口,try/catch 吞未桥接端在 helper 内统一处理) */
internal fun openBatteryPage(pager: Pager) {
    pager.openLovePage(BATTERY_PAGE_NAME)
}

/** 页内轮询节奏:60s(上报即带回双方,一请求两用) */
private const val BATTERY_POLL_INTERVAL_MS = 60_000L

/** 低电量提醒阈值(<20% 显橙可提醒,与位置页无耦合) */
private const val LOW_BATTERY_LEVEL = 20

/** 快照过期判定:超 24h 显「很久没打开 App」灰显注记 */
private const val STALE_MS = 24 * 60 * 60_000L

/**
 * BatteryPage — 情侣电量互看页(@Page "batteryPage",M26;PermissionPager 同款壳)
 * 用法:HomePage 快捷入口「情侣电量」openBatteryPage 打开;返回钮 closePage
 * 依赖:state/BatteryStore(快照单例)+base/BatteryModule(原生电量,Android 实现;
 *      其他端 getModule null→「暂不支持」降级);AppSwitch(共享开关);ChatStore.send
 *      (「提醒 TA 充电」走悄悄话);components(AppSubPageHeader/AppPillChip)
 * 事件:返回钮 closePage;开关切换 updateShare(失败 toast 不落值);提醒钮 send 悄悄话
 * 功能:
 *  - 进页首拉 + 60s 轮询(refresh 兼上报:原生电量可得→report 一次请求带回双方;桥缺失/
 *    读取失败/共享关→get 纯拉);pageDidAppear(App 回前台)补一轮——回前台 MainPager 已报,
 *    此处兜位置页直达场景
 *  - 配色:<10% 红 <20% 橙 充电中/已充满绿,正常主题色;TA 超 24h 未更新灰显「很久未在线」
 *    (无后台采集,靠 reportTime 过期语义兜底,产品定案)
 *  - 「提醒 TA 充电」:TA 低电量且未充电时出现,一键发悄悄话(旧路径 send,消息走常规
 *    增量拉回,红点自然亮)
 *  - 电量共享开关默认开(低敏);关闭后本端不再上报(服务端同闸拒收,双保险),TA 侧
 *    显「未开启电量共享」
 */
@Page("batteryPage", supportInLocal = true)
internal class BatteryPager : BasePager() {

    /** 回前台补拉 tick(pageDidAppear 单 Activity 一页模型下=App 回前台;0=冷启动首现跳过) */
    internal var refreshTick by observable(0)

    override fun willInit() {
        super.willInit()
        ThemeStore.initFromPageData(this)
        setContent {
            BatteryPage(this)
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
internal fun BatteryPage(pager: BatteryPager) {
    val theme = ThemeStore.current
    var supported by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var reminding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 进页首拉 + 60s 轮询(supported 判定在前:未桥接端不进循环,页面直落降级态)
    LaunchedEffect(Unit) {
        if (pager.getModule<BatteryModule>(BatteryModule.MODULE_NAME) == null) {
            supported = false
            return@LaunchedEffect
        }
        BatteryStore.loadSetting(pager)
        while (true) {
            BatteryStore.refresh(pager)
            loaded = true
            delay(BATTERY_POLL_INTERVAL_MS)
        }
    }
    // 回前台补一轮(refreshTick 驱动;store 幂等,与 MainPager 前台上报天然去重)
    LaunchedEffect(pager.refreshTick) {
        if (pager.refreshTick > 0 && supported) {
            BatteryStore.refresh(pager)
        }
    }

    Box(modifier = Modifier.fillMaxWidth().background(theme.bg)) {
        LazyColumn {
            item {
                AppSubPageHeader(
                    title = "情侣电量",
                    subtitle = "看看 TA 还剩多少电",
                    onBack = {
                        pager.closeLovePage()
                    },
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = AppTheme.PageHPadding)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    when {
                        !supported -> EmptyNote("当前设备暂不支持电量互看", "电量功能即将支持更多机型,敬请期待")
                        else -> {
                            BatteryCard(
                                title = "我的电量",
                                level = BatteryStore.myLevel,
                                charging = BatteryStore.myCharging,
                                reportTime = BatteryStore.myReportTime,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            BatteryCard(
                                title = "TA 的电量",
                                level = BatteryStore.partnerLevel,
                                charging = BatteryStore.partnerCharging,
                                reportTime = BatteryStore.partnerReportTime,
                                partnerShareEnabled = BatteryStore.partnerShareEnabled,
                                loaded = loaded,
                            ) {
                                // 提醒 TA 充电:共享中 + 低电量(<20) + 未充电才出现(读快照态)
                                val low = BatteryStore.partnerLevel in 1 until LOW_BATTERY_LEVEL
                                if (BatteryStore.partnerShareEnabled && low && BatteryStore.partnerCharging == 0) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    AppPillChip(
                                        if (reminding) "提醒发送中…" else "提醒 TA 充电",
                                        bg = theme.brandStart,
                                    ) {
                                        if (reminding) return@AppPillChip
                                        scope.launch {
                                            reminding = true
                                            val text = "宝贝,我这边看到你手机电量只剩 ${BatteryStore.partnerLevel}% 了,记得充电哦 ❤️"
                                            val result = ChatStore.send(pager, text)
                                            reminding = false
                                            if (result is ApiResult.Ok) {
                                                AppFeedback.toast(pager, "已用悄悄话提醒 TA")
                                            } else {
                                                toastCoupleResult(pager, result)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    // 电量共享开关(默认开,低敏):关=本端不再上报(TA 看不到我),我仍可看 TA
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "电量共享",
                                    color = AppTheme.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "开启后 TA 可以看到你的电量与充电状态",
                                    color = AppTheme.TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            AppSwitch(
                                checked = BatteryStore.batteryShareEnabled,
                                onCheckedChange = { on ->
                                    scope.launch {
                                        val result = BatteryStore.updateShare(pager, on)
                                        if (result !is ApiResult.Ok) {
                                            toastCoupleResult(pager, result)
                                        }
                                    }
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                    ) {
                        Column {
                            Text(
                                "小贴士",
                                color = AppTheme.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "电量在双方打开 App 时更新(打开本页/回到前台即刻上报);TA 手机电量长期未变,可能只是还没打开 App 哦。",
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

/** 电量卡(我的/TA 共用;TA 侧带共享关/未加载/未上报三空态,footer 插槽放提醒钮) */
@Composable
private fun BatteryCard(
    title: String,
    level: Int,
    charging: Int,
    reportTime: Long,
    partnerShareEnabled: Boolean = true,
    loaded: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
) {
    val stale = reportTime > 0 && nowEpochMs() - reportTime > STALE_MS
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = AppTheme.TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    relativeTimeLabel(reportTime).ifEmpty { "从未上报" },
                    color = AppTheme.TextTertiary,
                    fontSize = 11.sp,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            when {
                // TA 侧空态(我的卡恒有本端数据,哨兵值最多显「未知」)
                !loaded -> {
                    Text("查询中…", color = AppTheme.TextTertiary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                !partnerShareEnabled -> {
                    Text("TA 未开启电量共享", color = AppTheme.TextTertiary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "对方可以在「情侣电量」页打开共享开关",
                        color = AppTheme.TextTertiary,
                        fontSize = 12.sp,
                    )
                }
                level < 0 -> {
                    Text("未知", color = AppTheme.TextTertiary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                else -> {
                    val color = batteryColor(level, charging)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$level",
                            color = color,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("%", color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(12.dp))
                        // 充电状态胶囊(AppTag 直替:R9/10·4 内距/12sp 逐参覆写照旧;
                        // 未充电不显胶囊,「电量数字」即语义)
                        if (charging > 0) {
                            AppTag(
                                chargingLabel(charging),
                                modifier = Modifier.padding(bottom = 8.dp),
                                bgColor = color.copy(alpha = 0.12f),
                                textColor = color,
                                fontSize = 12.sp,
                                corner = 9.dp,
                                hPadding = 10.dp,
                                vPadding = 4.dp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // 电量条:轨道常驻 + 内条按比例填充(level=-1 分数 0 显空轨)
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .height(10.dp)
                            .background(AppTheme.Divider, RoundedCornerShape(5.dp)),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(level / 100f)
                                .height(10.dp)
                                .background(color, RoundedCornerShape(5.dp)),
                        )
                    }
                    if (stale) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "超过 24 小时未更新,TA 可能很久没打开 App 了",
                            color = AppTheme.TextTertiary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            footer?.invoke()
        }
    }
}

/** 电量配色:充电中/已充满绿,<10 红,<20 橙,正常主题色(红分支必须排在橙前,0..9 ⊂ 0..19) */
private fun batteryColor(level: Int, charging: Int): Color = when {
    charging > 0 -> AppTheme.Success
    level in 0..9 -> AppTheme.Danger
    level in 0 until LOW_BATTERY_LEVEL -> AppTheme.Warning
    else -> ThemeStore.current.brandStart
}

/** 充电状态文案(charging 0=未充电 1=充电中 2=已充满;-1 哨兵不进此路径) */
private fun chargingLabel(charging: Int): String = when (charging) {
    1 -> "充电中"
    2 -> "已充满"
    else -> "未充电"
}

/** 不支持空态卡(未桥接端:iOS/ohos/H5;结构与「小贴士」卡一致) */
@Composable
private fun EmptyNote(title: String, desc: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(AppTheme.CardBg, RoundedCornerShape(20.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(title, color = AppTheme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(desc, color = AppTheme.TextTertiary, fontSize = 12.sp)
        }
    }
}
