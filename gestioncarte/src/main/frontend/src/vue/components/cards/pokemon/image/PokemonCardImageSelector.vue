<template>
  <Row class="mb-3">
    <Column>
      <div class="d-inline-block">
        <SingleLineCard v-if="card" :card="card" localization="us" />
      </div>

      <div class="card-locales">
        <div v-for="locale in locales" :key="locale" class="locale">
          <div class="locale-label"><Flag :lang="locale" /></div>

          <div class="images">
            <div
                v-for="(item, index) in displayImages[locale] || []"
                :key="getImageKey(item, index, locale)"
                v-show="currentIndexByLocale[locale] === index"
                class="image-slot"
                @click="cycleImage(locale)"
            >
              <ExtractedImage
                  v-if="!item.isPlaceholder"
                  :image="item"
                  :show-on-error="true"
                  @loaded="onImageLoaded(locale, index, $event)"
                  @delete="item.source === 'Upload' ? deleteUpload(locale, index) : null"
              />

              <div v-else class="upload-placeholder">
                <UploadButton
                    multiple
                    @upload="(files) => handleFileSelection(locale, files)"
                    accept="image/png,image/jpeg,image/jpg,image/webp"
                    class="upload-button"
                >
                  <Icon src="/svg/download.svg" class="v-flip upload-icon" />
                </UploadButton>
              </div>
            </div>

            <div
                v-if="getRealImages(locale).length > 1 && !isCurrentPlaceholder(locale)"
                class="image-counter"
            >
              {{ getCurrentRealIndex(locale) + 1 }}/{{ getRealImages(locale).length }}
            </div>
          </div>
        </div>
      </div>
    </Column>
  </Row>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from "vue";
import Row from "@components/grid/Row.vue";
import Column from "@components/grid/Column.vue";
import SingleLineCard from "@components/cards/pokemon/SingleLineCard.vue";
import ExtractedImage from "@components/images/ExtractedImage.vue";
import { Flag } from "@/localization";
import { ExtractedImageDTO, PokemonCardDTO } from "@/types";
import Icon from "@components/Icon.vue";
import UploadButton from "@components/form/UploadButton.vue";

interface Props {
  card: PokemonCardDTO;
  extractedImages: Record<string, ExtractedImageDTO[]>;
}

const props = defineProps<Props>();
const emit = defineEmits(["upload", "delete-extracted", "user-selection"]);

const locales = ["us", "fr", "it", "pt", "de"] as const;

// State
const imageSizes = ref<Record<string, { width: number; height: number }[]>>({});
const currentIndexByLocale = ref<Record<string, number>>({});
const displayImages = ref<Record<string, ExtractedImageDTO[]>>({});
const uploadsByLocale = ref<Record<string, ExtractedImageDTO[]>>(
    locales.reduce((acc, loc) => ({ ...acc, [loc]: [] }), {} as Record<string, ExtractedImageDTO[]>)
);
const userSelectedIndexByLocale = ref<Record<string, number>>({});

// Initialize current index for all locales
locales.forEach(locale => {
  currentIndexByLocale.value[locale] = 0;
});

// Preload image size
const getImageSize = (url: string): Promise<{width: number, height: number}> => {
  return new Promise(resolve => {
    const img = new Image();
    img.onload = () => {
      console.log(`Preloaded image size: ${img.width}x${img.height} for ${url}`);
      resolve({ width: img.width, height: img.height });
    };
    img.onerror = () => {
      console.error(`Failed to preload image: ${url}`);
      resolve({ width: 0, height: 0 });
    };
    img.src = url;
  });
};

