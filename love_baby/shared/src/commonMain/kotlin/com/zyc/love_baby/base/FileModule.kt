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
 *  - pickImages(maxCount, onResult):仅选图,零压缩零上传零网络调用,回本地 file:// 路径字符串
 *    (Android 原生选图当下把 content:// 字节落盘应用私有目录 filesDir 再收口——content URI 临时授权随时间
 *    衰减,延迟到发布时点再读部分张失败真机实证;file:// 喂 rememberAsyncImagePainter 展示与
 *    uploadImage 上传皆稳,M24 三轮迁出 cacheDir 保失败重发跨重启可用),发布/发送时点再逐个传
 *    uploadImage——选图阶段零服务端调用零孤儿文件
 *  - uploadImage(url, token, uri, maxEdge, onResult):单张本地图压缩+上传(多张由调用方串行逐张
 *    调,进度可报);url 为完整上传端点(ApiConfig.url(pager, "/infra/file/upload")),token 由原生
 *    拼 Authorization: Bearer;maxEdge 压缩边长上限(缺省 512 头像场景,相册/聊天场景传 1080);
 *    directory 存储子目录(缺省空=不发字段,服务端走原逻辑;规范用 DIR_* 常量传业务目录,
 *    服务端日期前缀 yyyyMMdd 拼在其外层 → 最终 yyyyMMdd/业务目录/文件名)
 *  - pickAndUpload(url, token, maxEdge, maxCount, onResult):选图即上传一条龙(**仅头像场景保留**
 *    ——选图即动作本身、无取消路径、无孤儿文件;选图后还有确认步的场景一律 pickImages + uploadImage);
 *    maxCount 为最多选图张数(缺省 1 = 单选;Android 走 EXTRA_ALLOW_MULTIPLE,OEM 忽略 extra 只回
 *    单张也能发),原生内部完成「相册选图(ACTION_PICK 免权限)→ Glide centerInside 压缩(自动
 *    EXIF 矫正)→ multipart 串行直传(整批同成败 fail-fast)」;directory 同 uploadImage(头像场景
 *    传 DIR_AVATAR)
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
     *  file:// 路径(原生已落盘 filesDir 稳态副本)——maxCount=1 单值字符串 / >1 JSON 数组字符串
     *  (双形态同 pickAndUpload) */
    fun pickImages(maxCount: Int = 1, onResult: CallbackFn) {
        val methodArgs = JSONObject()
        methodArgs.put("maxCount", maxCount)
        toNative(false, METHOD_PICK_IMAGES, methodArgs.toString(), onResult)
    }

    /** 单张本地图压缩+上传(M15;一次性回调):uri 为 pickImages 回包的本地 URI,回 data 为
     *  服务端 URL 字符串;多张场景调用方串行逐张调(进度「上传中 i/N」可报);directory 存储
     *  子目录透传(空=不发字段;目录规范见 DIR_* 常量)。
     *  M24:成功回包另附顶层键 width/height(压缩后 Bitmap 实际 px,EXIF 已矫正=最终落库比例;
     *  失败/未知不附——调用方按缺失=0 处理,老调用方不读新键零感知) */
    fun uploadImage(url: String, token: String, uri: String, maxEdge: Int = 512, directory: String = "", onResult: CallbackFn) {
        val methodArgs = JSONObject()
        methodArgs.put("url", url)
        methodArgs.put("token", token)
        methodArgs.put("uri", uri)
        methodArgs.put("maxEdge", maxEdge)
        methodArgs.put("directory", directory)
        toNative(false, METHOD_UPLOAD_IMAGE, methodArgs.toString(), onResult)
    }

    /** 选图+压缩+上传一条龙(一次性回调;**仅头像场景保留**,见头注释):maxEdge 缺省 512(头像
     *  兼容),maxCount 缺省 1 单选(老调用方尾随 lambda 恒绑末参,零改动);>1 多选,整批同成败
     *  fail-fast;directory 存储子目录透传(空=不发字段,头像场景传 DIR_AVATAR)。回 data:
     *  maxCount=1 单 URL 字符串(老契约字节不变)/ >1 JSON 数组字符串 */
    fun pickAndUpload(url: String, token: String, maxEdge: Int = 512, maxCount: Int = 1, directory: String = "", onResult: CallbackFn) {
        val methodArgs = JSONObject()
        methodArgs.put("url", url)
        methodArgs.put("token", token)
        methodArgs.put("maxEdge", maxEdge)
        methodArgs.put("maxCount", maxCount)
        methodArgs.put("directory", directory)
        toNative(false, METHOD_PICK_UPLOAD, methodArgs.toString(), onResult)
    }

    // ---------- M23 富媒体聊天:选视频/媒体直传/抽帧封面/录音(Android KRFileModule 实现;
    //  iOS/ohos/H5 未注册 getModule=null,调用方判空 toast「该端暂不支持」渐进降级) ----------

    /** 选视频(M23):ACTION_PICK 系统相册 video 集合(免存储权限),单选;原生把视频字节落盘
     *  filesDir 转 file://(同 pickImages 稳态副本理由;M24 三轮迁出 cacheDir 保失败重发)并回读时长与体量,onResult data = JSON
     *  字符串 {"uri":"<file://>","duration":<秒>,"size":<字节>}(size 供 common 侧 16MB 守卫,
     *  common 无文件 stat 能力,原生单源读出);code=1 用户取消,其余非 0 失败带 msg */
    fun pickVideo(onResult: CallbackFn) {
        toNative(false, METHOD_PICK_VIDEO, JSONObject().toString(), onResult)
    }

    /** 混选图片+视频(M24 修订,用户裁决「不要弹框,点图片 icon 直接选择」):M24 十轮起主路径
     *  为应用内媒体选择器(微信式自建相册页,读媒体库运行时权限,图/视频混选全设备一致无系统
     *  加载条),拒绝权限降级系统选择器三级回退(九轮)——系统照片选择器 ACTION_PICK_IMAGES →
     *  ACTION_PICK 系统相册(无照片选择器模块设备;⚠仅图片可选) → ACTION_GET_CONTENT 文件
     *  选择器(最后兜底)。原生落盘转 file://(稳态副本)并按 mime 判型,onResult data =
     *  JSON 字符串 {"uri":"<file://>","kind":"image"|"video","duration":<秒,图片恒 0>,
     *  "size":<字节>};调用方按 kind 分派图片(uploadImage 链)/视频(videoCover+uploadMedia
     *  链)各自既有上传流程,16MB 视频守卫由调用方先行判断;code=1 用户取消,其余非 0 失败带 msg。
     *  ⚠ M24 十三轮起休眠:相册弹层(多选,MediaPickerSheet + queryMediaLibrary/copyMedia)
     *  替代为聊天发送主路径,本桥暂无调用方——保留为回滚安全网(单选链完备可一键回切) */
    fun pickMedia(onResult: CallbackFn) {
        toNative(false, METHOD_PICK_MEDIA, JSONObject().toString(), onResult)
    }

    /** 拍照(M24 六轮,加号面板「拍照」卡):ACTION_IMAGE_CAPTURE 系统相机(免权限),EXTRA_OUTPUT
     *  经 FileProvider 直落 filesDir/camera 全量原图(不进系统相册、无拷贝);onResult data =
     *  file:// 路径字符串,调用方走 launchImageSend 同图片链;code=1 用户取消(静默),其余失败
     *  带 msg;iOS/ohos/H5 未注册 getModule=null,调用方判空 toast「该端暂不支持」渐进降级 */
    fun takePhoto(onResult: CallbackFn) {
        toNative(false, METHOD_TAKE_PHOTO, JSONObject().toString(), onResult)
    }

    /** 媒体直传(M23,无压缩):uri 本地 file:// 路径(pickVideo/录音回包);fileName 含扩展名、
     *  mime 表意(视频 video/mp4 / 语音 audio/mp4),multipart 表单直传,回 data 服务端 URL 字符串,
     *  契约同 uploadImage;体量守卫(视频 >16MB 拒发)由 common 调用方先行判断 */
    fun uploadMedia(
        url: String,
        token: String,
        uri: String,
        fileName: String,
        mime: String,
        directory: String = "",
        onResult: CallbackFn,
    ) {
        val methodArgs = JSONObject()
            .put("url", url)
            .put("token", token)
            .put("uri", uri)
            .put("fileName", fileName)
            .put("mime", mime)
            .put("directory", directory)
        toNative(false, METHOD_UPLOAD_MEDIA, methodArgs.toString(), onResult)
    }

    /** 视频抽帧封面(M23):uri 本地 file:// 视频,取首帧落 filesDir JPEG(M24 三轮:工作副本
     *  一律落 filesDir 应用私有持久目录,不再走 cacheDir——系统清缓存不殃及失败重发),onResult
     *  data = 封面 file:// 路径;失败回非 0(调用方降级灰底+图标兜底,不阻塞发送) */
    fun videoCover(uri: String, onResult: CallbackFn) {
        val methodArgs = JSONObject()
            .put("uri", uri)
        toNative(false, METHOD_VIDEO_COVER, methodArgs.toString(), onResult)
    }

    /**
     * 录音开始(M23):RECORD_AUDIO 运行时权限原生内申请(拒绝回非 0「需要麦克风权限」);
     * 开始成功不回调,录音结束(到 maxSeconds 自动停/原生异常)才 onResult,data = JSON 字符串
     * {"uri":"<file://>","duration":<秒>}。⚠ **单一收口通道**:到点自动停/手动 [recordAudioStop]
     * (发送与取消两路)的结果统一从本回调收口——调用方把「上传+发送」全挂在此回调,recordAudioStop
     * 自身回调仅是 ack,不承载结果(若双通道并存,stop 偷换回调会让发起侧挂起协程永悬挂)。
     * code=0 定稿 / code=1 用户取消(静默)/ 其余失败带 msg。MPEG_4/AAC 落 filesDir(M24 三轮
     * 迁出 cacheDir:录音文件持久保留,失败重发跨进程重启可用),
     * 60s AAC 约 <1MB,无体量之忧
     */
    fun recordAudioStart(maxSeconds: Int, onResult: CallbackFn) {
        val methodArgs = JSONObject()
            .put("maxSeconds", maxSeconds)
        toNative(false, METHOD_RECORD_START, methodArgs.toString(), onResult)
    }

    /** 录音停止/取消(M23):cancel=true 丢文件回「发起回调」code=1(取消语义,调用方静默);
     *  false 定稿回「发起回调」code=0 data 同自动停形态;与自动停先到先得,后者 no-op(防双回调)。
     *  本方法自身回调仅 ack(见 [recordAudioStart] 单一收口通道注记),调用方传空 lambda 即可 */
    fun recordAudioStop(cancel: Boolean, onResult: CallbackFn) {
        val methodArgs = JSONObject()
            .put("cancel", cancel)
        toNative(false, METHOD_RECORD_STOP, methodArgs.toString(), onResult)
    }

    /** 当前上传进度查询(M24 三轮修订,配聊天媒体蒙层进度环):原生 uploadImage/uploadMedia
     *  保持单一收口契约零改动,进度走本独立查询——onResult data = JSON 字符串 {"progress":N}
     *  (0-100=当前上传已写字节百分比,100=字节全量已写待服务端响应;-1=空闲或压缩/抽帧等
     *  非网络窗口)。原生 inFlight 单飞 → 任一时刻至多一条上传链,全局单进度无歧义;纯查询
     *  不占 inFlight。调用方 200ms 轮询消费,上传收口后停止轮询 */
    fun uploadProgress(onResult: CallbackFn) {
        toNative(false, METHOD_UPLOAD_PROGRESS, JSONObject().toString(), onResult)
    }

    /** 媒体库查询(M24 十三轮,相册弹层数据源):未授权原生自动挂系统申请(拒绝回非 0 带 msg),
     *  授权(含 Android14「仅选中照片」部分授权,可见多少回多少)后回图+视频混排 DATE_ADDED
     *  DESC 最新 1000 条,onResult data = JSON 数组字符串 [{"uri":"<content://>","kind":
     *  "image"|"video","duration":<秒,图片恒 0>,"size":<字节>}](content uri 供弹层缩略图
     *  直出——KRImageAdapter content 分支 Glide 直载降采样;size 供调用方视频体量守卫选中时
     *  拦截,common 无文件 stat 能力原生单源)。纯查询不占 inFlight 选图飞行,可反复调(弹层
     *  每次打开刷新);iOS/ohos/H5 未注册 getModule=null,调用方判空落「该端暂不支持」空态 */
    fun queryMediaLibrary(onResult: CallbackFn) {
        toNative(false, METHOD_QUERY_MEDIA, JSONObject().toString(), onResult)
    }

    /** 已选媒体落盘(M24 十三轮,相册弹层多选收口):uris 为 queryMediaLibrary 回包的 content://
     *  列表(按选择顺序),原生逐个字节落盘 filesDir/pick_cache 转 file:// + mime 判型 + 视频时长
     *  回读 + 字节数,onResult data = JSON 数组字符串(字段同 pickMedia 回包,uri 为 file://;
     *  个别失败跳过该张,全失败回非 0 带 msg);独立于 inFlight 选图飞行。调用方逐项分派
     *  uploadImage / videoCover+uploadMedia 各自既有上传链 */
    fun copyMedia(uris: List<String>, onResult: CallbackFn) {
        val methodArgs = JSONObject()
            .put(
                "uris",
                // 手工拼 JSON 数组串(uri 均为 content:// 无需转义)
                uris.joinToString(",", "[", "]") { uri -> "\"$uri\"" },
            )
        toNative(false, METHOD_COPY_MEDIA, methodArgs.toString(), onResult)
    }

    companion object {
        /** 与各端原生注册名一致(Android KRFileModule.MODULE_NAME 同串) */
        const val MODULE_NAME = "HRFileModule"
        /** 存储子目录规范(经服务端 AppFileUploadReqVO.directory 透传,只传业务目录;
         *  服务端日期前缀拼在其外层,最终形态 yyyyMMdd/业务目录/文件名,如
         *  20261019/avatar/img_xxx.jpg):头像 avatar / 相册(瞬间) album / 聊天(图片/语音/视频) chat
         *  ——全小写英文,OSS 控制台排序友好 */
        const val DIR_AVATAR = "avatar"
        const val DIR_ALBUM = "album"
        const val DIR_CHAT = "chat"
        private const val METHOD_PICK_IMAGES = "pickImages"
        private const val METHOD_UPLOAD_IMAGE = "uploadImage"
        private const val METHOD_PICK_UPLOAD = "pickAndUpload"
        // M23 富媒体聊天:选视频/媒体直传/抽帧封面/录音四件;M24 修订加混选 pickMedia
        private const val METHOD_PICK_VIDEO = "pickVideo"
        private const val METHOD_PICK_MEDIA = "pickMedia"
        private const val METHOD_TAKE_PHOTO = "takePhoto"
        private const val METHOD_UPLOAD_MEDIA = "uploadMedia"
        private const val METHOD_VIDEO_COVER = "videoCover"
        private const val METHOD_RECORD_START = "recordAudioStart"
        private const val METHOD_RECORD_STOP = "recordAudioStop"
        private const val METHOD_UPLOAD_PROGRESS = "uploadProgress"
        // M24 十三轮 相册弹层:媒体库查询 + 已选落盘(多选主路径)
        private const val METHOD_QUERY_MEDIA = "queryMediaLibrary"
        private const val METHOD_COPY_MEDIA = "copyMedia"
    }
}
