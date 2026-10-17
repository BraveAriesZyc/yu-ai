package com.zyc.love_baby

import android.content.Context
import android.content.Intent
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
import com.zyc.love_baby.adapter.KRColorParserAdapter
import com.zyc.love_baby.adapter.KRFontAdapter
import com.zyc.love_baby.adapter.KRImageAdapter
import com.zyc.love_baby.adapter.KRLogAdapter
import com.zyc.love_baby.adapter.KRRouterAdapter
import com.zyc.love_baby.adapter.KRThreadAdapter
import com.zyc.love_baby.adapter.KRUncaughtExceptionHandlerAdapter
import com.zyc.love_baby.module.KRBridgeModule
import com.zyc.love_baby.module.KRFileModule
import com.zyc.love_baby.module.KRShareModule
import com.zyc.love_baby.module.KRWebSocketModule
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

    // 双击返回退出(2026-10-17 真机反馈:主界面误触返回手势直接退 App 太生硬):仅根页面
    // (main)启用本 callback 接管返回——首次提示「再按一次退出」,2s 内再次返回才 finish;
    // 二级页(每个 @Page 经 KRRouterAdapter 独立起一个 KuiklyRenderActivity 实例)callback
    // 不启用,系统默认出栈行为不变
    private var lastBackPressAt = 0L

    private val backToExitCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val now = System.currentTimeMillis()
            if (now - lastBackPressAt < EXIT_INTERVAL_MS) {
                finish() // 根 Activity finish = 退出任务(与原「返回即退」终态一致)
            } else {
                lastBackPressAt = now
                Toast.makeText(this@KuiklyRenderActivity, "再按一次退出", Toast.LENGTH_SHORT).show()
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
        backToExitCallback.isEnabled = pageName == PAGE_MAIN
        kuiklyRenderViewDelegator.onAttach(hrContainerView, "", pageName, createPageData())
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
        }
    }

    override fun registerExternalRenderView(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalRenderView(kuiklyRenderExport)
        with(kuiklyRenderExport) {

        }
    }

    /** 持久化主题 id(KRSharedPreferencesModule 同文件同 key,createPageData/window 背景共用一次读) */
    private fun persistedThemeId(): String =
        getSharedPreferences("KRSharedPreferencesModule", Context.MODE_PRIVATE)
            .getString(THEME_KEY, "") ?: ""

    private fun createPageData(): Map<String, Any> {
        val param = argsToMap()
        param["appId"] = 1
        // 首帧前传主题:与 KRSharedPreferencesModule 同文件同 key(其存储文件名即模块名),
        // 页面 willInit 读 pageData 直接定主题,避免启动先渲染默认主题再全局重组一遍
        param[THEME_KEY] = persistedThemeId()
        // 首帧前传会话:同文件同 key(与 shared state/SessionStore.PREF_KEY 一致)注入完整 session JSON
        // (accessToken/refreshToken/userId/expiresTime/clientId,§7.2)。GatePanel 组合期经
        // SessionStore.initFromPageData 判定:未过期放行 / 过期但 refreshToken 可用也放行
        // (首请求 401 触发单飞刷新)/ 都没有才 Gate,消除冷启动 Gate 闪帧
        param[SESSION_KEY] = getSharedPreferences("KRSharedPreferencesModule", Context.MODE_PRIVATE)
            .getString(SESSION_KEY, "") ?: ""
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
