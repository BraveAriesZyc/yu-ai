package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * BatteryModels — 电量互看域请求/响应模型(M26,§5.1/§7.1 DTO 约定同 TodoModels)
 * 用法:请求体 BatteryReportReq(...).toJson() 传 BatteryApi;回包 VO 经 BatteryRespInfo.from 解析
 * 依赖:仅 core JSONObject 手写映射(无序列化插件)
 * 功能:
 *  - battery 组:report(upsert 自己快照,一次回双方状态)/get(页面轮询,回包结构同 report)
 *  - ⚠ 空值哨兵约定(服务端 VO 字段可 null,JSON 缺 key 与 null 值统一按哨兵解析,双态兼容):
 *    level -1=未知或从未上报;charging -1=从未上报(0/1/2 正常态);reportTime 0=从未上报
 *    (CoupleModels.bindTime「0=未回传」同款);mine 整体 null=从未上报(嵌套对象 optJSONObject
 *    判空,无哨兵问题)
 *  - 错误码(1_003_015_xxx):我方开关关被拒收上报(002 电量/000 位置)——正常使用端上不会触发
 *    (开关状态驱动上报),统一走 BizError toast,不设常量
 */

// ---------- 请求体(POST + application/json) ----------

/** 上报电量快照(battery/report);charging 0=未充电 1=充电中 2=已充满 */
internal data class BatteryReportReq(val level: Int, val charging: Int) {
    fun toJson(): JSONObject = JSONObject()
        .put("batteryLevel", level)
        .put("chargingStatus", charging)
}

/** 更新共享开关(setting/update):两域可空,null=不改动该域(ShareApi.updateSetting 组装) */
internal data class ShareSettingUpdateReq(
    val batteryShare: Boolean?,
    val locationShare: Boolean?,
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        if (batteryShare != null) obj.put("batteryShare", batteryShare)
        if (locationShare != null) obj.put("locationShare", locationShare)
        return obj
    }
}

// ---------- 响应模型 ----------

/**
 * 我方电量快照(battery 回包 mine 结构);reportTime epoch 毫秒(服务端 update_time)
 */
internal data class BatterySnapshotInfo(
    val level: Int,
    val charging: Int,
    val reportTime: Long,
) {
    companion object {
        fun from(data: JSONObject?): BatterySnapshotInfo? {
            if (data == null) return null
            return BatterySnapshotInfo(
                level = data.optInt("batteryLevel", -1),
                charging = data.optInt("chargingStatus", -1),
                reportTime = data.optLong("reportTime", 0),
            )
        }
    }
}

/**
 * TA 的电量(battery 回包 partner 结构):shareEnabled=false 时电量字段为哨兵值
 * (端上显「TA 未开启电量共享」);shareEnabled=true + level=-1 = 开了但从未上报
 */
internal data class BatteryPartnerInfo(
    val shareEnabled: Boolean,
    val level: Int,
    val charging: Int,
    val reportTime: Long,
)

/** battery/report 与 battery/get 统一回包:mine 可 null(从未上报),partner 恒有 */
internal data class BatteryRespInfo(
    val mine: BatterySnapshotInfo?,
    val partner: BatteryPartnerInfo,
) {
    companion object {
        fun from(data: JSONObject?): BatteryRespInfo? {
            if (data == null) return null
            val partner = data.optJSONObject("partner") ?: return null
            return BatteryRespInfo(
                mine = BatterySnapshotInfo.from(data.optJSONObject("mine")),
                partner = BatteryPartnerInfo(
                    shareEnabled = partner.optBoolean("shareEnabled"),
                    level = partner.optInt("batteryLevel", -1),
                    charging = partner.optInt("chargingStatus", -1),
                    reportTime = partner.optLong("reportTime", 0),
                ),
            )
        }
    }
}

/** 共享开关(setting/get 与 setting/update 统一回包;懒创建默认:电量开/位置关) */
internal data class ShareSettingInfo(
    val batteryShare: Boolean,
    val locationShare: Boolean,
) {
    companion object {
        fun from(data: JSONObject?): ShareSettingInfo? {
            if (data == null) return null
            return ShareSettingInfo(
                batteryShare = data.optBoolean("batteryShare"),
                locationShare = data.optBoolean("locationShare"),
            )
        }
    }
}
