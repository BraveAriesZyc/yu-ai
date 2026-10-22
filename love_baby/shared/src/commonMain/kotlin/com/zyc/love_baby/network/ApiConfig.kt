package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager

/**
 * ApiConfig — app-api 基地址与全局常量(INTEGRATION-DESIGN §7.5 / §5.2)
 * 用法:HttpClient 内部经 ApiConfig.url(pager, path) 拼完整地址;clientId 取 ApiConfig.CLIENT_ID
 * 依赖:仅 core Pager.pageData(不走模块,无 willInit 时序问题)
 * 事件:无
 * 功能:
 *  - 默认基地址 http://10.0.2.2:48080(Android 模拟器访问宿主机)
 *  - pageData.params.apiBaseUrl 覆盖默认值(ThemeStore.initFromPageData 同款读法;
 *    真机局域网 IP / 各端宿主在 createPageData 注入,改宿主一处即可)
 *  - CLIENT_ID 固定 "couple-app"(§5.2 登录/刷新契约字段,SessionStore 持久化供刷新用)
 */
internal object ApiConfig {

    /** 默认基地址:仅兜底(Android 模拟器直跑用;真机/正式包由宿主 createPageData 注入 apiBaseUrl 覆盖) */
    const val DEFAULT_BASE_URL = "https://love.shattered-dream.top"

    /** OAuth2 client,§5.2 约定恒为 couple-app */
    const val CLIENT_ID = "couple-app"

    /**
     * 刷新令牌接口路径(§5.2)。唯一来源:仅 HttpClient.doRefresh 直连使用(不经 request 的 401
     * 通道,防刷新端点 401 递归单飞);AuthApi 不提供 refreshToken 接口,防止误走通用请求路径
     */
    const val PATH_REFRESH_TOKEN = "/love/auth/refresh-token"

    /** pageData.params 覆盖 key:宿主 createPageData 注入 apiBaseUrl(§7.5) */
    const val PAGE_DATA_KEY = "apiBaseUrl"

    /**
     * app-api 挂载前缀。§5.2 标题"前缀 /app-api/love"指最终 URL 命名空间(即 /app-api/love 斜杠前缀,§2 架构图同);
     * 表内路径自带 /love 模块段(yudao controller/app 的 @RequestMapping),故完整地址 = base + "/app-api" + path,
     * 如 path=/love/auth/login → {base}/app-api/love/auth/login
     */
    private const val API_PREFIX = "/app-api"

    /**
     * 解析当前基地址:pageData.params.apiBaseUrl 优先,否则默认常量。
     * pageData 在 willInit 前已就绪,任意时机调用安全;网络请求时才解析,支持同进程内多页不同配置
     */
    fun baseUrl(pager: Pager): String {
        val fromPageData = try {
            pager.pageData.params.optString(PAGE_DATA_KEY)
        } catch (_: Exception) {
            ""
        }
        val raw = if (fromPageData.isNotEmpty()) fromPageData else DEFAULT_BASE_URL
        return raw.trimEnd('/')
    }

    /** 拼完整接口地址:path 以 /love/ 开头,如 url(pager, "/love/auth/login") */
    fun url(pager: Pager, path: String): String = baseUrl(pager) + API_PREFIX + path

    /**
     * WS 触发器端点(D-16,§5.2 WS 通道):http(s)→ws(s) 换协议 + yudao 固定路径。
     * token 不在此拼——原生模块拼握手 query(?token=,鉴权走 TokenAuthenticationFilter Parameter 回退)
     */
    fun wsUrl(pager: Pager): String {
        val http = baseUrl(pager)
        val ws = if (http.startsWith("https://")) {
            "wss://" + http.removePrefix("https://")
        } else {
            "ws://" + http.removePrefix("http://")
        }
        return ws + WS_PATH
    }

    /** yudao WS 端点路径(application.yaml yudao.websocket.path;非 app-api,无 /app-api 前缀) */
    private const val WS_PATH = "/infra/ws"
}
