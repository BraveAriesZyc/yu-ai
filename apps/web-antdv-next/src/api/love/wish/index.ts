import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveWishApi {
  /** 心愿 */
  export interface Wish {
    id: number;
    /** 情侣关系编号（couple 共享双方互见；解绑重绑后旧数据留历史值） */
    coupleId: number;
    /** 创建人用户编号（删除权限依据：仅创建者可删 1_003_009_001；勾选完成不限创建者） */
    userId: number;
    /** 心愿标题（≤64 字，服务端 trim 后落库） */
    title: string;
    /** 是否完成（勾选完成时服务端自动发庆祝消息到悄悄话，CAS 单闸恰一条，D-19b） */
    done: boolean;
    /** 完成人用户编号（勾选者，可与创建人不同；未完成为 null） */
    doneBy: null | number;
    /** 完成时间（未完成为 null） */
    doneTime: Date | null;
    /** 创建时间 */
    createTime: Date;
  }
}

/** 查询心愿分页 */
export function getLoveWishPage(params: PageParam) {
  return requestClient.get<PageResult<LoveWishApi.Wish>>('/love/wish/page', {
    params,
  });
}

/** 删除心愿（逻辑删除；admin 兜底不受「仅创建者可删」限制；App 端传播走全量覆盖） */
export function deleteLoveWish(id: number) {
  return requestClient.delete(`/love/wish/delete?id=${id}`);
}
