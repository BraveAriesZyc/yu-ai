<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoveMoodOptionDefaultApi } from '#/api/love/moodOptionDefault';

import { Page, useVbenModal } from '@vben/common-ui';

import { message, Tag } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteLoveMoodOptionDefault,
  getLoveMoodOptionDefaultPage,
} from '#/api/love/moodOptionDefault';
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

/** 新增心情默认选项 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑心情默认选项 */
function handleEdit(row: LoveMoodOptionDefaultApi.MoodOptionDefault) {
  formModalApi.setData(row).open();
}

/** 删除心情默认选项 */
async function handleDelete(row: LoveMoodOptionDefaultApi.MoodOptionDefault) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.label]),
    duration: 0,
  });
  try {
    await deleteLoveMoodOptionDefault(row.id);
    message.success($t('ui.actionMessage.deleteSuccess', [row.label]));
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
          return await getLoveMoodOptionDefaultPage({
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
  } as VxeTableGridOptions<LoveMoodOptionDefaultApi.MoodOptionDefault>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="心情默认选项列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['心情默认选项']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['love:mood-option-default:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #color="{ row }">
        <div class="flex items-center gap-2">
          <span
            :style="{ backgroundColor: row.color }"
            class="inline-block h-3 w-3 rounded"
          ></span>
          {{ row.color }}
        </div>
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
              auth: ['love:mood-option-default:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['love:mood-option-default:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.label]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
