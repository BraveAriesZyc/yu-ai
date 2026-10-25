package com.zyc.love_baby.module

import android.content.Context
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.zyc.love_baby.KRApplication
import com.zyc.love_baby.KRMediaPickerActivity
import com.zyc.love_baby.KRPickImageActivity
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 选图/上传原生实现(文件服务里程碑,三方法):KRPickImageActivity 透明桥(ACTION_PICK 免存储
 * 权限)收 onActivityResult,经类级静态入口 onPickResult 转发回活跃实例,按 pendingMode 分流。
 *  - pickAndUpload(头像,M4 老契约):选图 → Glide 压缩 → okhttp multipart 直传一条龙,选图即动作
 *    本身无取消路径(无孤儿文件),保留;maxCount>1 多选串行压缩上传 fail-fast「第N张…」(M15)。
 *  - pickImages(M15 真机反馈「没上传不调接口,点击发布才是上传」裁决):仅选图零压缩零上传零
 *    网络,收口前先把 content:// 字节落盘 filesDir/pick_cache 转成 file:(content URI 临时
 *    授权随时间衰减,延迟读取部分张失败真机实证)——回本地 file: 路径字符串,展示与上传全走
 *    本地稳态副本(KRImageAdapter file 分支 / Glide 本命支持)。
 *  - uploadImage(M15):单张本地 URI → Glide 压缩 → multipart 直传,压缩移后台线程(主线程
 *    Bitmap.compress 100-300ms×N 张是「选图/发布卡顿」根因),回 data 单 URL 字符串;
 *    directory 表单字段透传服务端(AppFileUploadReqVO.directory,空不发字段=老契约字节不变)。
 * 压缩链:centerInside 等比缩到 maxEdge 内(EXIF 自动矫正),JPEG 80 出图避开 16MB 服务端限制;
 * 直传端点 /app-api/infra/file/upload(MEMBER token),解析 yudao CommonResult 收口回调给
 * common 侧(base/FileModule 声明,一次性回调 keepCallbackAlive=false)。
 * 收口 data:多选为 JSON 数组字符串,单选为单值字符串(URL 或本地 URI,按方法语义)。
 * 仅上传动作——URL 写回(user/update/create)由 common 侧收口,原生不碰业务接口。
 * 头契约与 HttpClient 一致:仅 Authorization: Bearer(token 内含租户上下文,无需 tenant-id 头)。
 */
class KRFileModule : KuiklyRenderBaseModule() {

    init {
        // 桥 Activity 只认类级静态入口,实例引用在此登记(渲染层按页创建实例,单 Activity 场景取最新)
        current = this
    }

    /** okhttp 客户端单例:上传超时收紧(头像小图,30s 足够;连接失败快速失败回 toast) */
    private var client: OkHttpClient? = null

    /** 一次性回调:pickAndUpload 时挂上,收口后置空防重放(整批共用,链上逐步续) */
    private var resultCallback: KuiklyRenderCallback? = null

    /** 流转状态(主线程读写):防上传中二次发起(common 侧 UI loading 之外的兜底);覆盖整批 */
    private var inFlight = false

    /** 当前上传进度(M24 三轮修订,配聊天媒体蒙层进度环):0-100=已写字节百分比(100=字节
     *  全量已写待服务端响应),-1=空闲/压缩/抽帧等非网络窗口。@Volatile:okhttp 写线程逐块写,
     *  uploadProgress 查询走桥线程读;inFlight 单飞保证任一时刻至多一条上传链,单值无歧义 */
    @Volatile
    private var uploadProgressPct = -1

    /** 回调统一转主线程(桥 Activity/Glide/okhttp 回调线程不一,common 侧 Compose 状态只认主线程) */
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---------- 录音域(M23,与选图 inFlight 分域:录音跨秒级长驻,互不挤占槽位) ----------

    private var recorder: MediaRecorder? = null

    /** 录音收口回调槽(发起/权限结果/手动停三路挂;先到先得,后到替换) */
    private var recordCallback: KuiklyRenderCallback? = null

    private var recordStartAt = 0L
    private var recordFile: File? = null
    private var recordMaxSeconds = 60

    /** 录音中(双通道[自动停/手动停]先到先得闸:收口后置 false,后到 no-op) */
    private var recording = false

