import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveDailyQuestionApi {
  /** 每日一问题目（全局共享运营内容池，B 端配置 App 只读经 day 快照下发） */
  export interface DailyQuestion {
    id: number;
    /** 题目内容 */
    content: string;
    /** 分类（自由文本：情感/日常/回忆/脑洞/未来…；App 端原样展示） */
    category?: null | string;
    /** 状态：0=启用 / 1=停用（停用行退出轮换池；删/停不影响已出过的天——day 快照） */
    status: number;
    /** 创建时间 */
    createTime: Date;
  }

  /** 新增题目参数（status 不传落库默认启用；category 不传落库 ''） */
  export interface CreateParams {
    category?: string;
    content: string;
    status?: number;
  }

  /** 修改题目参数（全字段保存，null 字段保持原值；不影响历史 day 快照） */
  export interface UpdateParams {
    id: number;
    category?: string;
    content: string;
    status?: number;
  }
}

/** 查询每日一问题目分页（内容模糊 / 分类模糊 / status 精确 / 创建时间段） */
export function getLoveDailyQuestionPage(params: PageParam) {
  return requestClient.get<PageResult<LoveDailyQuestionApi.DailyQuestion>>(
    '/love/daily-question/page',
    {
      params,
    },
  );
}

/** 新增每日一问题目 */
export function createLoveDailyQuestion(
  data: LoveDailyQuestionApi.CreateParams,
) {
  return requestClient.post('/love/daily-question/create', data);
}

/** 修改每日一问题目 */
export function updateLoveDailyQuestion(
  data: LoveDailyQuestionApi.UpdateParams,
) {
  return requestClient.put('/love/daily-question/update', data);
}

/** 删除每日一问题目 */
export function deleteLoveDailyQuestion(id: number) {
  return requestClient.delete(`/love/daily-question/delete?id=${id}`);
}
