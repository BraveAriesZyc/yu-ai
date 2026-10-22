package com.zyc.love_baby.network

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.AUTH_ERR_USER_DISABLED
import com.zyc.love_baby.data.model.AuthSession
import com.zyc.love_baby.state.SessionStore
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * HttpClient — app-api 网络单点收口(INTEGRATION-DESIGN §7.1 五条职责)
 * 用法:HttpClient.request(pager, "/love/auth/login", isPost = true, body = LoginReq(...).toJson());
 *      调用方在协程内(rememberCoroutineScope / LaunchedEffect)发起,结果按 ApiResult 分支处理
 * 依赖:core NetworkModule(suspendCoroutine 包装,照 BridgeModule.ssoRequest 先例);
 *      SessionStore(取 token / 刷新成功回写 / 失效清除);ApiConfig(拼地址/刷新路径/CLIENT_ID)
 * 事件:code==401 触发单飞静默刷新;code==1_003_005_002(停用)清会话;刷新成功重放原请求,
 *      仅刷新本身失败才 SessionStore.clear
 * 功能(§7.1):
 *  1. CommonResult 拆包:code==0 取 data;code==401 走刷新通道;code==1_003_005_002 账号停用
 *     清会话回 Gate(§5.1 对照表);其余 code!=0 转 BizError(msg 可直接 toast);
 *     非 JSON 回包(core 包成 {data:xxx} 形态)识别剥壳,剥不出按网络错误
 *  2. 每请求手动塞 Authorization: Bearer <token>
 *  3. 单飞静默刷新:全局共享同一个 in-flight 刷新结果,并发 401 只发一次 refresh-token(复用 couple-app
 *     client);成功统一用新 token 重放原请求;区分网络失败(保留会话可重试)与 refresh token 无效(clear 回 Gate);
 *     Owner 协程被取消时对等待者 complete(NetworkError) 后再清场 rethrow,通道永不挂死
 *  4. 写请求(POST)强制 Content-Type: application/json —— core-render-android 2.29 的
 *     KRNetworkModule.addBodyParamsIfNeed 仅在该 Content-Type 下才把 param 按 JSON 原文写出,
 *     否则走 k=v& 表单编码,yudao app-api 全是 @RequestBody,漏此项登录等 POST 直接 400 或字段全空
 *  5. 模块获取一律可空 getModule 降级,不走会双抛异常的 acquireModule 路径(铁律⑦)
 *  6. M2 增量 successBizCodes(最小扩展,默认空集不影响 M1 auth 行为):请求可声明「指定业务码按数据
 *     返回/免 toast」—— couple/get 未绑定的 1_003_004_003 是状态信号不是错误(§5.2),命中时返回
 *     空 data 的 Ok 由调用方凭传入集合自行分派,不产生 BizError;401 单飞刷新后重放的拆包同样生效
 * 7. M3 增量 数组 data:§5.2 todo/mood/message 列表端点的 data 为 JSON 数组,Ok 增 dataArray 形态
 *     携带(对象形态仍走 data,两者恒居其一);core 解析器把回包里的数组还原成 JSONArray 实例,
 *     optJSONObject 对数组值回 null 会把列表整个吞掉,拆包按实际类型分派(见 toOk)。
 *     注意:todo/mood/message 域未绑定的 1_003_004_003 仍走 BizError——状态信号放行是
 *     couple/get 专用,不外扩(M3 定案)
 * ⚠️ 原「仅 GET/POST 写入局限」已按 §5.2 user 组注记定案收敛(M3):app-api 写端点一律 POST,
 *     todo update/delete 已改 POST body(§5.2 todo 组表已同步修正),本类参数形态无需再扩展;
 *     isPost=true 恒指 POST(强制 JSON Content-Type),绝无"布尔吞任意 method"语义
 */

/** 拆包后的语义结果(HTTP 恒 200,401 只在 body.code,§5.1) */
internal sealed interface ApiResult {

