<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoveMessageApi } from '#/api/love/message';

import { Page } from '@vben/common-ui';

import { Image, message } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteLoveMessage, getLoveMessagePage } from '#/api/love/message';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 删除悄悄话消息 */
async function handleDelete(row: LoveMessageApi.Message) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.id]),
    duration: 0,
  });
  try {
    await deleteLoveMessage(row.id);
    message.success($t('ui.actionMessage.deleteSuccess', [row.id]));
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
          return await getLoveMessagePage({
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
  } as VxeTableGridOptions<LoveMessageApi.Message>,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="悄悄话消息列表">
      <!-- kind=3 图片消息显缩略图（antdv-next Image 自带点击放大），其余文本原样 -->
      <template #content="{ row }">
        <Image
          v-if="row.kind === 3 && row.content"
          :src="row.content"
          :width="40"
          :height="40"
          style="object-fit: cover; border-radius: 4px"
        />
        <span v-else>{{ row.content }}</span>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['love:message:delete'],
              popConfirm: {
                title: `删除后不可恢复（端上已拉取的消息将残留），确定删除编号为 ${row.id} 的消息吗？`,
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
