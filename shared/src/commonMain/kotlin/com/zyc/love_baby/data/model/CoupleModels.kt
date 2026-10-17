package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * CoupleModels — 情侣绑定/用户资料域请求响应模型(INTEGRATION-DESIGN §5.2 couple/user 组、§6.4 错误码、§7.1 DTO 约定)
 * 用法:请求体 XxxReq(...).toJson() 传 CoupleApi/UserApi;响应 data 经 InviteInfo.from/CoupleInfo.from/
 *      UserProfile.from 解析
 * 依赖:仅 core JSONObject 手写映射(§7.1:不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无
 * 功能:
 *  - couple 组:create-invite(→InviteInfo)/bind、get(→CoupleInfo)/unbind;user 组:get(→UserProfile)、update
 *  - id 一律 String(§5.1:NumberSerializer 对超 2^53-1 的 Long 输出字符串、范围内仍是数字,禁止按数字解析 id;
 *    core JSONObject.optString 经 JSON.toString 对数字值同样回字符串,双向兼容)
 *  - couple 段错误码(§6.4):000 邀请码无效/001 已存在情侣关系/002 不能与自己绑定/003 尚未绑定/004 邀请码已过期。
 *    端上仅 003 有差异化分支——它是 couple/get 的「未绑定」状态信号非错误(经 HttpClient successBizCodes
 *    通道按数据放行,免 toast);其余四个无端上分支,统一走 BizError toast 服务端 msg,
 *    按 AuthModels 同款惯例不设常量(避免死代码假覆盖)。错误码定义源 = 服务端 ErrorCodeConstants(只增不删)
 *  - ⚠ 与服务端 VO 的字段名契约(服务端 M2 并行开发,App 侧做平铺/嵌套双形态解析兜底,联调后收敛单一形态):
 *      create-invite data = {inviteCode, inviteExpireTime}(兼容别名 expireTime)
 *      couple get/bind data = {id, partnerId, partnerNickname, partnerAvatar, bindTime}
 *                            或嵌套 {id, partner:{userId, nickname, avatar, sex, birthday, signature}, bindTime}
 *      user get data = {id, mobile, nickname, avatar, sex, birthday, signature}
 *      user update 请求体 = {nickname?, avatar?, sex?, birthday?, signature?}
 *      (M16 资料三字段:sex 0=女 1=男 缺失/null=未设置;birthday "yyyy-MM-dd" 空串=未设置;
 *       signature 空串=无签名——update 语义 sex/birthday null=不改、signature 非null即写含空串=清空)
 *    时间字段(epoch 毫秒)缺失时按 0 解析,UI 降级为占位文案
 */

// ---------- couple 组错误码(§6.4,1_003_004_xxx) ----------

/** 1_003_004_003 尚未绑定情侣关系:couple/get 的未绑定状态信号,按数据放行(非错误,免 toast) */
internal const val COUPLE_ERR_NOT_BOUND = 1003004003L

// ---------- 请求体(§5.2,全部 POST + application/json) ----------

/** 凭邀请码绑定(couple/bind) */
internal data class CoupleBindReq(val inviteCode: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("inviteCode", inviteCode)
}

/**
 * 改个人资料(user/update,M2 定案用 POST 非 PUT:core 2.29 NetworkModule 仅支持 GET/POST)。
 * 守卫语义(M16):nickname/avatar/birthday 仅非空进 body(未传不上送,防误清空);
 * sex 非 null 即进;signature 非 null 即进——**含空串**,空串=端上显式清空(服务端落 '' 等同无签名)
 */
internal data class UserUpdateReq(
    val nickname: String? = null,
    val avatar: String? = null,
    val sex: Int? = null,
    val birthday: String? = null,
    val signature: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().also { json ->
        if (!nickname.isNullOrEmpty()) json.put("nickname", nickname)
        if (!avatar.isNullOrEmpty()) json.put("avatar", avatar)
        if (sex != null) json.put("sex", sex)
        if (!birthday.isNullOrEmpty()) json.put("birthday", birthday)
        if (signature != null) json.put("signature", signature)
    }
}

// ---------- 响应模型 ----------

/**
 * 我的邀请码(couple/create-invite 响应):存在未过期码则复用,否则重生成 + 续期 7 天(§5.2)。
 * expireTime 为 epoch 毫秒(yudao LocalDateTime 序列化),0 = 服务端未回传
 */
internal data class InviteInfo(
    val inviteCode: String,
    val expireTime: Long,
) {
    companion object {
        private const val KEY_INVITE_CODE = "inviteCode"
        private const val KEY_EXPIRE = "inviteExpireTime"
        private const val KEY_EXPIRE_ALT = "expireTime"

        /** inviteCode 为空判无效回包返回 null(调用方保留旧缓存) */
        fun from(data: JSONObject?): InviteInfo? {
            if (data == null) return null
            val code = data.optString(KEY_INVITE_CODE)
            if (code.isEmpty()) return null
            val expire = if (data.has(KEY_EXPIRE)) data.optLong(KEY_EXPIRE) else data.optLong(KEY_EXPIRE_ALT)
            return InviteInfo(code, expire)
        }
    }
}

/**
 * 绑定关系与伴侣信息(couple/get 与 couple/bind 响应;§5.2:伴侣昵称/头像由服务端回查 love_user)。
 * coupleId/partnerId 全 String;bindTime epoch 毫秒,0 = 未回传
 */
internal data class CoupleInfo(
    val coupleId: String,
    val partnerId: String,
    val partnerNickname: String,
    val partnerAvatar: String,
    val bindTime: Long,
    val partnerSex: Int? = null,
    val partnerBirthday: String = "",
    val partnerSignature: String = "",
) {
    companion object {
        /**
         * 兼容两种回包形态(见类头注释契约):平铺 partnerId/partnerNickname/partnerAvatar 或嵌套 partner{}。
         * 数据全空(couple/get 未绑定时的 Ok 空 data,或字段全缺)返回 null → 调用方按未绑定处理
         */
        fun from(data: JSONObject?): CoupleInfo? {
            if (data == null) return null
            val partner = data.optJSONObject("partner")
            val partnerId = firstNonEmpty(
                partner?.optString("userId").orEmpty(),
                partner?.optString("id").orEmpty(),
                data.optString("partnerId"),
            )
            val partnerNickname = firstNonEmpty(
                partner?.optString("nickname").orEmpty(),
                data.optString("partnerNickname"),
            )
            val partnerAvatar = firstNonEmpty(
                partner?.optString("avatar").orEmpty(),
                data.optString("partnerAvatar"),
            )
            // M16 资料三字段:缺键自然缺省,不参与 hasInfo 判定(旧回包兼容,防误翻 UNBOUND)
            val partnerSex = firstNonEmpty(
                partner?.optString("sex").orEmpty(),
                data.optString("partnerSex"),
            ).toIntOrNull()
            val partnerBirthday = firstNonEmpty(
                partner?.optString("birthday").orEmpty(),
                data.optString("partnerBirthday"),
            )
            val partnerSignature = firstNonEmpty(
                partner?.optString("signature").orEmpty(),
                data.optString("partnerSignature"),
            )
            val bindTime = data.optLong("bindTime")
            val hasInfo = data.optString("id").isNotEmpty() || partnerId.isNotEmpty() ||
                partnerNickname.isNotEmpty() || bindTime > 0
            if (!hasInfo) return null
            return CoupleInfo(
                coupleId = data.optString("id"),
                partnerId = partnerId,
                partnerNickname = partnerNickname,
                partnerAvatar = partnerAvatar,
                bindTime = bindTime,
                partnerSex = partnerSex,
                partnerBirthday = partnerBirthday,
                partnerSignature = partnerSignature,
            )
        }
    }
}

/**
 * 伴侣在线状态(couple/online 响应,M13,D-18):true = 对方 WS 内存会话表有任一在线连接;
 * false = 离线(或 websocket 关闭/未桥接端恒 false 的渐进降级)。⚠ 对象形态契约——服务端
 * 禁改回 CommonResult<Boolean> 标量(端上 HttpClient toOk 标量落空对象,M11 unread 同款)
 */
internal data class CoupleOnlineInfo(
    val partnerOnline: Boolean,
) {
    companion object {
        /**
         * 缺 partnerOnline 键判无效回包返回 null(调用方保留现值);false 合法(离线)必须
         * 区分于缺字段——has 守卫在前,optBoolean 缺省值只兜 null/类型意外
         */
        fun from(data: JSONObject?): CoupleOnlineInfo? {
            if (data == null || !data.has("partnerOnline")) return null
            return CoupleOnlineInfo(partnerOnline = data.optBoolean("partnerOnline", false))
        }
    }
}

/** 个人信息(user/get 响应;§5.2 M-6:MEMBER 上下文 buildUserInfo 返回空 Map,昵称/头像一律由此接口回查) */
internal data class UserProfile(
    val userId: String,
    val mobile: String,
    val nickname: String,
    val avatar: String,
    val sex: Int? = null,
    val birthday: String = "",
    val signature: String = "",
) {
    companion object {
        /** id/nickname 全空判无效回包返回 null(调用方保留占位展示) */
        fun from(data: JSONObject?): UserProfile? {
            if (data == null) return null
            val id = firstNonEmpty(data.optString("id"), data.optString("userId"))
            val nickname = data.optString("nickname")
            if (id.isEmpty() && nickname.isEmpty()) return null
            return UserProfile(
                userId = id,
                mobile = data.optString("mobile"),
                nickname = nickname,
                avatar = data.optString("avatar"),
                // M16 资料三字段:缺键缺省,不参与 id/nickname 判空口径
                sex = data.optString("sex").toIntOrNull(),
                birthday = data.optString("birthday"),
                signature = data.optString("signature"),
            )
        }
    }
}

/** 取第一个非空字符串(全空返回 "") */
private fun firstNonEmpty(vararg values: String): String =
    values.firstOrNull { it.isNotEmpty() }.orEmpty()

/**
 * 星座名(M16 资料卡):birthday "yyyy-MM-dd" → 十二星座(摩羯跨年 12/22-1/19)。
 * 非法/空/越界回 ""(调用方降级不显星座);纯字符串切片+区间表,commonMain 零依赖
 */
internal fun zodiacSignOf(birthday: String): String {
    if (birthday.length < 10) return ""
    val month = birthday.substring(5, 7).toIntOrNull() ?: return ""
    val day = birthday.substring(8, 10).toIntOrNull() ?: return ""
    if (month !in 1..12 || day !in 1..31) return ""
    return when (month) {
        1 -> if (day < 20) "摩羯座" else "水瓶座"
        2 -> if (day < 19) "水瓶座" else "双鱼座"
        3 -> if (day < 21) "双鱼座" else "白羊座"
        4 -> if (day < 20) "白羊座" else "金牛座"
        5 -> if (day < 21) "金牛座" else "双子座"
        6 -> if (day < 22) "双子座" else "巨蟹座"
        7 -> if (day < 23) "巨蟹座" else "狮子座"
        8 -> if (day < 23) "狮子座" else "处女座"
        9 -> if (day < 23) "处女座" else "天秤座"
        10 -> if (day < 24) "天秤座" else "天蝎座"
        11 -> if (day < 23) "天蝎座" else "射手座"
        else -> if (day < 22) "射手座" else "摩羯座"
    }
}
