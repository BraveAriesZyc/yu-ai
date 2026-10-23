import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { z } from '#/adapter/form';
import { getRangePickerDefaultProps } from '#/utils';

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
      component: 'Input',
      fieldName: 'emoji',
      label: '表情',
      componentProps: {
        placeholder: '请输入 emoji 字符（点选即插入聊天输入框）',
        maxLength: 16,
      },
      rules: z.string().min(1, '表情不能为空').max(16),
    },
    {
      component: 'Input',
      fieldName: 'category',
      label: '分类',
      componentProps: {
        placeholder: '请输入分类',
        maxLength: 20,
      },
      help: '自由填（建议: 爱意/情绪/手势/庆祝）；App 端按此聚合 tab，空串归「常用」',
    },
    {
      component: 'InputNumber',
      fieldName: 'sort',
      label: '排序',
      componentProps: {
        placeholder: '越小越靠前',
        min: 0,
        precision: 0,
      },
      help: 'App 端 sort asc, id asc 稳定序',
      rules: z.number({ message: '请输入排序' }).default(0),
    },
    {
      component: 'RadioGroup',
      fieldName: 'status',
      label: '状态',
      componentProps: {
        options: statusOptions,
      },
      help: '停用行不下发 App',
      rules: z.number().default(0),
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'category',
      label: '分类',
      component: 'Input',
      componentProps: {
        placeholder: '请输入分类关键字',
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
      field: 'emoji',
      title: '表情',
      minWidth: 100,
      slots: { default: 'emoji' },
    },
    {
      field: 'category',
      title: '分类',
      minWidth: 120,
      formatter: ({ cellValue }) => cellValue || '常用',
    },
    {
      field: 'sort',
      title: '排序',
      minWidth: 80,
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
