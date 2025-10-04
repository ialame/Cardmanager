<template>
  <SideButtons>
    <ScrollToTop />
  </SideButtons>

  <PokemonSetSearch v-model="set">
    <FormRow>
      <Column>
        <div class="d-flex flex-row float-end">
          <FormButton color="primary" @click="extract">Extraire</FormButton>
          <FormButton color="secondary" class="ms-2" @click="extractMissing">Cartes sans images</FormButton>
        </div>
      </Column>
    </FormRow>
  </PokemonSetSearch>

  <hr />

  <template v-if="set && totalExtractedImages > 0">
    <div class="container mt-2">
      <div class="d-flex justify-content-center">
        <div class="w-50 ms-auto me-auto">
          <ProgressBar
              :value="saved > -1 ? saved : (showMissingOnly ? missingImages.length : extractedImages.length)"
              :max="totalExtractedImages"
              :label="saved > -1
              ? `Enregistrement des images`
              : (showMissingOnly ? `Extraction des cartes sans images` : `Extraction des images`)"
          />
        </div>
        <div class="d-flex flex-row float-end">
          <FormButton color="primary" @click="save">Enregistrer</FormButton>
          <FormButton color="secondary" class="ms-2" title="Telecharger le resultat en json" @click="downloadJson()">
            <Icon src="/svg/download.svg" />
          </FormButton>
        </div>
      </div>
    </div>
    <hr />
  </template>

  <div class="full-screen-container mt-2">
    <template v-for="cardWrapper in showMissingOnly ? missingImages : extractedImages" :key="cardWrapper.card.id">
      <PokemonCardImageSelector
          :ref="el => setCardRef(el, cardWrapper.card.id)"
          :card="cardWrapper.card"
          :extractedImages="cardWrapper.imagesByLocale"
          :uploadsByLocale="cardWrapper.uploadsByLocale"
          :missingOnly="showMissingOnly"
          v-model:uploadsByLocale="cardWrapper.uploadsByLocale"
          @upload="f => onUpload(cardWrapper, f)"
          @delete-upload="f => onDeleteUpload(cardWrapper, f)"
          @user-selection="({ locale, index }) => onUserSelection(cardWrapper, locale, index)"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, triggerRef, nextTick } from "vue";
import { ScrollToTop, SideButtons } from "@components/side";
import { PokemonSetSearch } from "@components/cards/pokemon/set";
import Column from "@components/grid/Column.vue";
import FormRow from "@components/form/FormRow.vue";
import FormButton from "@components/form/FormButton.vue";
import { ProgressBar } from "@/progress";
import PokemonCardImageSelector from "@components/cards/pokemon/image/PokemonCardImageSelector.vue";
import { pokemonCardService } from "@components/cards/pokemon/service";
import rest from "@/rest";
import { usePageTitle } from "@/vue/composables/PageComposables";
import { useRaise } from "@/alert";
import { SaveComposables } from "@/vue/composables/SaveComposables";
import { useSound } from "@vueuse/sound";
import pingSfx from "@/../assets/sound/ping.mp3";
import { PokemonSetDTO, ExtractedPokemonImagesDTO, ExtractedImageDTO } from "@/types";
import Icon from "@components/Icon.vue";
import { downloadData, getDateStr } from '@/retriever';

const downloadJson = () => {
  const isMissingOnly = showMissingOnly.value;
  let hasImages = false;

  const dataToDownload = (isMissingOnly ? missingImages.value : extractedImages.value)
      .map(wrapper => {
        const cardData: any = { ...wrapper.card };

        if (!isMissingOnly) {
          const dbImagesByLocale = Object.fromEntries(
              Object.entries(wrapper.imagesByLocale).map(([locale, imgs]) => [
                locale,
                imgs.filter(img => img.source === "Database")
              ])
          );

          const nonEmptyLocales = Object.fromEntries(
              Object.entries(dbImagesByLocale).filter(([_, imgs]) => imgs.length > 0)
          );

          if (Object.keys(nonEmptyLocales).length > 0) {
            cardData.imagesByLocale = nonEmptyLocales;
            hasImages = true;
          }
        }

        return cardData;
      });

  const fileName =
      "extracted-pokemon-cards" +
      (hasImages ? "-with-images" : "") +
      "-" + getDateStr() + ".json";

  downloadData(fileName, dataToDownload);
};

