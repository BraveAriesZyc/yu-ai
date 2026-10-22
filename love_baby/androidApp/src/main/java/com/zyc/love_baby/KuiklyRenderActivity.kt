package com.zyc.love_baby

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.adapter.KuiklyRenderAdapterManager
import com.tencent.kuikly.core.render.android.css.ktx.toMap
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import com.tencent.kuikly.core.render.android.expand.component.KRTextFieldView
import com.zyc.love_baby.adapter.KRColorParserAdapter
import com.zyc.love_baby.adapter.KRFontAdapter
import com.zyc.love_baby.adapter.KRImageAdapter
import com.zyc.love_baby.adapter.KRLogAdapter
import com.zyc.love_baby.adapter.KRRouterAdapter
import com.zyc.love_baby.adapter.KRThreadAdapter
import com.zyc.love_baby.adapter.KRUncaughtExceptionHandlerAdapter
import com.zyc.love_baby.module.KRBridgeModule
import com.zyc.love_baby.module.KRFileModule
import com.zyc.love_baby.module.KRNotificationModule
import com.zyc.love_baby.module.KRShareModule
import com.zyc.love_baby.module.KRWebSocketModule
import com.zyc.love_baby.view.KRDefaultTypefaceTextFieldView
import org.json.JSONObject

class KuiklyRenderActivity : AppCompatActivity(), KuiklyRenderViewBaseDelegatorDelegate {

    private lateinit var hrContainerView: ViewGroup
    private lateinit var loadingView: View
    private lateinit var errorView: View

    private val kuiklyRenderViewDelegator = KuiklyRenderViewBaseDelegator(this)

    private val pageName: String
        get() {
            val pn = intent.getStringExtra(KEY_PAGE_NAME) ?: ""
            return if (pn.isNotEmpty()) {
                return pn
            } else {
                PAGE_MAIN
            }
        }

    // 返回键统一收口(2026-10-17 双击退出 + 2026-10-22 浮层返回关闭):callback 常开三段路由——
    // ① 先问 Kuikly 侧(delegator.onBackPressed:发 onBackPressed 事件进 common 层再读回 consumed;
    //    框架对该事件强制同步发送 innerSyncSendEvent,返回前 common 侧必已写好标记,非陈旧值):
    //    consumed = 页内有 BackHandler(全屏浮层)注册,返回键已用于关浮层,宿主直接放手;
    // ② 未消费且 main 页:双击退出窗口,2s 内再次返回才 finish(根 Activity finish = 退出任务);
    // ③ 未消费且二级页(每个 @Page 经 KRRouterAdapter 独立起一个 KuiklyRenderActivity 实例):
    //    finish() = 与系统默认出栈行为一致
    private var lastBackPressAt = 0L

    private val backToExitCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (kuiklyRenderViewDelegator.onBackPressed()) return
            if (pageName == PAGE_MAIN) {
                val now = System.currentTimeMillis()
                if (now - lastBackPressAt < EXIT_INTERVAL_MS) {
                    finish() // 根 Activity finish = 退出任务(与原「返回即退」终态一致)
                } else {
                    lastBackPressAt = now
                    Toast.makeText(this@KuiklyRenderActivity, "再按一次退出", Toast.LENGTH_SHORT).show()
                }
            } else {
                finish() // 二级页出栈(Kuikly 侧未消费 = 页内无浮层接管)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启动白屏治理(2026-10-17 真机反馈):加载期窗口背景对齐页面主题底色——默认主题由
        // styles.xml windowBackground(splash_bg=蜜桃恋语底)静态兜底,非默认主题在此按持久化
        // id 覆写;starting window→Activity window→Kuikly 首帧三级同色,白屏变主题底色无缝衔接
        window.setBackgroundDrawable(ColorDrawable(themeBgColor(persistedThemeId())))

        setContentView(R.layout.activity_hr)
        setupImmersiveMode()
        hrContainerView = findViewById(R.id.hr_container)
        loadingView = findViewById(R.id.hr_loading)
        errorView = findViewById(R.id.hr_error)
        onBackPressedDispatcher.addCallback(this, backToExitCallback)
        maybeRequestNotificationPermission(savedInstanceState)
        kuiklyRenderViewDelegator.onAttach(hrContainerView, "", pageName, createPageData())
    }

