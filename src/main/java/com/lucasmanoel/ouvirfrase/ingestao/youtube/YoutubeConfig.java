package com.lucasmanoel.ouvirfrase.ingestao.youtube;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@Profile("ingestao")
@EnableConfigurationProperties(YoutubeProperties.class)
public class YoutubeConfig {

    @Bean
    RestClient youtubeRestClient(YoutubeProperties props) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .requestFactory(fabrica)
                .baseUrl(props.baseUrl())
                .defaultHeader("X-Goog-Api-Key", props.apiKey())
                .build();
    }
}
