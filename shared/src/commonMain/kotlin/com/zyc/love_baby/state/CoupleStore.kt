package com.zyc.love_baby.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.CoupleInfo
import com.zyc.love_baby.data.model.InviteInfo
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.CoupleApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * CoupleStore — 全局情侣绑定状态(SessionStore/ThemeStore 三件套范式,INTEGRATION-DESIGN §7.3 三态状态机)
 * 用法:读 CoupleStore.bindState/invite/couple;绑定动作 CoupleStore.bind/unbind/createInvite;
 *      冷启动/登录后的校准由 MainPager 的 LaunchedEffect(isLoggedIn) 调 refresh(§7.4 拉取触发点);
 *      登出/会话失效调 reset(同在 MainPager 接线,防换号残留)
 * 依赖:androidx.compose.runtime 快照状态;CoupleApi/HttpClient(401 单飞刷新、未绑定状态信号放行)
 * 事件:bindState 翻转驱动 MainPager 的 BindingPanel 整树挂/卸(UNKNOWN/UNBOUND 显示,BOUND 卸载进主功能);
 *      MinePage 的情侣空间卡随 couple 显示
 * 功能:
 *  - 绑定三态:UNKNOWN(未校准,如冷启动/刚登录)/ UNBOUND(未绑定,展示引导)/ BOUND(已绑定,进主功能)
 *  - 邀请码缓存 invite(create-invite 结果,过期惰性判定 isInviteExpired)与伴侣信息 couple
 *  - ⚠ 不持久化 couple 态到 prefs:以服务端为准,避免解绑后本地态「复活」;每次冷启动/登录 refresh 校准
 *  - 解绑(D-11):服务端物理删除 couple 行,双方端各自在下次 couple/get(自身解绑即时,对方靠轮询)回未绑定
 */
internal enum class CoupleBindState {
    /** 未校准(冷启动/刚登录,refresh 进行中):UI 显示「确认绑定状态中」 */
    UNKNOWN,

    /** 未绑定:BindingGuide 引导层 */
    UNBOUND,

    /** 已绑定:进入主功能(MinePage 展示情侣空间卡) */
    BOUND,
}

internal object CoupleStore {

    /** 轮询间隔(§7.4 低频轮询,按绑定态自适应):未绑定=10s——A 等待对方输码的感知窗口,及时性优先;
     *  已绑定=60s——解绑/被解绑感知属常态低频,省电省请求 */
    const val POLL_INTERVAL_UNBOUND_MS = 10_000L
    const val POLL_INTERVAL_BOUND_MS = 60_000L

    /** 连续失败进入退避的阈值(§7.4):连败 N 次后转退避节奏,M3 起不再永久暂停(退避恢复见下) */
    const val POLL_MAX_CONSECUTIVE_FAILURES = 3

    /**
     * 退避重试间隔序列(F8,§7.4 退避恢复):连败达上限后按 60s→120s→300s(封顶)定时重试,
     * 任一次成功即回常规自适应间隔;回前台(MainPager.pageDidAppear)会重启轮询循环,立即恢复
     */
    val POLL_BACKOFF_STEPS_MS = longArrayOf(60_000L, 120_000L, 300_000L)

    /**
     * 回前台信号(F8,§7.4):MainPager.pageDidAppear(排除冷启动首次出现)自增。
     * MainPageImpl 两个消费点:LaunchedEffect 立即 refresh 校准一次;轮询 LaunchedEffect 同 key
     * 重启 → 连败暂停/退避中的轮询立即回常规自适应间隔
     */
    var foregroundTick by mutableIntStateOf(0)
        private set

    /** 回前台打点(MainPager.pageDidAppear 调用;快照自增驱动上述两个消费点) */
    fun notifyForeground() {
        foregroundTick++
    }

    var bindState: CoupleBindState by mutableStateOf(CoupleBindState.UNKNOWN)
        private set

    /**
     * 我的邀请码缓存(create-invite 结果)。
     * ⚠ 服务端 bind 成功即清双方 invite_code(CoupleServiceImpl.clearInviteCode)——邀请码并非
     * 「与 couple 无关」:bind/解绑后旧缓存是服务端已置 NULL 的死码,须置空(见 bind/unbind),
     * 否则 BindingPanel 在 invite 非空且未过期时只展示死码且无重生成入口,会话内重绑死锁
     */
    var invite: InviteInfo? by mutableStateOf(null)
        private set

