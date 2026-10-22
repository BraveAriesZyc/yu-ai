<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoveWishApi } from '#/api/love/wish';

import { Page } from '@vben/common-ui';

import { message, Tag } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteLoveWish, getLoveWishPage } from '#/api/love/wish';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 删除心愿（admin 兜底不受「仅创建者可删」限制；App 端传播走全量覆盖） */
async function handleDelete(row: LoveWishApi.Wish) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [`#${row.id}`]),
    duration: 0,
  });
  try {
    await deleteLoveWish(row.id);
    message.success($t('ui.actionMessage.deleteSuccess', [`#${row.id}`]));
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
          return await getLoveWishPage({
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
  } as VxeTableGridOptions<LoveWishApi.Wish>,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="心愿列表">
      <!-- done 本地映射（全域无字典先例，勿引 DICT_TYPE） -->
      <template #done="{ row }">
        <Tag :color="row.done ? 'success' : 'default'">
          {{ row.done ? '已完成' : '进行中' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['love:wish:delete'],
              popConfirm: {
                title: `${$t('ui.actionMessage.deleteConfirm', [`#${row.id}`])}删除后 App 端不再展示（下次拉取整体覆盖）。`,
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
