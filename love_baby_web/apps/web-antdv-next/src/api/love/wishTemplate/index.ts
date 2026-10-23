import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveWishTemplateApi {
  /** 心愿墙挑选模板（心情页心愿墙挑选列表数据源，B 端配置 App 只读） */
  export interface WishTemplate {
    id: number;
    /** 分类（App 端动态聚合 chips；模板 id 即 App 送出 wishKey） */
    category?: null | string;
    /** 标题（气泡主文案） */
    title: string;
    /** 副标题（卡片第二行，可空） */
    subtitle?: null | string;
    /** 卡片渐变起始色 hex #RRGGBB */
    colorStart: string;
    /** 卡片渐变结束色 hex #RRGGBB */
    colorEnd: string;
    /** 图标（端上 AppIconType 枚举 name，未知名端上兜底 Heart） */
    icon: string;
    /** 排序（App 端 sort asc, id asc） */
    sort: number;
    /** 状态：0=启用 / 1=停用（停用行不下发 App；删/停不影响历史消息——kind=2 快照式上送） */
    status: number;
    /** 创建时间 */
    createTime: Date;
  }

  /** 新增模板参数（sort 不传落库默认 0；status 不传落库默认启用；subtitle 不传落库 null） */
  export interface CreateParams {
    category?: string;
    colorStart: string;
    colorEnd: string;
    icon: string;
    sort?: number;
    status?: number;
    subtitle?: string;
    title: string;
  }

  /** 修改模板参数（全字段保存，null 字段保持原值） */
  export interface UpdateParams {
    id: number;
    category?: string;
    colorStart: string;
    colorEnd: string;
    icon: string;
    sort?: number;
    status?: number;
    subtitle?: string;
    title: string;
  }
}

/** 查询心愿模板分页（title 模糊 / category 模糊 / status 精确 / 创建时间段） */
export function getLoveWishTemplatePage(params: PageParam) {
  return requestClient.get<PageResult<LoveWishTemplateApi.WishTemplate>>(
    '/love/wish-template/page',
    {
      params,
    },
  );
}

/** 新增心愿模板 */
export function createLoveWishTemplate(
  data: LoveWishTemplateApi.CreateParams,
) {
  return requestClient.post('/love/wish-template/create', data);
}

/** 修改心愿模板 */
export function updateLoveWishTemplate(
  data: LoveWishTemplateApi.UpdateParams,
) {
  return requestClient.put('/love/wish-template/update', data);
}

/** 删除心愿模板 */
export function deleteLoveWishTemplate(id: number) {
  return requestClient.delete(`/love/wish-template/delete?id=${id}`);
}
