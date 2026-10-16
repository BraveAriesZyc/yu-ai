import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getRangePickerDefaultProps } from '#/utils';

/** 帐号状态选项（CommonStatusEnum：0=启用 / 1=停用），本期零字典，本地映射 */
const statusOptions = [
  { label: '启用', value: 0 },
  { label: '停用', value: 1 },
];

/** 编辑的表单（本页仅编辑，无新增） */
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
      fieldName: 'nickname',
      label: '用户昵称',
      componentProps: {
        placeholder: '请输入用户昵称',
        maxLength: 32,
      },
    },
    {
      component: 'RadioGroup',
      fieldName: 'status',
      label: '帐号状态',
      componentProps: {
        options: statusOptions,
      },
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'mobile',
      label: '手机号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入手机号',
        allowClear: true,
      },
    },
    {
      fieldName: 'nickname',
      label: '用户昵称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入用户昵称',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '帐号状态',
      component: 'Select',
      componentProps: {
        placeholder: '请选择帐号状态',
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
      minWidth: 80,
    },
    {
      field: 'mobile',
      title: '手机号',
      minWidth: 130,
    },
    {
      field: 'nickname',
      title: '昵称',
      minWidth: 120,
    },
    {
      field: 'avatar',
      title: '头像',
      minWidth: 80,
      slots: { default: 'avatar' },
    },
    {
      field: 'status',
      title: '状态',
      minWidth: 90,
      slots: { default: 'status' },
    },
    {
      field: 'lastLoginIp',
      title: '最后登录 IP',
      minWidth: 140,
    },
    {
      field: 'lastLoginTime',
      title: '最后登录时间',
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
      width: 130,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
