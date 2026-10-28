package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.dto.TrechoLegenda;
import com.lucasmanoel.ouvirfrase.exception.FalhaNaBuscaDeLegendaException;
import com.lucasmanoel.ouvirfrase.repository.VideoRepository;
import com.lucasmanoel.ouvirfrase.service.IndexadorDeVideo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestaoServiceTest {

    private static final Path CSV = Path.of("data/videos.csv");
    private static final VideoSemente A = new VideoSemente("AAAAAAAAAAA", "en", "A");
    private static final VideoSemente B = new VideoSemente("BBBBBBBBBBB", "en", "B");
    private static final VideoSemente C = new VideoSemente("CCCCCCCCCCC", "en", "C");
    private static final VideoSemente D = new VideoSemente("DDDDDDDDDDD", "en", "D");

    @Mock private VideoCsvReader csvReader;
    @Mock private FiltroEmbeddable filtro;
    @Mock private LegendaProvider legendaProvider;
    @Mock private IndexadorDeVideo indexador;
    @Mock private VideoRepository videoRepository;

    private IngestaoService service;

    @BeforeEach
    void preparar() {
        service = new IngestaoService(csvReader, filtro, legendaProvider, indexador, videoRepository, 0);
    }

    @Test
    void deveIndexarPularJaIndexadosEDescartarComMotivo() {
        List<TrechoLegenda> trechos = List.of(new TrechoLegenda("hello world", 1.0, 2.0));
        when(csvReader.ler(CSV)).thenReturn(List.of(A, B, C, D));
        when(videoRepository.existsByYoutubeId(A.youtubeId())).thenReturn(true);
        when(filtro.filtrar(List.of(B, C, D)))
                .thenReturn(new ResultadoFiltro(List.of(B, C), Map.of(D, MotivoDescarte.NAO_INCORPORAVEL)));
        when(legendaProvider.buscar(B.youtubeId(), "en")).thenReturn(Optional.of(trechos));
        when(legendaProvider.buscar(C.youtubeId(), "en")).thenReturn(Optional.empty());
        when(indexador.indexar(B.youtubeId(), "B", "en", trechos)).thenReturn(2);

        RelatorioIngestao relatorio = service.executar(CSV);

        assertThat(relatorio.total()).isEqualTo(4);
        assertThat(relatorio.indexados()).isEqualTo(1);
        assertThat(relatorio.jaIndexados()).isEqualTo(1);
        assertThat(relatorio.descartados())
                .containsEntry(C, MotivoDescarte.SEM_LEGENDA)
                .containsEntry(D, MotivoDescarte.NAO_INCORPORAVEL)
                .hasSize(2);
        verify(indexador).indexar(B.youtubeId(), "B", "en", trechos);
    }

    @Test
    void devePararTudoQuandoHaFalhaDeInfraestrutura() {
        when(csvReader.ler(CSV)).thenReturn(List.of(B, C));
        when(filtro.filtrar(List.of(B, C))).thenReturn(new ResultadoFiltro(List.of(B, C), Map.of()));
        when(legendaProvider.buscar(B.youtubeId(), "en"))
                .thenThrow(new FalhaNaBuscaDeLegendaException("IP bloqueado"));

        assertThatExceptionOfType(FalhaNaBuscaDeLegendaException.class)
                .isThrownBy(() -> service.executar(CSV));

        verify(legendaProvider, never()).buscar(C.youtubeId(), "en");
        verifyNoInteractions(indexador);
    }
}
