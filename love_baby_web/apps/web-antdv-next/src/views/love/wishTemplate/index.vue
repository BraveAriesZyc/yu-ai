<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoveWishTemplateApi } from '#/api/love/wishTemplate';

import { Page, useVbenModal } from '@vben/common-ui';

import { message, Tag } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteLoveWishTemplate,
  getLoveWishTemplatePage,
} from '#/api/love/wishTemplate';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 新增模板 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑模板 */
function handleEdit(row: LoveWishTemplateApi.WishTemplate) {
  formModalApi.setData(row).open();
}

/** 删除模板（历史消息快照式上送，不受影响） */
async function handleDelete(row: LoveWishTemplateApi.WishTemplate) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.title]),
    duration: 0,
  });
  try {
    await deleteLoveWishTemplate(row.id);
    message.success($t('ui.actionMessage.deleteSuccess', [row.title]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getLoveWishTemplatePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<LoveWishTemplateApi.WishTemplate>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="心愿模板列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['心愿模板']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['love:wish-template:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #colorStart="{ row }">
        <span class="flex items-center gap-2">
          <span
            class="inline-block size-4 shrink-0 rounded-full border"
            :style="{ backgroundColor: row.colorStart }"
          ></span>
          {{ row.colorStart }}
        </span>
      </template>
      <template #colorEnd="{ row }">
        <span class="flex items-center gap-2">
          <span
            class="inline-block size-4 shrink-0 rounded-full border"
            :style="{ backgroundColor: row.colorEnd }"
          ></span>
          {{ row.colorEnd }}
        </span>
      </template>
      <template #status="{ row }">
        <Tag :color="row.status === 0 ? 'success' : undefined">
          {{ row.status === 0 ? '启用' : '停用' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['love:wish-template:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['love:wish-template:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.title]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