    /** 伴侣与绑定信息(BOUND 时非空;MinePage 情侣空间卡消费) */
    var couple: CoupleInfo? by mutableStateOf(null)
        private set

    /** 最近一次 refresh 是否失败(供「校准中」卡片给出重试入口;成功即翻回 false) */
    var refreshFailed by mutableStateOf(false)
        private set

    val isBound: Boolean get() = bindState == CoupleBindState.BOUND

    /** 邀请码惰性过期判定(§4:expire_time 惰性校验,无定时任务);服务端 bind 侧仍会终审 */
    val isInviteExpired: Boolean
        get() = invite?.let { it.expireTime > 0 && it.expireTime <= nowEpochMs() } == true

    /**
     * 拉取绑定状态校准三态(§7.4 couple/get 触发点:Gate 消失后/BindingGuide 消失后/回前台轮询共用)。
     * NOT_BOUND 已被 CoupleApi 按数据放行为空 data 的 Ok(状态信号非错误);此处 Ok 且解析不出
     * 伴侣信息 = 未绑定。失败不翻状态(网络失败保留现态可重试,§7.4),只置 refreshFailed
     */
    suspend fun refresh(pager: Pager): ApiResult {
        val result = CoupleApi.getCouple(pager)
        when (result) {
            is ApiResult.Ok -> {
                refreshFailed = false
                val info = CoupleInfo.from(result.data)
                if (info == null) {
                    couple = null
                    bindState = CoupleBindState.UNBOUND
                } else {
                    couple = info
                    bindState = CoupleBindState.BOUND
                }
            }
            else -> refreshFailed = true
        }
        return result
    }

    /** 生成/刷新我的邀请码:成功落缓存;失败交调用方 toast */
    suspend fun createInvite(pager: Pager): ApiResult {
        val result = CoupleApi.createInvite(pager)
        if (result is ApiResult.Ok) {
            InviteInfo.from(result.data)?.let { invite = it }
        }
        return result
    }

    /**
     * 凭码绑定:成功即翻 BOUND(引导层随之卸载)。回包缺伴侣信息时兜底再拉一次 couple/get 校准;
     * 两者都拿不到(极端)保持 UNBOUND,等轮询/冷启动纠偏,不阻塞用户
     */
    suspend fun bind(pager: Pager, inviteCode: String): ApiResult {
        val result = CoupleApi.bind(pager, inviteCode)
        if (result is ApiResult.Ok) {
            // 服务端 bind 成功即清双方 invite_code(邀请已消费),本地缓存随之失真,置空防死码残留
            invite = null
            val info = CoupleInfo.from(result.data)
            if (info != null) {
                couple = info
                bindState = CoupleBindState.BOUND
            } else {
                refresh(pager)
            }
        }
        return result
    }

    /**
     * 解绑(双方任一可发起,D-11):成功清本地伴侣信息回 UNBOUND(引导层随之出现)。
     * invite 同步置空:服务端 bind 成功时已清双方 invite_code,解绑后旧缓存是服务端已置 NULL 的
     * 死码(他人凭码绑定恒 1_003_004_000),不清则 BindingPanel 只展示死码、会话内无法重新生成,
     * 重绑死锁需重启 App 才自愈
     */
    suspend fun unbind(pager: Pager): ApiResult {
        val result = CoupleApi.unbind(pager)
        if (result is ApiResult.Ok) {
            couple = null
            invite = null
            bindState = CoupleBindState.UNBOUND
        }
        return result
    }

    /** 登出/会话失效清内存绑定态(MainPager 接线调用);不持久化故无 prefs 要清(§7.3) */
    fun reset() {
        bindState = CoupleBindState.UNKNOWN
        invite = null
        couple = null
        refreshFailed = false
    }

    /** commonMain 墙钟毫秒(kotlin.time.Clock,SessionStore 同款) */
    @OptIn(ExperimentalTime::class)
    private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()
}
