import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { z } from '#/adapter/form';

/** 类型选项（1=情话 / 2=约会灵感），本期零字典，本地映射 */
const kindOptions = [
  { label: '情话', value: 1 },
  { label: '约会灵感', value: 2 },
];

const kindMap: Record<number, string> = {
  1: '情话',
  2: '约会灵感',
};

/** 状态选项（CommonStatusEnum：0=启用 / 1=停用），本地映射 */
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
      component: 'Select',
      fieldName: 'kind',
      label: '类型',
      componentProps: {
        placeholder: '请选择类型',
        options: kindOptions,
      },
      rules: z.number({ message: '请选择类型' }).min(1).max(2),
    },
    {
      component: 'Input',
      fieldName: 'title',
      label: '标题',
      componentProps: {
        placeholder: '约会灵感建议填写，情话可不填',
        maxLength: 60,
      },
    },
    {
      component: 'Textarea',
      fieldName: 'content',
      label: '内容',
      componentProps: {
        placeholder: '情话正文 / 约会灵感描述',
        maxLength: 200,
        rows: 3,
      },
      rules: z.string().min(1, '内容不能为空').max(200),
    },
    {
      component: 'Input',
      fieldName: 'category',
      label: '分类',
      componentProps: {
        placeholder: '请输入分类',
        maxLength: 20,
      },
      help: '情话建议: 甜宠/日常/表白；约会灵感建议: 室内/户外/美食/观影/手作（App 端按此聚合 chips）',
    },
    {
      component: 'RadioGroup',
      fieldName: 'status',
      label: '状态',
      componentProps: {
        options: statusOptions,
      },
      help: '停用行不出现在 App',
      rules: z.number().default(0),
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'kind',
      label: '类型',
      component: 'Select',
      componentProps: {
        placeholder: '请选择类型',
        allowClear: true,
        options: kindOptions,
      },
    },
    {
      fieldName: 'content',
      label: '内容',
      component: 'Input',
      componentProps: {
        placeholder: '请输入内容关键字',
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
      field: 'kind',
      title: '类型',
      minWidth: 100,
      formatter: ({ cellValue }) => kindMap[cellValue] ?? '-',
    },
    {
      field: 'title',
      title: '标题',
      minWidth: 140,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'content',
      title: '内容',
      minWidth: 240,
    },
    {
      field: 'category',
      title: '分类',
      minWidth: 100,
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
