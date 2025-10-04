<template>
  <FormButton
      v-bind="$attrs"
      color="secondary"
      @click.stop="openFileDialog"
      :disabled="disableClick"
  >
    <Icon class="v-flip" src="/svg/download.svg" />
    <input
        ref="fileInput"
        type="file"
        :accept="accept"
        multiple
        style="display: none"
        @change="handleFileChange"
    />
  </FormButton>
</template>

<script setup lang="ts">
import FormButton from "@components/form/FormButton.vue";
import Icon from "@components/Icon.vue";
import { ref } from "vue";

const props = defineProps({
  accept: {
    type: String,
    default: 'image/png,image/jpeg,image/jpg,image/webp'
  },
  disableClick: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['upload']);
const fileInput = ref<HTMLInputElement>();

const openFileDialog = () => {
  if (props.disableClick) return;
  fileInput.value?.click();
};

const handleFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement;
  if (!input.files || input.files.length === 0) return;

  // Convert FileList to array and emit
  const filesArray = Array.from(input.files);
  emit('upload', filesArray);

  input.value = ''; // Reset to allow selecting same files again
};

</script>