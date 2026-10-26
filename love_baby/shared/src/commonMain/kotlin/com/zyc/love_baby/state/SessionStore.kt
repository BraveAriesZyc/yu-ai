package com.zyc.love_baby.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.AuthSession

/**
 * SessionStore — 全局登录态与 token 持久化(INTEGRATION-DESIGN §7.2,ThemeStore 三件套范式)
 * 用法:读 SessionStore.isLoggedIn / accessToken / userId;登录成功 SessionStore.update(pager, session);
 *      退出/刷新失效 SessionStore.clear(pager);冷启动由 MainPager.willInit 调 initFromPageData(首帧判定,
 *      Gate 不进组合)+ GatePanel 的 restore(无参数端兜底)
 * 依赖:androidx.compose.runtime 快照状态;SharedPreferencesModule(可空 getModule 降级,禁 acquireModule);
 *      TimeKit.nowEpochMs(state 同包单源,commonMain 墙钟)
 * 事件:update/clear 翻 isLoggedIn,MainPageImpl 的 Gate 整树条件渲染随之挂/卸
 * 功能:
 *  - 完整 session 五字段持久化(accessToken/refreshToken/userId/expiresTime/clientId,只存 accessToken 会导致
 *    access 过期后冷启动必登出,静默刷新失效)
 *  - 双通道恢复:Android 宿主 createPageData 注入(首帧判定)/ SharedPreferences 兜底(iOS/鸿蒙/H5,晚一帧可接受)
 *  - initFromPageData 判定:未过期放行;过期但有 refreshToken 也放行(靠首请求 401 触发单飞刷新);都没有才 Gate
 *  - 局限:明文存储,情侣 App 首期接受(§10 m-3)
 */
internal object SessionStore {

    /** 持久化 key,与 androidApp KuiklyRenderActivity.createPageData 注入 key 保持一致 */
    const val PREF_KEY = "love_app_session"

    var session: AuthSession? by mutableStateOf(null)
        private set

    val isLoggedIn: Boolean get() = session != null

    val accessToken: String get() = session?.accessToken.orEmpty()
    val refreshToken: String get() = session?.refreshToken.orEmpty()
    val userId: String get() = session?.userId.orEmpty()
    val clientId: String get() = session?.clientId.orEmpty()

    /** 当前会话是否已过期(供 UI 展示;HttpClient 的刷新不看此值,以服务端 401 为准) */
    val isExpired: Boolean get() = session?.let { isExpiredSession(it) } == true

    /**
     * 登录/注册/刷新成功统一入口:翻内存状态 + 同步持久化完整 session JSON
     */
    fun update(pager: Pager, newSession: AuthSession) {
        session = newSession
        persist(pager, newSession.toJson().toString())
    }

    /**
     * 退出登录/刷新失效:清内存 + **同步清持久化(setItem(PREF_KEY, ""))**。
     * 只翻内存状态的话,冷启动 initFromPageData 又判已登录 → 首请求 401 → 再 clear → Gate 晚现,
     * 且退出登录后 session「复活」(§7.2)
     */
    fun clear(pager: Pager) {
        session = null
        persist(pager, "")
    }

    /**
     * 冷启动首帧判定:读 pageData.params(宿主注入的完整 session JSON)。
     * pageData 在 willInit 前就绪(不走模块,无时序问题,ThemeStore.initFromPageData 同款);
     * 内部有幂等保护,重复调用无副作用
     */
    fun initFromPageData(pager: Pager) {
        if (session != null) return
        try {
            applyPersisted(pager.pageData.params.optString(PREF_KEY))
        } catch (_: Exception) {
        }
    }

    /**
     * 无参数端(iOS/鸿蒙/H5)prefs 兜底(ThemeStore.restore 同款可空 getModule 降级):
     * 模块未就绪时返回 null → 维持 Gate;由 GatePanel 的 LaunchedEffect 调用(晚一帧,可接受 §10 m-2)
     */
    fun restore(pager: Pager) {
        if (session != null) return
        try {
            pager.getModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)?.let { sp ->
                applyPersisted(sp.getItem(PREF_KEY))
            }
        } catch (_: Exception) {
        }
    }

    /**
     * 判定(§7.2):JSON 可解析出 accessToken 之外,还要看 expiresTime——
     * 未过期直接放行;已过期但 refreshToken 非空仍放行(首请求 401 触发单飞刷新);
     * 连 refreshToken 都没有才维持 Gate
     */
    private fun applyPersisted(json: String) {
        if (json.isEmpty()) return
        val parsed = try {
            AuthSession.fromJson(JSONObject(json))
        } catch (_: Throwable) {
            null
        } ?: return
        if (isExpiredSession(parsed) && parsed.refreshToken.isEmpty()) return
        session = parsed
    }

    private fun isExpiredSession(s: AuthSession): Boolean =
        s.expiresTime > 0 && s.expiresTime <= nowEpochMs()

    /**
     * 持久化:可空 getModule 降级 + try/catch(ThemeStore.persist 同款容错,
     * 规避 acquireModule 的双抛异步炸弹——铁律⑦)
     */
    private fun persist(pager: Pager, json: String) {
        try {
            pager.getModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                ?.setItem(PREF_KEY, json)
        } catch (_: Exception) {
        }
    }
}
