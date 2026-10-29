package com.lucasmanoel.ouvirfrase.ingestao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lucasmanoel.ouvirfrase.exception.YouTubeApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lista os vídeos de um canal usando só endpoints baratos da YouTube Data API
 * (channels.list, playlistItems.list e videos.list: 1 unidade de cota cada).
 * Nunca usa search.list, que custa 100 unidades por chamada.
 */
@Component
@Profile("ingestao")
@RequiredArgsConstructor
public class YouTubeCanalClient {

    static final int TAMANHO_LOTE = 50;
    static final Duration DURACAO_MINIMA = Duration.ofSeconds(60); // descarta Shorts

    private static final Pattern HANDLE = Pattern.compile("@([\\w.\\-]+)");
    private static final Pattern ID_CANAL = Pattern.compile("(UC[\\w-]{22})");

    private final RestClient restClient;

    public record Canal(String titulo, String uploadsPlaylistId, String slug) {
    }

    public record VideoDoCanal(String youtubeId, String titulo) {
    }

    /** Aceita @handle, link do canal (youtube.com/@handle) ou o id do canal (UC...). */
    public Canal resolverCanal(String entrada) {
        Matcher handle = HANDLE.matcher(entrada);
        Matcher id = ID_CANAL.matcher(entrada);
        String handleLimpo = handle.find() ? handle.group(1) : null;
        String idCanal = handleLimpo == null && id.find() ? id.group(1) : null;
        String consulta = handleLimpo != null ? handleLimpo : (idCanal != null ? idCanal : entrada.trim());

        RespostaCanais resposta = chamar(uri -> {
            UriBuilder b = uri.path("/channels").queryParam("part", "snippet,contentDetails");
            return (idCanal != null ? b.queryParam("id", idCanal) : b.queryParam("forHandle", consulta)).build();
        }, RespostaCanais.class);

        if (resposta == null || resposta.items() == null || resposta.items().isEmpty()) {
            throw new IllegalArgumentException("Canal não encontrado: " + entrada);
        }
        ItemCanal item = resposta.items().get(0);
        String titulo = item.snippet() != null ? item.snippet().title() : consulta;
        String uploads = item.contentDetails().relatedPlaylists().uploads();
        String base = handleLimpo != null ? handleLimpo : titulo;
        return new Canal(titulo, uploads, slug(base));
    }

    /**
     * Percorre os uploads (do mais novo ao mais antigo) e devolve até {@code max} vídeos úteis:
     * com legenda (o YouTube informa que existe alguma, manual ou automática), com 60 s ou mais,
     * e que ainda não estão em {@code ignorar}.
     */
    public List<VideoDoCanal> listarVideos(String uploadsPlaylistId, Set<String> ignorar, int max) {
        List<VideoDoCanal> uteis = new ArrayList<>();
        String pagina = null;
        do {
            final String token = pagina;
            RespostaPlaylist resposta = chamar(uri -> {
                UriBuilder b = uri.path("/playlistItems").queryParam("part", "snippet")
                        .queryParam("playlistId", uploadsPlaylistId).queryParam("maxResults", TAMANHO_LOTE);
                return (token != null ? b.queryParam("pageToken", token) : b).build();
            }, RespostaPlaylist.class);
            if (resposta == null || resposta.items() == null) {
                break;
            }

            List<VideoDoCanal> candidatos = new ArrayList<>();
            for (ItemPlaylist item : resposta.items()) {
                if (item.snippet() == null || item.snippet().resourceId() == null) {
                    continue;
                }
                String idVideo = item.snippet().resourceId().videoId();
                String titulo = item.snippet().title();
                if (idVideo == null || ignorar.contains(idVideo) || titulo == null
                        || titulo.equals("Private video") || titulo.equals("Deleted video")) {
                    continue;
                }
                candidatos.add(new VideoDoCanal(idVideo, titulo.replaceAll("\\s+", " ").trim()));
            }

            Map<String, DetalhesVideo> detalhes = detalhes(candidatos);
            for (VideoDoCanal c : candidatos) {
                DetalhesVideo d = detalhes.get(c.youtubeId());
                if (d != null && "true".equals(d.caption()) && duracaoSuficiente(d.duration())) {
                    uteis.add(c);
                    if (uteis.size() >= max) {
                        return uteis;
                    }
                }
            }
            pagina = resposta.nextPageToken();
        } while (pagina != null);
        return uteis;
    }

    private Map<String, DetalhesVideo> detalhes(List<VideoDoCanal> videos) {
        Map<String, DetalhesVideo> mapa = new HashMap<>();
        for (int i = 0; i < videos.size(); i += TAMANHO_LOTE) {
            List<VideoDoCanal> lote = videos.subList(i, Math.min(i + TAMANHO_LOTE, videos.size()));
            String ids = String.join(",", lote.stream().map(VideoDoCanal::youtubeId).toList());
            RespostaVideos resposta = chamar(uri -> uri.path("/videos")
                    .queryParam("part", "contentDetails").queryParam("id", ids).build(), RespostaVideos.class);
            if (resposta != null && resposta.items() != null) {
                for (ItemVideo item : resposta.items()) {
                    if (item.id() != null && item.contentDetails() != null) {
                        mapa.put(item.id(), item.contentDetails());
                    }
                }
            }
        }
        return mapa;
    }

    private boolean duracaoSuficiente(String iso) {
        if (iso == null) {
            return false;
        }
        try {
            return Duration.parse(iso).compareTo(DURACAO_MINIMA) >= 0;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    static String slug(String texto) {
        String s = texto.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "canal" : s;
    }

    private <T> T chamar(Function<UriBuilder, URI> uri, Class<T> tipo) {
        try {
            return restClient.get().uri(uri).retrieve().body(tipo);
        } catch (RestClientResponseException e) {
            throw new YouTubeApiException("YouTube Data API respondeu HTTP "
                    + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new YouTubeApiException("Falha de comunicação com a YouTube Data API", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaCanais(List<ItemCanal> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemCanal(SnippetCanal snippet, ConteudoCanal contentDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SnippetCanal(String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConteudoCanal(Playlists relatedPlaylists) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Playlists(String uploads) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaPlaylist(List<ItemPlaylist> items, String nextPageToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemPlaylist(SnippetItem snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SnippetItem(String title, RecursoVideo resourceId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecursoVideo(String videoId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaVideos(List<ItemVideo> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemVideo(String id, DetalhesVideo contentDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DetalhesVideo(String duration, String caption) {
    }
}
