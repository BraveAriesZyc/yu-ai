package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.ShareSettingUpdateReq

/**
 * ShareApi — 共享开关域接口(M26,需登录;battery/location 两域共用一张用户配置)
 * 用法:协程内 val r = ShareApi.getSetting(pager) / updateSetting(pager, batteryShare = true),
 *      回包经 data.model.ShareSettingInfo.from 解析(调用方各自落自己 Store)
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);ShareSettingUpdateReq
 * 功能:
 *  - getSetting 懒创建语义:未落行回默认值(电量开/位置关),首次 update 才建行
 *  - updateSetting 两域传 null=不改动该域(电量页只传 batteryShare、位置页只传
 *    locationShare,互不覆盖对方域)
 */
internal object ShareApi {

    private const val PATH_SETTING_GET = "/love/share/setting/get"
    private const val PATH_SETTING_UPDATE = "/love/share/setting/update"

    /** 查我的共享开关(懒创建,未落行回默认) */
    suspend fun getSetting(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_SETTING_GET, isPost = false)

    /** 更新共享开关:null=不改动该域 */
    suspend fun updateSetting(pager: Pager, batteryShare: Boolean?, locationShare: Boolean?): ApiResult =
        HttpClient.request(
            pager,
            PATH_SETTING_UPDATE,
            isPost = true,
            body = ShareSettingUpdateReq(batteryShare, locationShare).toJson(),
        )
}
