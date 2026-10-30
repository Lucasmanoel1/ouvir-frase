package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.ingestao.catalogo.CatalogoDeVideos;
import com.lucasmanoel.ouvirfrase.ingestao.catalogo.VideoSemente;
import com.lucasmanoel.ouvirfrase.ingestao.indexacao.IndexadorDeVideo;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.FalhaNaBuscaDeLegendaException;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.LegendaBaixada;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.LegendaProvider;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.TrechoLegenda;
import com.lucasmanoel.ouvirfrase.model.VideoDescartado;
import com.lucasmanoel.ouvirfrase.repository.VideoDescartadoRepository;
import com.lucasmanoel.ouvirfrase.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestaoServiceTest {

    private static final Path DADOS = Path.of("data");
    private static final VideoSemente A = new VideoSemente("AAAAAAAAAAA", "en", "A");
    private static final VideoSemente B = new VideoSemente("BBBBBBBBBBB", "en", "B");
    private static final VideoSemente C = new VideoSemente("CCCCCCCCCCC", "en", "C");
    private static final VideoSemente D = new VideoSemente("DDDDDDDDDDD", "en", "D");

    @Mock private CatalogoDeVideos catalogo;
    @Mock private FiltroEmbeddable filtro;
    @Mock private LegendaProvider legendaProvider;
    @Mock private IndexadorDeVideo indexador;
    @Mock private VideoRepository videoRepository;
    @Mock private VideoDescartadoRepository descartadoRepository;

    private IngestaoService service;

    @BeforeEach
    void preparar() {
        service = new IngestaoService(catalogo, filtro, legendaProvider, indexador, videoRepository, descartadoRepository,
                new IngestaoProperties(DADOS, 0, 0));
    }

    @Test
    void deveIndexarPularJaIndexadosEDescartarComMotivo() {
        List<TrechoLegenda> trechos = List.of(new TrechoLegenda("hello world", 1.0, 2.0));
        when(catalogo.carregar(DADOS)).thenReturn(List.of(A, B, C, D));
        when(videoRepository.existsByYoutubeId(A.youtubeId())).thenReturn(true);
        when(filtro.filtrar(List.of(B, C, D)))
                .thenReturn(new ResultadoFiltro(List.of(B, C), Map.of(D, MotivoDescarte.NAO_INCORPORAVEL)));
        when(legendaProvider.buscar(B.youtubeId(), "en")).thenReturn(Optional.of(new LegendaBaixada(trechos, true)));
        when(legendaProvider.buscar(C.youtubeId(), "en")).thenReturn(Optional.empty());
        when(indexador.indexar(B.youtubeId(), "B", "en", null, trechos, true)).thenReturn(2);

        RelatorioIngestao relatorio = service.executar();

        assertThat(relatorio.total()).isEqualTo(4);
        assertThat(relatorio.indexados()).isEqualTo(1);
        assertThat(relatorio.jaIndexados()).isEqualTo(1);
        assertThat(relatorio.descartados())
                .containsEntry(C, MotivoDescarte.SEM_LEGENDA)
                .containsEntry(D, MotivoDescarte.NAO_INCORPORAVEL)
                .hasSize(2);
        verify(indexador).indexar(B.youtubeId(), "B", "en", null, trechos, true);

        ArgumentCaptor<VideoDescartado> gravados = ArgumentCaptor.forClass(VideoDescartado.class);
        verify(descartadoRepository, times(2)).save(gravados.capture());
        assertThat(gravados.getAllValues())
                .extracting(VideoDescartado::getYoutubeId, VideoDescartado::getMotivo)
                .containsExactlyInAnyOrder(tuple(C.youtubeId(), "SEM_LEGENDA"), tuple(D.youtubeId(), "NAO_INCORPORAVEL"));
    }

    @Test
    void deveIgnorarVideoJaDescartadoSemConsultarFiltroNemLegenda() {
        when(catalogo.carregar(DADOS)).thenReturn(List.of(C));
        when(descartadoRepository.existsById(C.youtubeId())).thenReturn(true);
        when(filtro.filtrar(List.of())).thenReturn(new ResultadoFiltro(List.of(), Map.of()));

        RelatorioIngestao relatorio = service.executar();

        assertThat(relatorio.jaDescartados()).isEqualTo(1);
        assertThat(relatorio.descartados()).isEmpty();
        verifyNoInteractions(legendaProvider, indexador);
        verify(descartadoRepository, never()).save(any());
    }

    @Test
    void devePararTudoComRelatorioParcialQuandoHaFalhaDeInfraestrutura() {
        when(catalogo.carregar(DADOS)).thenReturn(List.of(B, C));
        when(filtro.filtrar(List.of(B, C))).thenReturn(new ResultadoFiltro(List.of(B, C), Map.of()));
        when(legendaProvider.buscar(B.youtubeId(), "en"))
                .thenThrow(new FalhaNaBuscaDeLegendaException("IP bloqueado"));

        assertThatExceptionOfType(IngestaoInterrompidaException.class)
                .isThrownBy(service::executar)
                .withCauseInstanceOf(FalhaNaBuscaDeLegendaException.class)
                .satisfies(e -> assertThat(e.getRelatorioParcial().total()).isEqualTo(2));

        verify(legendaProvider, never()).buscar(C.youtubeId(), "en");
        verifyNoInteractions(indexador);
    }
}
