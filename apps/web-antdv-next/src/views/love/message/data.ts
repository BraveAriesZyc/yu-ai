import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getRangePickerDefaultProps } from '#/utils';

/** 消息类型映射（本地枚举语义，零字典） */
const MESSAGE_KIND_MAP: Record<number, string> = {
  1: '文本悄悄话',
  2: '心愿消息',
};

/** 审核状态映射（本地枚举语义，零字典） */
const AUDIT_STATUS_MAP: Record<number, string> = {
  0: '待审',
  1: '通过',
  2: '命中',
};

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
      fieldName: 'kind',
      label: '消息类型',
      component: 'Select',
      componentProps: {
        placeholder: '请选择消息类型',
        allowClear: true,
        options: [
          { label: '文本悄悄话', value: 1 },
          { label: '心愿消息', value: 2 },
        ],
      },
    },
    {
      fieldName: 'content',
      label: '内容关键字',
      component: 'Input',
      componentProps: {
        placeholder: '请输入内容关键字',
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
      minWidth: 80,
    },
    {
      field: 'coupleId',
      title: '情侣关系编号',
      minWidth: 110,
    },
    {
      field: 'senderId',
      title: '发送方用户编号',
      minWidth: 120,
    },
    {
      field: 'receiverId',
      title: '接收方用户编号',
      minWidth: 120,
    },
    {
      field: 'kind',
      title: '消息类型',
      minWidth: 100,
      formatter: ({ cellValue }) => MESSAGE_KIND_MAP[cellValue] ?? '-',
    },
    {
      field: 'content',
      title: '内容',
      minWidth: 200,
    },
    {
      field: 'wishKey',
      title: '心愿 key 快照',
      minWidth: 120,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'color',
      title: '心愿颜色快照',
      minWidth: 110,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'icon',
      title: '心愿图标快照',
      minWidth: 110,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'auditStatus',
      title: '审核状态',
      minWidth: 100,
      formatter: ({ cellValue }) => AUDIT_STATUS_MAP[cellValue] ?? '-',
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
