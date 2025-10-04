package com.pcagrade.painer.client;

import com.github.f4b6a3.ulid.Ulid;
import com.pcagrade.mason.web.client.IWebClientConfigurer;
import com.pcagrade.painter.common.image.IImageService;
import com.pcagrade.painter.common.image.ImageDTO;
import com.pcagrade.painter.common.image.ImageHelper;
import jakarta.annotation.Nonnull;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;


import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;

public class ClientImageService implements IImageService {

    private final WebClient webClient;

    public ClientImageService(@Nonnull PainterClientProperties properties, IWebClientConfigurer webClientConfigurer) {
        this.webClient = WebClient.builder()
                .baseUrl(properties.baseUrl() + "/api/images/")
                .apply(webClientConfigurer.json()
                        .oauth2(properties.oauth2RegistrationId())
                        .build())
                .build();
    }

    @Override
    public Optional<ImageDTO> findById(Ulid id) {
        if (id == null) {
            return Optional.empty();
        }

        return webClient.get()
                .uri(builder -> builder.path("/{id}").build(id))
                .retrieve()
                .bodyToMono(ImageDTO.class)
                .blockOptional();
    }

    @Override
    public ImageDTO create(String folder, String source, boolean internal, @Nonnull byte[] sourceImage) {
        var builder = new MultipartBodyBuilder();

        builder.part("file", new ByteArrayResource(sourceImage)).filename("image.png");
        builder.part("path", folder);
        builder.part("source", source);
        builder.part("internal", internal);

        return webClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(ImageDTO.class)
                .block();
    }

    @Override
    public ImageDTO create(String folder, String source, boolean internal, @Nonnull BufferedImage sourceImage) throws IOException {
        return create(folder, source, internal, ImageHelper.toByteArray(sourceImage, "png"));
    }

    @Override
    public ImageDTO create(String folder, String source, boolean internal, @Nonnull byte[] sourceImage, String modifiedUrl) throws IOException {
        var builder = new MultipartBodyBuilder();

        builder.part("file", new ByteArrayResource(sourceImage)).filename("image.png");
        builder.part("path", folder);
        builder.part("source", source);
        builder.part("internal", internal);
        builder.part("modifiedUrl", modifiedUrl);

        return webClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(ImageDTO.class)
                .block();
    }

    @Override
    public ImageDTO create(String folder, String source, boolean internal, @Nonnull BufferedImage sourceImage, String modifiedUrl) throws IOException {
        return create(folder, source, internal, ImageHelper.toByteArray(sourceImage, "png"), modifiedUrl);
    }

//    @Override
//    public Optional<ImageDTO> findLastByPathAndInternal(String path) {
//        return webClient.get()
//                .uri(builder -> builder.path("/last/internal").queryParam("path", path).build())
//                .retrieve()
//                .bodyToMono(ImageDTO.class)
//                .blockOptional();
//    }
//
//    @Override
//    public Optional<ImageDTO> findLastByPathAndExternal(String path) {
//        return webClient.get()
//                .uri(builder -> builder.path("/last/external").queryParam("path", path).build())
//                .retrieve()
//                .bodyToMono(ImageDTO.class)
//                .blockOptional();
//    }

//    @Override
//    public Optional<ImageDTO> findLastByPathAndInternal(String path) {
//        return imageRepository.findLastByPathAndInternal(path)
//                .map(image -> new ImageDTO(
//                        image.getId(),
//                        image.getPath(),
//                        image.getSource(),
//                        Instant.now(),
//                        image.isInternal(),
//                        image.getModifiedUrl()
//                ));
//    }
//
//    @Override
//    public Optional<ImageDTO> findLastByPathAndExternal(String path) {
//        return imageRepository.findLastByPathAndExternal(path)
//                .map(image -> new ImageDTO(
//                        image.getId(),
//                        image.getPath(),
//                        image.getSource(),
//                        Instant.now(),
//                        image.isInternal(),
//                        image.getModifiedUrl()
//                ));
//    }

}
