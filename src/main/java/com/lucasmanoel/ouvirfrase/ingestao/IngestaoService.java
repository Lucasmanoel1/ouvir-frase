package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.ingestao.catalogo.CatalogoDeVideos;
import com.lucasmanoel.ouvirfrase.ingestao.catalogo.VideoSemente;
import com.lucasmanoel.ouvirfrase.ingestao.indexacao.IndexadorDeVideo;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.FalhaNaBuscaDeLegendaException;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.LegendaBaixada;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.LegendaProvider;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.TrechoLegenda;
import com.lucasmanoel.ouvirfrase.ingestao.youtube.YouTubeApiException;
import com.lucasmanoel.ouvirfrase.model.VideoDescartado;
import com.lucasmanoel.ouvirfrase.repository.VideoDescartadoRepository;
import com.lucasmanoel.ouvirfrase.repository.VideoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orquestra a ingestão: pasta de CSVs -> filtro embeddable -> legenda -> índice.
 * Problema do vídeo (sem legenda, não incorporável...) = descarta e segue.
 * Todo descarte é gravado em {@code video_descartado}: nas rodadas seguintes o vídeo nem chega ao filtro.
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
    private final VideoDescartadoRepository descartadoRepository;
    private final long pausaMs;
    private final int limite;
    private final String idiomaDeTraducao;

    public IngestaoService(CatalogoDeVideos catalogo,
                           FiltroEmbeddable filtro,
                           LegendaProvider legendaProvider,
                           IndexadorDeVideo indexador,
                           VideoRepository videoRepository,
                           VideoDescartadoRepository descartadoRepository,
                           @Value("${ingestao.pausa-ms:1500}") long pausaMs,
                           @Value("${ingestao.limite:0}") int limite,
                           @Value("${ingestao.traduzir:}") String idiomaDeTraducao) {
        this.catalogo = catalogo;
        this.filtro = filtro;
        this.legendaProvider = legendaProvider;
        this.indexador = indexador;
        this.videoRepository = videoRepository;
        this.descartadoRepository = descartadoRepository;
        this.pausaMs = pausaMs;
        this.limite = limite;
        this.idiomaDeTraducao = idiomaDeTraducao == null ? "" : idiomaDeTraducao.trim();
    }

    public RelatorioIngestao executar(Path dados) {
        List<VideoSemente> sementes = catalogo.carregar(dados);

        List<VideoSemente> novas = new ArrayList<>();
        List<VideoSemente> semTraducao = new ArrayList<>();
        int jaIndexados = 0;
        int jaDescartados = 0;
        for (VideoSemente s : sementes) {
            if (videoRepository.existsByYoutubeId(s.youtubeId())) {
                jaIndexados++;
                completarCanal(s);
                if (precisaTraduzir(s)) {
                    semTraducao.add(s);
                }
            } else if (descartadoRepository.existsById(s.youtubeId())) {
                jaDescartados++;
            } else {
                novas.add(s);
            }
        }

        Map<VideoSemente, MotivoDescarte> descartados = new LinkedHashMap<>();
        int indexados = 0;
        int adiados = 0;
        int traduzidos = 0;
        try {
            ResultadoFiltro filtrado = filtro.filtrar(novas);
            descartados.putAll(filtrado.descartados());
            filtrado.descartados().forEach(this::registrarDescarte);

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

                Optional<LegendaBaixada> legenda = legendaProvider.buscar(video.youtubeId(), video.idioma());
                if (legenda.isEmpty()) {
                    descartar(descartados, video, MotivoDescarte.SEM_LEGENDA);
                    continue;
                }

                try {
                    boolean automatica = legenda.get().automatica();
                    int ocorrencias = indexador.indexar(video.youtubeId(), video.titulo(), video.idioma(),
                            video.canal(), legenda.get().trechos(), automatica);
                    indexados++;
                    log.info("Indexado [{}] {}{}: {} ocorrências", video.youtubeId(), video.titulo(),
                            automatica ? " (legenda automática)" : "", ocorrencias);
                } catch (IllegalArgumentException e) {
                    descartar(descartados, video, MotivoDescarte.SEM_PALAVRAS);
                    continue;
                }
                if (precisaTraduzir(video)) {
                    traduzidos += traduzir(video);
                }
            }

            // vídeos indexados antes da tradução existir: completa só a tradução
            for (int i = 0; i < semTraducao.size() && (limite <= 0 || i < limite); i++) {
                traduzidos += traduzir(semTraducao.get(i));
            }
            if (limite > 0 && semTraducao.size() > limite) {
                log.info("Tradução adiada pelo limite: {} vídeos (rode de novo)", semTraducao.size() - limite);
            }
        } catch (FalhaNaBuscaDeLegendaException | YouTubeApiException e) {
            throw new IngestaoInterrompidaException(
                    new RelatorioIngestao(sementes.size(), indexados, jaIndexados, jaDescartados, 0, descartados), e);
        }
        if (!idiomaDeTraducao.isEmpty()) {
            log.info("Traduzidos agora para '{}': {}", idiomaDeTraducao, traduzidos);
        }
        return new RelatorioIngestao(sementes.size(), indexados, jaIndexados, jaDescartados, adiados, descartados);
    }

    private boolean precisaTraduzir(VideoSemente semente) {
        if (idiomaDeTraducao.isEmpty() || semente.idioma().equalsIgnoreCase(idiomaDeTraducao)) {
            return false;
        }
        // vídeo recém-indexado entra aqui já sem tradução; legenda automática não tem tradução (só a manual)
        return videoRepository.findByYoutubeId(semente.youtubeId())
                .map(v -> v.getTraducaoIdioma() == null && !v.isLegendaAutomatica())
                .orElse(true);
    }

    /** Devolve 1 se o vídeo ganhou tradução. Falha de rede sobe e interrompe a ingestão, como na legenda. */
    private int traduzir(VideoSemente video) {
        pausar();
        Optional<List<TrechoLegenda>> traduzida =
                legendaProvider.buscarTraducao(video.youtubeId(), video.idioma(), idiomaDeTraducao);
        if (traduzida.isEmpty()) {
            indexador.marcarSemTraducao(video.youtubeId());
            return 0;
        }
        double taxa = indexador.aplicarTraducao(video.youtubeId(), idiomaDeTraducao, traduzida.get());
        if (taxa < 0.9) {
            log.warn("Tradução [{}] {}: só {}% dos trechos casaram", video.youtubeId(), video.titulo(), Math.round(taxa * 100));
        } else {
            log.info("Traduzido [{}] {}: {}% dos trechos", video.youtubeId(), video.titulo(), Math.round(taxa * 100));
        }
        return 1;
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
        registrarDescarte(video, motivo);
    }

    /** Grava na hora, para que uma interrupção (ex.: 429) não perca o que já foi decidido. */
    private void registrarDescarte(VideoSemente video, MotivoDescarte motivo) {
        descartadoRepository.save(new VideoDescartado(video.youtubeId(), motivo.name(), LocalDateTime.now()));
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
