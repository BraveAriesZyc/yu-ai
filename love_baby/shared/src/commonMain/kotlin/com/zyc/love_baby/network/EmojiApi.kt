package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager

/**
 * EmojiApi — 聊天表情包域接口(M25,需登录;全局共享运营内容无 couple/user 归属)
 * 用法:协程内 val r = EmojiApi.list(pager);数组回包 EmojiInfo.listFrom(r.dataArray)
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);EmojiModels
 * 事件:无(快照单例 EmojiStore 自持,登录首拉/回前台重拉,登出不 reset——会话无关运营内容)
 * 功能:
 *  - list 启用行全量(sort asc, id asc 稳定序);端上按 category 动态聚合分类 tab,无分页
 *  - 只读端点零错误路径(空表端上走 ChatStickers 内置兜底字典,面板永不空白)
 */
internal object EmojiApi {

    private const val PATH_LIST = "/love/emoji/list"

    /** 表情全量列表:data 为数组(ApiResult.Ok.dataArray) */
    suspend fun list(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_LIST, isPost = false)
}
