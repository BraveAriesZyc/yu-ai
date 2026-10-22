import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveUserApi {
  /** C 端用户信息 */
  export interface User {
    id?: number;
    /** 手机号 */
    mobile?: string;
    /** 用户昵称 */
    nickname?: string;
    /** 用户头像（URL） */
    avatar?: string;
    /** 性别：0=女 / 1=男，null=未设置——与系统字典语义相反 */
    sex?: number;
    /** 生日（yyyy-MM-dd） */
    birthday?: string;
    /** 个性签名 */
    signature?: string;
    /** 帐号状态：0=启用 / 1=停用 */
    status?: number;
    /** 最后登录 IP */
    lastLoginIp?: string;
    /** 最后登录时间 */
    lastLoginTime?: string;
    /** 创建时间 */
    createTime?: string;
  }

  /** 更新 C 端用户参数：业务字段均可选，仅更新提交的非空字段 */
  export interface UpdateParams {
    id: number;
    nickname?: string;
    avatar?: string;
    status?: number;
    signature?: string;
  }
}

/** 查询 C 端用户分页 */
export function getLoveUserPage(params: PageParam) {
  return requestClient.get<PageResult<LoveUserApi.User>>('/love/user/page', {
    params,
  });
}

/** 修改 C 端用户 */
export function updateLoveUser(data: LoveUserApi.UpdateParams) {
  return requestClient.put('/love/user/update', data);
}

/** 删除 C 端用户 */
export function deleteLoveUser(id: number) {
  return requestClient.delete(`/love/user/delete?id=${id}`);
}
