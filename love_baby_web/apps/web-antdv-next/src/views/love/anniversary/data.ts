import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getRangePickerDefaultProps } from '#/utils';

/** 重复类型展示映射（1=每年 2=一次性；零字典，§6.6 与后端 tinyint 值域对齐） */
const REPEAT_TYPE_LABELS: Record<number, string> = {
  1: '每年',
  2: '一次性',
};

/** 列表的搜索表单（仅 title + createTime 两项，与后端 PageReqVO 冻结口径一致） */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'title',
      label: '纪念日名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入纪念日名称',
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
      title: '纪念日名称',
      minWidth: 180,
    },
    {
      field: 'emoji',
      title: '图标',
      minWidth: 80,
      formatter: ({ cellValue }) => cellValue || '-', // M18 空值兜底
    },
    {
      field: 'annivDate',
      title: '纪念日日期',
      minWidth: 120,
    },
    {
      field: 'repeatType',
      title: '重复类型',
      minWidth: 100,
      formatter: ({ cellValue }) =>
        REPEAT_TYPE_LABELS[cellValue as number] ?? '未知',
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
