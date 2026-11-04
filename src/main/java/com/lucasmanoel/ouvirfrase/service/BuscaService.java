package com.lucasmanoel.ouvirfrase.service;

import com.lucasmanoel.ouvirfrase.dto.RespostaBusca;
import com.lucasmanoel.ouvirfrase.dto.ResultadoBusca;
import com.lucasmanoel.ouvirfrase.model.Video;
import com.lucasmanoel.ouvirfrase.repository.BuscaRepository;
import com.lucasmanoel.ouvirfrase.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BuscaService {

    static final double RECUO_SEG = 3.0;
    static final int LIMITE_RESULTADOS = 50;
    static final int LIMITE_CANDIDATAS = 20_000;
    static final int MAX_APROXIMADOS_POR_VIDEO = 2;

    private final Normalizador normalizador;
    private final BuscaRepository buscaRepository;
    private final CasadorPorLacunas casador;
    private final VideoRepository videoRepository;
    private final Transliterador transliterador;

    /**
     * Busca no idioma escolhido; se a frase não existe nele (ou está em outra escrita), procura nos outros
     * idiomas indexados que escrevem assim. Quando a frase existe nos dois, vale o escolhido.
     */
    @Transactional(readOnly = true)
    public RespostaBusca buscar(String frase, String idioma) {
        if (normalizador.tokenizar(frase, idioma).isEmpty()) {
            return new RespostaBusca(idioma, List.of(), null);
        }

        Set<Character.UnicodeScript> escritas = Escritas.de(frase);
        if (Escritas.compativel(idioma, escritas)) {
            List<ResultadoBusca> resultados = buscarEm(frase, idioma);
            if (!resultados.isEmpty()) {
                return resposta(frase, idioma, resultados);
            }
        }

        // idiomasDisponiveis() já vem do maior índice para o menor
        List<String> outros = videoRepository.idiomasDisponiveis().stream()
                .filter(i -> !i.equals(idioma) && Escritas.compativel(i, escritas))
                .toList();
        for (String outro : outros) {
            if (!exatos(normalizador.tokenizar(frase, outro), outro).isEmpty()) {
                return resposta(frase, outro, buscarEm(frase, outro));
            }
        }
        for (String outro : outros) {
            List<ResultadoBusca> resultados = buscarEm(frase, outro);
            if (!resultados.isEmpty()) {
                return resposta(frase, outro, resultados);
            }
        }
        return resposta(frase, idioma, List.of());
    }

    /** Primeiro as frases exatas; se sobrar espaço, as aproximadas (>= 70% das palavras, na ordem). */
    private List<ResultadoBusca> buscarEm(String frase, String idioma) {
        List<String> palavras = normalizador.tokenizar(frase, idioma);
        if (palavras.isEmpty()) {
            return List.of();
        }
        List<ResultadoBusca> resultados = new ArrayList<>(exatos(palavras, idioma));
        if (palavras.size() >= 2 && resultados.size() < LIMITE_RESULTADOS) {
            resultados.addAll(aproximados(palavras, idioma, LIMITE_RESULTADOS - resultados.size()));
        }
        return resultados;
    }

    private RespostaBusca resposta(String frase, String idioma, List<ResultadoBusca> resultados) {
        return new RespostaBusca(idioma, resultados, transliterador.transliterar(frase, idioma));
    }

    private List<ResultadoBusca> exatos(List<String> palavras, String idioma) {
        return buscaRepository.buscarSequencia(palavras, idioma, LIMITE_RESULTADOS).stream()
                .map(a -> new ResultadoBusca(a.youtubeId(), a.titulo(),
                        Math.max(0.0, a.inicioSeg() - RECUO_SEG), a.fimSeg(), a.legenda(), 1.0, true,
                        a.legendaAutomatica(), transliterador.transliterar(a.legenda(), idioma)))
                .toList();
    }

    private List<ResultadoBusca> aproximados(List<String> palavras, String idioma, int limite) {
        Map<Long, List<CasadorPorLacunas.Candidata>> porVideo = carregarCandidatas(palavras, idioma);
        List<Achado> achados = casarPorVideo(palavras, porVideo);

        Map<Long, Video> videos = videoRepository
                .findAllById(achados.stream().map(Achado::videoId).distinct().toList()).stream()
                .collect(Collectors.toMap(Video::getId, v -> v));

        // mesmo score: legenda manual antes da automática (false < true), depois o trecho mais compacto
        achados.sort(Comparator.comparingDouble((Achado a) -> a.casamento().score()).reversed()
                .thenComparing((Achado a) -> videos.get(a.videoId()).isLegendaAutomatica())
                .thenComparingInt(a -> a.casamento().span()));

        return achados.stream().limit(limite)
                .map(a -> montarResultado(a.casamento(), a.videoId(), videos.get(a.videoId()), idioma))
                .toList();
    }

    /** Ocorrências das palavras da frase, agrupadas por vídeo (só vídeos que têm palavras suficientes). */
    private Map<Long, List<CasadorPorLacunas.Candidata>> carregarCandidatas(List<String> palavras, String idioma) {
        int n = palavras.size();
        List<String> distintas = palavras.stream().distinct().toList();
        // o vídeo pode perder no máximo (n - mínimo) palavras, então precisa ter quase todas as distintas
        int distintasMinimas = Math.max(1, distintas.size() - (n - casador.minimoDeCasadas(n)));

        Map<Long, List<CasadorPorLacunas.Candidata>> porVideo = new LinkedHashMap<>();
        for (BuscaRepository.OcorrenciaCandidata o
                : buscaRepository.buscarCandidatas(distintas, idioma, distintasMinimas, LIMITE_CANDIDATAS)) {
            porVideo.computeIfAbsent(o.videoId(), k -> new ArrayList<>())
                    .add(new CasadorPorLacunas.Candidata(o.palavra(), o.posicao(), o.trechoId(), o.inicioSeg()));
        }
        return porVideo;
    }

    /** Casamentos aproximados de cada vídeo, sem repetir a frase exata (essa já veio da busca exata). */
    private List<Achado> casarPorVideo(List<String> palavras, Map<Long, List<CasadorPorLacunas.Candidata>> porVideo) {
        int n = palavras.size();
        List<Achado> achados = new ArrayList<>();
        porVideo.forEach((videoId, ocorrencias) -> {
            int aceitos = 0;
            for (CasadorPorLacunas.Casamento c : casador.casar(palavras, ocorrencias, 10)) {
                boolean exataContigua = c.casadas() == n && c.span() == n;
                if (!exataContigua && aceitos < MAX_APROXIMADOS_POR_VIDEO) {
                    achados.add(new Achado(videoId, c));
                    aceitos++;
                }
            }
        });
        return achados;
    }

    private ResultadoBusca montarResultado(CasadorPorLacunas.Casamento c, long videoId, Video v, String idioma) {
        String legenda = buscaRepository.legenda(videoId, c.primeira().trechoId(), c.ultima().trechoId());
        return new ResultadoBusca(v.getYoutubeId(), v.getTitulo(),
                Math.max(0.0, c.primeira().inicioSeg() - RECUO_SEG),
                buscaRepository.fimDoTrecho(c.ultima().trechoId()), legenda, c.score(), false,
                v.isLegendaAutomatica(), transliterador.transliterar(legenda, idioma));
    }

    private record Achado(long videoId, CasadorPorLacunas.Casamento casamento) {
    }

    /** Idiomas com vídeos indexados, do que tem mais vídeos para o que tem menos. */
    @Transactional(readOnly = true)
    public List<String> idiomas() {
        return videoRepository.idiomasDisponiveis();
    }
}
