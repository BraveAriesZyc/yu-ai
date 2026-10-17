package com.zyc.love_baby

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.zyc.love_baby.module.KRFileModule

/**
 * 透明桥 Activity(选图,文件服务里程碑):KRFileModule 无法自己收 onActivityResult
 * (KuiklyRenderBaseModule 无该钩子),由本壳代为 startActivityForResult + onActivityResult 转发,
 * 全程透明(theme=Translucent)无感知。ACTION_PICK 免存储权限(系统相册自带授权);
 * 选完/取消即 finish,不进任务栈语义(单实例应用,回调经 KRFileModule 静态引用交接)。
 * M15 多选:启动 extra EXTRA_MAX_COUNT > 1 时给系统相册带 EXTRA_ALLOW_MULTIPLE(相册支持则
 * 多选,OEM 忽略 extra 只回单张——回退内建);onActivityResult 优先迭代 clipData(多选形态),
 * data.data 回退(单选形态),统一 List<Uri> 交接(null=取消/失败原契约)
 */
class KRPickImageActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不做 resolveActivity 预检:Android 11+ package visibility 下未声明 <queries> 会误判 null,
        // 直接 startActivityForResult + 捕获 ActivityNotFoundException 更稳
        try {
            val maxCount = intent?.getIntExtra(EXTRA_MAX_COUNT, 1) ?: 1
            val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            if (maxCount > 1) {
                pickIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(pickIntent, REQUEST_PICK)
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
        // RESULT_OK 才收图:多选优先迭代 clipData(每项一个 Uri),OEM 忽略 extra 只回单张时
        // data.data 回退;取消/失败一律 null(统一走「未选图」回调)
        val uris = ArrayList<Uri>()
        if (resultCode == RESULT_OK && data != null) {
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { uris.add(it) }
                }
            } else {
                data.data?.let { uris.add(it) }
            }
        }
        KRFileModule.onPickResult(if (uris.isEmpty()) null else uris)
        finish()
    }

    companion object {
        private const val REQUEST_PICK = 1001

        /** 多选张数上限 extra(KRFileModule 发起时写入;缺省 1 = 单选老契约) */
        const val EXTRA_MAX_COUNT = "maxCount"
    }
}
