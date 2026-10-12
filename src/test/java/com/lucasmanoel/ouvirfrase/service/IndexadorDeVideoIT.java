package com.lucasmanoel.ouvirfrase.service;

import com.lucasmanoel.ouvirfrase.dto.TrechoLegenda;
import com.lucasmanoel.ouvirfrase.model.Ocorrencia;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
    void deveManterOrdemEntreTrechosSemBuracos() {
        List<TrechoLegenda> trechos = List.of(
                new TrechoLegenda("Welcome back", 7.0, 2.0),
                new TrechoLegenda("I really love [Music]", 10.0, 3.0),
                new TrechoLegenda("this idea", 13.0, 2.0));

        int total = indexador.indexar("abc123", "Teste2", "en", trechos);

        assertThat(total).isEqualTo(7);
        List<Ocorrencia> ocs = ocorrenciaRepository.findAll(Sort.by("posicao"));
        assertThat(ocs).extracting(Ocorrencia::getPalavra)
                .containsExactly("welcome", "back", "i", "really", "love", "this", "idea");
        assertThat(ocs).extracting(Ocorrencia::getPosicao)
                .containsExactly(0, 1, 2, 3, 4, 5, 6);
    }

    @Test
    void deveLancarIllegalArgumentException() {
        List<TrechoLegenda> trechos = List.of(
                new TrechoLegenda("", 7.0, 2.0),
                new TrechoLegenda("[Music]", 10.0, 3.0),
                new TrechoLegenda("", 13.0, 2.0));


        assertThrows(IllegalArgumentException.class,
                () -> indexador.indexar("abc123", "Teste3", "en", trechos));

        assertEquals(0, videoRepository.count());

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
    }

    @Test
    void naoDeveIndexarQubreaDeLinhaComoPalavra() {
        List<TrechoLegenda> trechos = List.of(
                new TrechoLegenda("I really love \n", 10.0, 3.0),
                new TrechoLegenda("this idea [Music]", 13.0, 2.0));

        int total = indexador.indexar("abc123", "Teste", "en", trechos);

        assertThat(total).isEqualTo(5);
        List<Ocorrencia> ocs = ocorrenciaRepository.findAll(Sort.by("posicao"));
        assertThat(ocs).extracting(Ocorrencia::getPalavra)
                .containsExactly("i", "really", "love", "this", "idea");
        assertThat(ocs).extracting(Ocorrencia::getPosicao)
                .containsExactly(0, 1, 2, 3, 4);
    }

}
