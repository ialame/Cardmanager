package com.pcagrade.retriever.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${painter.image.storage-path}")
    private String storagePath;

    // Rien à faire ici, car UlidConverter est enregistré via UlidConverterInitializer
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "DELETE");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Maps any request to /images/** to files inside storagePath
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + storagePath + "/");
    }
}