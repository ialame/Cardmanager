package com.pcagrade.retriever.card.pokemon.source.limitless;

import com.pcagrade.mason.localization.Localization;
import com.pcagrade.retriever.card.pokemon.PokemonCardDTO;
import com.pcagrade.retriever.card.pokemon.PokemonCardHelper;
import com.pcagrade.retriever.card.pokemon.image.IPokemonCardImageExtractor2;
import com.pcagrade.retriever.card.pokemon.serie.PokemonSerie;
import com.pcagrade.retriever.card.pokemon.serie.PokemonSerieRepository;
import com.pcagrade.retriever.card.pokemon.set.PokemonSetDTO;
import com.pcagrade.retriever.card.pokemon.set.PokemonSetService;
import com.pcagrade.retriever.image.ExtractedImageDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class LimitlessService implements IPokemonCardImageExtractor2 {
    public static final String NAME = "limitlesstcg.com";
    @Autowired
    private LimitlessParser limitlessParser;
    @Autowired
    private PokemonSetService pokemonSetService;
    @Autowired
    private PokemonSerieRepository pokemonSerieRepository;

    @Override
    @Transactional
    public List<ExtractedImageDTO> getImages(PokemonCardDTO card, Localization localization) {
        var cardTranslation = card.getTranslations().get(localization);

        if (cardTranslation == null || !cardTranslation.isAvailable()) {
            return Collections.emptyList();
        }

        var number = cardTranslation.getNumber();

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

        PokemonSerie serie = pokemonSerieRepository.findByNullableId(set.getSerieId()).orElse(null);

        var name = translation.getName();

        if (StringUtils.isBlank(name)) {
            return Collections.emptyList();
        }

        var path= limitlessParser.setNameToCode.get(set.getTranslations().get(Localization.USA).getName());
        var total = set.getPrintedTotal();

        return limitlessParser.getImages(path, localization).entrySet().stream()
                .filter(e ->
                        StringUtils.equalsIgnoreCase(number, PokemonCardHelper.rebuildNumber(e.getKey(), total))
                        || StringUtils.equalsIgnoreCase(number, PokemonCardHelper.rebuildNumber0(e.getKey(), total))
                )
                .map(Map.Entry::getValue)
                .map(img -> {

                    // Start with original URL
                    String cleanUrl = img.url();

                    // If it ends with _LG.png (before language code), remove only that part
                    if (cleanUrl.contains("_LG")) {
                        // cleanUrl = cleanUrl.replace("_LG", "");
                        cleanUrl = cleanUrl.replaceFirst("_LG(?=\\.[a-zA-Z0-9]+$)", "");
                    }

                    // Build modified URL
                    String modifiedUrl = "https://limitlesstcg.com/cards/"
                            + path + "/"
                            + Integer.parseInt(number.split("/")[0]); // remove leading zeros

                    // Create new ExtractedImageDTO with modifiedUrl
                    return new ExtractedImageDTO(
                            img.localization(),
                            NAME,           // source name
                            //img.url(),      // original image URL
                            cleanUrl,
                            img.internal(),
                            img.base64Image(),
                            modifiedUrl
                    );
                })
                .toList();
    }

    @Override
    public String name() {
        return "limitlesstcg.com";
    }

    @Override
    public byte[] getRawImage(ExtractedImageDTO image) {
        return limitlessParser.getImage(image.url());
    }
}
