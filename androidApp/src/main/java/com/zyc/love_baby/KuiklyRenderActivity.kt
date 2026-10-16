package com.zyc.love_baby

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
                "main"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_hr)
        setupImmersiveMode()
        hrContainerView = findViewById(R.id.hr_container)
        loadingView = findViewById(R.id.hr_loading)
        errorView = findViewById(R.id.hr_error)
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

    private fun createPageData(): Map<String, Any> {
        val param = argsToMap()
        param["appId"] = 1
        // 首帧前传主题:与 KRSharedPreferencesModule 同文件同 key(其存储文件名即模块名),
        // 页面 willInit 读 pageData 直接定主题,避免启动先渲染默认主题再全局重组一遍
        param[THEME_KEY] = getSharedPreferences("KRSharedPreferencesModule", Context.MODE_PRIVATE)
            .getString(THEME_KEY, "") ?: ""
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
