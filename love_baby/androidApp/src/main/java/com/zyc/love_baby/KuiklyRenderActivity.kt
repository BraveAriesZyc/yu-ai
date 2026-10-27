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
// WS 触发器原生件(M34):官方组件 KuiklyWebsocket 的 Android 实现(vendored 源码,原包名),
// 替代自研 module/KRWebSocketModule;common 侧组件 WebSocketModule(同注册名)经 platformExternalModules 注册
import com.tencent.kuiklybase.websocket.KRWebSocketModule
import com.zyc.love_baby.adapter.KRColorParserAdapter
import com.zyc.love_baby.adapter.KRFontAdapter
import com.zyc.love_baby.adapter.KRImageAdapter
import com.zyc.love_baby.adapter.KRLogAdapter
import com.zyc.love_baby.adapter.KRRouterAdapter
import com.zyc.love_baby.adapter.KRThreadAdapter
import com.zyc.love_baby.adapter.KRUncaughtExceptionHandlerAdapter
import com.zyc.love_baby.module.KRAudioModule
import com.zyc.love_baby.module.KRBatteryModule
import com.zyc.love_baby.module.KRBridgeModule
import com.zyc.love_baby.module.KRFileModule
import com.zyc.love_baby.module.KRLocationModule
import com.zyc.love_baby.module.KRNotificationModule
import com.zyc.love_baby.module.KRShareModule
import com.zyc.love_baby.module.PermissionSnapshot
import com.zyc.love_baby.view.KRAMapView
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
        // 首启隐私 Gate(M33 上架合规):同意前不装 Kuikly 渲染、不申请通知权限、不做任何采集,
        // 加载动画一并隐藏(弹窗背后不应见 spinner);同意回调里再走原启动链。二级页(每页独立
        // Activity)仅能从已同意会话内打开,天然过闸
        if (PrivacyGate.isAgreed(this)) {
            bootKuikly(savedInstanceState)
        } else {
            loadingView.visibility = View.INVISIBLE
            PrivacyGate.showAgreementDialog(this) {
                loadingView.visibility = View.VISIBLE
                bootKuikly(null)
            }
        }
    }

    /**
     * Kuikly 启动链(M33 从 onCreate 抽出):通知权限申请 + 渲染挂载,受首启隐私 Gate 管控——
     * 同意后才走。语义与原直连完全一致,仅多了 Gate 这一层时序
     */
    private fun bootKuikly(savedInstanceState: Bundle?) {
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
     *  桥回传 common;自动请求(本类 onCreate)无 pending 静默丢弃。
     *  M23 录音(RECORD_AUDIO)转发 KRFileModule 静态桥(request code 错开,双静态槽互不误收);
     *  M24 十三轮相册弹层读库权限同款第三槽(2003);
     *  M26 定位双权限(FINE/COARSE any-granted)同款第四槽(2004);
     *  M29 权限页麦克风(2005)/相册读(2006)申请同款第五/六槽 */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        KRNotificationModule.onPermissionResult(requestCode, grantResults)
        KRFileModule.onRecordPermissionResult(requestCode, grantResults)
        KRFileModule.onMediaPermissionResult(requestCode, grantResults)
        KRLocationModule.onPermissionResult(requestCode, grantResults)
        KRFileModule.onMicPermissionResult(requestCode, grantResults)
        KRFileModule.onMediaRequestPermissionResult(requestCode, grantResults)
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
        // 权限页状态回前台重推(M36):pageData 快照只在创建时读一次,从系统设置/应用详情
        // 改完权限返回时已是旧值,而模块回调在本页静默丢失[M29 实证]——pageDidAppear 的
        // 逐组模块重查到不了包。改由宿主重采快照经 delegator.sendEvent 直推(与首帧播种
        // 同一 PermissionSnapshot 单源,common 侧事件观察者落态),回页即真值;
        // renderView 未就绪时事件由 delegator 排队,core 未初始化时由渲染层 lazy 队列兜住
        if (pageName == PAGE_PERMISSION) {
            kuiklyRenderViewDelegator.sendEvent(
                PermissionSnapshot.EVENT_PERM_SNAPSHOT,
                mapOf(PermissionSnapshot.KEY_PERM_SNAPSHOT to PermissionSnapshot.build(this))
            )
        }
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
            // WS 触发器(M34):官方组件 KuiklyWebsocket 的 Android 原生件(vendored 源码),
            // common 侧组件 WebSocketModule(vendored,包名 com.tencent.kuiklybase.websocket)
            // 经 platformExternalModules 注册,双端同串 "KRWebSocketModule";ChatPushStore 经
            // getModule 取用,心跳/自动重连/网络感知由组件 common 侧 WebSocketManager 驱动
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
            // 语音播放(M23):common base/AudioModule 声明,聊天语音气泡起播/单飞切换/停播
            moduleExport(KRAudioModule.MODULE_NAME) {
                KRAudioModule()
            }
            // 电量互看(M26):common base/BatteryModule 声明,BatteryStore 轮询+回前台上报驱动,
            // sticky broadcast 窥视读值无权限;iOS/鸿蒙/H5 未注册自动降级「暂不支持」
            moduleExport(KRBatteryModule.MODULE_NAME) {
                KRBatteryModule()
            }
            // 位置共享(M26):common base/LocationModule 声明,LocationStore 节流上报/位置页驱动,
            // 原生前台闸仅前台采集,单飞搭车双 provider 定位;iOS/鸿蒙/H5 未注册自动降级
            moduleExport(KRLocationModule.MODULE_NAME) {
                KRLocationModule()
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
            // 位置页原生动态地图(M27):common base/AMapNativeView 声明,LocationPage 全屏地图
            // + 双人头像 Marker;高德 Android 平台 key 配 Manifest meta-data(未配 key 瓦片空白
            // 不崩)。iOS/鸿蒙/H5 未注册,common 侧整页降级「暂不支持」
            renderViewExport(KRAMapView.VIEW_NAME, { context ->
                KRAMapView(context)
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
        // ⑤ 权限页状态快照(M29):模块回调在权限管理页静默丢失真机实证(页永卡「查询中」),
        //    首帧真实状态由宿主直注绕开模块桥(theme/session 同款 pageData 通道);common 侧
        //    PermissionPager.willInit 播种,四组查询与模块 check 回包同一 companion 单源
        if (pageName == PAGE_PERMISSION) {
            param[PermissionSnapshot.KEY_PERM_SNAPSHOT] = PermissionSnapshot.build(this)
        }
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

        /** 权限管理页名(M29 快照直注闸;shared PermissionPage.kt PERMISSION_PAGE_NAME 同串) */
        private const val PAGE_PERMISSION = "permissionPage"

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
