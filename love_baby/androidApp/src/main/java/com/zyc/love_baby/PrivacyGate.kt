package com.zyc.love_baby

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * PrivacyGate — 首启隐私合规 Gate(M33 上架准备)
 * 用法:KuiklyRenderActivity.onCreate 未同意时 showAgreementDialog(activity) { bootKuikly() };
 *      KRApplication.onCreate 仅 isAgreed 时补调高德合规对
 * 依赖:appcompat AlertDialog;PrivacyWebActivity(应用内全文页);高德 MapsInitializer(合规对)
 * 事件:同意 → onAgreed 落库 + 高德合规补调 + onGranted 回调(宿主拉起 Kuikly);
 *      不同意 → 二级确认(重新查看 / 退出应用 finishAffinity)
 * 功能:
 *  - 国内应用商店硬性要求:首次启动先弹《用户协议与隐私政策》,同意前不初始化任何涉及个人
 *    信息采集的组件(Kuikly 渲染页/通知权限申请/高德 SDK 合规确认),不同意可退出
 *  - 同意态落独立 prefs 文件:不进 KRSharedPreferencesModule(登出清场/换号不得抹掉同意记录),
 *    POLICY_VERSION 版本化——将来协议实质修订时 +1,已装用户重新走一遍弹窗
 *  - 弹窗正文内《用户协议》《隐私政策》可点,应用内 WebView(PrivacyWebActivity)读全文
 */
object PrivacyGate {

    private const val PREFS_FILE = "love_privacy_gate"
    private const val KEY_AGREED_VERSION = "agreed_policy_version"

    /** 协议版本:全文实质性修订时 +1,已装用户将重新走一遍弹窗 */
    private const val POLICY_VERSION = 1

    /** 协议全文 URL(服务端 nginx 托管静态页;MinePage.AGREEMENT_URL 同域,勿单改一边) */
    const val URL_AGREEMENT = "https://love.shattered-dream.top/agreement.html"
    const val URL_PRIVACY = "https://love.shattered-dream.top/privacy.html"

    fun isAgreed(context: Context): Boolean =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getInt(KEY_AGREED_VERSION, 0) >= POLICY_VERSION

    /**
     * 同意落库(幂等,版本化)+ 补调被 Gate 拦下的采集类初始化(高德合规对);
     * onGranted 由宿主拉起 Kuikly(二次进弹窗/已同意重入时直通)
     */
    fun onAgreed(context: Context, onGranted: () -> Unit) {
        if (!isAgreed(context)) {
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                .edit().putInt(KEY_AGREED_VERSION, POLICY_VERSION).apply()
            ensureAmapCompliance(context)
        }
        onGranted()
    }

    /**
     * 高德 SDK 隐私合规(2021 铁律:任何高德 SDK 接口调用前必须先隐私确认;M33 起挂本 Gate——
     * 同意前不调,同意时点两处:KRApplication.onCreate 存量已同意 / onAgreed 弹窗同意后):
     * updatePrivacyShow/updatePrivacyAgree 必须在 MapView 创建前调一次;10.0.600 未调会拒绝
     * 初始化地图(瓦片空白/回调缺),不会崩。仅 3dmap 一件(定位走系统 LocationManager,
     * 未引高德定位 SDK 无需其合规对)
     */
    fun ensureAmapCompliance(context: Context) {
        try {
            com.amap.api.maps.MapsInitializer.updatePrivacyShow(context, true, true)
            com.amap.api.maps.MapsInitializer.updatePrivacyAgree(context, true)
        } catch (_: Throwable) {
            // 极端场景(SDK 未打包/初始化异常)静默:地图降级,不拖垮 App 启动
        }
    }

    /**
     * 首启协议弹窗(仅未同意时由宿主调用;cancelable=false 挡返回键关闭——退出路径只有
     * 「不同意并退出」,满足商店「拒绝后可退出」要求)
     */
    fun showAgreementDialog(activity: Activity, onGranted: () -> Unit) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("用户协议与隐私政策")
            .setMessage(buildSpannedMessage(activity))
            .setCancelable(false)
            .setPositiveButton("同意并继续") { _, _ ->
                onAgreed(activity.applicationContext, onGranted)
            }
            .setNegativeButton("不同意并退出") { _, _ -> showRefuseConfirm(activity, onGranted) }
            .create()
        dialog.show()
        // 正文链接可点(appcompat 默认 message TextView 不吃 ClickableSpan,须挂 LinkMovementMethod)
        (dialog.findViewById(android.R.id.message) as? TextView)?.apply {
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
        }
    }

    /** 不同意二级确认:重新查看回到首启弹窗,退出应用走 finishAffinity(商店要求给退出路径) */
    private fun showRefuseConfirm(activity: Activity, onGranted: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("温馨提示")
            .setMessage("未同意《用户协议》与《隐私政策》无法使用予爱。你可以重新查看协议,或退出应用。")
            .setCancelable(false)
            .setPositiveButton("重新查看") { _, _ -> showAgreementDialog(activity, onGranted) }
            .setNegativeButton("退出应用") { _, _ -> activity.finishAffinity() }
            .show()
    }

    /** 弹窗正文:承诺要点 + 两条可点链接(应用内 WebView 全文) */
    private fun buildSpannedMessage(activity: Activity): SpannableString {
        val text = "欢迎使用予爱。我们非常重视你的个人信息与隐私保护,在使用前请阅读并同意"
        val linkAgreement = "《用户协议》"
        val mid = "与"
        val linkPrivacy = "《隐私政策》"
        val tail = "。\n\n账号注册需提供手机号;位置共享默认关闭,开启并授权后才在应用前台采集;" +
            "我们不接入第三方统计与广告 SDK。点击「同意并继续」即代表你已阅读并同意上述协议。"
        val full = text + linkAgreement + mid + linkPrivacy + tail
        val spanned = SpannableString(full)
        fun linkAt(target: String, url: String, start: Int) {
            spanned.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    PrivacyWebActivity.start(activity, target.trim('《', '》'), url)
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.color = Color.parseColor("#4D8AFF")
                    ds.isUnderlineText = false
                }
            }, start, start + target.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        linkAt(linkAgreement, URL_AGREEMENT, text.length)
        linkAt(linkPrivacy, URL_PRIVACY, text.length + linkAgreement.length + mid.length)
        return spanned
    }
}