    /**
     * code==0,携带 CommonResult.data。对象形态(绝大多数端点)在 [data];数组形态(§5.2 todo/mood/message
     * 列表端点,M3 增量)在 [dataArray] 且 data 为空对象——两者恒居其一,列表端点调用方勿读 data
     */
    data class Ok(val data: JSONObject, val dataArray: JSONArray? = null) : ApiResult

    /** 业务失败(code!=0 且非 401 且非停用码,含 1_003_005_000/001、403):msg 直接可 toast(§5.1 对照表) */
    data class BizError(val code: Long, val msg: String) : ApiResult

    /** 会话失效(刷新无效/重放仍 401/账号停用 1_003_005_002):SessionStore 已清,UI 收尾即回 Gate */
    data object Unauthorized : ApiResult

    /** 网络不可达/超时/非 JSON 回包:会话保留,可重试,不崩 */
    data object NetworkError : ApiResult
}

internal object HttpClient {

    private const val CODE_OK = 0L
    private const val CODE_UNAUTHORIZED = 401L
    private const val KEY_CODE = "code"
    private const val KEY_DATA = "data"
    private const val KEY_MSG = "msg"

    private const val HEADER_AUTHORIZATION = "Authorization"
    private const val HEADER_CONTENT_TYPE = "Content-Type"
    private const val CONTENT_TYPE_JSON = "application/json"
    private const val BEARER_PREFIX = "Bearer "

    private const val KEY_REFRESH_TOKEN = "refreshToken"
    private const val KEY_CLIENT_ID = "clientId"

    /** 请求超时(秒),core httpRequest 默认 30,显式写出便于联调调整 */
    private const val TIMEOUT_SECONDS = 30

    /**
     * 单点入口:发一个 app-api 请求并完成 CommonResult 拆包/401 刷新重放。
     * @param path §5.2 表内路径(自带 /love 模块段),如 "/love/auth/login"
     * @param isPost true=POST(强制 Content-Type: application/json);false=GET。仅此二值 ——
     *        core NetworkModule.httpRequest 的 method 硬编码 POST/GET 二选一,见头注释第 7 条后的
     *        「仅 GET/POST 写入局限」收敛说明(M3:写端点一律 POST,勿再寻 PUT/DELETE 通道)
     * @param body 请求体(GET 传 null)
     * @param successBizCodes M2 增量:按「数据」放行的业务码集合(默认空集 = 原行为)。
     *        用于 couple/get 未绑定 1_003_004_003 这类状态信号:命中返回空 data 的 Ok,免 toast
     */
    suspend fun request(
        pager: Pager,
        path: String,
        isPost: Boolean = true,
        body: JSONObject? = null,
        successBizCodes: Set<Long> = emptySet(),
    ): ApiResult {
        val url = ApiConfig.url(pager, path)
        val token = SessionStore.accessToken
        return when (val first = rawRequest(pager, url, isPost, body, token)) {
            is RawResult.NetworkError -> ApiResult.NetworkError
            is RawResult.Common -> unwrap(pager, first.root, url, isPost, body, token, successBizCodes)
        }
    }

    // ---------- CommonResult 拆包与 401 通道 ----------

