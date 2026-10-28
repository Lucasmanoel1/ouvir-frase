package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.dto.TrechoLegenda;
import com.lucasmanoel.ouvirfrase.repository.VideoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import com.lucasmanoel.ouvirfrase.service.IndexadorDeVideo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Service
@Profile("ingestao")
@Slf4j
public class IngestaoService {

    private final VideoCsvReader csvReader;
    private final FiltroEmbeddable filtro;
    private final LegendaProvider legendaProvider;
    private final IndexadorDeVideo indexador;
    private final VideoRepository videoRepository;
    private final long pausaMs;

    public IngestaoService(VideoCsvReader csvReader,
                           FiltroEmbeddable filtro,
                           LegendaProvider legendaProvider,
                           IndexadorDeVideo indexador,
                           VideoRepository videoRepository,
                           @Value("${ingestao.pausa-ms:1500}") long pausaMs) {
        this.csvReader = csvReader;
        this.filtro = filtro;
        this.legendaProvider = legendaProvider;
        this.indexador = indexador;
        this.videoRepository = videoRepository;
        this.pausaMs = pausaMs;
    }

    public RelatorioIngestao executar(Path csv) {
        List<VideoSemente> sementes = csvReader.ler(csv);

        List<VideoSemente> novas = new ArrayList<>();
        int jaIndexados = 0;
        for (VideoSemente s : sementes) {
            if (videoRepository.existsByYoutubeId(s.youtubeId())) {
                jaIndexados++;
            } else {
                novas.add(s);
            }
        }

        ResultadoFiltro filtrado = filtro.filtrar(novas);
        Map<VideoSemente, MotivoDescarte> descartados = new LinkedHashMap<>(filtrado.descartados());

        int indexados = 0;
        List<VideoSemente> aprovados = filtrado.aprovados();
        for (int i = 0; i < aprovados.size(); i++) {
            VideoSemente video = aprovados.get(i);
            if (i > 0) {
                pausar();
            }

            Optional<List<TrechoLegenda>> legenda = legendaProvider.buscar(video.youtubeId(), video.idioma());
            if (legenda.isEmpty()) {
                descartar(descartados, video, MotivoDescarte.SEM_LEGENDA);
                continue;
            }

            try {
                int ocorrencias = indexador.indexar(video.youtubeId(), video.titulo(), video.idioma(), legenda.get());
                indexados++;
                log.info("Indexado [{}] {}: {} ocorrências", video.youtubeId(), video.titulo(), ocorrencias);
            } catch (IllegalArgumentException e) {
                descartar(descartados, video, MotivoDescarte.SEM_PALAVRAS);
            }
        }
        return new RelatorioIngestao(sementes.size(), indexados, jaIndexados, descartados);
    }

    private void descartar(Map<VideoSemente, MotivoDescarte> descartados, VideoSemente video, MotivoDescarte motivo) {
        log.warn("Vídeo descartado [{}] {}: {}", video.youtubeId(), video.titulo(), motivo);
        descartados.put(video, motivo);
    }

    private void pausar() {
        if (pausaMs <= 0) {
            return;
        }
        try {
            Thread.sleep(pausaMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ingestão interrompida", e);
        }
    }
}
