import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveMoodRecordApi {
  /** 心情记录 */
  export interface MoodRecord {
    id: number;
    /** 情侣关系编号 */
    coupleId: number;
    /** 记录用户编号（月历上区分本人/伴侣） */
    userId: number;
    /** 心情日期（纯日期，一人一天一条 uk_user_date） */
    moodDate: string;
    /** 心情名快照（记录时点，删除选项后历史不变灰） */
    moodLabel: string;
    /** 表情快照（记录时点） */
    emoji: string;
    /** 颜色 hex 快照（记录时点） */
    color: string;
    /** 创建时间 */
    createTime: Date;
  }
}

/** 查询心情记录分页 */
export function getLoveMoodRecordPage(params: PageParam) {
  return requestClient.get<PageResult<LoveMoodRecordApi.MoodRecord>>(
    '/love/mood-record/page',
    { params },
  );
}

/** 删除心情记录（逻辑删除；只删快照行，不动 couple 私有选项字典） */
export function deleteLoveMoodRecord(id: number) {
  return requestClient.delete(`/love/mood-record/delete?id=${id}`);
}
