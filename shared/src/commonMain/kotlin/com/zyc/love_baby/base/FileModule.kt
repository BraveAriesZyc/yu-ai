package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * FileModule — 选图/上传桥接(文件服务里程碑,跨端接口声明;各端原生壳注册同名模块)
 * 依赖:core Module.toNative;各端原生实现注册同名模块(Android=KRFileModule,okhttp multipart 直传;
 *  iOS/ohos/H5 未注册前 getModule 返回 null,调用方判空降级,渐进降级设计)
 * 用法:pager.getModule<FileModule>(MODULE_NAME) 取用;token 取 SessionStore.accessToken(原生
 *  直传不经 HttpClient 无 401 单飞刷新,上传前先经 UserApi.getUser 热身——热身属上传动作的一部分,
 *  放在提交时点而非选图时点,M15 真机反馈「没上传不调接口」裁决)
 * 入参(三方法,M15 真机反馈拆分「选图」与「上传」两步):
 *  - pickImages(maxCount, onResult):仅选图,零压缩零上传零网络调用,回本地 content:// URI 字符串
 *    (可直接喂 rememberAsyncImagePainter 展示,Android 端 KRImageAdapter 已扩 content: 分支),
 *    发布/发送时点再逐个传 uploadImage——选图阶段不产生任何服务端调用与孤儿文件
 *  - uploadImage(url, token, uri, maxEdge, onResult):单张本地图压缩+上传(多张由调用方串行逐张
 *    调,进度可报);url 为完整上传端点(ApiConfig.url(pager, "/infra/file/upload")),token 由原生
 *    拼 Authorization: Bearer;maxEdge 压缩边长上限(缺省 512 头像场景,相册/聊天场景传 1080)
 *  - pickAndUpload(url, token, maxEdge, maxCount, onResult):选图即上传一条龙(**仅头像场景保留**
 *    ——选图即动作本身、无取消路径、无孤儿文件;选图后还有确认步的场景一律 pickImages + uploadImage);
 *    maxCount 为最多选图张数(缺省 1 = 单选;Android 走 EXTRA_ALLOW_MULTIPLE,OEM 忽略 extra 只回
 *    单张也能发),原生内部完成「相册选图(ACTION_PICK 免权限)→ Glide centerInside 压缩(自动
 *    EXIF 矫正)→ multipart 串行直传(整批同成败 fail-fast)」
 * 事件:onResult 一次性回调(keepCallbackAlive=false),回参 {"code": 0, "data": "<...>", "msg": ""}——
 *  code=0 成功:data 按方法——pickImages 为本地 URI(maxCount=1 单 URI 字符串 / >1 JSON 数组字符串),
 *  uploadImage 为服务端 URL 字符串(yudao AppFileController 契约),pickAndUpload 为服务端 URL
 *  (maxCount=1 单 URL 字符串老契约 / >1 JSON 数组字符串);code=1 = 用户取消(未选图,调用方宜
 *  静默);其余非 0 失败(取消/IO/HTTP 非 0/解析/整批中任一张失败),msg 为用户可读文案(多图失败带「第N张」)
 * 功能:仅选图与上传动作,URL 写回(业务 create/update)由 common 侧调用方收口——原生不碰业务接口;
 *  服务端零改动(/app-api/infra/file/upload 现成,CommonResult<String> 返回完整 URL,
 *  下载端点 @PermitAll 匿名可读)
 */
internal class FileModule : Module() {

    override fun moduleName(): String {
        return MODULE_NAME
    }

    /** 仅选图不上传(M15 真机反馈「本地预览,发布才上传」裁决;一次性回调):回 data 为本地
     *  content:// URI——maxCount=1 单 URI 字符串 / >1 JSON 数组字符串(双形态同 pickAndUpload) */
    fun pickImages(maxCount: Int = 1, onResult: CallbackFn) {
        val methodArgs = JSONObject()
        methodArgs.put("maxCount", maxCount)
        toNative(false, METHOD_PICK_IMAGES, methodArgs.toString(), onResult)
    }

    /** 单张本地图压缩+上传(M15;一次性回调):uri 为 pickImages 回包的本地 URI,回 data 为
     *  服务端 URL 字符串;多张场景调用方串行逐张调(进度「上传中 i/N」可报) */
    fun uploadImage(url: String, token: String, uri: String, maxEdge: Int = 512, onResult: CallbackFn) {
        val methodArgs = JSONObject()
        methodArgs.put("url", url)
        methodArgs.put("token", token)
        methodArgs.put("uri", uri)
        methodArgs.put("maxEdge", maxEdge)
        toNative(false, METHOD_UPLOAD_IMAGE, methodArgs.toString(), onResult)
    }

    /** 选图+压缩+上传一条龙(一次性回调;**仅头像场景保留**,见头注释):maxEdge 缺省 512(头像
     *  兼容),maxCount 缺省 1 单选(老调用方尾随 lambda 恒绑末参,零改动);>1 多选,整批同成败
     *  fail-fast。回 data:maxCount=1 单 URL 字符串(老契约字节不变)/ >1 JSON 数组字符串 */
    fun pickAndUpload(url: String, token: String, maxEdge: Int = 512, maxCount: Int = 1, onResult: CallbackFn) {
        val methodArgs = JSONObject()
        methodArgs.put("url", url)
        methodArgs.put("token", token)
        methodArgs.put("maxEdge", maxEdge)
        methodArgs.put("maxCount", maxCount)
        toNative(false, METHOD_PICK_UPLOAD, methodArgs.toString(), onResult)
    }

    companion object {
        /** 与各端原生注册名一致(Android KRFileModule.MODULE_NAME 同串) */
        const val MODULE_NAME = "HRFileModule"
        private const val METHOD_PICK_IMAGES = "pickImages"
        private const val METHOD_UPLOAD_IMAGE = "uploadImage"
        private const val METHOD_PICK_UPLOAD = "pickAndUpload"
    }
}
