package com.pcagrade.retriever.card.pokemon.image;

import com.github.f4b6a3.ulid.Ulid;
import com.pcagrade.mason.localization.Localization;
import com.pcagrade.painter.common.image.IImageService;
import com.pcagrade.painter.common.image.ImageDTO;
import com.pcagrade.painter.common.image.card.CardImageDTO;
import com.pcagrade.painter.common.image.card.ICardImageService;
import com.pcagrade.painter.common.image.legacy.ILegacyImageService;
import com.pcagrade.painter.common.publicdata.PublicUrlService;
import com.pcagrade.retriever.card.pokemon.PokemonCardDTO;
import com.pcagrade.retriever.card.pokemon.PokemonCardService;
import com.pcagrade.retriever.card.pokemon.set.PokemonSetService;
import com.pcagrade.retriever.card.pokemon.source.limitless.LimitlessService;
import com.pcagrade.retriever.card.pokemon.source.mypcards.PokemonmypcardsService;
import com.pcagrade.retriever.card.pokemon.source.pokemoncom.PokemoncomService;
import com.pcagrade.retriever.image.ExtractedImageDTO;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.sql.DataSource;
import java.util.*;

@Service
public class PokemonCardImageService {

    private static final Logger LOGGER = LogManager.getLogger(PokemonCardImageService.class);


    private static final Comparator<ExtractedImageDTO> IMAGE_COMPARATOR = Comparator.<ExtractedImageDTO, Boolean>comparing
                    (i -> !i.hasSource(LimitlessService.NAME))
            .thenComparing(i -> !i.hasSource(PokemoncomService.NAME))
            .thenComparing(i -> !i.hasSource(PokemonmypcardsService.NAME))
            .thenComparing(i -> !i.hasSource(CurrentPokemonCardImageExtractor.NAME))
            ;
    //.thenComparing(i -> !i.hasSource(LegacyPokemonCardImageExtractor.NAME))
    //.thenComparing(i -> !i.hasSource("pokellector"));

    //    @Autowired
    //private List<IPokemonCardImageExtractor> cardImageExtractors;
    @Autowired
    private List<IPokemonCardImageExtractor2> cardImageExtractors;
    //private List<PokemoncomService> cardImageExtractors;
    //private List<LimitlessService> cardImageExtractors;
    //private List<PokemonmypcardsService> cardImageExtractors;
    //private List<CurrentPokemonCardImageExtractor> cardImageExtractors;
    @Autowired
    private PokemonSetService pokemonSetService;
    @Autowired
    private PokemonCardService pokemonCardService;
    @Autowired
    private IImageService imageService;
    @Autowired
    private ILegacyImageService legacyImageService;
    @Autowired
    private ICardImageService cardImageService;
    @Autowired
    private PublicUrlService publicUrlService;
    @Autowired
    private DataSource dataSource;

    @Value("${painter.image.storage-path}")
    private String storagePath;


    public Page<ExtractedPokemonImagesDTO> extractImages(Ulid setId, Pageable pageable, Localization localization) {
        var setOpt = pokemonSetService.findSet(setId);

        if (setOpt.isEmpty()) {
            return Page.empty();
        }

        var images = getCards(setId, pageable).map(c -> new ExtractedPokemonImagesDTO(c.getId(), getImages(c, localization)));

        LOGGER.info("Extracted {} images ({}) for {} cards in set {}",
                () -> images.stream().mapToInt(i -> i.images().size()).sum(),
                () -> FileUtils.byteCountToDisplaySize(images.stream().mapToInt(ExtractedPokemonImagesDTO::size).sum()),
                images::getSize,
                () -> setId);
        return images;
    }

