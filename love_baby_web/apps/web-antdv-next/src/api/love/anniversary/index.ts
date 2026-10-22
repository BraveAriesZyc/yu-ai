import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveAnniversaryApi {
  /** 纪念日信息 */
  export interface Anniversary {
    id: number;
    /** 情侣关系编号（couple 共享；解绑后留历史值） */
    coupleId: number;
    /** 创建人用户编号 */
    ownerId: number;
    /** 纪念日名称 */
    title: string;
    /** 纪念日日期，yyyy-MM-dd（LocalDate 出参） */
    annivDate: string;
    /** 重复类型：1=每年 2=一次性 */
    repeatType: number;
    /** emoji 图标（App 端预设字典选一，空串 = 未选） */
    emoji?: string;
    /** 创建时间 */
    createTime: Date;
  }
}

/** 查询纪念日分页 */
export function getLoveAnniversaryPage(params: PageParam) {
  return requestClient.get<PageResult<LoveAnniversaryApi.Anniversary>>(
    '/love/anniversary/page',
    { params },
  );
}

/** 删除纪念日 */
export function deleteLoveAnniversary(id: number) {
  return requestClient.delete(`/love/anniversary/delete?id=${id}`);
}
