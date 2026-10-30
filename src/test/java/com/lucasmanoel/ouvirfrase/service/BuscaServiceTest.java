package com.lucasmanoel.ouvirfrase.service;

import com.lucasmanoel.ouvirfrase.dto.RespostaBusca;
import com.lucasmanoel.ouvirfrase.dto.ResultadoBusca;
import com.lucasmanoel.ouvirfrase.ingestao.indexacao.IndexadorDeVideo;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.TrechoLegenda;
import com.lucasmanoel.ouvirfrase.repository.OcorrenciaRepository;
import com.lucasmanoel.ouvirfrase.repository.TrechoRepository;
import com.lucasmanoel.ouvirfrase.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Testcontainers
class BuscaServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired BuscaService buscaService;
    @Autowired IndexadorDeVideo indexador;
    @Autowired VideoRepository videoRepository;
    @Autowired TrechoRepository trechoRepository;
    @Autowired OcorrenciaRepository ocorrenciaRepository;

    @BeforeEach
    void prepararBanco() {
        ocorrenciaRepository.deleteAllInBatch();
        trechoRepository.deleteAllInBatch();
        videoRepository.deleteAllInBatch();
        indexador.indexar("abc123", "Video de teste", "en", List.of(
                new TrechoLegenda("I really love", 10.0, 3.0),
                new TrechoLegenda("this idea [Music]", 13.0, 2.0)));
    }

    @Test
    void deveAcharFraseQueCruzaDoisTrechosComRecuoDeTresSegundos() {
        RespostaBusca resposta = buscaService.buscar("Love THIS idea!", "en");

        assertThat(resposta.resultados()).hasSize(1);
        assertThat(resposta.resultados().get(0).youtubeId()).isEqualTo("abc123");
        assertThat(resposta.resultados().get(0).inicioSeg()).isEqualTo(9.0, within(0.001));
        assertThat(resposta.resultados().get(0).fimSeg()).isEqualTo(15.0, within(0.001));
        assertThat(resposta.resultados().get(0).legenda()).contains("I really love this idea");
    }

    @Test
    void deveMostrarLegendaManualAntesDaAutomatica() {
        var automatico = videoRepository.findByYoutubeId("abc123").orElseThrow();
        automatico.setLegendaAutomatica(true);
        videoRepository.save(automatico);
        indexador.indexar("manu01", "Video manual", "en", null, List.of(
                new TrechoLegenda("I really love", 10.0, 3.0),
                new TrechoLegenda("this idea", 13.0, 2.0)), false);

        assertThat(buscaService.buscar("really love", "en").resultados())
                .extracting(ResultadoBusca::youtubeId, ResultadoBusca::legendaAutomatica)
                .containsExactly(tuple("manu01", false), tuple("abc123", true));
    }

    @Test
    void deveEncontrarPalavrasNaOrdemMesmoComGaps() {
        assertThat(buscaService.buscar("i love", "en").resultados())
                .extracting(ResultadoBusca::legenda)
                .contains("I really love");
    }
}
