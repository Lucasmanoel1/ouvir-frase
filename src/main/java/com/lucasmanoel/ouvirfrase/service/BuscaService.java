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

    /** Primeiro as frases exatas; se sobrar espaço, as aproximadas (>= 70% das palavras, na ordem). */
    @Transactional(readOnly = true)
    public RespostaBusca buscar(String frase) {
        List<String> palavras = normalizador.tokenizar(frase);
        if (palavras.isEmpty()) {
            return new RespostaBusca(List.of());
        }

        List<ResultadoBusca> resultados = new ArrayList<>(exatos(palavras));
        if (palavras.size() >= 2 && resultados.size() < LIMITE_RESULTADOS) {
            resultados.addAll(aproximados(palavras, LIMITE_RESULTADOS - resultados.size()));
        }
        return new RespostaBusca(resultados);
    }

    private List<ResultadoBusca> exatos(List<String> palavras) {
        return buscaRepository.buscarSequencia(palavras, LIMITE_RESULTADOS).stream()
                .map(a -> new ResultadoBusca(a.youtubeId(), a.titulo(),
                        Math.max(0.0, a.inicioSeg() - RECUO_SEG), a.legenda(), 1.0, true))
                .toList();
    }

    private List<ResultadoBusca> aproximados(List<String> palavras, int limite) {
        int n = palavras.size();
        List<String> distintas = palavras.stream().distinct().toList();
        // o vídeo pode perder no máximo (n - mínimo) palavras, então precisa ter quase todas as distintas
        int distintasMinimas = Math.max(1, distintas.size() - (n - casador.minimoDeCasadas(n)));

        Map<Long, List<CasadorPorLacunas.Candidata>> porVideo = new LinkedHashMap<>();
        for (BuscaRepository.OcorrenciaCandidata o
                : buscaRepository.buscarCandidatas(distintas, distintasMinimas, LIMITE_CANDIDATAS)) {
            porVideo.computeIfAbsent(o.videoId(), k -> new ArrayList<>())
                    .add(new CasadorPorLacunas.Candidata(o.palavra(), o.posicao(), o.trechoId(), o.inicioSeg()));
        }

        record Achado(long videoId, CasadorPorLacunas.Casamento casamento) {
        }
        List<Achado> achados = new ArrayList<>();
        porVideo.forEach((videoId, ocorrencias) -> {
            int aceitos = 0;
            for (CasadorPorLacunas.Casamento c : casador.casar(palavras, ocorrencias, 10)) {
                boolean exataContigua = c.casadas() == n && c.span() == n; // essa já veio da busca exata
                if (!exataContigua && aceitos < MAX_APROXIMADOS_POR_VIDEO) {
                    achados.add(new Achado(videoId, c));
                    aceitos++;
                }
            }
        });

        achados.sort(Comparator.comparingDouble((Achado a) -> a.casamento().score()).reversed()
                .thenComparingInt(a -> a.casamento().span()));
        List<Achado> melhores = achados.stream().limit(limite).toList();

        Map<Long, Video> videos = videoRepository
                .findAllById(melhores.stream().map(Achado::videoId).distinct().toList()).stream()
                .collect(Collectors.toMap(Video::getId, v -> v));

        return melhores.stream().map(a -> {
            CasadorPorLacunas.Casamento c = a.casamento();
            Video v = videos.get(a.videoId());
            String legenda = buscaRepository.legenda(a.videoId(), c.primeira().trechoId(), c.ultima().trechoId());
            return new ResultadoBusca(v.getYoutubeId(), v.getTitulo(),
                    Math.max(0.0, c.primeira().inicioSeg() - RECUO_SEG), legenda, c.score(), false);
        }).toList();
    }
}