usePageTitle("Pokémon - Ajout d'images");

const raise = useRaise();
const { play } = useSound(pingSfx);

const set = ref<PokemonSetDTO>();
const extractedImages = ref<any[]>([]);
const missingImages = ref<any[]>([]);
const totalExtractedImages = ref(0);
const saved = ref(-1);
const showMissingOnly = ref(false);
const refsByCard: Record<string, any> = {};

const locales = ["us", "fr", "it", "pt", "de"] as const;
type LocalizationCode = typeof locales[number];

// ---- REFS MANAGEMENT ----
const setCardRef = (el: any, cardId: string) => {
  if (el) {
    refsByCard[cardId] = el;
  } else {
    delete refsByCard[cardId];
  }
};

// ---- IMAGE MANAGEMENT ----
const addImages = async (images: ExtractedPokemonImagesDTO[], locale: LocalizationCode) => {
  const cards = await Promise.all(
      images.map(async ({ cardId, images }) => {
        const card = await pokemonCardService.get(cardId);
        const imagesByLocale: Record<LocalizationCode, ExtractedImageDTO[]> = { us: [], fr: [], it: [], pt: [], de: [] };
        imagesByLocale[locale] = images;
        return {
          card,
          imagesByLocale,
          uploadsByLocale: { us: [], fr: [], it: [], pt: [], de: [] },
          userSelectedIndexByLocale: {},
          selected: 0
        };
      })
  );

  cards.forEach(newCard => {
    const existing = extractedImages.value.find(c => c.card.id === newCard.card.id);
    if (existing) existing.imagesByLocale[locale] = newCard.imagesByLocale[locale];
    else extractedImages.value.push(newCard);
  });

  extractedImages.value.sort((a, b) => (a.card.idPrim && b.card.idPrim) ? a.card.idPrim.localeCompare(b.card.idPrim) : 0);
  triggerRef(extractedImages);
};

const onUpload = (cardWrapper: any, { locale, image }: { locale: LocalizationCode, image: ExtractedImageDTO }) => {
  cardWrapper.uploadsByLocale = { ...cardWrapper.uploadsByLocale, [locale]: [image] };
};

const onDeleteUpload = (cardWrapper: any, { locale, index }: { locale: LocalizationCode, index: number }) => {
  cardWrapper.uploadsByLocale?.[locale]?.splice(index, 1);
  triggerRef(showMissingOnly.value ? missingImages : extractedImages);
};

const onUserSelection = (cardWrapper: any, locale: LocalizationCode, index: number) => {
  if (!cardWrapper.userSelectedIndexByLocale) {
    cardWrapper.userSelectedIndexByLocale = {};
  }
  cardWrapper.userSelectedIndexByLocale[locale] = index;
  console.log(`User selected image ${index} for ${locale} on card ${cardWrapper.card.id}`);
};