    private suspend fun unwrap(
        pager: Pager,
        root: JSONObject,
        url: String,
        isPost: Boolean,
        body: JSONObject?,
        failedToken: String,
        successBizCodes: Set<Long>,
    ): ApiResult {
        val code = root.optLong(KEY_CODE, 0L)
        if (code == CODE_OK) {
            return toOk(root)
        }
        // 账号已停用特判(§5.1 对照表/§6.5,须在通用非 401 分支前):已签发 token 在服务端 filter
        // 层仍有效,love 业务入口统一拒绝 1_003_005_002 —— 端上收口清会话回 Gate,不能保持登录态
        // 反复 toast。登录场景 session 本为空,clear 幂等无害(Unauthorized 由 Gate 面板统一 toast)
        if (code == AUTH_ERR_USER_DISABLED) {
            return disabledOutcome(pager)
        }
        // M2 增量:调用方声明的业务码按「数据」返回(状态信号非错误,§5.2 couple/get 未绑定),
        // 返回空 data 的 Ok 由调用方分派;默认空集永不命中,不影响 auth 组既有行为
        if (code in successBizCodes) {
            return ApiResult.Ok(JSONObject())
        }
        if (code != CODE_UNAUTHORIZED) {
            return ApiResult.BizError(code, root.optString(KEY_MSG))
        }

        // ---- code==401:单飞静默刷新(§7.1-3)。登录成功拉全量是并发场景,单飞为必选项而非优化 ----
        return when (silentRefresh(pager, failedToken)) {
            RefreshOutcome.Success -> {
                // 刷新成功:统一用新 token 重放原请求一次
                when (val replay = rawRequest(pager, url, isPost, body, SessionStore.accessToken)) {
                    is RawResult.NetworkError -> ApiResult.NetworkError
                    is RawResult.Common ->
                        if (replay.root.optLong(KEY_CODE, 0L) == CODE_UNAUTHORIZED) {
                            // 重放仍 401:新 token 也不被认,只能清会话回 Gate
                            SessionStore.clear(pager)
                            ApiResult.Unauthorized
                        } else {
                            toApiResult(pager, replay.root, successBizCodes)
                        }
                }
            }
            // 网络失败:保留会话、可重试(§7.1-3),不清
            RefreshOutcome.NetworkError -> ApiResult.NetworkError
            // refresh token 无效/账号停用等:清会话回 Gate(§5.1 对照表)
            RefreshOutcome.Invalid -> {
                SessionStore.clear(pager)
                ApiResult.Unauthorized
            }
        }
    }

    /**
     * CommonResult.data 双形态拆取:JSON 对象 → data(绝大多数端点);JSON 数组 → dataArray 且 data
     * 置空对象(§5.2 todo/mood/message 列表端点,M3 增量);缺省/null/标量(如 delete 回 true)→ 空对象。
     * core 解析器把回包里的数组还原成 JSONArray 实例,原 optJSONObject 对数组值回 null 会把整个列表
     * 吞掉,故必须按实际类型分派
     */
    private fun toOk(root: JSONObject): ApiResult.Ok = when (val raw = root.opt(KEY_DATA)) {
        is JSONObject -> ApiResult.Ok(raw)
        is JSONArray -> ApiResult.Ok(JSONObject(), raw)
        else -> ApiResult.Ok(JSONObject())
    }

    /** 非 401 回包 → 语义结果;停用码走同一收口(刷新成功→重放之间被停用的窗口);successBizCodes 同 [unwrap] */
    private fun toApiResult(pager: Pager, root: JSONObject, successBizCodes: Set<Long>): ApiResult {
        val code = root.optLong(KEY_CODE, 0L)
        return when {
            code == CODE_OK -> toOk(root)
            code == AUTH_ERR_USER_DISABLED -> disabledOutcome(pager)
            code in successBizCodes -> ApiResult.Ok(JSONObject())
            else -> ApiResult.BizError(code, root.optString(KEY_MSG))
        }
    }

    /** 停用账号收口(§5.1 对照表:1_003_005_002 → 清会话回 Gate);首响应与刷新后重放共用 */
    private fun disabledOutcome(pager: Pager): ApiResult {
        SessionStore.clear(pager)
        return ApiResult.Unauthorized
    }

    // ---------- 单飞静默刷新 ----------

    private sealed interface RefreshOutcome {
        /** 刷新成功(含"等其他请求刷完,token 已换新"),原请求可重放 */
        data object Success : RefreshOutcome

        /** 网络失败:会话保留,可重试 */
        data object NetworkError : RefreshOutcome