    @Nonnull
    private List<ExtractedImageDTO> getImages(PokemonCardDTO card, Localization localization) {
        List<ExtractedImageDTO> images = new ArrayList<>();

        // Helper to check if a URL is reachable
        Function<String, Boolean> urlValidator = (String url) -> {
            try {
                java.net.URL u = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                int code = conn.getResponseCode();
                return code >= 200 && code < 400;
            } catch (Exception e) {
                LOGGER.warn("Invalid image URL {}: {}", url, e.toString());
                return false;
            }
        };

        // 1️⃣ DB images
        cardImageService.findAllByCardId(card.getId()).stream()
                .filter(ci -> ci.localization().equals(localization.getCode()))
                .forEach(ci -> {

                    Path imagePath = Path.of("/home/rahafalhajjaj/images", ci.fichier());
                    // 1️⃣ Check internal image (local storage)
                    if (hasInternalImage(ci.fichier()) && Files.exists(imagePath)) {
                        String localUrl = "/images/" + ci.fichier();
                        LOGGER.info("Using local image for card {} in locale {}: {}", ci.cardId(), ci.localization(), localUrl);
                        images.add(new ExtractedImageDTO(
                                localization,
                                CurrentPokemonCardImageExtractor.NAME,
                                localUrl,
                                true,  // internal
                                null,
                                "Not Applicable"
                        ));

                    }

                     // 2️⃣ Check external image (DB/external URL)

                    String modifiedUrl = hasExternalImage(ci.fichier());
                    Optional<String> sourceOpt = cardImageService.getFullImageUrlByCardAndLocalization(ci.cardId(), ci.localization());
                    if (modifiedUrl != null && sourceOpt.isPresent()) {
                        String source = sourceOpt.get();
                        LOGGER.info("Using external image for card {} in locale {}: {}", ci.cardId(), ci.localization(), source);
                        images.add(new ExtractedImageDTO(
                                localization,
                                CurrentPokemonCardImageExtractor.NAME,
                                source,
                                false,  // external
                                null,
                                modifiedUrl
                            ));
                    } else {
                        LOGGER.warn("No external URL found for card {} in locale {}", ci.cardId(), ci.localization());
                    }
                });

        // 2️⃣ External extractors
        cardImageExtractors.stream()
                .<ExtractedImageDTO>mapMulti((extractor, downstream) -> {
                    try {
                        extractor.getImages(card, localization).forEach(img -> {
                            if (img.url() != null && urlValidator.apply(img.url())) {
                                downstream.accept(img);
                            } else {
                                LOGGER.warn("Skipping external image from {} for card {}: {}", extractor.name(), card.getId(), img.url());
                            }
                        });
                    } catch (Exception ex) {
                        LOGGER.warn("Extractor {} failed for card {}: {}", extractor.name(), card.getId(), ex.toString());
                    }
                })
                .distinct()
                .sorted(IMAGE_COMPARATOR)
                .forEach(images::add);

        return images;
    }

    private Page<PokemonCardDTO> getCards(Ulid setId, Pageable pageable) {
        var allCards = pokemonCardService.getAllCardsInSet(setId);
        var size = allCards.size();
        var start = (int) pageable.getOffset();

        if (start >= size) {
            return Page.empty();
        }
        var end = Math.min(start + pageable.getPageSize(), size);

        return new PageImpl<>(allCards.subList(start, end), pageable, allCards.size());
    }


    public void setImage(Ulid cardId, ExtractedImageDTO image) {
        if (image.hasSource(CurrentPokemonCardImageExtractor.NAME)) {
            LOGGER.debug("Ignoring current image for card {}", cardId);
            return;
        }

        LOGGER.info("Setting image for card {}", cardId);

        try {
            ImageDTO imageDTO = null;
            if (image.hasSource(LegacyPokemonCardImageExtractor.NAME)) {
                imageDTO = legacyImageService.restoreImage("cards/pokemon/", image.url(), image.internal()).orElse(null);
            }
            if (imageDTO == null) {
                imageDTO = imageService.create(
                        "cards/pokemon/",
                        image.internal() ? "" : image.url(),
                        image.internal(),
                        getRawImage(image),
                        image.modifiedUrl()
                );
            }
            cardImageService.saveCardImage(
                    new CardImageDTO(
                            cardId,
                            image.localization().getCode(),
                            imageDTO.id(),
                            imageDTO.path()
                    )
            );
        } catch (Exception e) {
            LOGGER.error("Failed to set image for card {}", cardId, e);
        }
    }

