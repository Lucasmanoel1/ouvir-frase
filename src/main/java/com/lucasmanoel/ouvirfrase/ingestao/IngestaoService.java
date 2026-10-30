package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.ingestao.catalogo.CatalogoDeVideos;
import com.lucasmanoel.ouvirfrase.ingestao.catalogo.VideoSemente;
import com.lucasmanoel.ouvirfrase.ingestao.indexacao.IndexadorDeVideo;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.FalhaNaBuscaDeLegendaException;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.LegendaBaixada;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.LegendaProvider;
import com.lucasmanoel.ouvirfrase.ingestao.youtube.YouTubeApiException;
import com.lucasmanoel.ouvirfrase.model.VideoDescartado;
import com.lucasmanoel.ouvirfrase.repository.VideoDescartadoRepository;
import com.lucasmanoel.ouvirfrase.repository.VideoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

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
    private final IngestaoProperties props;

    public IngestaoService(CatalogoDeVideos catalogo,
                           FiltroEmbeddable filtro,
                           LegendaProvider legendaProvider,
                           IndexadorDeVideo indexador,
                           VideoRepository videoRepository,
                           VideoDescartadoRepository descartadoRepository,
                           IngestaoProperties props) {
        this.catalogo = catalogo;
        this.filtro = filtro;
        this.legendaProvider = legendaProvider;
        this.indexador = indexador;
        this.videoRepository = videoRepository;
        this.descartadoRepository = descartadoRepository;
        this.props = props;
    }

    /** Linha de comando: todos os CSVs, com o limite das propriedades. */
    public RelatorioIngestao executar() {
        return executar(null, props.limite(), mensagem -> { });
    }

    /**
     * @param canal    só os vídeos desse canal (nome do CSV sem a extensão); null = todos
     * @param limite   máximo de vídeos indexados nesta chamada; 0 = sem limite
     * @param progresso recebe uma frase curta a cada passo (o painel mostra na tela)
     */
    public RelatorioIngestao executar(String canal, int limite, Consumer<String> progresso) {
        List<VideoSemente> sementes = catalogo.carregar(props.dados()).stream()
                .filter(s -> canal == null || canal.equals(s.canal()))
                .toList();
        Triagem triagem = triar(sementes);

        Map<VideoSemente, MotivoDescarte> descartados = new LinkedHashMap<>();
        Placar placar = new Placar();
        try {
            progresso.accept("Conferindo " + triagem.novas().size() + " vídeos novos no YouTube…");
            ResultadoFiltro filtrado = filtro.filtrar(triagem.novas());
            descartados.putAll(filtrado.descartados());
            filtrado.descartados().forEach(this::registrarDescarte);

            indexarAprovados(filtrado.aprovados(), limite, descartados, placar, progresso);
        } catch (FalhaNaBuscaDeLegendaException | YouTubeApiException e) {
            throw new IngestaoInterrompidaException(
                    new RelatorioIngestao(sementes.size(), placar.indexados, triagem.jaIndexados(),
                            triagem.jaDescartados(), 0, descartados), e);
        }
        return new RelatorioIngestao(sementes.size(), placar.indexados, triagem.jaIndexados(),
                triagem.jaDescartados(), placar.adiados, descartados);
    }

    /** Separa o que já foi indexado, o que já foi descartado antes e o que ainda é novo. */
    private Triagem triar(List<VideoSemente> sementes) {
        List<VideoSemente> novas = new ArrayList<>();
        int jaIndexados = 0;
        int jaDescartados = 0;
        for (VideoSemente s : sementes) {
            if (videoRepository.existsByYoutubeId(s.youtubeId())) {
                jaIndexados++;
            } else if (descartadoRepository.existsById(s.youtubeId())) {
                jaDescartados++;
            } else {
                novas.add(s);
            }
        }
        return new Triagem(novas, jaIndexados, jaDescartados);
    }

    private void indexarAprovados(List<VideoSemente> aprovados, int limite,
                                  Map<VideoSemente, MotivoDescarte> descartados, Placar placar,
                                  Consumer<String> progresso) {
        int lote = limite > 0 ? Math.min(limite, aprovados.size()) : aprovados.size();
        for (int i = 0; i < aprovados.size(); i++) {
            if (limite > 0 && i >= limite) {
                placar.adiados = aprovados.size() - i;
                break;
            }
            if (i > 0) {
                pausar();
            }
            VideoSemente video = aprovados.get(i);
            progresso.accept("Indexando " + (i + 1) + " de " + lote + ": " + video.titulo());
            if (indexar(video, descartados)) {
                placar.indexados++;
            }
        }
    }

    /** Devolve true se o vídeo entrou no índice; false se foi descartado (e registrado). */
    private boolean indexar(VideoSemente video, Map<VideoSemente, MotivoDescarte> descartados) {
        Optional<LegendaBaixada> legenda = legendaProvider.buscar(video.youtubeId(), video.idioma());
        if (legenda.isEmpty()) {
            descartar(descartados, video, MotivoDescarte.SEM_LEGENDA);
            return false;
        }
        try {
            boolean automatica = legenda.get().automatica();
            int ocorrencias = indexador.indexar(video.youtubeId(), video.titulo(), video.idioma(),
                    video.canal(), legenda.get().trechos(), automatica);
            log.info("Indexado [{}] {}{}: {} ocorrências", video.youtubeId(), video.titulo(),
                    automatica ? " (legenda automática)" : "", ocorrencias);
            return true;
        } catch (IllegalArgumentException e) {
            descartar(descartados, video, MotivoDescarte.SEM_PALAVRAS);
            return false;
        }
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
        if (props.pausaMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(props.pausaMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ingestão interrompida", e);
        }
    }

    private record Triagem(List<VideoSemente> novas, int jaIndexados, int jaDescartados) {
    }

    /** Contagem do que já aconteceu, para o relatório parcial se a ingestão for interrompida. */
    private static final class Placar {
        int indexados;
        int adiados;
    }
}
