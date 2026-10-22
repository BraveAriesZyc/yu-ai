<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoveCheckinApi } from '#/api/love/checkin';

import { Page } from '@vben/common-ui';

import { message } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteLoveCheckin, getLoveCheckinPage } from '#/api/love/checkin';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 删除打卡记录（无标题字段，提示以编号标识） */
async function handleDelete(row: LoveCheckinApi.Checkin) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [`#${row.id}`]),
    duration: 0,
  });
  try {
    await deleteLoveCheckin(row.id);
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
          return await getLoveCheckinPage({
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
  } as VxeTableGridOptions<LoveCheckinApi.Checkin>,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="打卡列表">
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['love:checkin:delete'],
              popConfirm: {
                title: `${$t('ui.actionMessage.deleteConfirm', [`#${row.id}`])}删除后 App 端当日可重新打卡。`,
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
