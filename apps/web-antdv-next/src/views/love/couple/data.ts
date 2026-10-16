import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getRangePickerDefaultProps } from '#/utils';

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'userANickname',
      label: '发起方昵称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入发起方昵称',
        allowClear: true,
      },
    },
    {
      fieldName: 'userBNickname',
      label: '受邀方昵称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入受邀方昵称',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '关系状态',
      component: 'Select',
      componentProps: {
        placeholder: '请选择关系状态',
        allowClear: true,
        options: [{ label: '正常', value: 0 }],
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
      title: '关系编号',
      minWidth: 90,
    },
    {
      field: 'userAId',
      title: '发起方用户编号',
      minWidth: 120,
    },
    {
      field: 'userANickname',
      title: '发起方昵称',
      minWidth: 120,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'userBId',
      title: '受邀方用户编号',
      minWidth: 120,
    },
    {
      field: 'userBNickname',
      title: '受邀方昵称',
      minWidth: 120,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'status',
      title: '关系状态',
      minWidth: 90,
      formatter: ({ cellValue }) =>
        cellValue === 0 ? '正常' : `未知（${cellValue}）`,
    },
    {
      field: 'bindTime',
      title: '绑定时间',
      minWidth: 180,
      formatter: 'formatDateTime',
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
