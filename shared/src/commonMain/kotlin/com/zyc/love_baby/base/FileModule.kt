package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * FileModule — 头像上传桥接(文件服务里程碑,跨端接口声明;各端原生壳注册同名模块)
 * 依赖:core Module.toNative;各端原生实现注册同名模块(Android=KRFileModule,okhttp multipart 直传;
 *  iOS/ohos/H5 未注册前 getModule 返回 null,调用方判空降级——头像行维持占位 toast,渐进降级设计)
 * 用法:MainPager 头像上传协程经 pager.getModule<FileModule>(MODULE_NAME) 取用;
 *  token 取 SessionStore.accessToken(发起前先经 UserApi.getUser 热身,401 单飞刷新保证 token 新鲜——
 *  原生直传不经 HttpClient,刷新机制覆盖不到)
 * 入参:pickAndUpload(url, token, onResult)——url 为完整上传端点(ApiConfig.url(pager, "/infra/file/upload")),
 *  token 由原生拼 Authorization: Bearer;原生内部完成「相册选图(ACTION_PICK 免权限)→ Glide 512px JPEG
 *  压缩(自动 EXIF 矫正)→ multipart 直传」一条龙
 * 事件:onResult 一次性回调(keepCallbackAlive=false),回参 {"code": 0, "data": "<文件URL>", "msg": ""}——
 *  code=0 成功 data 即服务端 URL(yudao AppFileController 契约);非 0 失败(取消/IO/HTTP 非 0/解析),
 *  msg 为用户可读文案
 * 功能:仅上传动作,URL 写回(UserApi.updateUser)由 common 侧调用方收口——原生不碰业务接口;
 *  服务端零改动(/app-api/infra/file/upload 现成,CommonResult<String> 返回完整 URL,
 *  下载端点 @PermitAll 匿名可读,avatar varchar(256) 容量充裕)
 */
internal class FileModule : Module() {

    override fun moduleName(): String {
        return MODULE_NAME
    }

    /** 选图+压缩+上传一条龙(一次性回调):回参契约见头注释「事件」段 */
    fun pickAndUpload(url: String, token: String, onResult: CallbackFn) {
        val methodArgs = JSONObject()
        methodArgs.put("url", url)
        methodArgs.put("token", token)
        toNative(false, METHOD_PICK_UPLOAD, methodArgs.toString(), onResult)
    }

    companion object {
        /** 与各端原生注册名一致(Android KRFileModule.MODULE_NAME 同串) */
        const val MODULE_NAME = "HRFileModule"
        private const val METHOD_PICK_UPLOAD = "pickAndUpload"
    }
}
