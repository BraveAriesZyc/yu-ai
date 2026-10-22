import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveInspirationApi {
  /** 恋爱灵感（首页「今日情话」「约会灵感」两卡内容池，B 端配置 App 只读） */
  export interface Inspiration {
    id: number;
    /** 类型：1=情话 / 2=约会灵感 */
    kind: number;
    /** 标题（约会灵感用，情话为 null） */
    title?: null | string;
    /** 内容（情话正文 / 约会灵感描述） */
    content: string;
    /** 分类（情话: 甜宠/日常/表白；约会灵感: 室内/户外/美食/观影/手作；App 端动态聚合） */
    category?: null | string;
    /** 状态：0=启用 / 1=停用（停用行不出现在 App） */
    status: number;
    /** 创建时间 */
    createTime: Date;
  }

  /** 新增灵感参数（status 不传落库默认启用；category 不传落库空串） */
  export interface CreateParams {
    kind: number;
    title?: string;
    content: string;
    category?: string;
    status?: number;
  }

  /** 修改灵感参数（全字段保存，null 字段保持原值） */
  export interface UpdateParams {
    id: number;
    kind: number;
    title?: string;
    content: string;
    category?: string;
    status?: number;
  }
}

/** 查询灵感分页（content 模糊 / kind 精确 / status 精确 / 创建时间段） */
export function getLoveInspirationPage(params: PageParam) {
  return requestClient.get<PageResult<LoveInspirationApi.Inspiration>>(
    '/love/inspiration/page',
    {
      params,
    },
  );
}

/** 新增灵感 */
export function createLoveInspiration(data: LoveInspirationApi.CreateParams) {
  return requestClient.post('/love/inspiration/create', data);
}

/** 修改灵感 */
export function updateLoveInspiration(data: LoveInspirationApi.UpdateParams) {
  return requestClient.put('/love/inspiration/update', data);
}

/** 删除灵感 */
export function deleteLoveInspiration(id: number) {
  return requestClient.delete(`/love/inspiration/delete?id=${id}`);
}
