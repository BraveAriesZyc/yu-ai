<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoveMoodRecordApi } from '#/api/love/moodRecord';

import { Page } from '@vben/common-ui';

import { message } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteLoveMoodRecord,
  getLoveMoodRecordPage,
} from '#/api/love/moodRecord';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 删除心情记录（逻辑删除；删除后 App 端月历该日消失） */
async function handleDelete(row: LoveMoodRecordApi.MoodRecord) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [`#${row.id}`]),
    duration: 0,
  });
  try {
    await deleteLoveMoodRecord(row.id);
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
          return await getLoveMoodRecordPage({
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
  } as VxeTableGridOptions<LoveMoodRecordApi.MoodRecord>,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="心情记录列表">
      <template #mood="{ row }">
        <div class="flex items-center gap-2">
          <span
            :style="{
              backgroundColor: row.color
                ? `${row.color}1F`
                : 'rgba(0, 0, 0, 0.06)',
            }"
            class="inline-block h-6 w-6 rounded-md text-center leading-6"
          >
            {{ row.emoji || row.moodLabel?.slice(0, 1) }}
          </span>
          {{ row.moodLabel || '-' }}
        </div>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['love:mood-record:delete'],
              popConfirm: {
                title: `${$t('ui.actionMessage.deleteConfirm', [`#${row.id}`])}删除后 App 端月历该日不再显示。`,
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
