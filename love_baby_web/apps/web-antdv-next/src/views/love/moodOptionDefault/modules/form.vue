<script lang="ts" setup>
import type { LoveMoodOptionDefaultApi } from '#/api/love/moodOptionDefault';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import {
  createLoveMoodOptionDefault,
  updateLoveMoodOptionDefault,
} from '#/api/love/moodOptionDefault';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<LoveMoodOptionDefaultApi.MoodOptionDefault>();
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['心情默认选项'])
    : $t('ui.actionTitle.create', ['心情默认选项']);
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
    const data =
      (await formApi.getValues()) as LoveMoodOptionDefaultApi.UpdateParams;
    try {
      await (formData.value?.id
        ? updateLoveMoodOptionDefault(data)
        : createLoveMoodOptionDefault(data));
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
    const data =
      modalApi.getData() as LoveMoodOptionDefaultApi.MoodOptionDefault;
    if (!data || !data.id) {
      return;
    }
    formData.value = data;
    await formApi.setValues({
      id: data.id,
      label: data.label,
      emoji: data.emoji,
      color: data.color,
      sort: data.sort,
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
