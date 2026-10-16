package com.zyc.love_baby.module

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
import com.zyc.love_baby.KRPickImageActivity
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 头像上传一条龙原生实现(文件服务里程碑):ACTION_PICK 选图(KRPickImageActivity 透明桥收
 * onActivityResult)→ Glide 512px JPEG 压缩(自动 EXIF 矫正,头像场景省流量避开 16MB 服务端限制)
 * → okhttp multipart 直传 /app-api/infra/file/upload(MEMBER token)→ 解析 yudao CommonResult
 * 回调给 common 侧(base/FileModule 声明,一次性回调 keepCallbackAlive=false)。
 * 仅上传动作——URL 写回(user/update)由 common 侧收口,原生不碰业务接口。
 * 头契约与 HttpClient 一致:仅 Authorization: Bearer(token 内含租户上下文,无需 tenant-id 头)。
 */
class KRFileModule : KuiklyRenderBaseModule() {

    init {
        // 桥 Activity 只认类级静态入口,实例引用在此登记(渲染层按页创建实例,单 Activity 场景取最新)
        current = this
    }

    /** okhttp 客户端单例:上传超时收紧(头像小图,30s 足够;连接失败快速失败回 toast) */
    private var client: OkHttpClient? = null

    /** 一次性回调:pickAndUpload 时挂上,收口后置空防重放 */
    private var resultCallback: KuiklyRenderCallback? = null

    /** 流转状态(主线程读写):防上传中二次发起(common 侧 UI loading 之外的兜底) */
    private var inFlight = false

    /** 回调统一转主线程(桥 Activity/Glide/okhttp 回调线程不一,common 侧 Compose 状态只认主线程) */
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            METHOD_PICK_UPLOAD -> pickAndUpload(params, callback)
            else -> callback?.invoke(mapOf("code" to -1, "msg" to "方法不存在"))
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
        pendingUploadUrl = url
        pendingToken = token
        // 透明桥 Activity 收选图结果,结果经类级静态入口转发回本实例(生命周期解耦:
        // 桥 Activity 只认 companion 的 onPickResult,不持模块引用)。
        // ⚠ 必须 catch:桥 Activity 声明缺失等配置错误在此抛 ActivityNotFoundException,
        // Kuikly UI scheduler 线程的未捕获异常会直接杀进程(2026-10-17 真机闪退实证),
        // 桥接失败必须收口回调回 toast,不让异常穿透
        try {
            activity.startActivity(Intent(activity, KRPickImageActivity::class.java))
        } catch (e: Exception) {
            finishFlight(callback, CODE_ERROR, "无法打开相册,请重试")
        }
    }

    /** 选图结果处理(uri=null=取消/读图失败):须主线程(桥 Activity/Glide 回调均已在主线程) */
    private fun handlePickResult(uri: Uri?) {
        val callback = resultCallback
        if (!inFlight || callback == null) {
            return // 已收口(如模块销毁清理),桥回包作废
        }
        if (uri == null) {
            finishFlight(callback, CODE_CANCEL, "未选择图片")
            return
        }
        val context = context
        if (context == null) {
            finishFlight(callback, CODE_ERROR, "上下文不可用")
            return
        }
        // Glide 压缩:centerInside 等比缩到 512px 内,EXIF 旋转自动矫正(相册竖拍图常见坑);
        // 压完进 upload,链路仍持 callback 引用
        Glide.with(context)
            .asBitmap()
            .load(uri)
            .centerInside()
            .override(AVATAR_MAX_EDGE)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val bytes = ByteArrayOutputStream().use { out ->
                        resource.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                        out.toByteArray()
                    }
                    upload(bytes)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    mainHandler.post { finishFlight(callback, CODE_ERROR, "图片读取失败") }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // 资源回收钩子,上传已改持 byte[],无需处理
                }
            })
    }

    /** multipart 直传;okhttp 异步回调线程 → mainHandler 转主线程解析回包 */
    private fun upload(jpegBytes: ByteArray) {
        val callback = resultCallback ?: return
        val token = pendingToken
        val uploadUrl = pendingUploadUrl
        if (token.isNullOrEmpty() || uploadUrl.isNullOrEmpty()) {
            finishFlight(callback, CODE_ERROR, "上传参数丢失")
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
                "file", "avatar_${System.currentTimeMillis()}.jpg",
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
                mainHandler.post { resolveResponse(callback, text) }
            }

            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                mainHandler.post { finishFlight(callback, CODE_ERROR, "网络异常,请重试") }
            }
        })
    }

    /** 解析 yudao CommonResult<String>:code=0 data 即文件 URL;其余透传服务端 msg */
    private fun resolveResponse(callback: KuiklyRenderCallback, text: String) {
        try {
            val json = JSONObject(text)
            val code = json.optInt("code", -1)
            if (code == 0) {
                val fileUrl = json.optString("data")
                if (fileUrl.isNotEmpty()) {
                    finishFlight(callback, 0, "", fileUrl)
                } else {
                    finishFlight(callback, CODE_ERROR, "服务端未返回地址")
                }
            } else {
                finishFlight(callback, code, json.optString("msg").ifEmpty { "上传失败" })
            }
        } catch (_: Exception) {
            finishFlight(callback, CODE_ERROR, "响应解析失败")
        }
    }

    /** 统一收口:清流转状态+一次性回调;成功时额外带 data(文件 URL) */
    private fun finishFlight(callback: KuiklyRenderCallback?, code: Int, msg: String, data: String? = null) {
        inFlight = false
        resultCallback = null
        pendingUploadUrl = null
        pendingToken = null
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
        pendingUploadUrl = null
        pendingToken = null
        if (current === this) {
            current = null
        }
        super.onDestroy()
    }

    companion object {
        /** common 侧 base/FileModule.MODULE_NAME 同串(KR 前缀原生类名、HR 前缀注册名,照 WS 桥接惯例) */
        const val MODULE_NAME = "HRFileModule"
        private const val METHOD_PICK_UPLOAD = "pickAndUpload"
        private const val CODE_CANCEL = 1
        private const val CODE_ERROR = -1
        private const val AVATAR_MAX_EDGE = 512
        private const val JPEG_QUALITY = 80

        /** 当前活跃模块实例(桥 Activity 静态入口转发目标;init 登记、onDestroy 注销) */
        @Volatile
        var current: KRFileModule? = null

        /** 发起时写入、收口清空的上传参数(跟随模块自身 companion,不落桥 Activity 静态) */
        @Volatile
        var pendingUploadUrl: String? = null

        @Volatile
        var pendingToken: String? = null

        /** 桥 Activity 回传入口(uri=null=取消/无相册):转发给活跃实例,无实例则静默丢弃 */
        fun onPickResult(uri: Uri?) {
            current?.handlePickResult(uri)
        }
    }
}
