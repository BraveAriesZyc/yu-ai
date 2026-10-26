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
      component: 'Textarea',
      fieldName: 'content',
      label: '题目内容',
      componentProps: {
        placeholder: '如：最近一次让你觉得「有 TA 真好」的瞬间是什么？',
        maxLength: 200,
        rows: 3,
        showCount: true,
      },
      rules: z.string().min(1, '题目内容不能为空').max(200),
    },
    {
      component: 'Input',
      fieldName: 'category',
      label: '分类',
      componentProps: {
        placeholder: '请输入分类',
        maxLength: 32,
      },
      help: '自由填（情感/日常/回忆/脑洞/未来…）；App 端原样展示，可空',
    },
    {
      component: 'RadioGroup',
      fieldName: 'status',
      label: '状态',
      componentProps: {
        options: statusOptions,
      },
      help: '停用行退出轮换池；已出过题的天走 day 快照，不受删/停影响',
      rules: z.number().default(0),
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'content',
      label: '题目内容',
      component: 'Input',
      componentProps: {
        placeholder: '请输入题目内容关键字',
        allowClear: true,
      },
    },
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
      field: 'content',
      title: '题目内容',
      minWidth: 260,
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
