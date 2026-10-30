package com.lucasmanoel.ouvirfrase.ingestao.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
@Profile("ingestao")
@RequiredArgsConstructor
public class YouTubeDataClient {

    static final int TAMANHO_LOTE = 50;

    private final RestClient restClient;


    public Map<String, Boolean> consultarEmbeddable(Collection<String> ids) {
        List<String> unicos = new ArrayList<>(new LinkedHashSet<>(ids));
        Map<String, Boolean> resultado = new HashMap<>();

        for (int i = 0; i < unicos.size(); i += TAMANHO_LOTE) {
            List<String> lote = unicos.subList(i, Math.min(i + TAMANHO_LOTE, unicos.size()));
            consultarLote(lote, resultado);
        }
        return resultado;
    }

    private void consultarLote(List<String> lote, Map<String, Boolean> resultado) {
        RespostaVideos resposta;
        try {
            resposta = restClient.get()
                    .uri(uri -> uri.path("/videos")
                            .queryParam("part", "status")
                            .queryParam("id", String.join(",", lote))
                            .build())
                    .retrieve()
                    .body(RespostaVideos.class);
        } catch (RestClientResponseException e) {
            throw new YouTubeApiException("YouTube Data API respondeu HTTP "
                    + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new YouTubeApiException("Falha de comunicação com a YouTube Data API", e);
        }

        if (resposta == null || resposta.items() == null) {
            return;
        }
        for (Item item : resposta.items()) {
            boolean embeddable = item.status() != null
                    && Boolean.TRUE.equals(item.status().embeddable());
            if (item.id() != null && !item.id().isBlank()) {
                resultado.put(item.id(), embeddable);
            }

        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaVideos(List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String id, Status status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Status(Boolean embeddable) {
    }
}