package com.lucasmanoel.ouvirfrase.ingestao.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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

    private final YouTubeApi api;

    public Map<String, Boolean> consultarEmbeddable(Collection<String> ids) {
        List<String> unicos = new ArrayList<>(new LinkedHashSet<>(ids));
        Map<String, Boolean> resultado = new HashMap<>();

        for (List<String> lote : YouTubeApi.emLotes(unicos)) {
            consultarLote(lote, resultado);
        }
        return resultado;
    }

    private void consultarLote(List<String> lote, Map<String, Boolean> resultado) {
        RespostaVideos resposta = api.get(uri -> uri.path("/videos")
                .queryParam("part", "status")
                .queryParam("id", String.join(",", lote))
                .build(), RespostaVideos.class);

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