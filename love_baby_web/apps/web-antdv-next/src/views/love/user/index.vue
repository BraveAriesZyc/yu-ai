<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoveUserApi } from '#/api/love/user';

import { Page, useVbenModal } from '@vben/common-ui';

import { Image, message, Tag } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteLoveUser, getLoveUserPage } from '#/api/love/user';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 行展示名：优先昵称，其次手机号，兜底编号 */
function rowName(row: LoveUserApi.User) {
  return row.nickname || row.mobile || `#${row.id}`;
}

/** 删除确认文案：写明级联后果（逻辑删除仅界面不可见、库内留档；解绑与手机号改写不可恢复） */
function deleteConfirmText(row: LoveUserApi.User) {
  return (
    `删除用户「${rowName(row)}」将：逻辑删除该用户及其名下待办、心情、悄悄话` +
    `（含对方与其互发的消息，界面不再可见、仅数据库留档可追溯），` +
    `并物理解绑其情侣关系、改写其手机号（此二者不可恢复）。确认删除吗？`
  );
}

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 编辑 C 端用户 */
function handleEdit(row: LoveUserApi.User) {
  formModalApi.setData(row).open();
}

/** 删除 C 端用户 */
async function handleDelete(row: LoveUserApi.User) {
  const name = rowName(row);
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [name]),
    duration: 0,
  });
  try {
    await deleteLoveUser(row.id as number);
    message.success($t('ui.actionMessage.deleteSuccess', [name]));
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
          return await getLoveUserPage({
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
  } as VxeTableGridOptions<LoveUserApi.User>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="C 端用户列表">
      <template #avatar="{ row }">
        <Image v-if="row.avatar" :src="row.avatar" :width="32" :height="32" />
      </template>
      <template #status="{ row }">
        <Tag :color="row.status === 0 ? 'success' : 'error'">
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
              auth: ['love:user:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['love:user:delete'],
              popConfirm: {
                title: deleteConfirmText(row),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
