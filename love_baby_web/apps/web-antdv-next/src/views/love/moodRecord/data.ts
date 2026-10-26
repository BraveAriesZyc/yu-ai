import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getRangePickerDefaultProps } from '#/utils';

/** 列表的搜索表单（coupleId/userId 精确 + moodLabel 模糊 + moodDate 区间，与后端 PageReqVO 口径一致） */
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
      fieldName: 'moodLabel',
      label: '心情名',
      component: 'Input',
      componentProps: {
        placeholder: '请输入心情名关键字',
        allowClear: true,
      },
    },
    {
      // 后端 moodDate 是 LocalDate[]（@DateTimeFormat yyyy-MM-dd，DATE 列按日筛口径），
      // 覆写 valueFormat 为纯日期（moment 页 createTime 同款修法，M31 页新建即带）
      fieldName: 'moodDate',
      label: '心情日期',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        format: 'YYYY-MM-DD',
        valueFormat: 'YYYY-MM-DD',
        showTime: false,
        placeholder: ['开始日期', '结束日期'],
      },
    },
  ];
}

/** 列表的字段（心情列 = emoji 彩底小方块 + 名称快照，App 端月历同款视觉） */
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
      field: 'moodLabel',
      title: '心情',
      minWidth: 140,
      slots: { default: 'mood' },
    },
    {
      field: 'moodDate',
      title: '心情日期',
      minWidth: 120,
      formatter: ({ cellValue }) => cellValue || '-',
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
