package com.lucasmanoel.idiomando.service;

import com.lucasmanoel.idiomando.dto.RespostaBusca;
import com.lucasmanoel.idiomando.repository.BuscaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O orçamento de tempo da busca: o banco cancela a consulta que estoura e a busca devolve o que já tem. */
@SpringBootTest
@Testcontainers
class BuscaOrcamentoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired BuscaService buscaService;
    @MockitoBean BuscaRepository buscaRepository;

    @Test
    void deveDevolverSoAsExatasQuandoAsAproximadasEstouram() {
        when(buscaRepository.buscarSequencia(anyList(), eq("en"), anyInt())).thenReturn(List.of(
                new BuscaRepository.Acerto("abc123", "Video", 12.0, 14.0, 10.0, 15.0, "love this", false)));
        when(buscaRepository.buscarCandidatas(anyList(), eq("en"), anyInt(), anyInt()))
                .thenThrow(new QueryTimeoutException("canceling statement due to statement timeout"));

        RespostaBusca resposta = buscaService.buscar("love this", "en");

        assertThat(resposta.resultados()).hasSize(1);
        assertThat(resposta.resultados().get(0).youtubeId()).isEqualTo("abc123");
        assertThat(resposta.resultados().get(0).exata()).isTrue();
        // a transação está abortada: nada de carregar vídeos nem falas depois do cancelamento
        verify(buscaRepository, never()).falas(anyLong(), anyLong(), anyLong());
    }

    @Test
    void devePararSemNovasConsultasQuandoAExataEstoura() {
        when(buscaRepository.buscarSequencia(anyList(), eq("en"), anyInt()))
                .thenThrow(new QueryTimeoutException("canceling statement due to statement timeout"));

        RespostaBusca resposta = buscaService.buscar("love this", "en");

        assertThat(resposta.resultados()).isEmpty();
        assertThat(resposta.idioma()).isEqualTo("en");
        verify(buscaRepository, times(1)).buscarSequencia(anyList(), eq("en"), anyInt());
        verify(buscaRepository, never()).buscarCandidatas(anyList(), eq("en"), anyInt(), anyInt());
    }
}
