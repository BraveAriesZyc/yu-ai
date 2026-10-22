package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager

/**
 * LoveConfigApi — 业务配置域接口(M23,需登录;全局共享运营 KV 无 couple/user 归属)
 * 用法:协程内 val r = LoveConfigApi.list(pager);回包单对象 = configKey→configValue 映射
 *      (JSONObject 形态,keySet() 遍历取对,见 LoveConfigStore.refresh 的拆包)
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新)
 * 事件:无(快照进 LoveConfigStore 全局单例,与页面生命周期解耦)
 * 功能:
 *  - list 全部启用配置(仅 status=0 行;停用/已删不下发,端上对缺键走内置兜底值);
 *    十几行量级 KV 全量返回无分页;服务端空表/全停用回空 Map 零错误路径(不打错)
 */
internal object LoveConfigApi {

    private const val PATH_LIST = "/love/config/list"

    /** 全部启用配置:data 为单对象(configKey→configValue 映射,恒对象形态,M11 冻结口径) */
    suspend fun list(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_LIST, isPost = false)
}
