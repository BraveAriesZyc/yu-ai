import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { z } from '#/adapter/form';
import { getRangePickerDefaultProps } from '#/utils';

/**
 * 图标选项 — 与 shared AppIcons.kt 的 AppIconType 枚举 name 全集对齐，
 * 新增枚举须同步此表（端上 appIconFromName 未知名兜底 Heart）
 */
const APP_ICON_OPTIONS = [
  // 业务装饰（心愿卡常用，排前）
  { label: 'Star（星星）', value: 'Star' },
  { label: 'Heart（爱心）', value: 'Heart' },
  { label: 'Bell（铃铛）', value: 'Bell' },
  { label: 'Moon（月亮）', value: 'Moon' },
  { label: 'Sun（太阳）', value: 'Sun' },
  // 其余枚举全集
  { label: 'Home（首页）', value: 'Home' },
  { label: 'Todo（待办）', value: 'Todo' },
  { label: 'Shop（商店）', value: 'Shop' },
  { label: 'Mine（我的）', value: 'Mine' },
  { label: 'ChevronRight（右箭头）', value: 'ChevronRight' },
  { label: 'ChevronLeft（左箭头）', value: 'ChevronLeft' },
  { label: 'Plus（加号）', value: 'Plus' },
  { label: 'Search（搜索）', value: 'Search' },
  { label: 'Close（关闭）', value: 'Close' },
  { label: 'Check（对勾）', value: 'Check' },
  { label: 'Chat（聊天）', value: 'Chat' },
  { label: 'Send（发送）', value: 'Send' },
  { label: 'Image（图片）', value: 'Image' },
  { label: 'Pin（图钉）', value: 'Pin' },
  { label: 'Wallet（钱包）', value: 'Wallet' },
  { label: 'Service（客服）', value: 'Service' },
  { label: 'Info（信息）', value: 'Info' },
  { label: 'Components（组件）', value: 'Components' },
  { label: 'Flag（旗帜）', value: 'Flag' },
  { label: 'GenderFemale（女）', value: 'GenderFemale' },
  { label: 'GenderMale（男）', value: 'GenderMale' },
  { label: 'Emoji（表情）', value: 'Emoji' },
  { label: 'Play（播放）', value: 'Play' },
  { label: 'Pause（暂停）', value: 'Pause' },
  { label: 'Mic（麦克风）', value: 'Mic' },
  { label: 'Keyboard（键盘）', value: 'Keyboard' },
  { label: 'Camera（相机）', value: 'Camera' },
  { label: 'Clock（时钟）', value: 'Clock' },
];

/** 状态选项（CommonStatusEnum：0=启用 / 1=停用），本地映射 */
const statusOptions = [
  { label: '启用', value: 0 },
  { label: '停用', value: 1 },
];

/** hex 色值校验（与服务端 @Pattern 同款正则） */
const hexColorRule = z
  .string()
  .regex(/^#[0-9A-Fa-f]{6}$/, '格式须为 #RRGGBB（如 #FF8FA3）');

/** 新增/修改的表单 */
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
      fieldName: 'title',
      label: '标题',
      componentProps: {
        placeholder: '气泡主文案，如：求亲亲',
        maxLength: 32,
      },
      rules: z.string().min(1, '标题不能为空').max(32),
    },
    {
      component: 'Input',
      fieldName: 'subtitle',
      label: '副标题',
      componentProps: {
        placeholder: '卡片第二行，可空（如：mua 一口就跑）',
        maxLength: 64,
      },
    },
    {
      component: 'Input',
      fieldName: 'category',
      label: '分类',
      componentProps: {
        placeholder: '请输入分类',
        maxLength: 20,
      },
      help: '自由填（建议: 想亲亲/想抱抱/想约会）；App 端按此聚合 chips，空串归「其他」',
    },
    {
      component: 'Input',
      fieldName: 'colorStart',
      label: '渐变起色',
      componentProps: {
        placeholder: '#FF8FA3',
        maxLength: 7,
      },
      formItemClass: 'col-span-1',
      rules: hexColorRule,
    },
    {
      component: 'Input',
      fieldName: 'colorEnd',
      label: '渐变止色',
      componentProps: {
        placeholder: '#FF5A8C',
        maxLength: 7,
      },
      formItemClass: 'col-span-1',
      rules: hexColorRule,
    },
    {
      component: 'Select',
      fieldName: 'icon',
      label: '图标',
      componentProps: {
        placeholder: '请选择图标',
        options: APP_ICON_OPTIONS,
        showSearch: true,
      },
      help: '端上 AppIconType 枚举名，未知名兜底 Heart',
      rules: z.string().min(1, '请选择图标'),
    },
    {
      component: 'InputNumber',
      fieldName: 'sort',
      label: '排序',
      componentProps: {
        placeholder: '越小越靠前',
        min: 0,
        precision: 0,
      },
      help: 'App 端 sort asc, id asc 稳定序',
      rules: z.number({ message: '请输入排序' }).default(0),
    },
    {
      component: 'RadioGroup',
      fieldName: 'status',
      label: '状态',
      componentProps: {
        options: statusOptions,
      },
      help: '停用行不下发 App；删/停不影响历史消息（kind=2 快照式上送）',
      rules: z.number().default(0),
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'title',
      label: '标题',
      component: 'Input',
      componentProps: {
        placeholder: '请输入标题关键字',
        allowClear: true,
      },
    },
    {
      fieldName: 'category',
      label: '分类',
      component: 'Input',
      componentProps: {
        placeholder: '请输入分类关键字',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        placeholder: '请选择状态',
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
        placeholder: ['开始时间', '结束时间'],
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
      field: 'title',
      title: '标题',
      minWidth: 130,
    },
    {
      field: 'subtitle',
      title: '副标题',
      minWidth: 150,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'category',
      title: '分类',
      minWidth: 100,
      formatter: ({ cellValue }) => cellValue || '其他',
    },
    {
      field: 'colorStart',
      title: '渐变起色',
      minWidth: 130,
      slots: { default: 'colorStart' },
    },
    {
      field: 'colorEnd',
      title: '渐变止色',
      minWidth: 130,
      slots: { default: 'colorEnd' },
    },
    {
      field: 'icon',
      title: '图标',
      minWidth: 100,
    },
    {
      field: 'sort',
      title: '排序',
      minWidth: 80,
    },
    {
      field: 'status',
      title: '状态',
      minWidth: 90,
      slots: { default: 'status' },
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
