package com.zyc.love_baby

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * PrivacyWebActivity — 协议全文页(M33 上架合规)
 * 用法:PrivacyGate 弹窗链接与 MinePage「用户协议与隐私政策」入口(后者走系统浏览器,
 *      本页仅首启弹窗用——首启尚未同意,系统浏览器也算可用)
 * 依赖:android.webkit(WebView);全文为服务端 nginx 托管的静态 HTML
 * 事件:无交互逻辑;页内链接 WebView 默认行为跳转,返回键默认 finish
 * 功能:应用内读《用户协议》/《隐私政策》全文(商店审核要求协议在 App 内可查看)。
 *      仅静态 HTML,不开 JavaScript(缩攻击面);URL 恒 HTTPS(release 全局禁明文)
 */
class PrivacyWebActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = WebViewClient()
        webView.loadUrl(intent.getStringExtra(EXTRA_URL) ?: PrivacyGate.URL_PRIVACY)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {

        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, title: String, url: String) {
            val intent = Intent(context, PrivacyWebActivity::class.java)
            intent.putExtra(EXTRA_TITLE, title)
            intent.putExtra(EXTRA_URL, url)
            context.startActivity(intent)
        }
    }
}
