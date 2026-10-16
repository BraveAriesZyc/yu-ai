<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoveCoupleApi } from '#/api/love/couple';

import { Page } from '@vben/common-ui';

import { message } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getLoveCouplePage, unbindLoveCouple } from '#/api/love/couple';

import { useGridColumns, useGridFormSchema } from './data';

/** 行展示文案：优先双方昵称（空串同样回退），已删用户回退为用户编号 */
function getRowLabel(row: LoveCoupleApi.Couple): string {
  return `${row.userANickname || row.userAId} 与 ${row.userBNickname || row.userBId}`;
}

/** 强制解绑情侣关系（物理删除关系行，不可恢复） */
async function handleUnbind(row: LoveCoupleApi.Couple) {
  const label = getRowLabel(row);
  const hideLoading = message.loading({
    content: `正在强制解绑 ${label} 的情侣关系 ...`,
    duration: 0,
  });
  try {
    await unbindLoveCouple(row.id);
    message.success(`${label} 的情侣关系已强制解绑`);
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
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
          return await getLoveCouplePage({
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
  } as VxeTableGridOptions<LoveCoupleApi.Couple>,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="情侣关系列表">
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '强制解绑',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['love:couple:update'],
              popConfirm: {
                title: `确定要强制解绑「${getRowLabel(row)}」的情侣关系吗？解绑后双方将恢复未绑定状态，且不可恢复。`,
                confirm: handleUnbind.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
