package com.lucasmanoel.ouvirfrase.service;

import com.lucasmanoel.ouvirfrase.dto.TrechoLegenda;
import com.lucasmanoel.ouvirfrase.model.Ocorrencia;
import com.lucasmanoel.ouvirfrase.model.Trecho;
import com.lucasmanoel.ouvirfrase.repository.OcorrenciaRepository;
import com.lucasmanoel.ouvirfrase.repository.TrechoRepository;
import com.lucasmanoel.ouvirfrase.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Sort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class IndexadorDeVideoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    IndexadorDeVideo indexador;
    @Autowired
    VideoRepository videoRepository;
    @Autowired
    TrechoRepository trechoRepository;
    @Autowired
    OcorrenciaRepository ocorrenciaRepository;

    @BeforeEach
    void limpar() {
        ocorrenciaRepository.deleteAllInBatch();
        trechoRepository.deleteAllInBatch();
        videoRepository.deleteAllInBatch();
    }

    @Test
    void deveManterPosicaoContinuaEntreTrechos() {
        List<TrechoLegenda> trechos = List.of(
                new TrechoLegenda("I really love", 10.0, 3.0),
                new TrechoLegenda("this idea [Music]", 13.0, 2.0));

        int total = indexador.indexar("abc123", "Teste", "en", trechos);

        assertThat(total).isEqualTo(5);
        List<Ocorrencia> ocs = ocorrenciaRepository.findAll(Sort.by("posicao"));
        assertThat(ocs).extracting(Ocorrencia::getPalavra)
                .containsExactly("i", "really", "love", "this", "idea");
        assertThat(ocs).extracting(Ocorrencia::getPosicao)
                .containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void naoDeveGravarTrechoSoComMarcacaoDeSom() {
        List<TrechoLegenda> trechos = List.of(
                new TrechoLegenda("Welcome back", 7.0, 2.0),
                new TrechoLegenda("[Music]", 9.0, 1.0),
                new TrechoLegenda("I really love", 10.0, 3.0));

        int total = indexador.indexar("abc123", "Teste", "en", trechos);

        assertThat(total).isEqualTo(5);
        assertThat(trechoRepository.findAll(Sort.by("ordem")))
                .extracting(Trecho::getOrdem)
                .containsExactly(0, 1);
        assertThat(ocorrenciaRepository.findAll(Sort.by("posicao")))
                .extracting(Ocorrencia::getPosicao)
                .containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void deveRejeitarLegendaSemPalavrasEFazerRollback() {
        List<TrechoLegenda> trechos = List.of(
                new TrechoLegenda("", 7.0, 2.0),
                new TrechoLegenda("[Music]", 10.0, 3.0),
                new TrechoLegenda("", 13.0, 2.0));

        assertThatIllegalArgumentException().isThrownBy(
                () -> indexador.indexar("abc123", "Teste3", "en", trechos));

        assertThat(videoRepository.count()).isZero();
    }

    @Test
    void deveVerificarInicioEstimado() {
        List<TrechoLegenda> trechos = List.of(
                new TrechoLegenda("I really love", 10.0, 3.0),
                new TrechoLegenda("this idea [Music]", 13.0, 2.0));

        int total = indexador.indexar("abc123", "Teste", "en", trechos);

        assertThat(total).isEqualTo(5);
        List<Ocorrencia> ocs = ocorrenciaRepository.findAll(Sort.by("posicao"));

        assertThat(ocs.get(1).getInicioSeg()).isEqualTo(11.0, within(0.001));
        assertThat(ocs.get(4).getInicioSeg()).isEqualTo(14.0, within(0.001));
    }

    @Test
    void naoDeveIndexarQuebraDeLinhaComoPalavra() {
        List<TrechoLegenda> trechos = List.of(
                new TrechoLegenda("I really\nlove \n", 10.0, 3.0),
                new TrechoLegenda("this idea [Music]", 13.0, 2.0));

        int total = indexador.indexar("abc123", "Teste", "en", trechos);

        assertThat(total).isEqualTo(5);

        assertThat(trechoRepository.findAll(Sort.by("ordem")).get(0).getTexto())
                .isEqualTo("I really love");
    }

}
