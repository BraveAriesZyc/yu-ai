<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoveMomentApi } from '#/api/love/moment';

import { Page } from '@vben/common-ui';

import { Image, message } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteLoveMoment, getLoveMomentPage } from '#/api/love/moment';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 删除瞬间记录（无标题字段，提示以编号标识） */
async function handleDelete(row: LoveMomentApi.Moment) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [`#${row.id}`]),
    duration: 0,
  });
  try {
    await deleteLoveMoment(row.id);
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
          return await getLoveMomentPage({
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
  } as VxeTableGridOptions<LoveMomentApi.Moment>,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="瞬间列表">
      <template #imageUrl="{ row }">
        <Image
          v-if="row.imageUrl"
          :src="row.imageUrl"
          :width="60"
          :height="60"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['love:moment:delete'],
              popConfirm: {
                title: `${$t('ui.actionMessage.deleteConfirm', [`#${row.id}`])}删除后 App 端不可见（图片文件留档不删）。`,
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