// Update display images with sorting
const updateDisplayImages = async () => {
  console.log('Updating display images...');

  for (const locale of locales) {
    let db = [...(props.extractedImages?.[locale] || [])];

    console.log(`Locale ${locale}: ${db.length} DB images, ${uploadsByLocale.value[locale].length} uploads`);

    // Preload sizes for DB images
    for (let i = 0; i < db.length; i++) {
      if (db[i].url && !imageSizes.value[locale]?.[i]) {
        const size = await getImageSize(db[i].url);
        if (!imageSizes.value[locale]) imageSizes.value[locale] = [];
        imageSizes.value[locale][i] = size;
      }
    }

    // Sort DB images by resolution (highest first)
    db.sort((a, b) => {
      const aOriginalIdx = (props.extractedImages?.[locale] || []).indexOf(a);
      const bOriginalIdx = (props.extractedImages?.[locale] || []).indexOf(b);
      const aSize = imageSizes.value[locale]?.[aOriginalIdx];
      const bSize = imageSizes.value[locale]?.[bOriginalIdx];
      const aRes = aSize ? aSize.width * aSize.height : 0;
      const bRes = bSize ? bSize.width * bSize.height : 0;

      console.log(`Comparing: ${aRes} vs ${bRes}`);
      return bRes - aRes;
    });

    const uploads = uploadsByLocale.value[locale] || [];
    displayImages.value[locale] = [
      ...db,
      ...uploads,
      { isPlaceholder: true, source: "Upload", localization: locale } as ExtractedImageDTO
    ];

    console.log(`Display images for ${locale}:`, displayImages.value[locale].map((img, i) => ({
      index: i,
      source: img.source,
      isPlaceholder: img.isPlaceholder,
      size: imageSizes.value[locale]?.[i]
    })));
  }
};

// Watch for changes
watch(() => props.extractedImages, () => {
  updateDisplayImages();
}, { immediate: true, deep: true });

watch(() => uploadsByLocale.value, () => {
  updateDisplayImages();
}, { deep: true });

// Reset user selection when images change
watch(displayImages, () => {
  userSelectedIndexByLocale.value = {};
}, { deep: true });

// Helper to get stable keys
const getImageKey = (item: ExtractedImageDTO, index: number, locale: string): string => {
  if (item.isPlaceholder) return `placeholder_${locale}`;
  return `${item.source}_${item.localization}_${index}_${locale}`;
};

// Get real images (no placeholder)
const getRealImages = (locale: string) => {
  return (displayImages.value[locale] || []).filter(img => !img.isPlaceholder);
};

// Check if current item is placeholder
const isCurrentPlaceholder = (locale: string): boolean => {
  const idx = currentIndexByLocale.value[locale] || 0;
  const items = displayImages.value[locale] || [];
  return items[idx]?.isPlaceholder === true;
};

// Get current real image index (among non-placeholders)
const getCurrentRealIndex = (locale: string): number => {
  const currentIdx = currentIndexByLocale.value[locale] || 0;
  const items = displayImages.value[locale] || [];
  let realCount = 0;
  for (let i = 0; i < currentIdx && i < items.length; i++) {
    if (!items[i].isPlaceholder) realCount++;
  }
  return realCount;
};

// Cycle through images - detect user manual selection
const cycleImage = (locale: string) => {
  const items = displayImages.value[locale] || [];
  const newIndex = ((currentIndexByLocale.value[locale] || 0) + 1) % items.length;
  currentIndexByLocale.value[locale] = newIndex;

  // Mark this as a user manual selection
  userSelectedIndexByLocale.value[locale] = newIndex;

  console.log(`User MANUALLY selected image ${newIndex} for ${locale}`);
  emit('user-selection', { locale, index: newIndex });
};

// Handle file uploads
const handleFileSelection = async (locale: string, files: File[] | FileList) => {
  let fileArray: File[] = [];

  if (files instanceof File) fileArray = [files];
  else if (files instanceof FileList) fileArray = Array.from(files);
  else if (Array.isArray(files)) fileArray = files;

  const validFiles = fileArray.filter(f => f.type.match(/^image\/(png|jpe?g|webp)$/));
  if (!validFiles.length) return;

  for (const file of validFiles) {
    const reader = new FileReader();

    reader.onload = async () => {
      let base64 = reader.result as string;
      if (base64.startsWith("data:")) base64 = base64.split(",")[1] ?? base64;

      const url = URL.createObjectURL(file);
      const dto: ExtractedImageDTO = {
        localization: locale,
        source: "Upload",
        internal: true,
        base64Image: base64,
        modifiedUrl: "Not Applicable",
        url: url
      };

      console.log(`Uploading file for ${locale}:`, file.name);

      // Get size for uploaded image
      const size = await getImageSize(url);
      console.log(`Upload size: ${size.width}x${size.height}`);

      uploadsByLocale.value[locale].push(dto);
      emit("upload", { locale, image: dto });

      await nextTick();
      await updateDisplayImages();

      // Find the uploaded image in displayImages
      const items = displayImages.value[locale] || [];
      const uploadIndex = items.findIndex(item =>
          item.source === "Upload" && !item.isPlaceholder && item.url === dto.url
      );

      console.log(`Upload added at index: ${uploadIndex}`);

      if (uploadIndex >= 0) {
        if (!imageSizes.value[locale]) imageSizes.value[locale] = [];
        imageSizes.value[locale][uploadIndex] = size;
        currentIndexByLocale.value[locale] = uploadIndex;
        userSelectedIndexByLocale.value[locale] = undefined;
      }
    };

    reader.readAsDataURL(file);
  }
};

