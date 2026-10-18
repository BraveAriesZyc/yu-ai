import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getRangePickerDefaultProps } from '#/utils';

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'coupleId',
      label: '情侣关系编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入情侣关系编号',
        allowClear: true,
      },
    },
    {
      fieldName: 'ownerId',
      label: '创建人编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入创建人用户编号',
        allowClear: true,
      },
    },
    {
      fieldName: 'createTime',
      label: '创建时间',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        placeholder: ['开始日期', '结束日期'],
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
      minWidth: 100,
    },
    {
      field: 'coupleId',
      title: '情侣关系编号',
      minWidth: 120,
    },
    {
      field: 'ownerId',
      title: '创建人编号',
      minWidth: 110,
    },
    {
      field: 'title',
      title: '待办标题',
      minWidth: 200,
    },
    {
      field: 'done',
      title: '是否完成',
      minWidth: 100,
      formatter: ({ cellValue }) => (cellValue ? '已完成' : '未完成'),
    },
    {
      field: 'tag',
      title: '标签',
      minWidth: 100,
      formatter: ({ cellValue }) => cellValue || '-', // M18 空值兜底(待办可无标签)
    },
    {
      field: 'color',
      title: '标签颜色',
      minWidth: 120,
      slots: { default: 'color' },
    },
    {
      field: 'createTime',
      title: '创建时间',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 150,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
