import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { z } from '#/adapter/form';

/** 状态选项（CommonStatusEnum：0=启用 / 1=停用），本期零字典，本地映射 */
const statusOptions = [
  { label: '启用', value: 0 },
  { label: '停用', value: 1 },
];

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
      fieldName: 'label',
      label: '心情名',
      componentProps: {
        placeholder: '请输入心情名',
        maxLength: 16,
      },
      rules: z.string().min(1, '心情名不能为空').max(16),
    },
    {
      component: 'Input',
      fieldName: 'emoji',
      label: '表情',
      componentProps: {
        placeholder: '请输入表情，如 😄',
        maxLength: 16,
      },
      rules: z.string().min(1, '表情不能为空').max(16),
    },
    {
      component: 'Input',
      fieldName: 'color',
      label: '颜色',
      componentProps: {
        placeholder: '请输入颜色，如 #FFB93C',
        maxLength: 7,
      },
      help: '输入 hex 模式的颜色, 例如 #FFB93C',
      rules: z
        .string()
        .regex(/^#[0-9a-fA-F]{6}$/, '颜色必须为合法的 hex 色值，如 #FFB93C'),
    },
    {
      component: 'InputNumber',
      fieldName: 'sort',
      label: '显示顺序',
      componentProps: {
        class: '!w-full',
        min: 0,
        placeholder: '请输入显示顺序',
      },
      help: '越小越靠前',
      rules: z.number().default(0),
    },
    {
      component: 'RadioGroup',
      fieldName: 'status',
      label: '状态',
      componentProps: {
        options: statusOptions,
      },
      help: '停用行不参与绑定播种',
      rules: z.number().default(0),
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'label',
      label: '心情名',
      component: 'Input',
      componentProps: {
        placeholder: '请输入心情名关键字',
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
      field: 'label',
      title: '心情名',
      minWidth: 120,
    },
    {
      field: 'emoji',
      title: '表情',
      minWidth: 80,
    },
    {
      field: 'color',
      title: '颜色',
      minWidth: 120,
      slots: { default: 'color' },
    },
    {
      field: 'sort',
      title: '显示顺序',
      minWidth: 100,
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
