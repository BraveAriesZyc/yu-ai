import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { z } from '#/adapter/form';
import { getRangePickerDefaultProps } from '#/utils';

/** 状态选项（CommonStatusEnum：0=启用 / 1=停用），本地映射 */
const statusOptions = [
  { label: '启用', value: 0 },
  { label: '停用', value: 1 },
];

/** 配置键规则（与后端 LoveConfigSaveReqVO.KEY_PATTERN 一致） */
const KEY_PATTERN = /^[a-z][a-z0-9_]{0,63}$/;

/** 新增/修改的表单 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'id',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '配置名',
      componentProps: {
        placeholder: '请输入配置名（B 端展示用，如：客服微信号）',
        maxLength: 64,
      },
      rules: z.string().min(1, '配置名不能为空').max(64),
    },
    {
      component: 'Input',
      fieldName: 'configKey',
      label: '配置键',
      componentProps: {
        placeholder: '如 customer_service_wechat',
        maxLength: 64,
      },
      help: 'App 端取值契约键：小写字母开头，仅小写字母/数字/下划线；创建后请勿随意变更（改名即换 App 取值键）',
      rules: z
        .string()
        .min(1, '配置键不能为空')
        .max(64)
        .regex(
          KEY_PATTERN,
          '小写字母开头，仅含小写字母/数字/下划线，最长 64 位',
        ),
    },
    {
      component: 'Textarea',
      fieldName: 'configValue',
      label: '配置值',
      componentProps: {
        placeholder: '请输入配置值（下发 App 的实际内容）',
        maxLength: 500,
        rows: 3,
      },
      rules: z.string().min(1, '配置值不能为空').max(500),
    },
    {
      component: 'Input',
      fieldName: 'remark',
      label: '备注',
      componentProps: {
        placeholder: '选填，记录用途/变更原因',
        maxLength: 200,
      },
    },
    {
      component: 'RadioGroup',
      fieldName: 'status',
      label: '状态',
      componentProps: {
        options: statusOptions,
      },
      help: '停用行不下发 App（App 端对应项走内置兜底值）',
      rules: z.number().default(0),
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '配置名',
      component: 'Input',
      componentProps: {
        placeholder: '请输入配置名关键字',
        allowClear: true,
      },
    },
    {
      fieldName: 'configKey',
      label: '配置键',
      component: 'Input',
      componentProps: {
        placeholder: '请输入配置键关键字',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        placeholder: '请选择状态',
        allowClear: true,
        options: statusOptions,
      },
    },
    {
      fieldName: 'createTime',
      label: '创建时间',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        placeholder: ['开始时间', '结束时间'],
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'id',
      title: '编号',
      minWidth: 80,
    },
    {
      field: 'name',
      title: '配置名',
      minWidth: 160,
    },
    {
      field: 'configKey',
      title: '配置键',
      minWidth: 200,
    },
    {
      field: 'configValue',
      title: '配置值',
      minWidth: 220,
    },
    {
      field: 'remark',
      title: '备注',
      minWidth: 160,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'status',
      title: '状态',
      minWidth: 90,
      slots: { default: 'status' },
    },
    {
      field: 'createTime',
      title: '创建时间',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 130,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
