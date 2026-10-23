import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveEmojiApi {
  /** 聊天表情包（表情面板字典，B 端配置 App 只读；端上按 category 动态聚合 tab） */
  export interface Emoji {
    id: number;
    /** 分类（自由填，App 端动态聚合 chips；空串归「常用」桶） */
    category?: null | string;
    /** emoji 字符（点选即插入输入框） */
    emoji: string;
    /** 排序（App 端 sort asc, id asc） */
    sort: number;
    /** 状态：0=启用 / 1=停用（停用行不下发 App） */
    status: number;
    /** 创建时间 */
    createTime: Date;
  }

  /** 新增表情参数（status 不传落库默认启用；category 不传落库空串） */
  export interface CreateParams {
    category?: string;
    emoji: string;
    sort?: number;
    status?: number;
  }

  /** 修改表情参数（全字段保存，null 字段保持原值） */
  export interface UpdateParams {
    id: number;
    category?: string;
    emoji: string;
    sort?: number;
    status?: number;
  }
}

/** 查询表情分页（category 模糊 / status 精确 / 创建时间段） */
export function getLoveEmojiPage(params: PageParam) {
  return requestClient.get<PageResult<LoveEmojiApi.Emoji>>(
    '/love/emoji/page',
    {
      params,
    },
  );
}

/** 新增表情 */
export function createLoveEmoji(data: LoveEmojiApi.CreateParams) {
  return requestClient.post('/love/emoji/create', data);
}

/** 修改表情 */
export function updateLoveEmoji(data: LoveEmojiApi.UpdateParams) {
  return requestClient.put('/love/emoji/update', data);
}

/** 删除表情 */
export function deleteLoveEmoji(id: number) {
  return requestClient.delete(`/love/emoji/delete?id=${id}`);
}