    /** 发起时未授权挂起中(handleRecordPermission 消费;防游离权限结果误启动) */
    private var recordPendingPermission = false

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            METHOD_PICK_UPLOAD -> pickAndUpload(params, callback)
            METHOD_PICK_IMAGES -> pickImages(params, callback)
            METHOD_UPLOAD_IMAGE -> uploadImage(params, callback)
            METHOD_PICK_VIDEO -> pickVideo(params, callback)
            METHOD_PICK_MEDIA -> pickMedia(params, callback)
            METHOD_TAKE_PHOTO -> takePhoto(callback)
            METHOD_UPLOAD_MEDIA -> uploadMedia(params, callback)
            METHOD_VIDEO_COVER -> videoCover(params, callback)
            METHOD_RECORD_START -> recordAudioStart(params, callback)
            METHOD_RECORD_STOP -> recordAudioStop(params, callback)
            METHOD_UPLOAD_PROGRESS -> uploadProgressQuery(callback)
            METHOD_QUERY_MEDIA -> queryMediaLibrary(callback)
            METHOD_COPY_MEDIA -> copyMedia(params, callback)
            METHOD_CHECK_MIC -> checkMicPermission(callback)
            METHOD_REQUEST_MIC -> requestMicPermission(callback)
            METHOD_CHECK_MEDIA -> checkMediaPermission(callback)
            METHOD_REQUEST_MEDIA -> requestMediaPermission(callback)
            METHOD_OPEN_APP_DETAILS -> openAppDetails()
            else -> callback?.invoke(mapOf("code" to -1, "msg" to "方法不存在"))
        }
    }

    /** 仅选图(M15):ACTION_PICK 选完字节落盘 filesDir 转 file:// 再收口,不压缩不上传零网络;
     *  maxCount 缺省 1 单选,>1 多选。收口 data 双形态同 pickAndUpload(=1 单 file:// 字符串 /
     *  >1 JSON 数组字符串) */
    private fun pickImages(params: String?, callback: KuiklyRenderCallback?) {
        if (params.isNullOrEmpty()) {
            callback?.invoke(mapOf("code" to -1, "msg" to "参数缺失"))
            return
        }
        val maxCount = JSONObject(params).optInt("maxCount", 1).coerceAtLeast(1)
        val activity = activity
        if (activity == null || activity.isFinishing) {
            callback?.invoke(mapOf("code" to -1, "msg" to "宿主不可用"))
            return
        }
        if (inFlight) {
            callback?.invoke(mapOf("code" to -1, "msg" to "图片处理中,请稍候"))
            return
        }
        inFlight = true
        resultCallback = callback
        pendingMode = MODE_PICK
        pendingMaxCount = maxCount
        // ⚠ 必须 catch(同 pickAndUpload):桥 Activity 配置错误抛 ActivityNotFoundException 会
        // 杀进程,必须收口回调回 toast
        try {
            activity.startActivity(
                Intent(activity, KRPickImageActivity::class.java)
                    .putExtra(KRPickImageActivity.EXTRA_MAX_COUNT, maxCount),
            )
        } catch (e: Exception) {
            finishFlight(callback, CODE_ERROR, "无法打开相册,请重试")
        }
    }

    /** 单张本地图压缩+上传(M15,配 pickImages):uri 为 pickImages 回包的本地 content:// URI,
     *  Glide 原生支持直载;回 data 单 URL 字符串。多张场景由 common 侧串行逐张调用(进度可报) */
    private fun uploadImage(params: String?, callback: KuiklyRenderCallback?) {
        if (params.isNullOrEmpty()) {
            callback?.invoke(mapOf("code" to -1, "msg" to "参数缺失"))
            return
        }
        val paramJSON = JSONObject(params)
        val url = paramJSON.optString("url")
        val token = paramJSON.optString("token")
        val uri = paramJSON.optString("uri")
        if (url.isEmpty() || token.isEmpty() || uri.isEmpty()) {
            callback?.invoke(mapOf("code" to -1, "msg" to "参数缺失"))
            return
        }
        val maxEdge = paramJSON.optInt("maxEdge", DEFAULT_MAX_EDGE)
        // 存储子目录透传(空=不发字段,服务端走原逻辑;头像/相册/聊天按业务归目录)
        val directory = paramJSON.optString("directory")
        if (inFlight) {
            callback?.invoke(mapOf("code" to -1, "msg" to "图片处理中,请稍候"))
            return
        }
        val context = context
        if (context == null) {
            callback?.invoke(mapOf("code" to -1, "msg" to "上下文不可用"))
            return
        }
        inFlight = true
        resultCallback = callback
        pendingMode = MODE_UPLOAD
        pendingUploadUrl = url
        pendingToken = token
        pendingMaxEdge = maxEdge
        pendingDirectory = directory.takeIf { it.isNotEmpty() }
        compressAndUpload(context, Uri.parse(uri)) { fileUrl, msg, width, height ->
            if (fileUrl != null) {
                // M24:宽高附回包顶层键(data=URL 契约不变,common 侧不读即零感知;0,0=未知不附)
                finishFlight(resultCallback, 0, "", fileUrl, width, height)
            } else {
                finishFlight(resultCallback, CODE_ERROR, msg)
            }
        }
    }

    private fun pickAndUpload(params: String?, callback: KuiklyRenderCallback?) {
        if (params.isNullOrEmpty()) {
            callback?.invoke(mapOf("code" to -1, "msg" to "参数缺失"))
            return
        }
        val paramJSON = JSONObject(params)
        val url = paramJSON.optString("url")
        val token = paramJSON.optString("token")
        if (url.isEmpty() || token.isEmpty()) {
            callback?.invoke(mapOf("code" to -1, "msg" to "参数缺失"))
            return
        }
        // 压缩上限可传(M9 相册场景传 1080 供大图预览;缺省 512 保持头像场景兼容)
        val maxEdge = paramJSON.optInt("maxEdge", DEFAULT_MAX_EDGE)
        // 多选张数上限可传(M15 相册多图传余量;缺省 1 = 单选老契约,≤0 防御回 1)
        val maxCount = paramJSON.optInt("maxCount", 1).coerceAtLeast(1)
        // 存储子目录透传(同 uploadImage;头像场景 DIR_AVATAR)
        val directory = paramJSON.optString("directory")
        val activity = activity
        if (activity == null || activity.isFinishing) {
            callback?.invoke(mapOf("code" to -1, "msg" to "宿主不可用"))
            return
        }
        if (inFlight) {
            callback?.invoke(mapOf("code" to -1, "msg" to "上传进行中,请稍候"))
            return
        }
        inFlight = true
        resultCallback = callback
        pendingMode = MODE_PICK_UPLOAD
        pendingUploadUrl = url
        pendingToken = token
        pendingMaxEdge = maxEdge
        pendingMaxCount = maxCount
        pendingDirectory = directory.takeIf { it.isNotEmpty() }
        // 透明桥 Activity 收选图结果,结果经类级静态入口转发回本实例(生命周期解耦:
        // 桥 Activity 只认 companion 的 onPickResult,不持模块引用)。
        // ⚠ 必须 catch:桥 Activity 声明缺失等配置错误在此抛 ActivityNotFoundException,
        // Kuikly UI scheduler 线程的未捕获异常会直接杀进程(2026-10-17 真机闪退实证),
        // 桥接失败必须收口回调回 toast,不让异常穿透
        try {
            activity.startActivity(
                Intent(activity, KRPickImageActivity::class.java)
                    .putExtra(KRPickImageActivity.EXTRA_MAX_COUNT, maxCount),
            )
        } catch (e: Exception) {
            finishFlight(callback, CODE_ERROR, "无法打开相册,请重试")
        }
    }

    /** 仅选视频(M23):ACTION_PICK 系统相册 video 集合(单选,免存储权限),落盘转 file:// 并
     *  回读时长,data = {"uri","duration"} JSON 字符串;契约对齐 pickImages(CODE_CANCEL 取消静默) */
    private fun pickVideo(params: String?, callback: KuiklyRenderCallback?) {
        val activity = activity
        if (activity == null || activity.isFinishing) {
            callback?.invoke(mapOf("code" to -1, "msg" to "宿主不可用"))
            return
        }
        if (inFlight) {
            callback?.invoke(mapOf("code" to -1, "msg" to "媒体处理中,请稍候"))
            return
        }
        inFlight = true
        resultCallback = callback
        pendingMode = MODE_PICK_VIDEO
        // ⚠ 必须 catch(同 pickImages):桥 Activity 配置异常不让其穿透杀进程
        try {
            activity.startActivity(
                Intent(activity, KRPickImageActivity::class.java)
                    .putExtra(KRPickImageActivity.EXTRA_MEDIA_TYPE, KRPickImageActivity.MEDIA_VIDEO),
            )
        } catch (e: Exception) {
            finishFlight(callback, CODE_ERROR, "无法打开相册,请重试")
        }
    }

    /** 混选图片+视频(M24 修订,用户裁决「不要弹框,点图片 icon 直接选择」):M24 十轮起主路径
     *  为**应用内媒体选择器**(KRMediaPickerActivity,微信式自建相册页——微信同款思路:不碰
     *  系统选择器,自读 MediaStore 自绘网格,无文件管理器加载条、图/视频混选全设备一致;读库
     *  需运行时权限,拒绝由其内部降级 KRPickImageActivity 系统选择器三级链),落盘转 file://
     *  (稳态副本同 pickVideo 理由)并按 mime 判型,data = {"uri","kind":"image"|"video",
     *  "duration":<秒,图片恒 0>,"size":<字节>} JSON 字符串;kind 供 common 侧分派图片/视频
     *  各自既有预览+上传链;code=1 用户取消静默 */
    private fun pickMedia(params: String?, callback: KuiklyRenderCallback?) {
        val activity = activity
        if (activity == null || activity.isFinishing) {
            callback?.invoke(mapOf("code" to -1, "msg" to "宿主不可用"))
            return
        }
        if (inFlight) {
            callback?.invoke(mapOf("code" to -1, "msg" to "媒体处理中,请稍候"))
            return
        }
        inFlight = true
        resultCallback = callback
        pendingMode = MODE_PICK_MEDIA
        // ⚠ 必须 catch(同 pickVideo):桥 Activity 配置异常不让其穿透杀进程
        try {
            activity.startActivity(Intent(activity, KRMediaPickerActivity::class.java))
        } catch (e: Exception) {
            finishFlight(callback, CODE_ERROR, "无法打开相册,请重试")
        }
    }

    /** 拍照(M24 六轮,加号面板「拍照」卡):ACTION_IMAGE_CAPTURE 系统相机(应用未声明 CAMERA
     *  权限即免权限免弹窗),EXTRA_OUTPUT 经 FileProvider 指向 filesDir/camera/img_<ts>.jpg
     *  直落全量原图(不进系统相册、无拷贝无转码);onResult data = file:// 路径字符串(code=0)/
     *  code=1 用户取消(静默)/ 其余失败带 msg;common 侧收口后走 launchImageSend 同图片链 */
    private fun takePhoto(callback: KuiklyRenderCallback?) {
        val activity = activity
        if (activity == null || activity.isFinishing) {
            callback?.invoke(mapOf("code" to -1, "msg" to "宿主不可用"))
            return
        }
        if (inFlight) {
            callback?.invoke(mapOf("code" to -1, "msg" to "媒体处理中,请稍候"))
            return
        }
        inFlight = true
        resultCallback = callback
        pendingMode = MODE_TAKE_PHOTO
        // ⚠ 必须 catch(同 pickMedia):桥 Activity 配置异常不让其穿透杀进程
        try {
            activity.startActivity(
                Intent(activity, KRPickImageActivity::class.java)
                    .putExtra(KRPickImageActivity.EXTRA_MEDIA_TYPE, KRPickImageActivity.MEDIA_CAMERA),
            )
        } catch (e: Exception) {
            finishFlight(callback, CODE_ERROR, "无法打开相机,请重试")
        }
    }

    /** 媒体直传(M23,无压缩):uri 本地 file://(pickVideo/录音回包),fileName/mime 由 common 给;
     *  后台读字节 → multipart 直传(图片链 uploadBytes 泛化复用),回 data 服务端 URL 字符串 */
    private fun uploadMedia(params: String?, callback: KuiklyRenderCallback?) {
        if (params.isNullOrEmpty()) {
            callback?.invoke(mapOf("code" to -1, "msg" to "参数缺失"))
            return
        }
        val paramJSON = JSONObject(params)
        val url = paramJSON.optString("url")
        val token = paramJSON.optString("token")
        val uri = paramJSON.optString("uri")
        val fileName = paramJSON.optString("fileName")
        val mime = paramJSON.optString("mime")
        val directory = paramJSON.optString("directory")
        if (url.isEmpty() || token.isEmpty() || uri.isEmpty() || fileName.isEmpty() || mime.isEmpty()) {
            callback?.invoke(mapOf("code" to -1, "msg" to "参数缺失"))
            return
        }
        if (inFlight) {
            callback?.invoke(mapOf("code" to -1, "msg" to "上传进行中,请稍候"))
            return
        }
        val context = context
        if (context == null) {
            callback?.invoke(mapOf("code" to -1, "msg" to "上下文不可用"))
            return
        }
        inFlight = true
        resultCallback = callback
        pendingMode = MODE_UPLOAD_MEDIA
        pendingUploadUrl = url
        pendingToken = token
        pendingDirectory = directory.takeIf { it.isNotEmpty() }
        BG_EXECUTOR.execute {
            var bytes: ByteArray? = null
            try {
                val path = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
                bytes = File(path).readBytes()
            } catch (_: Exception) {
            }
            mainHandler.post {
                val cb = resultCallback
                if (!inFlight || cb == null) {
                    return@post // 已收口/模块销毁,读盘结果作废
                }
                val data = bytes
                if (data == null) {
                    finishFlight(cb, CODE_ERROR, "文件读取失败")
                } else {
                    uploadBytes(data, fileName, mime) { uploadedUrl, msg ->
                        if (uploadedUrl != null) {
                            finishFlight(resultCallback, 0, "", uploadedUrl)
                        } else {
                            finishFlight(resultCallback, CODE_ERROR, msg)
                        }
                    }
                }
            }
        }
    }

    /** 视频抽帧封面(M23):uri 本地 file://,首帧落 filesDir JPEG 回 file:// 路径;fire-and-forget
     *  不占 inFlight 槽(发送链路串行,无并发挤兑);失败回非 0,调用方降级灰底兜底 */
    private fun videoCover(params: String?, callback: KuiklyRenderCallback?) {
        if (params.isNullOrEmpty()) {
            callback?.invoke(mapOf("code" to -1, "msg" to "参数缺失"))
            return
        }
        val uri = JSONObject(params).optString("uri")
        val context = context
        if (uri.isEmpty() || context == null) {
            callback?.invoke(mapOf("code" to -1, "msg" to "上下文不可用"))
            return
        }
        BG_EXECUTOR.execute {
            var coverPath = ""
            try {
                val mmr = MediaMetadataRetriever()
                try {
                    val path = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
                    mmr.setDataSource(path)
                    val frame = mmr.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        val dir = File(context.filesDir, PICK_CACHE_DIR)
                        dir.mkdirs()
                        val file = File(dir, "cover_${System.currentTimeMillis()}.jpg")
                        file.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                        coverPath = "file://${file.absolutePath}"
                        frame.recycle()
                    }
                } finally {
                    try {
                        mmr.release()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
            mainHandler.post {
                if (coverPath.isEmpty()) {
                    callback?.invoke(mapOf("code" to -1, "msg" to "封面提取失败"))
                } else {
                    callback?.invoke(mapOf("code" to 0, "data" to coverPath, "msg" to ""))
                }
            }
        }
    }

    // ---------- M24 十三轮 相册弹层(多选):媒体库查询 + 已选落盘(common 侧 Kuikly
    //  ModalBottomSheet + LazyVerticalGrid 网格,缩略图零原生工作——KRImageAdapter content 分支
    //  Glide 直载降采样;单选系统选择器链(pickMedia/KRMediaPickerActivity)退役为降级层) ----------

    /** 媒体权限查询挂起槽(静态桥转发收口,同录音 recordPendingPermission 套路) */
    @Volatile
    private var mediaQueryCallback: KuiklyRenderCallback? = null

    @Volatile
    private var mediaQueryPending = false

    /** 读媒体库所需权限(33+ 细分媒体两权限 / 33- 传统存储;申请时附 VISUAL_USER_SELECTED
     *  兼容 Android14「仅选中照片」部分授权,any-granted 即放行) */
    private fun mediaReadPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** 媒体库查询(M24 十三轮):未授权先挂系统申请(静态桥收口,同录音套路),授权(含部分授权)
     *  后后台线程查图+视频两集合 DATE_ADDED DESC 归并取最新 QUERY_MAX_ITEMS 条;onResult data =
     *  JSON 数组字符串 [{"uri":"<content://>","kind":"image"|"video","duration":<秒,图片恒0>,
     *  "size":<字节>}](size 供 common 侧选中时视频 16MB 守卫;common 无文件 stat 能力,原生单源)。
     *  纯查询不占 inFlight 选图飞行,可反复调(弹层每次打开刷新) */
    private fun queryMediaLibrary(callback: KuiklyRenderCallback?) {
        if (callback == null) return
        val context = context
        if (context == null) {
            callback.invoke(mapOf("code" to -1, "msg" to "上下文不可用"))
            return
        }
        val missing = mediaReadPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            val activity = activity
            if (activity == null || activity.isFinishing) {
                callback.invoke(mapOf("code" to -1, "msg" to "宿主不可用"))
                return
            }
            if (mediaQueryPending) {
                callback.invoke(mapOf("code" to -1, "msg" to "处理中,请稍候"))
                return
            }
            mediaQueryCallback = callback
            mediaQueryPending = true
            val request = if (Build.VERSION.SDK_INT >= 33) {
                missing + android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            } else {
                missing
            }
            try {
                activity.requestPermissions(request.toTypedArray(), REQUEST_CODE_MEDIA)
            } catch (_: Exception) {
                mediaQueryCallback = null
                mediaQueryPending = false
                callback.invoke(mapOf("code" to -1, "msg" to "无法申请相册权限"))
            }
            return
        }
        doQueryMediaLibrary(context, callback)
    }

    /** 媒体权限结果转发(any-granted 含部分授权 → 查询;全拒 → 回错文案,common 落弹层空态) */
    private fun handleMediaPermissionResult(grantResults: IntArray) {
        val callback = mediaQueryCallback
        val pending = mediaQueryPending
        mediaQueryCallback = null
        mediaQueryPending = false
        if (!pending || callback == null) {
            return
        }
        val context = context
        val granted = grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        if (!granted || context == null) {
            callback.invoke(mapOf("code" to -1, "msg" to "未授权访问相册,请在系统设置中开启后重试"))
            return
        }
        doQueryMediaLibrary(context, callback)
    }

    // ---------- M29 权限管理页:麦克风/相册读权限的状态与申请(权限页四行状态胶囊数据源;
    //  纯状态查询不弹框,申请独立方法;回调一律 mainHandler 投递——KRNotificationModule 类头
    //  铁律,call() 内同步回包真机实证不送达) ----------

    /** 麦克风权限申请挂起槽(静态桥转发收口,同 recordPendingPermission 套路) */
    @Volatile
    private var micRequestCallback: KuiklyRenderCallback? = null

    @Volatile
    private var micRequestPending = false

    /** 相册读权限申请挂起槽(M29 权限页独立通道;与 2003 相册弹层查询通道分槽互不误收) */
    @Volatile
    private var mediaRequestCallback: KuiklyRenderCallback? = null

    @Volatile
    private var mediaRequestPending = false

    /** 查询麦克风权限(M29):纯查询不弹框,granted=RECORD_AUDIO 已授权 */
    private fun checkMicPermission(callback: KuiklyRenderCallback?) {
        if (callback == null) return
        val context = context
        val granted = context != null && isPermissionGranted(context, android.Manifest.permission.RECORD_AUDIO)
        mainHandler.post {
            callback.invoke(mapOf("code" to CODE_OK, "data" to "{\"granted\":$granted}", "msg" to ""))
        }
    }

    /** 发起麦克风运行时授权(M29):已授权短路(asked=false 零动作);否则挂系统申请,结果
     *  静态桥收口;denied 引导(跳应用详情页)由 common 侧收口 */
    private fun requestMicPermission(callback: KuiklyRenderCallback?) {
        if (callback == null) return
        val context = context
        if (context == null) {
            callback.invoke(mapOf("code" to CODE_ERROR, "msg" to "上下文不可用"))
            return
        }
        if (isPermissionGranted(context, android.Manifest.permission.RECORD_AUDIO)) {
            mainHandler.post {
                callback.invoke(mapOf("code" to CODE_OK, "data" to "{\"granted\":true,\"asked\":false}", "msg" to ""))
            }
            return
        }
        val activity = activity
        if (activity == null || activity.isFinishing) {
            callback.invoke(mapOf("code" to CODE_ERROR, "msg" to "宿主不可用"))
            return
        }
        micRequestCallback = callback
        micRequestPending = true
        try {
            activity.requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_CODE_MIC)
        } catch (_: Exception) {
            micRequestCallback = null
            micRequestPending = false
            callback.invoke(mapOf("code" to CODE_ERROR, "msg" to "无法申请麦克风权限"))
        }
    }

    /** 麦克风权限结果转发(KuiklyRenderActivity.onRequestPermissionsResult 静态桥消费;
     *  仅 pending 槽,游离结果静默) */
    private fun handleMicPermission(grantResults: IntArray) {
        val callback = micRequestCallback
        val pending = micRequestPending
        micRequestCallback = null
        micRequestPending = false
        if (!pending || callback == null) {
            return
        }
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        callback.invoke(mapOf("code" to CODE_OK, "data" to "{\"granted\":$granted,\"asked\":true}", "msg" to ""))
    }

    /** 查询相册读权限(M29):纯查询不弹框。granted=完整读权限;partial=Android14「仅选中
     *  照片」部分授权(granted=false 才有意义) */
    private fun checkMediaPermission(callback: KuiklyRenderCallback?) {
        if (callback == null) return
        val context = context
        val state = context?.let { mediaPermState(it) } ?: MediaPermState(granted = false, partial = false)
        mainHandler.post {
            callback.invoke(
                mapOf("code" to CODE_OK, "data" to "{\"granted\":${state.granted},\"partial\":${state.partial}}", "msg" to ""),
            )
        }
    }

    /** 发起相册读授权(M29):完整授权已持有则短路;否则挂系统申请(33+ 附 VISUAL_USER_SELECTED
     *  保 Android14「仅选中照片」路径;部分授权态不短路——再申请系统给「选择更多照片」升级
     *  弹层,是部分授权态的自然升级路径),结果静态桥收口 */
    private fun requestMediaPermission(callback: KuiklyRenderCallback?) {
        if (callback == null) return
        val context = context
        if (context == null) {
            callback.invoke(mapOf("code" to CODE_ERROR, "msg" to "上下文不可用"))
            return
        }
        if (mediaPermState(context).granted) {
            mainHandler.post {
                callback.invoke(
                    mapOf("code" to CODE_OK, "data" to "{\"granted\":true,\"partial\":false,\"asked\":false}", "msg" to ""),
                )
            }
            return
        }
        val activity = activity
        if (activity == null || activity.isFinishing) {
            callback.invoke(mapOf("code" to CODE_ERROR, "msg" to "宿主不可用"))
            return
        }
        if (mediaRequestPending) {
            callback.invoke(mapOf("code" to CODE_ERROR, "msg" to "处理中,请稍候"))
            return
        }
        mediaRequestCallback = callback
        mediaRequestPending = true
        try {
            val request = if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO,
                    android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                )
            } else {
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            activity.requestPermissions(request, REQUEST_CODE_MEDIA_REQUEST)
        } catch (_: Exception) {
            mediaRequestCallback = null
            mediaRequestPending = false
            callback.invoke(mapOf("code" to CODE_ERROR, "msg" to "无法申请相册权限"))
        }
    }

    /** 相册读权限结果转发(申请序 33+=[IMAGES,VIDEO,VISUAL_USER_SELECTED] / 33-=[存储];
     *  仅 pending 槽,游离结果静默) */
    private fun handleMediaRequestPermission(grantResults: IntArray) {
        val callback = mediaRequestCallback
        val pending = mediaRequestPending
        mediaRequestCallback = null
        mediaRequestPending = false
        if (!pending || callback == null) {
            return
        }
        val state = grantResultsToMediaState(grantResults)
        callback.invoke(
            mapOf(
                "code" to CODE_OK,
                "data" to "{\"granted\":${state.granted},\"partial\":${state.partial},\"asked\":true}",
                "msg" to "",
            ),
        )
    }

    /** 跳应用详情页(M29):麦克风/相册权限的「权限获取页」落点(应用详情 → 权限,可查看/
     *  改授权);Application context 起页必须 FLAG_ACTIVITY_NEW_TASK(KRNotificationModule 同款) */
    private fun openAppDetails() {
        val context = context ?: KRApplication.application
        try {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
            // 极端 ROM 深链无匹配 Activity,静默(官方文档要求 try/catch)
        }
    }

    /** 相册读权限状态快照(查询/申请短路共用口径):33+ IMAGES+VIDEO 双授权=完整;仅
     *  VISUAL_USER_SELECTED=Android14 部分授权(API 34 起该权限才有语义,33 恒查不到);33-
     *  传统存储=完整 */
    private fun mediaPermState(context: Context): MediaPermState {
        return if (Build.VERSION.SDK_INT >= 33) {
            val images = isPermissionGranted(context, android.Manifest.permission.READ_MEDIA_IMAGES)
            val video = isPermissionGranted(context, android.Manifest.permission.READ_MEDIA_VIDEO)
            if (images && video) {
                MediaPermState(granted = true, partial = false)
            } else {
                MediaPermState(
                    granted = false,
                    partial = Build.VERSION.SDK_INT >= 34 &&
                        isPermissionGranted(context, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
                )
            }
        } else {
            MediaPermState(
                granted = isPermissionGranted(context, android.Manifest.permission.READ_EXTERNAL_STORAGE),
                partial = false,
            )
        }
    }

    // isPermissionGranted / mediaPermState / grantResultsToMediaState 三助手已上收 companion
    // (M29 宿主直注 PermissionSnapshot 共用单源;companion 私有成员实例方法内直接解析)

    /** 两集合归并查询(模式固定全 API 级安全;BG_EXECUTOR 单线程串行;读失败落空数组,弹层空态) */
    private fun doQueryMediaLibrary(context: Context, callback: KuiklyRenderCallback) {
        BG_EXECUTOR.execute {
            val merged = ArrayList<MediaEntry>()
            try {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DATE_ADDED,
                        MediaStore.MediaColumns.SIZE,
                    ),
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    while (c.moveToNext()) {
                        merged.add(
                            MediaEntry(
                                uri = ContentUris.withAppendedId(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    c.getLong(idCol),
                                ).toString(),
                                isVideo = false,
                                durationSec = 0,
                                sizeBytes = if (sizeCol >= 0) c.getLong(sizeCol) else 0L,
                                dateAdded = c.getLong(dateCol),
                            ),
                        )
                    }
                }
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DATE_ADDED,
                        MediaStore.MediaColumns.SIZE,
                        MediaStore.Video.Media.DURATION,
                    ),
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    while (c.moveToNext()) {
                        merged.add(
                            MediaEntry(
                                uri = ContentUris.withAppendedId(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    c.getLong(idCol),
                                ).toString(),
                                isVideo = true,
                                durationSec = if (durCol >= 0) (c.getLong(durCol) / 1000).toInt() else 0,
                                sizeBytes = if (sizeCol >= 0) c.getLong(sizeCol) else 0L,
                                dateAdded = c.getLong(dateCol),
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
            }
            merged.sortByDescending { it.dateAdded }
            val data = merged.take(QUERY_MAX_ITEMS)
                .joinToString(",") { e ->
                    JSONObject()
                        .put("uri", e.uri)
                        .put("kind", if (e.isVideo) "video" else "image")
                        .put("duration", e.durationSec)
                        .put("size", e.sizeBytes)
                        .toString()
                }
            mainHandler.post { callback.invoke(mapOf("code" to 0, "data" to "[$data]", "msg" to "")) }
        }
    }

    /** 媒体库单条目(查询归并中间态) */
    private data class MediaEntry(
        val uri: String,
        val isVideo: Boolean,
        val durationSec: Int,
        val sizeBytes: Long,
        val dateAdded: Long,
    )

    /** 已选媒体落盘(M24 十三轮):common 相册弹层多选收口——uris(JSON 数组字符串,content://)
     *  逐个字节落盘 filesDir/pick_cache 转 file://(稳态副本同 pickMedia 理由)+ mime 判型 +
     *  视频时长回读 + 字节数,onResult data = JSON 数组字符串(同 queryMediaLibrary 形态,
     *  uri 为 file://);个别失败跳过该张,全失败回错。独立于 inFlight 选图飞行(不走系统选择器
     *  链收口),BG_EXECUTOR 单线程串行天然防并发 */
    private fun copyMedia(params: String?, callback: KuiklyRenderCallback?) {
        if (callback == null) return
        if (params.isNullOrEmpty()) {
            callback.invoke(mapOf("code" to -1, "msg" to "参数缺失"))
            return
        }
        val urisStr = JSONObject(params).optString("uris").orEmpty()
        if (urisStr.isEmpty()) {
            callback.invoke(mapOf("code" to -1, "msg" to "参数缺失"))
            return
        }
        val context = context
        if (context == null) {
            callback.invoke(mapOf("code" to -1, "msg" to "上下文不可用"))
            return
        }
        val uris = try {
            JSONArray(urisStr)
        } catch (_: Exception) {
            null
        }
        if (uris == null || uris.length() == 0) {
            callback.invoke(mapOf("code" to -1, "msg" to "参数缺失"))
            return
        }
        BG_EXECUTOR.execute {
            val dir = File(context.filesDir, PICK_CACHE_DIR)
            dir.deleteRecursively() // 每批先清旧缓存(防积累,卸载系统兜底)
            dir.mkdirs()
            val results = ArrayList<String>()
            for (i in 0 until uris.length()) {
                val uri = try {
                    Uri.parse(uris.optString(i).orEmpty())
                } catch (_: Exception) {
                    null
                } ?: continue
                try {
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    val isVideo = mime.startsWith("video/")
                    val file = File(dir, "pick_${System.currentTimeMillis()}_$i${if (isVideo) ".mp4" else ".jpg"}")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (file.length() > 0) {
                        results.add(
                            JSONObject()
                                .put("uri", "file://${file.absolutePath}")
                                .put("kind", if (isVideo) "video" else "image")
                                .put("duration", if (isVideo) readVideoDuration(file.absolutePath) else 0)
                                .put("size", file.length())
                                .toString(),
                        )
                    }
                } catch (_: Exception) {
                }
            }
            mainHandler.post {
                if (results.isEmpty()) {
                    callback.invoke(mapOf("code" to -1, "msg" to "媒体读取失败,请重试"))
                } else {
                    callback.invoke(mapOf("code" to 0, "data" to "[${results.joinToString(",")}]", "msg" to ""))
                }
            }
        }
    }

    /** 录音开始(M23):RECORD_AUDIO 已授权直接起录;未授权登记回调挂系统申请(结果静态桥转发
     *  handleRecordPermission)。开始成功不回包;到 maxSeconds 自动停/异常时回 data={"uri","duration"} */
    private fun recordAudioStart(params: String?, callback: KuiklyRenderCallback?) {
        if (callback == null) return // startRecording 收口依赖非空回调,无回调调用无意义
        if (params.isNullOrEmpty()) {
            callback?.invoke(mapOf("code" to -1, "msg" to "参数缺失"))
            return
        }
        val maxSeconds = JSONObject(params).optInt("maxSeconds", 60).coerceIn(1, 600)
        if (recording || recordPendingPermission) {
            callback?.invoke(mapOf("code" to -1, "msg" to "已在录音中"))
            return
        }
        val context = context
        if (context == null) {
            callback?.invoke(mapOf("code" to -1, "msg" to "上下文不可用"))
            return
        }
        val activity = activity
        if (activity == null || activity.isFinishing) {
            callback?.invoke(mapOf("code" to -1, "msg" to "宿主不可用"))
            return
        }
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            recordCallback = callback
            recordMaxSeconds = maxSeconds
            recordPendingPermission = true
            try {
                activity.requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD)
            } catch (_: Exception) {
                recordCallback = null
                recordPendingPermission = false
                callback?.invoke(mapOf("code" to -1, "msg" to "无法申请麦克风权限"))
            }
            return
        }
        startRecording(context, maxSeconds, callback)
    }

    /** 权限结果转发(KuiklyRenderActivity.onRequestPermissionsResult 静态桥调,requestCode 错开
     *  KRNotificationModule 2001):授权→启动录音;拒绝→回错。仅消费 pending 槽,游离结果静默 */
    private fun handleRecordPermission(grantResults: IntArray) {
        val callback = recordCallback
        val pending = recordPendingPermission
        recordCallback = null
        recordPendingPermission = false
        if (!pending || callback == null) {
            return
        }
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        val context = context
        if (!granted || context == null) {
            callback.invoke(mapOf("code" to -1, "msg" to "需要麦克风权限才能发语音"))
            return
        }
        startRecording(context, recordMaxSeconds, callback)
    }

    /** 起录(MPEG_4/AAC 落 filesDir/chat_voice):到时自动停由 setMaxDuration + OnInfoListener 收口
     *  (原生+端上双保险);起录失败清理后回错 */
    private fun startRecording(context: Context, maxSeconds: Int, callback: KuiklyRenderCallback) {
        val dir = File(context.filesDir, RECORD_CACHE_DIR)
        dir.mkdirs()
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder = r
        recordFile = file
        recordStartAt = System.currentTimeMillis()
        recordCallback = callback
        recording = true
        recordMaxSeconds = maxSeconds
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.setMaxDuration(maxSeconds * 1000)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    finalizeRecording(cancel = false) // 到时自动停:正常收口
                }
            }
            r.prepare()
            r.start()
        } catch (_: Exception) {
            try {
                r.release()
            } catch (_: Exception) {
            }
            recorder = null
            recordFile = null
            recordCallback = null
            recording = false
            callback.invoke(mapOf("code" to -1, "msg" to "录音启动失败,请重试"))
        }
    }

    /** 录音停止/取消(M23):双通道(自动停/手动停)先到先得——收口后 recording=false,后到 no-op;
     *  cancel=true 丢文件回 CODE_CANCEL,定稿回 data={"uri","duration"}(时长按实际秒,钳 ≥1)。
     *  ⚠ 收口回包统一走「发起时挂的 recordCallback」单一通道(本调用回调仅立即 ack)——若本调用
     *  偷换回调,common 侧挂在发起回调上的挂起协程永不被恢复(悬挂泄漏),端上以 ack 即返回设计规避 */
    private fun recordAudioStop(params: String?, callback: KuiklyRenderCallback?) {
        val cancel = if (params.isNullOrEmpty()) false else JSONObject(params).optBoolean("cancel", false)
        if (!recording) {
            callback?.invoke(mapOf("code" to -1, "msg" to "未在录音中"))
            return
        }
        callback?.invoke(mapOf("code" to 0, "msg" to "")) // 本调用立即 ack(结果由发起回调统一收口)
        finalizeRecording(cancel)
    }

    /** 录音统一收口:stop(未录到帧会抛,按失败处理)→ release → 回包;槽位全清 */
    private fun finalizeRecording(cancel: Boolean) {
        val callback = recordCallback
        val file = recordFile
        val startedAt = recordStartAt
        recordCallback = null
        recordPendingPermission = false
        recording = false
        val r = recorder
        recorder = null
        recordFile = null
        if (r != null) {
            try {
                r.stop()
            } catch (_: Exception) {
            }
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
        if (callback == null) {
            return
        }
        if (cancel) {
            file?.delete()
            callback.invoke(mapOf("code" to CODE_CANCEL, "msg" to "已取消"))
            return
        }
        var uri = ""
        var duration = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
        if (file != null && file.length() > 0) {
            uri = "file://${file.absolutePath}"
            if (duration < 1) duration = 1
        }
        if (uri.isEmpty()) {
            file?.delete()
            callback.invoke(mapOf("code" to -1, "msg" to "录音失败,请重试"))
        } else {
            val data = JSONObject().put("uri", uri).put("duration", duration).toString()
            callback.invoke(mapOf("code" to 0, "data" to data, "msg" to ""))
        }
    }

    /** 视频时长回读(秒;BG 线程调用):Metadata 缺失/解析失败回 0,调用方钳最小 1 */
    private fun readVideoDuration(path: String): Int = try {
        val mmr = MediaMetadataRetriever()
        try {
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            (ms / 1000L).toInt()
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {
            }
        }
    } catch (_: Exception) {
        0
    }

    /** 选图结果处理(uris=null/空=取消/读图失败):须主线程(桥 Activity/Glide 回调均已在主线程);
     *  按 pendingMode 分流——PICK 落盘转 file:// 后收口(零网络),PICK_UPLOAD 走压缩上传链 */
    private fun handlePickResult(uris: List<Uri>?) {
        val callback = resultCallback
        if (!inFlight || callback == null) {
            return // 已收口(如模块销毁清理),桥回包作废
        }
        if (uris.isNullOrEmpty()) {
            finishFlight(callback, CODE_CANCEL, "未选择图片")
            return
        }
        val context = context
        if (context == null) {
            finishFlight(callback, CODE_ERROR, "上下文不可用")
            return
        }
        if (pendingMode == MODE_TAKE_PHOTO) {
            // M24 六轮拍照:桥 ACTION_IMAGE_CAPTURE 已持 FileProvider EXTRA_OUTPUT 直落
            // filesDir/camera/img_<ts>.jpg(全量原图,免存储权限、不进系统相册),此处仅按
            // file:// 回包验文件在且非空再收口——无拷贝无转码;相机进程异常/文件缺失回错(可重试)
            val file = File(uris[0].path.orEmpty())
            if (!file.isFile || file.length() == 0L) {
                finishFlight(callback, CODE_ERROR, "照片获取失败,请重试")
            } else {
                finishFlight(callback, 0, "", "file://${file.absolutePath}")
            }
            return
        }
        if (pendingMode == MODE_PICK_MEDIA) {
            // M24 修订混选(图片+视频):字节落盘 filesDir 转 file://(稳态副本同 pickVideo 理由),
            // 按 mime 判型(video/* → 视频回读时长;getType null 等异常兜底按图片处理)。GET_CONTENT
            // 单选只回 1 个 URI,防御取首个;kind/duration/size 一并回包(common 无文件 stat 能力,
            // 原生单源读出,size 供 common 侧视频 16MB 守卫)
            val uri = uris[0]
            BG_EXECUTOR.execute {
                val dir = File(context.filesDir, PICK_CACHE_DIR)
                dir.deleteRecursively() // 每次选媒体先清旧缓存(防积累,卸载系统兜底)
                dir.mkdirs()
                var outUri = ""
                var kind = "image"
                var durationSec = 0
                var sizeBytes = 0L
                try {
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    val isVideo = mime.startsWith("video/")
                    val file = File(dir, "pick_${System.currentTimeMillis()}${if (isVideo) ".mp4" else ".jpg"}")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (file.length() > 0) {
                        outUri = "file://${file.absolutePath}"
                        if (isVideo) {
                            kind = "video"
                            durationSec = readVideoDuration(file.absolutePath)
                        }
                        sizeBytes = file.length()
                    }
                } catch (_: Exception) {
                }
                mainHandler.post {
                    val cb = resultCallback
                    if (!inFlight || cb == null) {
                        return@post // 已收口/模块销毁,拷贝结果作废
                    }
                    if (outUri.isEmpty()) {
                        finishFlight(cb, CODE_ERROR, "媒体读取失败,请重试")
                    } else {
                        val data = JSONObject()
                            .put("uri", outUri)
                            .put("kind", kind)
                            .put("duration", durationSec)
                            .put("size", sizeBytes)
                            .toString()
                        finishFlight(cb, 0, "", data)
                    }
                }
            }
            return
        }
        if (pendingMode == MODE_PICK_VIDEO) {
            // M23 仅选视频:视频字节落盘 filesDir 转 file://(content URI 临时授权衰减,同 pickImages
            // 稳态副本理由)+ MediaMetadataRetriever 回读时长;data = {"uri","duration","size"} JSON
            // 字符串(size=落盘字节数,common 侧 16MB 守卫依赖它——common 无文件 stat 能力,原生单源读出)
            val uri = uris[0] // 视频单选(桥对 video 类型不开多选)
            BG_EXECUTOR.execute {
                val dir = File(context.filesDir, PICK_CACHE_DIR)
                dir.deleteRecursively() // 每次选媒体先清旧缓存(防积累,卸载系统兜底)
                dir.mkdirs()
                var outUri = ""
                var durationSec = 0
                var sizeBytes = 0L
                try {
                    val file = File(dir, "pick_${System.currentTimeMillis()}.mp4")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (file.length() > 0) {
                        outUri = "file://${file.absolutePath}"
                        durationSec = readVideoDuration(file.absolutePath)
                        sizeBytes = file.length()
                    }
                } catch (_: Exception) {
                }
                mainHandler.post {
                    val cb = resultCallback
                    if (!inFlight || cb == null) {
                        return@post // 已收口/模块销毁,拷贝结果作废
                    }
                    if (outUri.isEmpty()) {
                        finishFlight(cb, CODE_ERROR, "视频读取失败,请重试")
                    } else {
                        val data = JSONObject()
                            .put("uri", outUri)
                            .put("duration", durationSec)
                            .put("size", sizeBytes)
                            .toString()
                        finishFlight(cb, 0, "", data)
                    }
                }
            }
            return
        }
        if (pendingMode == MODE_PICK) {
            // 仅选图:立即把 content:// 字节拷贝到 filesDir 转 file:// 再收口。真机实证(2026-10-17):
            // ACTION_PICK 的 content URI 临时授权/可达性随时间衰减,延迟到发布时点再读(Glide 载
            // content)部分张 onLoadFailed「图片读取失败」;选图当下权限新鲜文件必达,字节流拷贝
            // (不解码,几十 ms/张)后台串行,收口 file:// 后展示(KRImageAdapter file 分支)与
            // 上传(Glide 压缩)全走本地稳态路径。单张拷贝失败剔除尽量交付其余,全失败回错
            BG_EXECUTOR.execute {
                val dir = File(context.filesDir, PICK_CACHE_DIR)
                dir.deleteRecursively() // 每次选图先清旧缓存(防积累,卸载系统兜底)
                dir.mkdirs()
                val copied = ArrayList<String>()
                uris.forEachIndexed { index, uri ->
                    try {
                        val file = File(dir, "pick_${System.currentTimeMillis()}_$index.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (file.length() > 0) {
                            copied.add("file://${file.absolutePath}")
                        }
                    } catch (_: Exception) {
                        // 单张失败剔除,不中断其余
                    }
                }
                mainHandler.post {
                    val cb = resultCallback
                    if (!inFlight || cb == null) {
                        return@post // 已收口/模块销毁,拷贝结果作废
                    }
                    if (copied.isEmpty()) {
                        finishFlight(cb, CODE_ERROR, "图片读取失败,请重试")
                    } else {
                        // 双形态同 pickAndUpload 契约(maxCount=1 单值字符串 / >1 JSON 数组字符串)
                        val data = if (pendingMaxCount > 1) {
                            val arr = JSONArray()
                            copied.forEach { arr.put(it) }
                            arr.toString()
                        } else {
                            copied[0]
                        }
                        finishFlight(cb, 0, "", data)
                    }
                }
            }
            return
        }
        processNext(context, uris, 0, ArrayList())
    }

    /** 串行压缩上传链(M15 多选,pickAndUpload 专用):第 index 张压缩上传,成功进下一张;
     *  index 到头整批收口(data 多选 = JSON 数组字符串 / 单选 = 单 URL 字符串老契约) */
    private fun processNext(context: Context, uris: List<Uri>, index: Int, collected: MutableList<String>) {
        val callback = resultCallback ?: return // 已收口(如模块销毁清理),链终止
        if (index >= uris.size) {
            val data = if (pendingMaxCount > 1) {
                val arr = JSONArray()
                collected.forEach { arr.put(it) }
                arr.toString()
            } else {
                collected[0]
            }
            finishFlight(callback, 0, "", data)
            return
        }
        // 压缩+上传单张;链路仍持 callback 引用,失败 msg 按「单张透传/多选第N张」分流(老契约);
        // 多选链(M15 pickAndUpload)不消费宽高——data 仍为 URL 列表老契约(M24 宽高仅 uploadImage 附)
        compressAndUpload(context, uris[index]) { url, msg, _, _ ->
            if (url != null) {
                collected.add(url)
                processNext(context, uris, index + 1, collected)
            } else if (uris.size == 1) {
                // 单张:服务端 msg 透传(老契约字节不变)
                finishFlight(resultCallback, CODE_ERROR, msg)
            } else {
                // 多选 fail-fast:第 N 张失败整批收口
                finishFlight(resultCallback, CODE_ERROR, "第${index + 1}张图片上传失败")
            }
        }
    }

    /** 单张压缩+上传(公共底座):Glide centerInside 压缩(自动 EXIF 矫正)→ JPEG 出图 → upload。
     *  ⚠ 压缩在后台单线程执行器:主线程 Bitmap.compress 100-300ms/张,连选多张反复阻塞主线程
     *  是「选图卡/发布卡」的真机根因(M15 真机反馈);压完回主线程进 upload
     *  (onDone 收口权在调用方——processNext 续链 / uploadImage 直接收口)。
     *  M24:onDone 附带压缩后 Bitmap 宽高(EXIF 已矫正的最终落库比例;失败 0,0)——
     *  在此取值而非选图落盘时 BitmapFactory 读原始文件,原因 = 原始字节带 EXIF 旋转,
     *  宽高未矫正会导致横竖图比例颠倒 */
    private fun compressAndUpload(context: Context, uri: Uri, onDone: (url: String?, msg: String, width: Int, height: Int) -> Unit) {
        Glide.with(context)
            .asBitmap()
            .load(uri)
            .centerInside()
            .override(pendingMaxEdge)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val imgWidth = resource.width
                    val imgHeight = resource.height
                    BG_EXECUTOR.execute {
                        val bytes = ByteArrayOutputStream().use { out ->
                            resource.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                            out.toByteArray()
                        }
                        mainHandler.post {
                            uploadBytes(bytes, "img_${System.currentTimeMillis()}.jpg", "image/jpeg") { url, msg ->
                                onDone(url, msg, imgWidth, imgHeight)
                            }
                        }
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    mainHandler.post { onDone(null, "图片读取失败", 0, 0) }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // 资源回收钩子,上传已改持 byte[],无需处理
                }
            })
    }

    /** multipart 直传(M23 泛化:文件名/mime 参数化,图片/语音/视频共用):okhttp 异步回调线程 →
     *  mainHandler 转主线程解析,结果经 onDone 上交(url=null=失败携 msg;收口权在调用方——
     *  单张直接收口/多选链需续链分流,M15;媒体直传 uploadMedia 单发收口)。
     *  M24 三轮修订:文件体包 CountingBody(ForwardingSink 逐块累计已写字节 → uploadProgressPct,
     *  端上 uploadProgress 轮询读)——只写内存不回调,uploadImage/uploadMedia 单一收口契约零改动 */
    private fun uploadBytes(bytes: ByteArray, fileName: String, mime: String, onDone: (url: String?, msg: String) -> Unit) {
        val token = pendingToken
        val uploadUrl = pendingUploadUrl
        if (token.isNullOrEmpty() || uploadUrl.isNullOrEmpty()) {
            onDone(null, "上传参数丢失")
            return
        }
        uploadProgressPct = 0 // 新上传起表(上一链残留清零;inFlight 单飞保证单链无歧义)
        val httpClient = client ?: OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
            .also { client = it }
        // M24 三轮修订 进度计量体:分块手写字节流(ByteArray 原生 writeTo 一次性倾倒,包
        // ForwardingSink 只见单块无进度意义),逐 64KB 块累计 → uploadProgressPct(okhttp 线程
        // 写,@Volatile 供桥查询线程读;只写内存不回调,uploadImage/uploadMedia 单一收口契约
        // 零改动)
        val countingBody = object : RequestBody() {
            override fun contentType() = mime.toMediaTypeOrNull()
            override fun contentLength() = bytes.size.toLong()
            override fun writeTo(sink: okio.BufferedSink) {
                val total = bytes.size.toLong().coerceAtLeast(1L)
                val chunk = 64 * 1024
                var written = 0L
                var offset = 0
                while (offset < bytes.size) {
                    val len = minOf(chunk, bytes.size - offset)
                    sink.write(bytes, offset, len)
                    offset += len
                    written += len
                    uploadProgressPct = ((written * 100) / total).toInt().coerceIn(0, 99)
                }
                uploadProgressPct = 100 // 字节全量已交(响应未回;端上环画满,POST 前停表)
            }
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", fileName,
                countingBody,
            )
            .also { builder ->
                // 存储子目录透传(AppFileUploadReqVO.directory 服务端校验后直通存储器;
                // 空=null 不发字段,老契约字节不变)
                pendingDirectory?.takeIf { it.isNotEmpty() }?.let { builder.addFormDataPart("directory", it) }
            }
            .build()
        val request = Request.Builder()
            .url(uploadUrl)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val text = response.body?.string() ?: ""
                response.close()
                mainHandler.post { resolveResponse(text, onDone) }
            }

            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                mainHandler.post { onDone(null, "网络异常,请重试") }
            }
        })
    }

    /** 解析 yudao CommonResult<String>:code=0 data 即文件 URL(经 onDone 上交,收口权在调用方);
     *  其余透传服务端 msg */
    private fun resolveResponse(text: String, onDone: (url: String?, msg: String) -> Unit) {
        try {
            val json = JSONObject(text)
            val code = json.optInt("code", -1)
            if (code == 0) {
                val fileUrl = json.optString("data")
                if (fileUrl.isNotEmpty()) {
                    onDone(fileUrl, "")
                } else {
                    onDone(null, "服务端未返回地址")
                }
            } else {
                onDone(null, json.optString("msg").ifEmpty { "上传失败" })
            }
        } catch (_: Exception) {
            onDone(null, "响应解析失败")
        }
    }

    /** 当前上传进度查询(M24 三轮修订,配聊天媒体蒙层进度环):纯查询不占 inFlight、不动流转
     *  状态,uploadImage/uploadMedia 单一收口契约零改动。回 data = JSON 字符串
     *  {"progress":N}(0-100;-1=空闲或压缩/抽帧等非网络窗口),端上 200ms 轮询消费 */
    private fun uploadProgressQuery(callback: KuiklyRenderCallback?) {
        callback?.invoke(mapOf("code" to 0, "data" to "{\"progress\":$uploadProgressPct}"))
    }

    /** 统一收口:清流转状态+一次性回调;成功时额外带 data(单值或 JSON 数组字符串);
     *  M24:width/height > 0 时额外附顶层键(M24 仅 uploadImage 路径传值,其余调用点默认 0 不附——
     *  老回包字节不变,common 侧不读新键即零感知) */
    private fun finishFlight(callback: KuiklyRenderCallback?, code: Int, msg: String, data: String? = null, width: Int = 0, height: Int = 0) {
        inFlight = false
        resultCallback = null
        pendingMode = MODE_NONE
        pendingUploadUrl = null
        pendingToken = null
        pendingMaxEdge = DEFAULT_MAX_EDGE
        pendingMaxCount = 1
        pendingDirectory = null
        val result = LinkedHashMap<String, Any>()
        result["code"] = code
        if (!data.isNullOrEmpty()) {
            result["data"] = data
        }
        result["msg"] = msg
        if (width > 0 && height > 0) {
            result["width"] = width
            result["height"] = height
        }
        callback?.invoke(result)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (recording || recordPendingPermission) {
            // 页销毁(切二级页/登出)录音即弃:release 防 recorder 泄漏,丢文件 + 回调作废(取消语义)
            finalizeRecording(cancel = true)
        }
        inFlight = false
        resultCallback = null
        pendingMode = MODE_NONE
        pendingUploadUrl = null
        pendingToken = null
        pendingMaxEdge = DEFAULT_MAX_EDGE
        pendingMaxCount = 1
        pendingDirectory = null
        if (current === this) {
            current = null
        }
        super.onDestroy()
    }

    companion object {
        /** common 侧 base/FileModule.MODULE_NAME 同串(KR 前缀原生类名、HR 前缀注册名,照 WS 桥接惯例) */
        const val MODULE_NAME = "HRFileModule"
        private const val METHOD_PICK_UPLOAD = "pickAndUpload"
        private const val METHOD_PICK_IMAGES = "pickImages"
        private const val METHOD_UPLOAD_IMAGE = "uploadImage"
        // M23 富媒体聊天
        private const val METHOD_PICK_VIDEO = "pickVideo"
        private const val METHOD_PICK_MEDIA = "pickMedia"
        private const val METHOD_TAKE_PHOTO = "takePhoto"
        private const val METHOD_UPLOAD_MEDIA = "uploadMedia"
        private const val METHOD_VIDEO_COVER = "videoCover"
        private const val METHOD_RECORD_START = "recordAudioStart"
        private const val METHOD_RECORD_STOP = "recordAudioStop"
        private const val METHOD_UPLOAD_PROGRESS = "uploadProgress"
        private const val METHOD_QUERY_MEDIA = "queryMediaLibrary" // M24 十三轮 相册弹层
        private const val METHOD_COPY_MEDIA = "copyMedia" // M24 十三轮 已选落盘
        // M29 权限管理页:麦克风/相册读权限状态与申请 + 应用详情跳转
        private const val METHOD_CHECK_MIC = "checkMicPermission"
        private const val METHOD_REQUEST_MIC = "requestMicPermission"
        private const val METHOD_CHECK_MEDIA = "checkMediaPermission"
        private const val METHOD_REQUEST_MEDIA = "requestMediaPermission"
        private const val METHOD_OPEN_APP_DETAILS = "openAppDetails"
        private const val CODE_OK = 0
        private const val CODE_CANCEL = 1
        private const val CODE_ERROR = -1
        /** 压缩边长缺省(头像场景;common 侧不传 maxEdge 时走此值,512px 对 3x 屏头像圆环充裕) */
        private const val DEFAULT_MAX_EDGE = 512
        private const val JPEG_QUALITY = 80
        /** 选图回包分流模式:handlePickResult 按发起方法分流(仅选图 / 选图即上传一条龙) */
        private const val MODE_NONE = 0
        private const val MODE_PICK = 1
        private const val MODE_PICK_UPLOAD = 2
        private const val MODE_UPLOAD = 3
        private const val MODE_UPLOAD_MEDIA = 4 // M23 媒体直传(不占压缩链,仅走 pending 参数槽)
        private const val MODE_PICK_VIDEO = 5 // M23 仅选视频(落盘+时长回读)
        private const val MODE_PICK_MEDIA = 6 // M24 修订 混选图片+视频(mime 判型分派)
        private const val MODE_TAKE_PHOTO = 7 // M24 六轮 拍照(EXTRA_OUTPUT 直落 filesDir/camera)

        /** 图片后台单线程执行器(串行:压缩与选图落盘共用,一次一任务内存有界;daemon 不阻进程退出) */
        private val BG_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "kr-image-bg").apply { isDaemon = true }
        }

        /** 选图落盘缓存目录名(filesDir 下;每次 pickImages 清空重建) */
        private const val PICK_CACHE_DIR = "pick_cache"

        /** 录音落盘目录名(filesDir 下,M23;逐文件时间戳命名不清理,卸载系统兜底) */
        private const val RECORD_CACHE_DIR = "chat_voice"

        /** RECORD_AUDIO 运行时权限申请 requestCode(错开 KRNotificationModule 2001,双静态槽互不误收) */
        private const val REQUEST_CODE_RECORD = 2002

        /** 相册读权限申请 requestCode(M24 十三轮;错开 2001/2002 三静态槽互不误收) */
        private const val REQUEST_CODE_MEDIA = 2003

        /** 麦克风权限申请 requestCode(M29 权限页;错开 2001-2004 五静态槽互不误收) */
        private const val REQUEST_CODE_MIC = 2005

        /** 相册读权限申请 requestCode(M29 权限页独立通道;错开 2003 相册弹层查询通道) */
        private const val REQUEST_CODE_MEDIA_REQUEST = 2006

        /** 媒体库查询条数上限(DATE_ADDED DESC 截头保最新;防超大媒体库全量入内存) */
        private const val QUERY_MAX_ITEMS = 1000

        /** 录音权限结果转发口(KuiklyRenderActivity.onRequestPermissionsResult 调,KRNotificationModule
         *  同款静态桥):转发活跃实例主线程消费;无实例静默丢弃 */
        fun onRecordPermissionResult(requestCode: Int, grantResults: IntArray) {
            if (requestCode != REQUEST_CODE_RECORD) return
            val instance = current ?: return
            instance.mainHandler.post { instance.handleRecordPermission(grantResults) }
        }

        /** 相册权限结果转发口(M24 十三轮,同 onRecordPermissionResult 静态桥) */
        fun onMediaPermissionResult(requestCode: Int, grantResults: IntArray) {
            if (requestCode != REQUEST_CODE_MEDIA) return
            val instance = current ?: return
            instance.mainHandler.post { instance.handleMediaPermissionResult(grantResults) }
        }

        /** 麦克风权限结果转发口(M29 权限页,同 onRecordPermissionResult 静态桥) */
        fun onMicPermissionResult(requestCode: Int, grantResults: IntArray) {
            if (requestCode != REQUEST_CODE_MIC) return
            val instance = current ?: return
            instance.mainHandler.post { instance.handleMicPermission(grantResults) }
        }

        /** 相册读权限结果转发口(M29 权限页独立通道,同上;2003=相册弹层查询、2006=权限页
         *  申请,双通道各自独立 pending 槽互不误收) */
        fun onMediaRequestPermissionResult(requestCode: Int, grantResults: IntArray) {
            if (requestCode != REQUEST_CODE_MEDIA_REQUEST) return
            val instance = current ?: return
            instance.mainHandler.post { instance.handleMediaRequestPermission(grantResults) }
        }

        /** 当前活跃模块实例(桥 Activity 静态入口转发目标;init 登记、onDestroy 注销) */
        @Volatile
        var current: KRFileModule? = null

        /** 发起时写入、收口清空的选图模式(仅选图 / 一条龙;onPickResult 分流依据) */
        @Volatile
        var pendingMode: Int = MODE_NONE

        /** 发起时写入、收口清空的上传参数(跟随模块自身 companion,不落桥 Activity 静态) */
        @Volatile
        var pendingUploadUrl: String? = null

        @Volatile
        var pendingToken: String? = null

        /** 发起时写入(缺省 DEFAULT_MAX_EDGE)、收口清空的压缩参数(相册场景 1080) */
        @Volatile
        var pendingMaxEdge: Int = DEFAULT_MAX_EDGE

        /** 发起时写入(缺省 1)、收口清空的多选张数(M15;收口 data 形态据此分流) */
        @Volatile
        var pendingMaxCount: Int = 1

        /** 发起时写入、收口清空的存储子目录(common 侧 directory 透传;null=不发字段走服务端原逻辑) */
        @Volatile
        var pendingDirectory: String? = null

        /** 桥 Activity 回传入口(uris=null/空=取消/无相册):转发给活跃实例,无实例则静默丢弃 */
        fun onPickResult(uris: List<Uri>?) {
            current?.handlePickResult(uris)
        }

        // ---------- M29 权限快照单源(实例 check/申请 与宿主直注 PermissionSnapshot 共用;
        //  无实例状态,companion 私有成员实例方法内直接解析) ----------

        private fun isPermissionGranted(context: Context, permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        /** 麦克风授权单值(宿主直注快照用;实例 checkMicPermission 同源) */
        fun micGrantedSnapshot(context: Context): Boolean =
            isPermissionGranted(context, android.Manifest.permission.RECORD_AUDIO)

        /** 相册读权限状态快照(查询/申请短路共用口径):33+ IMAGES+VIDEO 双授权=完整;仅
         *  VISUAL_USER_SELECTED=Android14 部分授权(API 34 起该权限才有语义,33 恒查不到);
         *  33- 传统存储=完整 */
        private fun mediaPermState(context: Context): MediaPermState {
            return if (Build.VERSION.SDK_INT >= 33) {
                val images = isPermissionGranted(context, android.Manifest.permission.READ_MEDIA_IMAGES)
                val video = isPermissionGranted(context, android.Manifest.permission.READ_MEDIA_VIDEO)
                if (images && video) {
                    MediaPermState(granted = true, partial = false)
                } else {
                    MediaPermState(
                        granted = false,
                        partial = Build.VERSION.SDK_INT >= 34 &&
                            isPermissionGranted(context, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
                    )
                }
            } else {
                MediaPermState(
                    granted = isPermissionGranted(context, android.Manifest.permission.READ_EXTERNAL_STORAGE),
                    partial = false,
                )
            }
        }

        /** 相册读权限快照(宿主直注用;Map 形态与 check 回包 data 同构) */
        fun mediaPermSnapshot(context: Context): Map<String, Any> =
            mediaPermState(context).let { mapOf("granted" to it.granted, "partial" to it.partial) }

        /** 申请结果 → 状态(数组序与申请序一致;33+ 双全量=granted,仅第三位部分授权=partial) */
        private fun grantResultsToMediaState(grantResults: IntArray): MediaPermState {
            if (Build.VERSION.SDK_INT >= 33) {
                val g = grantResults
                val full = g.size >= 2 &&
                    g[0] == PackageManager.PERMISSION_GRANTED &&
                    g[1] == PackageManager.PERMISSION_GRANTED
                val visualOnly = !full && g.size >= 3 && g[2] == PackageManager.PERMISSION_GRANTED
                return MediaPermState(granted = full, partial = visualOnly)
            }
            return MediaPermState(
                granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED,
                partial = false,
            )
        }
    }
}

/** 相册读权限状态快照(M29 查询/申请收口共用载荷形态,common 侧 checkMediaPermission
 *  requestMediaPermission 回包同构):granted=完整读权限;partial=Android14「仅选中照片」
 *  部分授权(granted=false 才有意义) */
private data class MediaPermState(val granted: Boolean, val partial: Boolean)