// ---- SAVE LOGIC ----
const { save } = SaveComposables.useLockedSaveAsync(async () => {
  saved.value = 0;

  const cardsToSave = showMissingOnly.value ? missingImages.value : extractedImages.value;

  await nextTick();

  for (const cardWrapper of cardsToSave) {
    console.log('=== Saving card:', cardWrapper.card.id);

    const selector = refsByCard[cardWrapper.card.id];
    const selection = selector?.getSelectionForSave?.() || {};

    console.log('Selection for save:', selection);

    for (const locale of locales) {
      const displayImagesForLocale = selector?.displayImages?.[locale] || [];
      const selectedIndex = selection[locale] != null && selection[locale] >= 0 && selection[locale] < displayImagesForLocale.length
          ? selection[locale]
          : 0;

      const selectedImage = displayImagesForLocale[selectedIndex];

      console.log(`Locale ${locale}: Selected image at index ${selectedIndex}`, {
        source: selectedImage?.source,
        isPlaceholder: selectedImage?.isPlaceholder,
        isUpload: selectedImage?.source === 'Upload'
      });

      if (selectedImage && !selectedImage.isPlaceholder) {
        console.log(`Saving image for ${locale}:`, selectedImage.source);
        await rest.put(`/api/cards/pokemon/${cardWrapper.card.id}/images`, { data: selectedImage });
      } else {
        console.log(`Skipping save for ${locale} - no valid image selected`);
      }
    }

    saved.value++;
  }

  raise.success("Images enregistrées");
  saved.value = -1;
  set.value = undefined;
  extractedImages.value = [];
  missingImages.value = [];
  totalExtractedImages.value = 0;
  play();
});

// ---- EXTRACTION ----
const extract = async () => {
  showMissingOnly.value = false;
  extractedImages.value = [];
  totalExtractedImages.value = 0;

  const id = set.value?.id;
  if (!id) return;

  for (const locale of locales) {
    let page = 0, totalPages = 1;
    while (page < totalPages) {
      try {
        const response = await rest.get(`/api/cards/pokemon/images/extract/sets/${id}`, { params: { locale, page, size: 25 } });
        await addImages(response.content, locale);
        totalPages = response.totalPages;
        totalExtractedImages.value = response.totalElements;
        page++;
      } catch (err) {
        console.error(`Error fetching locale ${locale}, page ${page}:`, err);
        break;
      }
    }
  }

  play();
};

// ---- EXTRACT MISSING ONLY ----
const extractMissing = async () => {
  showMissingOnly.value = true;
  missingImages.value = [];
  totalExtractedImages.value = 0;

  const id = set.value?.id;
  if (!id) return;

  const allCards: any[] = [];

  for (const locale of locales) {
    let page = 0, totalPages = 1;
    while (page < totalPages) {
      try {
        const response = await rest.get(`/api/cards/pokemon/images/extract/sets/${id}`, { params: { locale, page, size: 25 } });

        for (const { cardId, images } of response.content) {
          const card = await pokemonCardService.get(cardId);
          let existing = allCards.find(c => c.card.id === card.id);
          if (!existing) {
            existing = {
              card,
              imagesByLocale: { us: [], fr: [], it: [], pt: [], de: [] },
              uploadsByLocale: { us: [], fr: [], it: [], pt: [], de: [] },
              userSelectedIndexByLocale: {},
              selected: 0
            };
            allCards.push(existing);
          }

          existing.imagesByLocale[locale] = images;
        }

        totalPages = response.totalPages;
        page++;
      } catch (err) {
        console.error(`Error fetching locale ${locale}, page ${page}:`, err);
        break;
      }
    }
  }

  missingImages.value = allCards
      .filter(cardWrapper => locales.some(locale => (cardWrapper.imagesByLocale[locale] ?? []).length === 0))
      .map(cardWrapper => {
        const missingImagesByLocale: Record<string, ExtractedImageDTO[]> = {};
        const missingUploadsByLocale: Record<string, ExtractedImageDTO[]> = {};
        locales.forEach(locale => {
          if ((cardWrapper.imagesByLocale[locale] ?? []).length === 0) {
            missingImagesByLocale[locale] = [];
            missingUploadsByLocale[locale] = cardWrapper.uploadsByLocale[locale] || [];
          }
        });
        return {
          card: cardWrapper.card,
          imagesByLocale: missingImagesByLocale,
          uploadsByLocale: missingUploadsByLocale,
          userSelectedIndexByLocale: {},
          selected: 0
        };
      });

  missingImages.value.sort((a, b) => (a.card.idPrim && b.card.idPrim) ? a.card.idPrim.localeCompare(b.card.idPrim) : 0);
  totalExtractedImages.value = missingImages.value.length;
  triggerRef(missingImages);
  play();
};
</script>