        /** refresh token 无效:清会话回 Gate */
        data object Invalid : RefreshOutcome
    }

    /** joinRefresh 的三种归宿 */
    private sealed interface RefreshJoin {
        /** 等锁期间 token 已被他人刷出新值,无需再刷 */
        data object TokenRefreshed : RefreshJoin

        /** 已有 in-flight 刷新,等它 */
        class Waiter(val deferred: CompletableDeferred<RefreshOutcome>) : RefreshJoin

        /** 本协程成为刷新执行者 */
        class Owner(val deferred: CompletableDeferred<RefreshOutcome>) : RefreshJoin
    }

    private val refreshMutex = Mutex()
    private var inFlightRefresh: CompletableDeferred<RefreshOutcome>? = null

    /**
     * 并发 401 汇入点:只有第一个进来者真发 refresh-token,其余挂起等同一个结果。
     * inFlightRefresh 只在 mutex 内读写,check-then-act 无竞态。
     * ⚠️ Owner 协程取消兜底:Owner 在 doRefresh 挂起期间被取消(真实触发:Gate 面板 when 条件
     * 渲染切换/LoginPanel→RegisterPanel 销毁旧面板组合取消其 rememberCoroutineScope;M3 二级页
     * closePage 同理)时 CancellationException 直接上抛,若不 complete deferred,等待者将在
     * await() 上永久悬挂、inFlightRefresh 永不清,此后一切 401 都成为 Waiter 卡死 —— 故取消
     * 时先以 NetworkError complete(等待者按可重试处理,会话保留),finally 再清 inFlightRefresh
     * (即便 finally 因取消竞争没清成,joinRefresh 的 !existing.isCompleted 对已 complete 的
     * deferred 判假,下一个 401 自动成为新 Owner 自愈),最后 rethrow 保持结构化取消
     */
    private suspend fun silentRefresh(pager: Pager, failedAccessToken: String): RefreshOutcome {
        return when (val join = joinRefresh(failedAccessToken)) {
            RefreshJoin.TokenRefreshed -> RefreshOutcome.Success
            is RefreshJoin.Waiter -> join.deferred.await()
            is RefreshJoin.Owner -> {
                // 网络调用不放 mutex 内:等待者靠 deferred 等结果,不阻塞在锁上
                try {
                    val outcome = doRefresh(pager)
                    join.deferred.complete(outcome)
                    outcome
                } catch (e: CancellationException) {
                    join.deferred.complete(RefreshOutcome.NetworkError)
                    throw e
                } finally {
                    refreshMutex.withLock {
                        if (inFlightRefresh === join.deferred) inFlightRefresh = null
                    }
                }
            }
        }
    }

    private suspend fun joinRefresh(failedAccessToken: String): RefreshJoin = refreshMutex.withLock {
        val current = SessionStore.accessToken
        if (current.isNotEmpty() && current != failedAccessToken) {
            // 已有人刷出新 token(与失败的那个不同),直接重放
            RefreshJoin.TokenRefreshed
        } else {
            val existing = inFlightRefresh
            if (existing != null && !existing.isCompleted) {
                RefreshJoin.Waiter(existing)
            } else {
                RefreshJoin.Owner(CompletableDeferred<RefreshOutcome>().also { inFlightRefresh = it })
            }
        }
    }

