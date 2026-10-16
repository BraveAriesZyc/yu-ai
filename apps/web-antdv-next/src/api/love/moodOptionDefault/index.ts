import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveMoodOptionDefaultApi {
  /** 心情默认选项模板（情侣绑定时按模板播种为双方私有心情字典） */
  export interface MoodOptionDefault {
    id: number;
    /** 心情名 */
    label: string;
    /** 表情（emoji 文本，原样透传） */
    emoji: string;
    /** 颜色，hex 色值字符串（如 #FFB93C） */
    color: string;
    /** 显示顺序（越小越靠前） */
    sort: number;
    /** 状态：0=启用 / 1=停用（停用行不参与绑定播种） */
    status: number;
    /** 创建时间 */
    createTime: Date;
  }

  /** 新增心情默认选项参数（status 不传落库默认启用） */
  export interface CreateParams {
    label: string;
    emoji: string;
    color: string;
    sort: number;
    status?: number;
  }

  /** 修改心情默认选项参数 */
  export interface UpdateParams {
    id: number;
    label: string;
    emoji: string;
    color: string;
    sort: number;
    status?: number;
  }
}

/** 查询心情默认选项分页（label 模糊 / status 精确） */
export function getLoveMoodOptionDefaultPage(params: PageParam) {
  return requestClient.get<
    PageResult<LoveMoodOptionDefaultApi.MoodOptionDefault>
  >('/love/mood-option-default/page', {
    params,
  });
}

/** 新增心情默认选项 */
export function createLoveMoodOptionDefault(
  data: LoveMoodOptionDefaultApi.CreateParams,
) {
  return requestClient.post('/love/mood-option-default/create', data);
}

/** 修改心情默认选项 */
export function updateLoveMoodOptionDefault(
  data: LoveMoodOptionDefaultApi.UpdateParams,
) {
  return requestClient.put('/love/mood-option-default/update', data);
}

/** 删除心情默认选项 */
export function deleteLoveMoodOptionDefault(id: number) {
  return requestClient.delete(`/love/mood-option-default/delete?id=${id}`);
}
