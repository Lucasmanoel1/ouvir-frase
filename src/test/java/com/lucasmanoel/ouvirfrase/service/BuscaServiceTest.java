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
    void deveAcharFraseQueCruzaDoisTrechosComOsTemposDoVideo() {
        RespostaBusca resposta = buscaService.buscar("Love THIS idea!", "en");

        assertThat(resposta.resultados()).hasSize(1);
        assertThat(resposta.resultados().get(0).youtubeId()).isEqualTo("abc123");
        // love: 3ª de 3 palavras em 10-13 s; idea: 2ª de 2 em 13-15 s ([Music] sai antes de contar)
        assertThat(resposta.resultados().get(0).inicioSeg()).isEqualTo(12.0, within(0.001));
        assertThat(resposta.resultados().get(0).ultimaPalavraSeg()).isEqualTo(14.0, within(0.001));
        assertThat(resposta.resultados().get(0).falaInicioSeg()).isEqualTo(10.0, within(0.001));
        assertThat(resposta.resultados().get(0).falaFimSeg()).isEqualTo(15.0, within(0.001));
        assertThat(resposta.resultados().get(0).legenda()).contains("I really love this idea");
        assertThat(resposta.fraseTransliterada()).isNull();
        assertThat(resposta.resultados().get(0).legendaTransliterada()).isNull();
    }

    @Test
    void deveCortarOFimDaFalaNoInicioDaSeguinte() {
        // legenda automática: a duração do trecho (20 a 25 s) invade o seguinte, que começa aos 23 s
        indexador.indexar("auto01", "Video auto", "en", List.of(
                new TrechoLegenda("we should go", 20.0, 5.0),
                new TrechoLegenda("right now", 23.0, 4.0)));

        ResultadoBusca r = buscaService.buscar("should go", "en").resultados().get(0);

        assertThat(r.exata()).isTrue();
        assertThat(r.falaFimSeg()).isEqualTo(23.0, within(0.001));
    }

    @Test
    void deveTransliterarAFraseEALegendaEmRusso() {
        indexador.indexar("rus001", "Video ru", "ru", List.of(new TrechoLegenda("Привет, друзья", 4.0, 2.0)));

        RespostaBusca resposta = buscaService.buscar("привет", "ru");

        assertThat(resposta.fraseTransliterada()).isEqualTo("privet");
        assertThat(resposta.resultados()).hasSize(1);
        assertThat(resposta.resultados().get(0).legendaTransliterada()).startsWith("Privet");
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
