package com.lucasmanoel.ouvirfrase.ingestao.youtube;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Porta de entrada da YouTube Data API: faz o GET e converte qualquer falha HTTP ou de rede em
 * {@link YouTubeApiException} (falha de infraestrutura, que interrompe a ingestão).
 */
@Component
@Profile("ingestao")
@RequiredArgsConstructor
public class YouTubeApi {

    /** Máximo de ids por chamada (videos.list) e de itens por página (playlistItems.list). */
    static final int TAMANHO_LOTE = 50;

    private final RestClient restClient;

    <T> T get(Function<UriBuilder, URI> uri, Class<T> tipo) {
        try {
            return restClient.get().uri(uri).retrieve().body(tipo);
        } catch (RestClientResponseException e) {
            throw new YouTubeApiException("YouTube Data API respondeu HTTP "
                    + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new YouTubeApiException("Falha de comunicação com a YouTube Data API", e);
        }
    }

    /** Quebra a lista em pedaços de até {@link #TAMANHO_LOTE} itens. */
    static <T> List<List<T>> emLotes(List<T> itens) {
        List<List<T>> lotes = new ArrayList<>();
        for (int i = 0; i < itens.size(); i += TAMANHO_LOTE) {
            lotes.add(itens.subList(i, Math.min(i + TAMANHO_LOTE, itens.size())));
        }
        return lotes;
    }
}
