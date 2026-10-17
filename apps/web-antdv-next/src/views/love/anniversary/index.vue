<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoveAnniversaryApi } from '#/api/love/anniversary';

import { Page } from '@vben/common-ui';

import { message } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteLoveAnniversary,
  getLoveAnniversaryPage,
} from '#/api/love/anniversary';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 删除纪念日 */
async function handleDelete(row: LoveAnniversaryApi.Anniversary) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.title]),
    duration: 0,
  });
  try {
    await deleteLoveAnniversary(row.id);
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
          return await getLoveAnniversaryPage({
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
  } as VxeTableGridOptions<LoveAnniversaryApi.Anniversary>,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="纪念日列表">
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['love:anniversary:delete'],
              popConfirm: {
                title: `${$t('ui.actionMessage.deleteConfirm', [row.title])}删除后不可恢复。`,
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
