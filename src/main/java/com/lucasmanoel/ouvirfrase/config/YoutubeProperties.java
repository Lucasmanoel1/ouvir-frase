package com.lucasmanoel.ouvirfrase.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "youtube")
public record YoutubeProperties (
        @NotBlank String apiKey,
        @DefaultValue("https://www.googleapis.com/youtube/v3") String baseUrl){
}
