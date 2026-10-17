import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveMomentApi {
  /** 瞬间 */
  export interface Moment {
    id: number;
    /** 情侣关系编号（发布时挂的 couple；解绑重绑后刷新为当前值） */
    coupleId: number;
    /** 发布用户编号（仅上传者本人可删，1_003_008_001） */
    userId: number;
    /** 图片 URL（infra 文件服务上传返回的完整地址，本体留档 infra_file 不级联删） */
    imageUrl: string;
    /** 配文（可选一句话，≤64 字，空串 = 无配文） */
    text: string;
    /** 创建时间 */
    createTime: Date;
  }
}

/** 查询瞬间分页 */
export function getLoveMomentPage(params: PageParam) {
  return requestClient.get<PageResult<LoveMomentApi.Moment>>(
    '/love/moment/page',
    { params },
  );
}

/** 删除瞬间记录（逻辑删除；App 端图片本体留档 infra_file 不级联删） */
export function deleteLoveMoment(id: number) {
  return requestClient.delete(`/love/moment/delete?id=${id}`);
}
