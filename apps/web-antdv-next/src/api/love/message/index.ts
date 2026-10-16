import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveMessageApi {
  /** 悄悄话消息信息 */
  export interface Message {
    id: number;
    /** 情侣关系编号（解绑后留历史值，D-11） */
    coupleId: number;
    /** 发送方用户编号 */
    senderId: number;
    /** 接收方用户编号（快照列，解绑后审计归对依据，D-11） */
    receiverId: number;
    /** 消息类型：1=文本悄悄话 / 2=心愿消息 */
    kind: number;
    /** 文本内容，或心愿文案快照 */
    content?: string;
    /** 心愿 key 快照，kind=2 时有值 */
    wishKey?: string;
    /** 心愿颜色快照，kind=2 时有值 */
    color?: string;
    /** 心愿图标快照，kind=2 时有值 */
    icon?: string;
    /** 审核状态：0=待审 / 1=通过 / 2=命中；首期恒为 1 */
    auditStatus: number;
    createTime: string;
  }
}

/** 查询悄悄话消息分页 */
export function getLoveMessagePage(params: PageParam) {
  return requestClient.get<PageResult<LoveMessageApi.Message>>(
    '/love/message/page',
    { params },
  );
}

/** 删除悄悄话消息 */
export function deleteLoveMessage(id: number) {
  return requestClient.delete(`/love/message/delete?id=${id}`);
}
