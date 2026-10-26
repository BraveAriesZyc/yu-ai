<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoveDailyQuestionApi } from '#/api/love/dailyQuestion';

import { Page, useVbenModal } from '@vben/common-ui';

import { message, Tag } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteLoveDailyQuestion,
  getLoveDailyQuestionPage,
} from '#/api/love/dailyQuestion';
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

/** 新增题目 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑题目 */
function handleEdit(row: LoveDailyQuestionApi.DailyQuestion) {
  formModalApi.setData(row).open();
}

/** 删除题目（已出过题的天走 day 快照，App 历史不受影响） */
async function handleDelete(row: LoveDailyQuestionApi.DailyQuestion) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.content]),
    duration: 0,
  });
  try {
    await deleteLoveDailyQuestion(row.id);
    message.success($t('ui.actionMessage.deleteSuccess', [row.content]));
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
          return await getLoveDailyQuestionPage({
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
  } as VxeTableGridOptions<LoveDailyQuestionApi.DailyQuestion>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="每日一问题目列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['每日一问题目']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['love:daily-question:create'],
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
              auth: ['love:daily-question:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['love:daily-question:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.content]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