// Track image sizes when loaded
const onImageLoaded = (locale: string, index: number, size: { width: number; height: number }) => {
  if (!imageSizes.value[locale]) imageSizes.value[locale] = [];
  imageSizes.value[locale][index] = size;

  console.log(`Image loaded - Locale: ${locale}, Index: ${index}, Size: ${size.width}x${size.height}`);
};

// Delete uploaded image
const deleteUpload = async (locale: string, index: number) => {
  const items = displayImages.value[locale] || [];
  const dbCount = (props.extractedImages?.[locale] || []).length;
  const uploadIndex = index - dbCount;

  if (uploadIndex >= 0 && uploadIndex < uploadsByLocale.value[locale].length) {
    uploadsByLocale.value[locale].splice(uploadIndex, 1);

    await nextTick();
    await updateDisplayImages();

    const newItems = displayImages.value[locale] || [];
    if (currentIndexByLocale.value[locale] >= newItems.length) {
      currentIndexByLocale.value[locale] = Math.max(0, newItems.length - 1);
    }
  }
};

// Get best image index for saving (highest resolution)
const getBestImageIndex = (locale: string): number => {
  const images = displayImages.value[locale] || [];
  if (!images.length) return 0;

  let bestIdx = 0;
  let bestRes = 0;

  for (let i = 0; i < images.length; i++) {
    if (images[i].isPlaceholder) continue;

    const size = imageSizes.value[locale]?.[i];
    if (size) {
      const res = size.width * size.height;
      console.log(`Image ${i} (${images[i].source}) resolution: ${res} (${size.width}x${size.height})`);
      if (res > bestRes) {
        bestRes = res;
        bestIdx = i;
        console.log(`New best: index ${i}, source: ${images[i].source}, resolution ${res}`);
      }
    }
  }

  console.log(`Best image for ${locale}: index ${bestIdx}, source: ${images[bestIdx]?.source}, resolution ${bestRes}`);
  return bestIdx;
};

// Get selection for all locales (prefers user selection, falls back to auto)
const getSelectionForSave = () => {
  console.log('Getting selection for save...');
  const selection: Record<string, number> = {};

  locales.forEach(locale => {
    // Prefer user manual selection, fall back to automatic best quality
    if (userSelectedIndexByLocale.value[locale] !== undefined) {
      selection[locale] = userSelectedIndexByLocale.value[locale];
      console.log(`Using USER selection for ${locale}: index ${selection[locale]}`);
    } else {
      selection[locale] = getBestImageIndex(locale);
      console.log(`Using AUTO selection for ${locale}: index ${selection[locale]}`);
    }
  });

  console.log('Final selection:', selection);
  return selection;
};

// Expose for parent component
defineExpose({ getSelectionForSave, displayImages, userSelectedIndexByLocale });
</script>

<style scoped lang="scss">
@import "src/variables";

.images {
  position: relative;
  width: 252px;
  height: 356px;

  .image-slot {
    width: 100%;
    height: 100%;
    cursor: pointer;

    &:focus,
    &:active,
    &:focus-visible {
      outline: none !important;
      box-shadow: none !important;
      background: transparent !important;
    }
  }

  .upload-placeholder {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 100%;
    height: 100%;
    background-color: #f0f0f0;
    border-radius: 6px;
  }

  .upload-icon {
    width: 48px !important;
    height: 48px !important;
    color: #a0a0a0 !important;
  }

  .v-flip {
    transform: scaleY(-1) !important;
  }

  .image-counter {
    position: absolute;
    top: 4px;
    right: 4px;
    background: rgba(0, 0, 0, 0.6);
    color: white;
    border-radius: 3px;
    padding: 2px 5px;
    font-size: 0.75rem;
    pointer-events: none;
  }
}

.card-locales {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  justify-content: center;
}

.locale {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 120px;
}

.locale:focus,
.locale:active,
.locale:focus-within {
  outline: none !important;
  box-shadow: none !important;
  background: transparent !important;
}

.image-slot,
.locale,
.locale-label,
.images {
  user-select: none !important;
  -webkit-user-drag: none !important;
  -webkit-tap-highlight-color: transparent !important;
}
</style>