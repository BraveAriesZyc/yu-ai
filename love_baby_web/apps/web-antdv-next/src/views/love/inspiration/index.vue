<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoveInspirationApi } from '#/api/love/inspiration';

import { Page, useVbenModal } from '@vben/common-ui';

import { message, Tag } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteLoveInspiration,
  getLoveInspirationPage,
} from '#/api/love/inspiration';
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

/** 新增灵感 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑灵感 */
function handleEdit(row: LoveInspirationApi.Inspiration) {
  formModalApi.setData(row).open();
}

/** 删除灵感 */
async function handleDelete(row: LoveInspirationApi.Inspiration) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.title || row.content]),
    duration: 0,
  });
  try {
    await deleteLoveInspiration(row.id);
    message.success(
      $t('ui.actionMessage.deleteSuccess', [row.title || row.content]),
    );
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
          return await getLoveInspirationPage({
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
  } as VxeTableGridOptions<LoveInspirationApi.Inspiration>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="恋爱灵感列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['恋爱灵感']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['love:inspiration:create'],
              onClick: handleCreate,
            },
          ]"
        />
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
              auth: ['love:inspiration:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['love:inspiration:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [
                  row.title || row.content,
                ]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
