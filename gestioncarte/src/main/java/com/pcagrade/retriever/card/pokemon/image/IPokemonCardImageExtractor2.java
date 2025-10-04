package com.pcagrade.retriever.card.pokemon.image;

import com.pcagrade.mason.localization.Localization;
import com.pcagrade.retriever.card.pokemon.PokemonCardDTO;
import com.pcagrade.retriever.image.ExtractedImageDTO;

import java.io.IOException;
import java.util.List;

public interface IPokemonCardImageExtractor2 {// identique à IPokemonCardImageExtractor2

    List<ExtractedImageDTO> getImages(PokemonCardDTO card, Localization localization) throws IOException;

    String name();

    byte[] getRawImage(ExtractedImageDTO image);
}
