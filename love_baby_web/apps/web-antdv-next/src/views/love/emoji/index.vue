<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoveEmojiApi } from '#/api/love/emoji';

import { Page, useVbenModal } from '@vben/common-ui';

import { message, Tag } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteLoveEmoji, getLoveEmojiPage } from '#/api/love/emoji';
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

/** 新增表情 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑表情 */
function handleEdit(row: LoveEmojiApi.Emoji) {
  formModalApi.setData(row).open();
}

/** 删除表情 */
async function handleDelete(row: LoveEmojiApi.Emoji) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.emoji]),
    duration: 0,
  });
  try {
    await deleteLoveEmoji(row.id);
    message.success($t('ui.actionMessage.deleteSuccess', [row.emoji]));
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
          return await getLoveEmojiPage({
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
  } as VxeTableGridOptions<LoveEmojiApi.Emoji>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="表情包列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['表情']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['love:emoji:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #emoji="{ row }">
        <!-- 大字号直显 emoji 字符，所见即 App 所得 -->
        <span style="font-size: 22px; line-height: 1">{{ row.emoji }}</span>
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
              auth: ['love:emoji:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['love:emoji:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.emoji]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
