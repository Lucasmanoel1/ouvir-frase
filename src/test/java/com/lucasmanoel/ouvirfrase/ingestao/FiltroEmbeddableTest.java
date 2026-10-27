package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.exception.YouTubeApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiltroEmbeddableTest {

    private static final String A = "AAAAAAAAAAA";
    private static final String B = "BBBBBBBBBBB";
    private static final String C = "CCCCCCCCCCC";

    @Mock
    private YouTubeDataClient client;

    private FiltroEmbeddable filtro;

    @BeforeEach
    void preparar() {
        filtro = new FiltroEmbeddable(client);
    }

    private static VideoSemente semente(String id) {
        return new VideoSemente(id, "en", "Titulo " + id);
    }

    @Test
    void deveSepararAprovadoNaoEncontradoENaoIncorporavel() {

        when(client.consultarEmbeddable(List.of(A, B, C)))
                .thenReturn(Map.of(A, true, B, false));

        ResultadoFiltro resultado = filtro.filtrar(List.of(semente(A), semente(B), semente(C)));

        assertThat(resultado.aprovados()).containsExactly(semente(A));
        assertThat(resultado.descartados())
                .containsEntry(semente(B), MotivoDescarte.NAO_INCORPORAVEL)
                .containsEntry(semente(C), MotivoDescarte.NAO_ENCONTRADO)
                .hasSize(2);
    }

    @Test
    void deveManterAOrdemDaListaDeEntradaNosAprovados() {

        when(client.consultarEmbeddable(List.of(C, A, B)))
                .thenReturn(Map.of(A, true, B, true, C, true));

        ResultadoFiltro resultado = filtro.filtrar(List.of(semente(C), semente(A), semente(B)));

        assertThat(resultado.aprovados()).containsExactly(semente(C), semente(A), semente(B));
        assertThat(resultado.descartados()).isEmpty();
    }

    @Test
    void deveManterAOrdemDaListaDeEntradaNosDescartados() {
        when(client.consultarEmbeddable(List.of(B, A)))
                .thenReturn(Map.of(A, false, B, false));

        ResultadoFiltro resultado = filtro.filtrar(List.of(semente(B), semente(A)));

        assertThat(resultado.descartados().keySet()).containsExactly(semente(B), semente(A));
    }

    @Test
    void naoDeveChamarOClientQuandoListaVazia() {
        ResultadoFiltro resultado = filtro.filtrar(List.of());

        assertThat(resultado.aprovados()).isEmpty();
        assertThat(resultado.descartados()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void devePropagarYouTubeApiExceptionSemEngolir() {
        when(client.consultarEmbeddable(anyCollection()))
                .thenThrow(new YouTubeApiException("cota estourada"));

        assertThatExceptionOfType(YouTubeApiException.class)
                .isThrownBy(() -> filtro.filtrar(List.of(semente(A))))
                .withMessageContaining("cota");
    }
}