    /**
     * 打开即请求通知权限(M22,用户裁决「在打开的时候获取」):targetSdk 33 起系统不再
     * 自动弹窗(≤32 时代每个安装只弹一次、拒绝即永久静默——「需要自己去开权限」的根因),
     * 改由应用对系统申请。仅主页触发(单 Activity 每页模型下不加 main 闸则每开一个二级页
     * 都会走到这里),且仅真冷启动触发——重建(savedInstanceState 非空,旋转/深浅色等配置
     * 变更)不再弹,防止每次重建烧系统「拒绝 2 次」预算(预算烧完连权限页的弹框机会一并
     * 失效,3-skeptic minor);重复冷启动反复申请由系统自带上限收口(拒绝两次后
     * requestPermissions 不再弹框但仍回调 DENIED,静默拒绝),无需自建持久化闸门;
     * 已授权/低版本(<13 无运行时权限)直接短路。
     * 结果不接回调(用户在权限管理页可查状态,红点/静默降级兜底)
     */
    private fun maybeRequestNotificationPermission(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        if (pageName != PAGE_MAIN) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        try {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), KRNotificationModule.REQUEST_CODE_PERMISSION)
        } catch (_: Exception) {
            // 极端场景(申请窗口异常)不崩:红点兜底,权限管理页可查可补
        }
    }

    /** 权限申请结果统一转发:页面主动申请(METHOD_REQUEST)经 KRNotificationModule 静态
     *  桥回传 common;自动请求(本类 onCreate)无 pending 静默丢弃 */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        KRNotificationModule.onPermissionResult(requestCode, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        kuiklyRenderViewDelegator.onDetach()
    }

    override fun onPause() {
        super.onPause()
        kuiklyRenderViewDelegator.onPause()
    }

    override fun onResume() {
        super.onResume()
        kuiklyRenderViewDelegator.onResume()
    }

    override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalModule(kuiklyRenderExport)
        with(kuiklyRenderExport) {
            moduleExport(KRBridgeModule.MODULE_NAME) {
                KRBridgeModule()
            }
            moduleExport(KRShareModule.MODULE_NAME) {
                KRShareModule()
            }
            // WS 触发器(D-16):common base/WebSocketModule 声明,ChatPushStore 经 getModule 取用
            moduleExport(KRWebSocketModule.MODULE_NAME) {
                KRWebSocketModule()
            }
            // 头像上传(文件服务里程碑):common base/FileModule 声明,选图+压缩+直传一条龙
            moduleExport(KRFileModule.MODULE_NAME) {
                KRFileModule()
            }
            // 后台消息通知(M21):common base/NotificationModule 声明,pull 落态钩子驱动,
            // 前台 no-op(KRApplication started 计数),App 后台才发系统通知
            moduleExport(KRNotificationModule.MODULE_NAME) {
                KRNotificationModule()
            }
        }
    }

    override fun registerExternalRenderView(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalRenderView(kuiklyRenderExport)
        with(kuiklyRenderExport) {
            // 密码框占位符字体修复:同名覆盖内置 KRTextFieldView(本 hook 由框架在内置注册之后
            // 回调,viewExportCreator 同名后写覆盖,2.29 时序见 KRDefaultTypefaceTextFieldView 头注释;
            // softInputMode() 取 delegate 默认,与内置注册 KRTextFieldView(context, delegate.softInputMode()) 同参)。
            // ⚠ 位置传参:renderViewExport 末位 shadowExportCreator 是可空函数参,尾随 lambda 会绑
            // 到它上(renderViewExportCreator 漏参编译错),lambda 必须按位置传(toNative 回调/pickAndUpload 同款坑)
            renderViewExport(KRTextFieldView.VIEW_NAME, { context ->
                KRDefaultTypefaceTextFieldView(context, softInputMode())
            })
        }
    }

    /**
     * 启动期原生 prefs(KRSharedPreferencesModule 存储文件,shared 各 Store 持久化同源):
     * window 背景 / createPageData 三路注入共用一份实例(框架按文件缓存,读取零成本)
     */
    private val bootPrefs by lazy {
        getSharedPreferences("KRSharedPreferencesModule", Context.MODE_PRIVATE)
    }

    /** 持久化主题 id(bootPrefs 同文件同 key,window 背景与 createPageData 共用一次读) */
    private fun persistedThemeId(): String = bootPrefs.getString(THEME_KEY, "") ?: ""

    private fun createPageData(): Map<String, Any> {
        val param = argsToMap()
        param["appId"] = 1
        // 首帧前原生 prefs 一次读、四路注入(同文件同 key,与 shared 各 Store 持久化 key 双源一致):
        // ① 主题(ThemeStore):页面 willInit 读 pageData 直接定主题,避免启动先渲染默认主题再全局重组一遍;
        // ② 会话(SessionStore):注入完整 session JSON(accessToken/refreshToken/userId/expiresTime/
        //    clientId,§7.2),GatePanel 组合期经 initFromPageData 判定放行,消除冷启动 Gate 闪帧;
        // ③ 绑定提示(CoupleStore):已绑定冷启动 willInit 乐观恢复直接翻 BOUND 进主功能,
        //    不闪 BindingPanel「校准中」卡(2026-10-17 真机反馈);
        // ④ api 基地址(ApiConfig):debug/release 各自的 BuildConfig.API_BASE_URL 注入,
        //    覆盖 shared ApiConfig 默认 10.0.2.2(§7.5 pageData 双通道)
        param[THEME_KEY] = persistedThemeId()
        val sessionJson = bootPrefs.getString(SESSION_KEY, "") ?: ""
        param[SESSION_KEY] = sessionJson
        // 会话非空才传 hint:登出后 prefs 里的 hint 是登出前残留,空会话配 bound 提示会让
        // 未登录首帧误判;shared 侧 initFromPageData 未登录守卫与此双保险
        if (sessionJson.isNotEmpty()) {
            param[BIND_HINT_KEY] = bootPrefs.getString(BIND_HINT_KEY, "") ?: ""
        }
        // ④ api 基地址:WS 地址由 shared ApiConfig.wsUrl 派生,改此处 HTTP/WS 一处双生效
        param[API_BASE_URL_KEY] = BuildConfig.API_BASE_URL
        return param
    }

    private fun argsToMap(): MutableMap<String, Any> {
        val jsonStr = intent.getStringExtra(KEY_PAGE_DATA) ?: return mutableMapOf()
        return JSONObject(jsonStr).toMap()
    }

    private fun setupImmersiveMode() {
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 关闭系统给透明导航栏自动加的对比度蒙层,否则手势条区域会有一层灰底
                isNavigationBarContrastEnforced = false
            }
        }
        // 内容延伸到状态栏/导航栏下面(全面屏沉浸);
        // 安全距离由渲染层通过 pageData.safeAreaInsets 下发给页面做底部避让
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = true
    }

    companion object {

        private const val KEY_PAGE_NAME = "pageName"
        private const val KEY_PAGE_DATA = "pageData"

        /** 根页面名(pageName 缺省值):仅此页面接管返回做双击退出 */
        private const val PAGE_MAIN = "main"

        /** 双击返回退出的判定窗口 */
        private const val EXIT_INTERVAL_MS = 2_000L

        /**
         * 持久化主题 id → 页面背景色(与 shared components/AppTheme.kt ThemeStore.all 各主题 bg
         * 一一对应,色值双源同步勿单改一边;未知 id/未持久化走默认「蜜桃恋语」,与 splash_bg 同源)
         */
        private fun themeBgColor(themeId: String): Int = when (themeId) {
            "sunset" -> 0xFFFFF7F2.toInt()
            "mint" -> 0xFFF2FAF5.toInt()
            "ocean" -> 0xFFF3F8FD.toInt()
            "lavender" -> 0xFFF8F6FD.toInt()
            else -> 0xFFFDF6F4.toInt()
        }

        /** 主题持久化 key,与 shared ThemeStore.PREF_KEY 保持一致 */
        private const val THEME_KEY = "app_theme_id"

        /** 会话持久化 key,与 shared state/SessionStore.PREF_KEY 保持一致 */
        private const val SESSION_KEY = "love_app_session"

        /** 绑定态提示持久化 key,与 shared state/CoupleStore.BIND_HINT_KEY 同串双源,勿单改一边 */
        private const val BIND_HINT_KEY = "couple_bind_hint"

        /** api 基地址 pageData key,与 shared network/ApiConfig.PAGE_DATA_KEY 同串双源,勿单改一边 */
        private const val API_BASE_URL_KEY = "apiBaseUrl"

        init {
            initKuiklyAdapter()
        }

        fun start(context: Context, pageName: String, pageData: JSONObject) {
            val starter = Intent(context, KuiklyRenderActivity::class.java)
            starter.putExtra(KEY_PAGE_NAME, pageName)
            starter.putExtra(KEY_PAGE_DATA, pageData.toString())
            context.startActivity(starter)
        }

        private fun initKuiklyAdapter() {
            with(KuiklyRenderAdapterManager) {
                krImageAdapter = KRImageAdapter(KRApplication.application)
                krLogAdapter = KRLogAdapter
                krUncaughtExceptionHandlerAdapter = KRUncaughtExceptionHandlerAdapter
                krFontAdapter = KRFontAdapter
                krColorParseAdapter = KRColorParserAdapter(KRApplication.application)
                krRouterAdapter = KRRouterAdapter
                krThreadAdapter = KRThreadAdapter()
            }
        }
    }
}
