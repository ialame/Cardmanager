package com.pcagrade.retriever.card.pokemon.image.web;

import com.github.f4b6a3.ulid.Ulid;
import com.pcagrade.mason.localization.Localization;
import com.pcagrade.painter.common.image.card.CardImageDTO;
import com.pcagrade.painter.common.image.card.ICardImageService;
import com.pcagrade.retriever.card.pokemon.PokemonCardDTO;
import com.pcagrade.retriever.card.pokemon.image.ExtractedPokemonImagesDTO;
import com.pcagrade.retriever.card.pokemon.image.PokemonCardImageService;
import com.pcagrade.retriever.image.ExtractedImageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

import java.util.*;

@RestController
@RequestMapping("/api/cards/pokemon")
public class PokemonCardImageRestController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PokemonCardImageRestController.class);

    @Autowired
    private PokemonCardImageService pokemonCardImageService;

    @Autowired
    private ICardImageService cardImageService;


    @GetMapping("/images/extract/sets/{setId}")
    public Page<ExtractedPokemonImagesDTO> extractImages(
            @PathVariable Ulid setId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String locale) {
            Localization localization = Localization.getByCode(locale);
        System.out.printf("Extracting images for setId: %s, page: %s, size: %s, locale: %s", setId, page, size, locale);
        if (localization == null) {
            localization = Localization.USA; // ou une autre locale par défaut
        }
            return pokemonCardImageService.extractImages(setId, Pageable.ofSize(Math.min(25, size)).withPage(page),localization);
    }

    @PostMapping("/images/reload")
    public ExtractedImageDTO reloadImage(@RequestBody ExtractedImageDTO image) {
        return pokemonCardImageService.reloadImage(image);
    }
//ImageHelper.toBufferedImage(sourceImage).getHeight()
    @PutMapping("/{cardId}/images")
    public void setImage(@PathVariable Ulid cardId, @RequestBody ExtractedImageDTO image) {
        pokemonCardImageService.setImage(cardId, image);
    }

    @GetMapping("/images/missing/sets/{setId}")
    public Page<PokemonCardDTO> extractCardsWithoutImages(
            @PathVariable Ulid setId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String locale) {

        Localization localization = Localization.getByCode(locale);
        if (localization == null) {
            localization = Localization.USA;
        }

        return pokemonCardImageService.findCardsWithoutImages(setId, Pageable.ofSize(Math.min(25, size)).withPage(page), localization);
    }
}