    public ExtractedImageDTO reloadImage(ExtractedImageDTO image) {
        if (image.hasSource(CurrentPokemonCardImageExtractor.NAME, LegacyPokemonCardImageExtractor.NAME)) {
            return image;
        }

        try {
            return new ExtractedImageDTO(image.localization(), image.source(), image.url(), image.internal(), Base64.getEncoder().encodeToString(getRawImage(image)), image.modifiedUrl());
        } catch (Exception e) {
            LOGGER.error("Failed to reload image {}", image, e);
            return image;
        }
    }

    private byte[] getRawImage(ExtractedImageDTO image) {
        if (StringUtils.isNotBlank(image.base64Image())) {
            return Base64.getDecoder().decode(image.base64Image());
        }

        return cardImageExtractors.stream()
                .filter(e -> image.hasSource(e.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No image extractor found for source " + image.source()))
                .getRawImage(image);
    }

    public Page<PokemonCardDTO> findCardsWithoutImages(Ulid setId, Pageable pageable, Localization localization) {
        // Get a page of all cards in the set
        Page<PokemonCardDTO> allCardsPage = getCards(setId, pageable);

        // Filter cards with no images for the given localeF
        List<PokemonCardDTO> filteredCards = allCardsPage.getContent().stream()
                .filter(card -> cardImageService.findAllByCardId(card.getId()).stream()
                        .noneMatch(img -> img.localization().equals(localization.getCode())))
                .toList();

        // Return a new PageImpl of filtered cards with original pageable and total count
        return new PageImpl<>(filteredCards, pageable, allCardsPage.getTotalElements());
    }

    private boolean isValidImageUrl(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) u.openConnection();
            connection.setRequestMethod("HEAD"); // faster than GET
            connection.setConnectTimeout(3000);  // 3 sec timeout
            connection.setReadTimeout(3000);
            int code = connection.getResponseCode();
            return code >= 200 && code < 400; // 2xx or 3xx are OK
        } catch (Exception e) {
            LOGGER.warn("Invalid image URL {}: {}", url, e.toString());
            return false;
        }
    }

    public String getExternalModifiedUrl(String imagePath) {
        String modifiedUrl = null;

        String sql = "SELECT modified_url FROM dev.image WHERE path = ? AND internal = false ORDER BY creation_date DESC LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, imagePath);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                modifiedUrl = rs.getString("modified_url");
                System.out.println("Modified URL found: " + modifiedUrl);
            } else {
                System.out.println("No external image found with path: " + imagePath);
            }
        } catch (SQLException e) {
            System.err.println("Database error for path: " + imagePath);
            e.printStackTrace();
        }

        return modifiedUrl;
    }

    private boolean hasInternalImage(String imagePath) {
        String sql = "SELECT 1 FROM dev.image WHERE path = ? AND internal = true LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, imagePath);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                LOGGER.debug("Internal image found for path: {}", imagePath);
                return true;
            } else {
                LOGGER.debug("No internal image found for path: {}", imagePath);
                return false;
            }

        } catch (SQLException e) {
            LOGGER.error("Database error checking internal image for path: {}", imagePath, e);
            return false; // always return something to satisfy method contract
        }
    }


    private String hasExternalImage(String imagePath) {
        String sql = "SELECT modified_url FROM dev.image WHERE path = ? AND internal = false LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, imagePath);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String modifiedUrl = rs.getString("modified_url"); // must match DB column
                LOGGER.debug("External image found for path: {}, modifiedUrl={}", imagePath, modifiedUrl);
                return modifiedUrl;
            } else {
                LOGGER.debug("No external image found for path: {}", imagePath);
                return null;
            }

        } catch (SQLException e) {
            LOGGER.error("Database error checking external image for path: {}", imagePath, e);
            return null;
        }
    }

}
