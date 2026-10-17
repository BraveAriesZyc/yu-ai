import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveCheckinApi {
  /** 打卡记录 */
  export interface Checkin {
    id: number;
    /** 情侣关系编号（打卡时挂的 couple；解绑重绑后刷新为当前值） */
    coupleId: number;
    /** 打卡用户编号 */
    userId: number;
    /** 打卡日期，yyyy-MM-dd（LocalDate 出参） */
    checkinDate: string;
    /** 创建时间 */
    createTime: Date;
  }
}

/** 查询打卡分页 */
export function getLoveCheckinPage(params: PageParam) {
  return requestClient.get<PageResult<LoveCheckinApi.Checkin>>(
    '/love/checkin/page',
    { params },
  );
}

/** 删除打卡记录（逻辑删除；App 端用户当日重新打卡会复活原行） */
export function deleteLoveCheckin(id: number) {
  return requestClient.delete(`/love/checkin/delete?id=${id}`);
}
