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
      // M18 修复:服务端 createTime 是 LocalDate[](@DateTimeFormat yyyy-MM-dd,按日筛口径),
      // 默认 valueFormat 'YYYY-MM-DD HH:mm:ss' 必 400——覆写为纯日期,label 随之改「创建日期」;
      // showTime: false 关掉默认带出的时间选择列(纯日期口径下是零影响的死控件)
      fieldName: 'createTime',
      label: '创建日期',
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
      // M18 补透出(M15 多图):imageUrls 为 JSON 数组文本,显示张数;解析失败按无多图兜底
      field: 'imageUrls',
      title: '多图',
      minWidth: 90,
      formatter: ({ cellValue }) => {
        if (!cellValue) return '-';
        try {
          const n = (JSON.parse(cellValue) as string[]).length;
          return n > 1 ? `${n} 张` : '-';
        } catch {
          return '-';
        }
      },
    },
    {
      field: 'text',
      title: '配文',
      minWidth: 160,
      formatter: ({ cellValue }) => cellValue || '-', // M18 空值兜底(瞬间可无配文)
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
