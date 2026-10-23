package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager

/**
 * WishTemplateApi — 心愿墙挑选模板域接口(M25,需登录;全局共享运营内容无 couple/user 归属)
 * 用法:协程内 val r = WishTemplateApi.list(pager);数组回包 WishTemplateInfo.listFrom(r.dataArray)
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);WishTemplateModels
 * 事件:无(快照单例 WishStore 自持,登录首拉/回前台重拉,登出不 reset——会话无关运营内容)
 * 功能:
 *  - list 启用行全量(sort asc, id asc 稳定序);端上按 category 动态聚合 chips,无分页
 *  - 模板 id 即送出 wishKey(kind=2 快照式上送,渲染不查本表,删/停模板不影响历史消息)
 *  - 只读端点零错误路径(空表端上走 allWishes 内置兜底,心愿墙永不空白)
 */
internal object WishTemplateApi {

    private const val PATH_LIST = "/love/wish-template/list"

    /** 心愿模板全量列表:data 为数组(ApiResult.Ok.dataArray) */
    suspend fun list(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_LIST, isPost = false)
}
