package com.zyc.love_baby

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.zyc.love_baby.module.KRFileModule

/**
 * 透明桥 Activity(头像上传,文件服务里程碑):KRFileModule 无法自己收 onActivityResult
 * (KuiklyRenderBaseModule 无该钩子),由本壳代为 startActivityForResult + onActivityResult 转发,
 * 全程透明(theme=Translucent)无感知。ACTION_PICK 免存储权限(系统相册自带授权);
 * 选完/取消即 finish,不进任务栈语义(单实例应用,回调经 KRFileModule 静态引用交接)
 */
class KRPickImageActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不做 resolveActivity 预检:Android 11+ package visibility 下未声明 <queries> 会误判 null,
        // 直接 startActivityForResult + 捕获 ActivityNotFoundException 更稳
        try {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_PICK)
        } catch (_: Exception) {
            // 系统无相册应用等极端情况:按取消收场,回调交还 common 侧
            KRFileModule.onPickResult(null)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK) {
            return
        }
        // RESULT_OK 且 data.data 有效才带 URI;取消/失败一律 null(统一走「未选图」回调)
        val uri = if (resultCode == RESULT_OK) data?.data else null
        KRFileModule.onPickResult(uri)
        finish()
    }

    companion object {
        private const val REQUEST_PICK = 1001
    }
}
