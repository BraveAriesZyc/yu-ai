import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getRangePickerDefaultProps } from '#/utils';

/** 列表的搜索表单（仅 userId + checkinDate 两项，与后端 PageReqVO 冻结口径一致） */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
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
      fieldName: 'checkinDate',
      label: '打卡日期',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        // M18 修复:服务端 checkinDate 是 LocalDate[](@DateTimeFormat yyyy-MM-dd),
        // 默认 valueFormat 'YYYY-MM-DD HH:mm:ss' 必 400——覆写为纯日期(仓内 fms/closing 先例);
        // showTime: false 关掉默认带出的时间选择列(纯日期口径下是零影响的死控件)
        format: 'YYYY-MM-DD',
        valueFormat: 'YYYY-MM-DD',
        showTime: false,
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
      field: 'userId',
      title: '用户编号',
      minWidth: 110,
    },
    {
      field: 'checkinDate',
      title: '打卡日期',
      minWidth: 120,
    },
    {
      field: 'createTime',
      title: '打卡时间',
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
