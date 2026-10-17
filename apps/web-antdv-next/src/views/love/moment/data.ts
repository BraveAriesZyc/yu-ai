import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getRangePickerDefaultProps } from '#/utils';

/** 列表的搜索表单（coupleId + userId + createTime 三项，与后端 PageReqVO 冻结口径一致） */
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
      fieldName: 'userId',
      label: '用户编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入用户编号',
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

/** 列表的字段（imageUrl 走缩略图 slot，点击可放大预览） */
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
      field: 'userId',
      title: '用户编号',
      minWidth: 110,
    },
    {
      field: 'imageUrl',
      title: '图片',
      minWidth: 100,
      slots: { default: 'imageUrl' },
    },
    {
      field: 'text',
      title: '配文',
      minWidth: 160,
    },
    {
      field: 'createTime',
      title: '发布时间',
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
