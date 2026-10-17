import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import dayjs from 'dayjs';

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

/** 列表的字段（done 用本地双色 tag，全域无字典先例、勿引 DICT_TYPE；doneBy/doneTime 空值 '-'） */
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
      title: '创建人编号',
      minWidth: 110,
    },
    {
      field: 'title',
      title: '心愿标题',
      minWidth: 200,
    },
    {
      field: 'done',
      title: '状态',
      minWidth: 100,
      slots: { default: 'done' },
    },
    {
      field: 'doneBy',
      title: '完成人编号',
      minWidth: 110,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'doneTime',
      title: '完成时间',
      minWidth: 180,
      // 可空（未完成为 null），不能走 'formatDateTime' 字符串 formatter（null 会渲染 Invalid Date）
      formatter: ({ cellValue }) =>
        cellValue ? dayjs(cellValue).format('YYYY-MM-DD HH:mm:ss') : '-',
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
