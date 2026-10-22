package com.zyc.love_baby

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.zyc.love_baby.module.KRFileModule
import java.io.File

/**
 * 透明桥 Activity(选图,文件服务里程碑):KRFileModule 无法自己收 onActivityResult
 * (KuiklyRenderBaseModule 无该钩子),由本壳代为 startActivityForResult + onActivityResult 转发,
 * 全程透明(theme=Translucent)无感知。ACTION_PICK 免存储权限(系统相册自带授权);
 * M24 修订 mixed 模式同屏单选图片+视频,五轮起优先系统照片选择器(MediaStore.ACTION_PICK_IMAGES
 * 谷歌式干净网格,Android 11+/GMSCore 与 13+ 原生支持)——用户真机反馈 GET_CONTENT 打开的
 * DocumentsUI 底部出现加载进度条;九轮修订回退链(用户真机:选中后底部条一闪、条完气泡才现
 * =设备未解析到照片选择器仍静默落 GET_CONTENT 文件管理器,其浏览/打开文件自带底部加载条):
 * ACTION_PICK_IMAGES → ACTION_PICK 系统相册(OEM 图库,干净;⚠仅图片) → ACTION_GET_CONTENT
 * (最后兜底,视频仍可选);六轮加 camera 模式(ACTION_IMAGE_CAPTURE + FileProvider EXTRA_OUTPUT
 * 直落 filesDir/camera);
 * 选完/取消即 finish,不进任务栈语义(单实例应用,回调经 KRFileModule 静态引用交接)。
 * M15 多选:启动 extra EXTRA_MAX_COUNT > 1 时给系统相册带 EXTRA_ALLOW_MULTIPLE(相册支持则
 * 多选,OEM 忽略 extra 只回单张——回退内建);onActivityResult 优先迭代 clipData(多选形态),
 * data.data 回退(单选形态),统一 List<Uri> 交接(null=取消/失败原契约)
 */
class KRPickImageActivity : Activity() {

    /** 拍照输出文件(M24 六轮,仅 MEDIA_CAMERA 分支写入):onActivityResult 分流依据兼回包来源 */
    private var cameraOutput: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不做 resolveActivity 预检:Android 11+ package visibility 下未声明 <queries> 会误判 null,
        // 直接 startActivityForResult + 捕获 ActivityNotFoundException 更稳
        try {
            val maxCount = intent?.getIntExtra(EXTRA_MAX_COUNT, 1) ?: 1
            // 媒体类型分流:video = ACTION_PICK 系统相册视频集合(单选,免存储权限);
            // mixed = 系统照片选择器图片+视频同屏单选(M24 修订「不要弹框,点图片 icon 直接选择」,
            // 五轮起 ACTION_PICK_IMAGES 优先——免存储权限,EXTRA_MIME_TYPES 限定图片/视频两类;
            // ACTION_PICK_IMAGES 是编译期内联 String 常量,老系统上发送仅在无处理方时抛
            // ActivityNotFoundException,统一走回退);13- /选择器未解析到(OEM 无谷歌选择器)回退
            // ACTION_GET_CONTENT 文件选择器(EXTRA_LOCAL_ONLY 只给本地文件);缺省/其它走图片
            // 集合老契约(字节不变)
            val mediaType = intent?.getStringExtra(EXTRA_MEDIA_TYPE) ?: MEDIA_IMAGE
            if (mediaType == MEDIA_CAMERA) {
                // M24 六轮拍照:ACTION_IMAGE_CAPTURE 系统相机(应用未声明 CAMERA 权限即免权限),
                // EXTRA_OUTPUT 经 FileProvider 指向 filesDir/camera 全量原图(不进系统相册、
                // 无拷贝;file:// 不外传,授权 URI 临时仅相机可写);onActivityResult OK 后按
                // file:// 交还 KRFileModule(MODE_TAKE_PHOTO 分流)
                val out = File(filesDir, "camera/img_${System.currentTimeMillis()}.jpg")
                out.parentFile?.mkdirs()
                cameraOutput = out
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(
                        MediaStore.EXTRA_OUTPUT,
                        FileProvider.getUriForFile(this@KRPickImageActivity, "${packageName}.fileprovider", out),
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                startActivityForResult(cameraIntent, REQUEST_PICK)
            } else if (mediaType == MEDIA_MIXED) {
                val photoPicker = Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                }
                try {
                    startActivityForResult(photoPicker, REQUEST_PICK)
                } catch (_: ActivityNotFoundException) {
                    // M24 九轮:照片选择器未解析到(HarmonyOS/无 GMS 国产 ROM 常态)→ 回退
                    // ACTION_PICK 系统相册(OEM 图库接管,免存储权限,UI 干净无加载条)——旧回退
                    // ACTION_GET_CONTENT 打开的是文件管理器样式选择器,浏览目录与「选中打开文件」
                    // 都自带底部加载条(用户五/八/九轮连续反馈的「进度条」即它;五轮换照片选择器
                    // 优先,在该类设备上未解析到仍静默落回文件管理器,等于没换)。⚠ 相册模式仅图片
                    // 可选(视频不可选);GET_CONTENT 降为最后兜底(连图库都没有的极端设备,视频
                    // 仍可经它选)
                    try {
                        startActivityForResult(buildGalleryPickIntent(), REQUEST_PICK)
                    } catch (_: ActivityNotFoundException) {
                        startActivityForResult(buildGetContentIntent(), REQUEST_PICK)
                    }
                }
            } else {
                val pickIntent = Intent(
                    Intent.ACTION_PICK,
                    if (mediaType == MEDIA_VIDEO) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    },
                )
                if (maxCount > 1 && mediaType == MEDIA_IMAGE) {
                    pickIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(pickIntent, REQUEST_PICK)
            }
        } catch (_: Exception) {
            // 系统无相册应用等极端情况:按取消收场,回调交还 common 侧
            KRFileModule.onPickResult(null)
            finish()
        }
    }

    /** mixed 一级回退 intent(M24 九轮):ACTION_PICK 系统相册图片集合(免存储权限,OEM 图库
     *  接管——无谷歌照片选择器模块的设备 UI 干净无加载条;仅图片可选) */
    private fun buildGalleryPickIntent(): Intent = Intent(
        Intent.ACTION_PICK,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    )

    /** mixed 最后兜底 intent:ACTION_GET_CONTENT 文件选择器(M24 五轮引入;九轮降为二级回退
     *  ——其「选中打开文件」与浏览目录均自带底部加载条,能用图库就不用它) */
    private fun buildGetContentIntent(): Intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "*/*"
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK) {
            return
        }
        // 拍照回包(EXTRA_OUTPUT 模式 data 恒 null,通用 clipData/data 收集不适用):OK 交还
        // file://(文件在否由 KRFileModule MODE_TAKE_PHOTO 分支验——缺失回错);取消 null(静默)
        val out = cameraOutput
        if (out != null) {
            cameraOutput = null
            KRFileModule.onPickResult(if (resultCode == RESULT_OK) listOf(Uri.fromFile(out)) else null)
            finish()
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

        /** 媒体类型 extra(M23):image 老契约 / video 单选视频 / mixed 图片+视频同屏单选(M24 修订) /
         *  camera 拍照(M24 六轮,FileProvider EXTRA_OUTPUT 直落 filesDir/camera) */
        const val EXTRA_MEDIA_TYPE = "mediaType"
        const val MEDIA_IMAGE = "image"
        const val MEDIA_VIDEO = "video"
        const val MEDIA_MIXED = "mixed"
        const val MEDIA_CAMERA = "camera"
    }
}
