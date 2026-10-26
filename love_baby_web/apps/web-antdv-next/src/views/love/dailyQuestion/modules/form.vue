<script lang="ts" setup>
import type { LoveDailyQuestionApi } from '#/api/love/dailyQuestion';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import {
  createLoveDailyQuestion,
  updateLoveDailyQuestion,
} from '#/api/love/dailyQuestion';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<LoveDailyQuestionApi.DailyQuestion>();
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['每日一问题目'])
    : $t('ui.actionTitle.create', ['每日一问题目']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 80,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单（新增/编辑复用同一弹窗，有 id 走更新、无 id 走新增）
    const data = (await formApi.getValues()) as
      LoveDailyQuestionApi.UpdateParams;
    try {
      await (formData.value?.id
        ? updateLoveDailyQuestion(data)
        : createLoveDailyQuestion(data));
      // 关闭并提示
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = undefined;
      return;
    }
    // 本模块无详情接口，直接用列表行数据回填（仅取可编辑字段）
    const data = modalApi.getData() as LoveDailyQuestionApi.DailyQuestion;
    if (!data || !data.id) {
      return;
    }
    formData.value = data;
    await formApi.setValues({
      id: data.id,
      content: data.content,
      category: data.category ?? '',
      status: data.status,
    });
  },
});
</script>

<template>
  <Modal class="w-2/5" :title="getTitle">
    <Form class="mx-4" />
  </Modal>
</template>
