import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveCoupleApi {
  /** 情侣关系 */
  export interface Couple {
    /** 绑定时间 */
    bindTime: Date;
    /** 创建时间 */
    createTime?: Date;
    /** 关系编号 */
    id: number;
    /** 关系状态，首期恒为 0（正常） */
    status: number;
    /** 发起方用户编号（邀请码属主） */
    userAId: number;
    /** 发起方昵称（join love_user 带出，已删用户为 null） */
    userANickname?: string;
    /** 受邀方用户编号 */
    userBId: number;
    /** 受邀方昵称（join love_user 带出，已删用户为 null） */
    userBNickname?: string;
  }
}

/** 查询情侣关系分页（status 精确 + 双方昵称模糊，join love_user） */
export function getLoveCouplePage(params: PageParam) {
  return requestClient.get<PageResult<LoveCoupleApi.Couple>>(
    '/love/couple/page',
    { params },
  );
}

/** 强制解绑情侣关系（按关系编号物理删除，双方恢复未绑定状态，不可恢复） */
export function unbindLoveCouple(id: number) {
  return requestClient.delete(`/love/couple/unbind?id=${id}`);
}
