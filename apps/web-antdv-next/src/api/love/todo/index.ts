import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveTodoApi {
  /** 待办信息 */
  export interface Todo {
    id: number;
    /** 情侣关系编号（couple 共享；解绑后留历史值） */
    coupleId: number;
    /** 创建人用户编号 */
    ownerId: number;
    /** 待办标题 */
    title: string;
    /** 是否完成 */
    done: boolean;
    /** 标签文本（家庭/恋爱/健康/生活/随手记） */
    tag?: string;
    /** 标签颜色，hex 色值字符串 */
    color?: string;
    /** 创建时间 */
    createTime: Date;
  }
}

/** 查询待办分页 */
export function getLoveTodoPage(params: PageParam) {
  return requestClient.get<PageResult<LoveTodoApi.Todo>>('/love/todo/page', {
    params,
  });
}

/** 删除待办 */
export function deleteLoveTodo(id: number) {
  return requestClient.delete(`/love/todo/delete?id=${id}`);
}
