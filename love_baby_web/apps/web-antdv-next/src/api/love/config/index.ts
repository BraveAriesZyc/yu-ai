import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace LoveConfigApi {
  /** 业务配置（love 业务 KV 配置，config_key 为 App 端取值契约） */
  export interface Config {
    id: number;
    /** 配置名（B 端展示用） */
    name: string;
    /** 配置键（App 端 GET /love/config/list 回包的 Map key；小写字母开头 + 小写/数字/下划线） */
    configKey: string;
    /** 配置值 */
    configValue: string;
    /** 备注 */
    remark?: null | string;
    /** 状态：0=启用 / 1=停用（停用行不下发 App） */
    status: number;
    /** 创建时间 */
    createTime: Date;
  }

  /** 新增配置参数（status 不传落库默认启用；键重复报 1_003_012_001） */
  export interface CreateParams {
    name: string;
    configKey: string;
    configValue: string;
    remark?: string;
    status?: number;
  }

  /** 修改配置参数（全字段保存，null 字段保持原值） */
  export interface UpdateParams {
    id: number;
    name: string;
    configKey: string;
    configValue: string;
    remark?: string;
    status?: number;
  }
}

/** 查询配置分页（name 模糊 / configKey 模糊 / status 精确 / 创建时间段） */
export function getLoveConfigPage(params: PageParam) {
  return requestClient.get<PageResult<LoveConfigApi.Config>>(
    '/love/config/page',
    {
      params,
    },
  );
}

/** 新增配置 */
export function createLoveConfig(data: LoveConfigApi.CreateParams) {
  return requestClient.post('/love/config/create', data);
}

/** 修改配置 */
export function updateLoveConfig(data: LoveConfigApi.UpdateParams) {
  return requestClient.put('/love/config/update', data);
}

/** 删除配置 */
export function deleteLoveConfig(id: number) {
  return requestClient.delete(`/love/config/delete?id=${id}`);
}