    /**
     * 真发 refresh-token(免登,不带 Authorization);成功回写 SessionStore(全字段)。
     * 直连不走 [request]:若经 request,刷新端点自身回 code==401 会再触发单飞刷新(语义相悖)。
     * 刷新路径唯一来源 [ApiConfig.PATH_REFRESH_TOKEN],AuthApi 不重复定义防改一漏一
     */
    private suspend fun doRefresh(pager: Pager): RefreshOutcome {
        val oldRefreshToken = SessionStore.refreshToken
        if (oldRefreshToken.isEmpty()) return RefreshOutcome.Invalid
        val body = JSONObject()
            .put(KEY_REFRESH_TOKEN, oldRefreshToken)
            .put(KEY_CLIENT_ID, ApiConfig.CLIENT_ID)
        val url = ApiConfig.url(pager, ApiConfig.PATH_REFRESH_TOKEN)
        return when (val raw = rawRequest(pager, url, isPost = true, body = body, token = "")) {
            is RawResult.NetworkError -> RefreshOutcome.NetworkError
            is RawResult.Common -> {
                val fresh = if (raw.root.optLong(KEY_CODE, 0L) == CODE_OK) {
                    AuthSession.from(raw.root.optJSONObject(KEY_DATA), fallbackClientId = ApiConfig.CLIENT_ID)
                } else {
                    null
                }
                if (fresh == null) {
                    // 刷新被服务端拒(令牌无效/停用等)→ 清会话回 Gate
                    RefreshOutcome.Invalid
                } else {
                    // 响应缺字段的兜底合并,保证持久化 JSON 五字段完整(§7.2)
                    SessionStore.update(
                        pager,
                        fresh.copy(
                            refreshToken = fresh.refreshToken.ifEmpty { oldRefreshToken },
                            userId = fresh.userId.ifEmpty { SessionStore.userId },
                        ),
                    )
                    RefreshOutcome.Success
                }
            }
        }
    }

    // ---------- 底层请求(照 BridgeModule.ssoRequest 先例:core 网络模块 + suspendCoroutine 包装) ----------

    private sealed interface RawResult {
        /** 已解析出 CommonResult 根对象(含 code/data/msg) */
        class Common(val root: JSONObject) : RawResult

        /** HTTP 失败/超时/非 JSON 回包 */
        data object NetworkError : RawResult
    }

    private suspend fun rawRequest(
        pager: Pager,
        url: String,
        isPost: Boolean,
        body: JSONObject?,
        token: String,
    ): RawResult {
        // 铁律⑦:一律可空 getModule;模块未就绪降级为网络错误(可重试),绝不走 acquireModule 双抛路径
        val network = try {
            pager.getModule<NetworkModule>(NetworkModule.MODULE_NAME)
        } catch (_: Exception) {
            null
        } ?: return RawResult.NetworkError

        val headers = JSONObject()
        // §7.1-4:写请求强制 JSON Content-Type(core 原生侧仅此类型才按 JSON 原文写出 body)
        if (isPost) {
            headers.put(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
        }
        // §7.1-2:每请求手动塞 Bearer token(免登接口 token 为空则不带)
        if (token.isNotEmpty()) {
            headers.put(HEADER_AUTHORIZATION, BEARER_PREFIX + token)
        }

        return suspendCoroutine { continuation ->
            network.httpRequest(
                url,
                isPost,
                body ?: JSONObject(),
                headers,
                null,
                TIMEOUT_SECONDS,
            ) { data, success, _, _ ->
                continuation.resume(parseRaw(data, success))
            }
        }
    }

    /**
     * 回包预处理:success=false(真·非 2xx,网关 502/超时)按网络错误;
     * 非 JSON 回包被 core 包成 {data:原始文本}(NetworkModule 实现),剥一层能出 CommonResult 就用,
     * 剥不出按网络错误(§7.1-1)
     */
    private fun parseRaw(data: JSONObject, success: Boolean): RawResult {
        if (!success) return RawResult.NetworkError
        if (!data.has(KEY_CODE)) {
            val inner = data.optString(KEY_DATA)
            if (inner.isNotEmpty()) {
                val parsed = try {
                    JSONObject(inner)
                } catch (_: Throwable) {
                    null
                }
                if (parsed != null && parsed.has(KEY_CODE)) return RawResult.Common(parsed)
            }
            return RawResult.NetworkError
        }
        return RawResult.Common(data)
    }
}
