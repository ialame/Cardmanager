<template>
  <div v-if="!error || showOnError" class="extracted-image me-2 mb-2">
    <span v-if="width > 0 && height > 0" class="image-size">
      {{ width }}x{{ height }}
    </span>

    <img
        :src="image.base64Image ? `data:image/png;base64,${image.base64Image}` : image.url"
        @load="onLoad"
        @error="onError"
    />

    <span v-if="error" class="extracted-image-error">&nbsp;</span>

    <FormButton
        v-if="image.source === 'Upload'"
        class="round-btn-sm image-delete-button"
        @click.stop="$emit('delete')"
    >
      <Icon class="icon-24" name="trash-outline" />
    </FormButton>

    <FormButton
        color="secondary"
        class="round-btn-sm image-info-button"
        @click.stop="openModal"
    >
      <Icon class="icon-24" name="search-outline" />
    </FormButton>

    <Modal ref="modal" size="xxl">
      <div class="modal-container" @click.stop>
        <div class="modal-image-container" @wheel.prevent="onWheel">
          <img
              ref="modalImage"
              :src="image.base64Image ? `data:image/png;base64,${image.base64Image}` : image.url"
              @load="(e) => onLoad(e.target as HTMLImageElement)"
              @error="onError"
              :style="{ transform: `scale(${zoom})` }"
          />
          <span v-if="error" class="image-error">Image not available</span>
        </div>

        <div class="modal-info-container">
          <div class="info-row"><strong>Taille:</strong> {{ width }}x{{ height }}</div>
          <div class="info-row"><strong>Langue:</strong> <Flag :lang="image.localization" /></div>
          <div class="info-row"><strong>Source:</strong> {{ image.source }}</div>

          <div v-if="showUrls">
            <div class="info-row">
              <strong>Image URL:</strong>
              <a :href="image.url" target="_blank">{{ image.url }}</a>
            </div>
            <div class="info-row" v-if="image.modifiedUrl">
              <strong>Card URL:</strong>
              <a :href="image.modifiedUrl" target="_blank">{{ image.modifiedUrl }}</a>
            </div>
          </div>

          <div class="zoom-controls">
            <FormButton color="secondary" class="round-btn-sm" @click="zoomIn">
              <Icon class="icon-24" name="add-outline" />
            </FormButton>
            <FormButton color="secondary" class="round-btn-sm" @click="zoomOut">
              <Icon class="icon-24" name="remove-outline" />
            </FormButton>
            <FormButton color="secondary" class="round-btn-sm" @click="resetZoom">
              <Icon class="icon-24" name="refresh-outline" />
            </FormButton>
          </div>
        </div>
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import Modal from "@components/modal/Modal.vue";
import FormButton from "@components/form/FormButton.vue";
import Icon from "@components/Icon.vue";
import { Flag } from "@/localization";

interface ExtractedImageDTO {
  localization: string;
  source: string;
  url?: string;
  internal: boolean;
  base64Image?: string;
  modifiedUrl?: string;
}

interface Props {
  image: ExtractedImageDTO;
  showOnError?: boolean;
}

const props = withDefaults(defineProps<Props>(), { showOnError: false });
const emit = defineEmits(["error", "click", "loaded", "delete"]);

const modal = ref<InstanceType<typeof Modal>>();
const modalImage = ref<HTMLImageElement>();
const width = ref(0);
const height = ref(0);
const error = ref(false);
const zoom = ref(1);

const onLoad = (e: Event | HTMLImageElement) => {
  const img = e instanceof Event ? (e.target as HTMLImageElement) : e;
  width.value = img.naturalWidth || 0;
  height.value = img.naturalHeight || 0;
  error.value = width.value === 0 || height.value === 0;
  emit("loaded", { width: width.value, height: height.value });
};

const onError = () => {
  error.value = true;
  emit("error");
};

const openModal = () => {
  resetZoom();
  modal.value?.show();
};

const zoomIn = () => { zoom.value = Math.min(zoom.value + 0.2, 5); };
const zoomOut = () => { zoom.value = Math.max(zoom.value - 0.2, 0.2); };
const resetZoom = () => { zoom.value = 1; };
const onWheel = (e: WheelEvent) => { e.deltaY < 0 ? zoomIn() : zoomOut(); };

const showUrls = computed(() => {
  if (!props.image) return false;
  if (props.image.source === "Upload") return false;
  if (props.image.source === "Database" && props.image.url?.startsWith("/images/cards")) return false;
  return true;
});
</script>


<style scoped lang="scss">
@use "@/retriever.scss" as retr;

.extracted-image {
  position: relative;
  padding: 0.1rem;
  width: 100%;
  height: 100%;

  > img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }

  .image-info-button {
    position: absolute;
    right: 0.5rem;
    bottom: 0.5rem;
  }

  .image-delete-button {
    position: absolute;
    left: 0.5rem;
    bottom: 0.5rem;

    ::v-deep(.btn) {
      @include retr.highlight(retr.$danger);
      background-color: retr.$danger !important;
      border-color: retr.$danger !important;
      color: white !important;
    }
  }
}

.image-size {
  font-size: 0.8rem;
  background-color: rgba(lightgray, 0.5);
  padding: 0.2rem;
  position: absolute;
}

.modal-container {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.modal-image-container {
  width: 378px;
  height: 526.39px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  overflow: auto;

  :deep(img) {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: contain;
    transform-origin: center center;
  }
}

.modal-info-container {
  flex: 1;
  min-width: 260px;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 0.5rem;
  overflow-wrap: anywhere;
}

.info-row strong {
  display: inline-block;
  width: 80px;
}

.zoom-controls {
  margin-top: 1rem;
  display: flex;
  gap: 0.5rem;
}
</style>