package com.pcagrade.retriever.card.pokemon.source.mypcards;

import com.pcagrade.mason.localization.Localization;
import com.pcagrade.retriever.card.pokemon.PokemonCardDTO;
import com.pcagrade.retriever.card.pokemon.image.IPokemonCardImageExtractor2;
import com.pcagrade.retriever.card.pokemon.set.PokemonSetDTO;
import com.pcagrade.retriever.card.pokemon.set.PokemonSetService;
import com.pcagrade.retriever.card.pokemon.source.pokemoncom.PokemoncomParser;
import com.pcagrade.retriever.image.ExtractedImageDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Service
public class PokemonmypcardsService implements IPokemonCardImageExtractor2 {
    public static final String NAME = "mypcards.com";
    @Autowired
    private PokemonSetService pokemonSetService;
    @Autowired
    private PokemoncomParser pokemoncomParser;
    @Autowired
    private PokemonExtensionParser pokemonExtensionParser;

    @Override
    @Transactional
    public List<ExtractedImageDTO> getImages(PokemonCardDTO card, Localization localization) throws IOException {
        var cardTranslation = card.getTranslations().get(localization);

        if (cardTranslation == null || !cardTranslation.isAvailable()) {
            return Collections.emptyList();
        }

        var number = cardTranslation.getNumber();

        String cardNumber = number.split("/")[0];
        String cardNumsur = number.split("/")[1];
        try {
            cardNumber = String.valueOf(Integer.parseInt(cardNumber));
        } catch (NumberFormatException e) {
            System.out.println("Error parsing card number: " + cardNumber);
        }

        var opt = card.getSetIds().stream()
                .<PokemonSetDTO>mapMulti((setId, downstream) -> pokemonSetService.findSet(setId).ifPresent(downstream))
                .filter(s -> s.getTranslations().containsKey(localization))
                .findFirst();

        if (opt.isEmpty()) {
            return Collections.emptyList();
        }

        var set = opt.get();

        var englishTranslation = set.getTranslations().get(Localization.USA);

        if (englishTranslation == null) {
            return Collections.emptyList();
        }

        var name = englishTranslation.getName();


        if (StringUtils.isBlank(name)) {
            return Collections.emptyList();
        }


        String filePath = "mypcards.html";
        List<PokemonExtensionParser.Extension> extensions = pokemonExtensionParser.getPokemonExtensionsFromFile(filePath);
        PokemonExtensionParser.Extension extension = null;

        extension = extensions.stream()
                .filter(ext -> {
                    String raw = ext.getSubtitulo().isEmpty() ? ext.getNome() : ext.getSubtitulo();

                    String nameExt = "";

                    String[] colonSplit = raw.split(":");
                    if (colonSplit.length > 1) {
                        nameExt = colonSplit[1].trim();
                    } else {
                        // Try em dash
                        String[] dashSplit = raw.split("—");
                        if (dashSplit.length > 1) {
                            nameExt = dashSplit[1].trim();
                        } else {
                            // Try en dash
                            String[] enDashSplit = raw.split("–");
                            if (enDashSplit.length > 1) {
                                nameExt = enDashSplit[1].trim();
                            } else {
                                // Try hyphen-minus
                                String[] hyphenSplit = raw.split("-");
                                if (hyphenSplit.length > 1) {
                                    nameExt = hyphenSplit[1].trim();
                                } else {
                                    // No separator found, fallback to whole string
                                    nameExt = raw.trim();
                                }
                            }
                        }
                    }

                    return nameExt.equals(name);
                })
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No matching extension found for name: " + name));

        int n = cardNumsur.length();
        cardNumber = String.format("%0" + n + "d", Integer.parseInt(cardNumber));
        String imageUrl = String.format(
                "https://img.mypcards.com/img/2/%s/pokemon_%s_%s_%s/pokemon_%s_%s_%s_%s.jpg",
                extension.getDataKey(), extension.getSigla().toLowerCase(), cardNumber, cardNumsur, extension.getSigla().toLowerCase(), cardNumber,
                cardNumsur, localization.getCode().equals("us") ? "en" : localization.getCode().toLowerCase()
        );

        String modifiedUrl = "https://mypcards.com/cards/" + extension.getSigla() + "/" + Integer.parseInt(cardNumber);

        ExtractedImageDTO imageDTO = new ExtractedImageDTO(
                localization,
                "mypcards.com",
                imageUrl,
                false,
                null,
                modifiedUrl        // UI-friendly URL
        );
        return List.of(imageDTO);
    }

    @Override
    public String name() {
        return "mypcards.com";
    }

    @Override
    public byte[] getRawImage(ExtractedImageDTO image) {
        return pokemoncomParser.getImage(image.url());
    }

}
