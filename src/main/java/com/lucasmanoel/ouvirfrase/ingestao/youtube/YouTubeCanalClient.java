package com.lucasmanoel.ouvirfrase.ingestao.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lucasmanoel.ouvirfrase.ingestao.catalogo.ListaDeVideos;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriBuilder;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lista os vídeos de um canal ou de uma playlist usando só endpoints baratos da YouTube Data API
 * (channels.list, playlists.list, playlistItems.list e videos.list: 1 unidade de cota cada).
 * Nunca usa search.list, que custa 100 unidades por chamada.
 */
@Component
@Profile("ingestao")
@RequiredArgsConstructor
public class YouTubeCanalClient {

    static final Duration DURACAO_MINIMA = Duration.ofSeconds(60); // descarta Shorts

    private static final Pattern HANDLE = Pattern.compile("@([\\w.\\-]+)");
    private static final Pattern ID_CANAL = Pattern.compile("(UC[\\w-]{22})");
    private static final Pattern LISTA = Pattern.compile("[?&]list=([\\w-]+)");
    /** Assistir mais tarde, Gostei e músicas curtidas: listas pessoais que a API não lê. */
    private static final Set<String> LISTAS_PESSOAIS = Set.of("WL", "LL", "LM");

    private final YouTubeApi api;

    /** De onde vêm os vídeos: a playlist a percorrer e o nome do CSV (sem a extensão). */
    public record Fonte(String titulo, String playlistId, String nomeArquivo) {
    }

    public record VideoDoCanal(String youtubeId, String titulo) {
    }

    /** Aceita link de playlist (com {@code list=}), @handle, link do canal (youtube.com/@handle) ou o id do canal (UC...). */
    public Fonte resolverFonte(String entrada) {
        Matcher lista = LISTA.matcher(entrada);
        return lista.find() ? resolverPlaylist(lista.group(1), entrada) : resolverCanal(entrada);
    }

    private Fonte resolverCanal(String entrada) {
        Matcher handle = HANDLE.matcher(entrada);
        Matcher id = ID_CANAL.matcher(entrada);
        String handleLimpo = handle.find() ? handle.group(1) : null;
        String idCanal = handleLimpo == null && id.find() ? id.group(1) : null;
        String consulta = handleLimpo != null ? handleLimpo : (idCanal != null ? idCanal : entrada.trim());

        RespostaCanais resposta = api.get(uri -> {
            UriBuilder b = uri.path("/channels").queryParam("part", "snippet,contentDetails");
            return (idCanal != null ? b.queryParam("id", idCanal) : b.queryParam("forHandle", consulta)).build();
        }, RespostaCanais.class);

        if (resposta == null || resposta.items() == null || resposta.items().isEmpty()) {
            throw new IllegalArgumentException("Canal não encontrado: " + entrada);
        }
        ItemCanal item = resposta.items().get(0);
        String titulo = item.snippet() != null ? item.snippet().title() : consulta;
        String uploads = item.contentDetails().relatedPlaylists().uploads();
        // handle ou título podem não ter letra ASCII: o id do canal (vindo da resposta) é a reserva do nome
        return new Fonte(titulo, uploads, slug(handleLimpo != null ? handleLimpo : titulo, item.id()));
    }

    private Fonte resolverPlaylist(String playlistId, String entrada) {
        if (playlistId.startsWith("RD") || LISTAS_PESSOAIS.contains(playlistId)) {
            throw new IllegalArgumentException("Mixes automáticos e listas pessoais (Assistir mais tarde, Gostei) "
                    + "não podem ser lidos pela API. Use uma playlist pública.");
        }
        RespostaPlaylists resposta = api.get(uri -> uri.path("/playlists")
                .queryParam("part", "snippet").queryParam("id", playlistId).build(), RespostaPlaylists.class);
        if (resposta == null || resposta.items() == null || resposta.items().isEmpty()) {
            throw new IllegalArgumentException("Playlist não encontrada ou privada: " + entrada);
        }
        Snippet snippet = resposta.items().get(0).snippet();
        String titulo = snippet != null && snippet.title() != null ? snippet.title() : playlistId;
        return new Fonte(titulo, playlistId, ListaDeVideos.PREFIXO_PLAYLIST + slug(titulo, playlistId));
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
            RespostaPlaylist resposta = api.get(uri -> {
                UriBuilder b = uri.path("/playlistItems").queryParam("part", "snippet")
                        .queryParam("playlistId", uploadsPlaylistId).queryParam("maxResults", YouTubeApi.TAMANHO_LOTE);
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
        for (List<VideoDoCanal> lote : YouTubeApi.emLotes(videos)) {
            String ids = String.join(",", lote.stream().map(VideoDoCanal::youtubeId).toList());
            RespostaVideos resposta = api.get(uri -> uri.path("/videos")
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

    /** Nome de arquivo seguro; se o texto não tiver letra ASCII, usa a reserva (o id do canal ou da playlist). */
    static String slug(String texto, String reserva) {
        String s = normalizar(texto);
        if (s.isEmpty()) {
            s = normalizar(reserva);
        }
        return s.isEmpty() ? "canal" : s;
    }

    private static String normalizar(String texto) {
        return texto == null ? ""
                : texto.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaCanais(List<ItemCanal> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemCanal(String id, Snippet snippet, ConteudoCanal contentDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Snippet(String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaPlaylists(List<Playlist> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Playlist(Snippet snippet) {
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
