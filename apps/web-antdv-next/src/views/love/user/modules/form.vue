<script lang="ts" setup>
import type { LoveUserApi } from '#/api/love/user';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import { updateLoveUser } from '#/api/love/user';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<LoveUserApi.User>();
/** 本页仅编辑（C 端用户无新增入口），标题固定 */
const getTitle = $t('ui.actionTitle.edit', ['用户']);

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
    // 提交表单（昵称/状态可选，服务端仅更新提交的非空字段）
    const data = (await formApi.getValues()) as LoveUserApi.UpdateParams;
    try {
      await updateLoveUser(data);
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
    // 本模块无详情接口，直接用列表行数据回填（仅取可编辑字段，头像不做编辑）
    const data = modalApi.getData() as LoveUserApi.User;
    if (!data || !data.id) {
      return;
    }
    formData.value = data;
    await formApi.setValues({
      id: data.id,
      nickname: data.nickname,
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
