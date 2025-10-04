package com.pcagrade.retriever.card.pokemon.source.pokemoncom;

import com.pcagrade.mason.localization.Localization;
import com.pcagrade.retriever.card.pokemon.PokemonCardDTO;
import com.pcagrade.retriever.card.pokemon.image.IPokemonCardImageExtractor2;
import com.pcagrade.retriever.card.pokemon.set.PokemonSetDTO;
import com.pcagrade.retriever.card.pokemon.set.PokemonSetService;
import com.pcagrade.retriever.image.ExtractedImageDTO;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static liquibase.ui.UIServiceEnum.LOGGER;

@Service
public class PokemoncomService implements IPokemonCardImageExtractor2 {
    public static final String NAME = "pokemon.com";

    private static final Logger LOGGER = LogManager.getLogger(PokemoncomService.class);

    @Autowired
    private PokemonSetService pokemonSetService;
    @Autowired
    private PokemoncomParser pokemoncomParser;

    @Override
    @Transactional
    public List<ExtractedImageDTO> getImages(PokemonCardDTO card, Localization localization) {
        var cardTranslation = card.getTranslations().get(localization);

        if (cardTranslation == null || !cardTranslation.isAvailable()) {
            return Collections.emptyList();
        }

        var number = cardTranslation.getNumber();

        String cardNumber = number.split("/")[0];
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
        var translation = set.getTranslations().get(localization);

        if (translation == null) {
            return Collections.emptyList();
        }

        var name = translation.getName();

        if (StringUtils.isBlank(name)) {
            return Collections.emptyList();
        }

        var setNameEnglish = set.getTranslations().get(Localization.USA).getName();
        String setCodePokemonCom = mapSetCodeForPokemonCom(setNameEnglish);
        if (setCodePokemonCom.equals(""))
            setCodePokemonCom = set.getShortName();
        String langCode = getLangCode(localization);
        String imageUrl = String.format(
                "https://assets.pokemon.com/static-assets/content-assets/cms2%s/img/cards/web/%s/%s_%s_%s.png",
                langCode, setCodePokemonCom.toUpperCase(), setCodePokemonCom.toUpperCase(),
                getImageFilenameLangCode(localization)
                , cardNumber
        );

        String modifiedUrl = "https://pokemon.com/cards/" + setCodePokemonCom.toUpperCase() + "/" + cardNumber;

        ExtractedImageDTO imageDTO = new ExtractedImageDTO(
                localization,
                "pokemon.com",
                imageUrl,
                false,
                null,
                modifiedUrl
        );
        return List.of(imageDTO);
    }

    @Override
    public String name() {
        return "pokemon.com";
    }

    @Override
    public byte[] getRawImage(ExtractedImageDTO image) {
        return pokemoncomParser.getImage(image.url());
    }


    private String getLangCode(Localization localization) {
        switch (localization.getCode()) {
            case "fr": return "-fr-fr";
            case "us": return "";
            case "es": return "-es-es";
            case "it": return "-it-it";
            case "de": return "-de-de";
            case "pt": return "-pt-br";
            default: return "";
        }
    }

    private String getImageFilenameLangCode(Localization localization) {
        switch (localization.getCode()) {
            case "us": return "EN";
            case "pt": return "PT-BR";  // underscore-safe
            default: return localization.getCode().toUpperCase();
        }
    }


    private String mapSetCodeForPokemonCom(String setNameEnglish) {
        switch (setNameEnglish) {
            case "Scarlet & Violet": return "sv01";
            case "Paldea Evolved": return "sv02";
            case "Obsidian Flames": return "sv03";
            case "151": return "sv3pt5";
            case "Paradox Rift": return "sv04";
            case "Temporal Forces": return "sv05";
            case "Twilight Masquerade": return "sv06";
            case "Shrouded Fable": return "sv6pt5";
            case "Stellar Crown": return "sv07";
            case "Surging Sparks": return "sv08";
            case "Prismatic Evolutions": return "sv8pt5";
            case "Friendly Adventures": return "sv09";
            case "Scarlet & Violet Promo Cards": return "svp";
            case "Arceus": return "pl4";
            case "Pokémon Rumble": return "ru1";
            case "Detective Pikachu": return "det";
            case "Crown Zenith": return "SWSH12PT5GG";
            default: return "";
        }
    }

}