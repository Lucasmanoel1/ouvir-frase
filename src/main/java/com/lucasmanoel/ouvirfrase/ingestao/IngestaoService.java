package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.dto.TrechoLegenda;
import com.lucasmanoel.ouvirfrase.exception.FalhaNaBuscaDeLegendaException;
import com.lucasmanoel.ouvirfrase.exception.IngestaoInterrompidaException;
import com.lucasmanoel.ouvirfrase.exception.YouTubeApiException;
import com.lucasmanoel.ouvirfrase.repository.VideoRepository;
import com.lucasmanoel.ouvirfrase.service.IndexadorDeVideo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orquestra a ingestão: pasta de CSVs -> filtro embeddable -> legenda -> índice.
 * Problema do vídeo (sem legenda, não incorporável...) = descarta e segue.
 * Falha de infraestrutura (FalhaNaBuscaDeLegendaException, YouTubeApiException) = para tudo,
 * mas devolve o relatório parcial dentro de {@link IngestaoInterrompidaException}.
 */
@Service
@Profile("ingestao")
@Slf4j
public class IngestaoService {

    private final CatalogoDeVideos catalogo;
    private final FiltroEmbeddable filtro;
    private final LegendaProvider legendaProvider;
    private final IndexadorDeVideo indexador;
    private final VideoRepository videoRepository;
    private final long pausaMs;
    private final int limite;

    public IngestaoService(CatalogoDeVideos catalogo,
                           FiltroEmbeddable filtro,
                           LegendaProvider legendaProvider,
                           IndexadorDeVideo indexador,
                           VideoRepository videoRepository,
                           @Value("${ingestao.pausa-ms:1500}") long pausaMs,
                           @Value("${ingestao.limite:0}") int limite) {
        this.catalogo = catalogo;
        this.filtro = filtro;
        this.legendaProvider = legendaProvider;
        this.indexador = indexador;
        this.videoRepository = videoRepository;
        this.pausaMs = pausaMs;
        this.limite = limite;
    }

    public RelatorioIngestao executar(Path dados) {
        List<VideoSemente> sementes = catalogo.carregar(dados);

        List<VideoSemente> novas = new ArrayList<>();
        int jaIndexados = 0;
        for (VideoSemente s : sementes) {
            if (videoRepository.existsByYoutubeId(s.youtubeId())) {
                jaIndexados++;
                completarCanal(s);
            } else {
                novas.add(s);
            }
        }

        Map<VideoSemente, MotivoDescarte> descartados = new LinkedHashMap<>();
        int indexados = 0;
        int adiados = 0;
        try {
            ResultadoFiltro filtrado = filtro.filtrar(novas);
            descartados.putAll(filtrado.descartados());

            List<VideoSemente> aprovados = filtrado.aprovados();
            for (int i = 0; i < aprovados.size(); i++) {
                if (limite > 0 && i >= limite) {
                    adiados = aprovados.size() - i;
                    break;
                }
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
                    int ocorrencias = indexador.indexar(video.youtubeId(), video.titulo(), video.idioma(),
                            video.canal(), legenda.get());
                    indexados++;
                    log.info("Indexado [{}] {}: {} ocorrências", video.youtubeId(), video.titulo(), ocorrencias);
                } catch (IllegalArgumentException e) {
                    descartar(descartados, video, MotivoDescarte.SEM_PALAVRAS);
                }
            }
        } catch (FalhaNaBuscaDeLegendaException | YouTubeApiException e) {
            throw new IngestaoInterrompidaException(
                    new RelatorioIngestao(sementes.size(), indexados, jaIndexados, 0, descartados), e);
        }
        return new RelatorioIngestao(sementes.size(), indexados, jaIndexados, adiados, descartados);
    }

    /** Vídeos indexados antes da coluna 'canal' existir ganham o canal ao reaparecer no catálogo. */
    private void completarCanal(VideoSemente semente) {
        if (semente.canal() == null) {
            return;
        }
        videoRepository.findByYoutubeId(semente.youtubeId())
                .filter(v -> v.getCanal() == null)
                .ifPresent(v -> {
                    v.setCanal(semente.canal());
                    videoRepository.save(v);
                });
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
