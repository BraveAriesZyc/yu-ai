package com.zyc.love_baby.module

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
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
 *    网络,收口前先把 content:// 字节落盘 cacheDir/pick_cache 转成 file:(content URI 临时
 *    授权随时间衰减,延迟读取部分张失败真机实证)——回本地 file: 路径字符串,展示与上传全走
 *    本地稳态副本(KRImageAdapter file 分支 / Glide 本命支持)。
 *  - uploadImage(M15):单张本地 URI → Glide 压缩 → multipart 直传,压缩移后台线程(主线程
 *    Bitmap.compress 100-300ms×N 张是「选图/发布卡顿」根因),回 data 单 URL 字符串。
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

    /** 回调统一转主线程(桥 Activity/Glide/okhttp 回调线程不一,common 侧 Compose 状态只认主线程) */
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            METHOD_PICK_UPLOAD -> pickAndUpload(params, callback)
            METHOD_PICK_IMAGES -> pickImages(params, callback)
            METHOD_UPLOAD_IMAGE -> uploadImage(params, callback)
            else -> callback?.invoke(mapOf("code" to -1, "msg" to "方法不存在"))
        }
    }

    /** 仅选图(M15):ACTION_PICK 选完字节落盘 cacheDir 转 file:// 再收口,不压缩不上传零网络;
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
        compressAndUpload(context, Uri.parse(uri)) { fileUrl, msg ->
            if (fileUrl != null) {
                finishFlight(resultCallback, 0, "", fileUrl)
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
        if (pendingMode == MODE_PICK) {
            // 仅选图:立即把 content:// 字节拷贝到 cacheDir 转 file:// 再收口。真机实证(2026-10-17):
            // ACTION_PICK 的 content URI 临时授权/可达性随时间衰减,延迟到发布时点再读(Glide 载
            // content)部分张 onLoadFailed「图片读取失败」;选图当下权限新鲜文件必达,字节流拷贝
            // (不解码,几十 ms/张)后台串行,收口 file:// 后展示(KRImageAdapter file 分支)与
            // 上传(Glide 压缩)全走本地稳态路径。单张拷贝失败剔除尽量交付其余,全失败回错
            BG_EXECUTOR.execute {
                val dir = File(context.cacheDir, PICK_CACHE_DIR)
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
        // 压缩+上传单张;链路仍持 callback 引用,失败 msg 按「单张透传/多选第N张」分流(老契约)
        compressAndUpload(context, uris[index]) { url, msg ->
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
     *  (onDone 收口权在调用方——processNext 续链 / uploadImage 直接收口) */
    private fun compressAndUpload(context: Context, uri: Uri, onDone: (url: String?, msg: String) -> Unit) {
        Glide.with(context)
            .asBitmap()
            .load(uri)
            .centerInside()
            .override(pendingMaxEdge)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    BG_EXECUTOR.execute {
                        val bytes = ByteArrayOutputStream().use { out ->
                            resource.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                            out.toByteArray()
                        }
                        mainHandler.post { upload(bytes, onDone) }
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    mainHandler.post { onDone(null, "图片读取失败") }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // 资源回收钩子,上传已改持 byte[],无需处理
                }
            })
    }

    /** multipart 直传;okhttp 异步回调线程 → mainHandler 转主线程解析,结果经 onDone 上交
     *  (url=null=失败携 msg;收口权在调用方——单张直接收口/多选链需续链分流,M15) */
    private fun upload(jpegBytes: ByteArray, onDone: (url: String?, msg: String) -> Unit) {
        val token = pendingToken
        val uploadUrl = pendingUploadUrl
        if (token.isNullOrEmpty() || uploadUrl.isNullOrEmpty()) {
            onDone(null, "上传参数丢失")
            return
        }
        val httpClient = client ?: OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
            .also { client = it }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "img_${System.currentTimeMillis()}.jpg",
                RequestBody.create("image/jpeg".toMediaTypeOrNull(), jpegBytes),
            )
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

    /** 统一收口:清流转状态+一次性回调;成功时额外带 data(单值或 JSON 数组字符串) */
    private fun finishFlight(callback: KuiklyRenderCallback?, code: Int, msg: String, data: String? = null) {
        inFlight = false
        resultCallback = null
        pendingMode = MODE_NONE
        pendingUploadUrl = null
        pendingToken = null
        pendingMaxEdge = DEFAULT_MAX_EDGE
        pendingMaxCount = 1
        val result = LinkedHashMap<String, Any>()
        result["code"] = code
        if (!data.isNullOrEmpty()) {
            result["data"] = data
        }
        result["msg"] = msg
        callback?.invoke(result)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        inFlight = false
        resultCallback = null
        pendingMode = MODE_NONE
        pendingUploadUrl = null
        pendingToken = null
        pendingMaxEdge = DEFAULT_MAX_EDGE
        pendingMaxCount = 1
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

        /** 图片后台单线程执行器(串行:压缩与选图落盘共用,一次一任务内存有界;daemon 不阻进程退出) */
        private val BG_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "kr-image-bg").apply { isDaemon = true }
        }

        /** 选图落盘缓存目录名(cacheDir 下;每次 pickImages 清空重建) */
        private const val PICK_CACHE_DIR = "pick_cache"

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

        /** 桥 Activity 回传入口(uris=null/空=取消/无相册):转发给活跃实例,无实例则静默丢弃 */
        fun onPickResult(uris: List<Uri>?) {
            current?.handlePickResult(uris)
        }
    }
}
