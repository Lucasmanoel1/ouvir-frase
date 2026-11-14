package com.lucasmanoel.idiomando.repository;

import com.lucasmanoel.idiomando.ingestao.indexacao.IndexadorDeVideo;
import com.lucasmanoel.idiomando.ingestao.legenda.TrechoLegenda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class BuscaPorPalavraTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired BuscaRepository buscaRepository;
    @Autowired IndexadorDeVideo indexador;
    @Autowired VideoRepository videoRepository;
    @Autowired TrechoRepository trechoRepository;
    @Autowired OcorrenciaRepository ocorrenciaRepository;

    @BeforeEach
    void prepararBanco() {
        ocorrenciaRepository.deleteAllInBatch();
        trechoRepository.deleteAllInBatch();
        videoRepository.deleteAllInBatch();
        // automáticos indexados entre os manuais: a ordem não pode depender da ordem de inserção
        indexador.indexar("auto01", "Auto 1", "en", null, List.of(
                new TrechoLegenda("we go now", 0.0, 4.0),
                new TrechoLegenda("then go home", 4.0, 4.0)), true);
        indexador.indexar("manu01", "Manual 1", "en", null, List.of(
                new TrechoLegenda("we go home", 0.0, 4.0),
                new TrechoLegenda("then go again", 4.0, 4.0),
                new TrechoLegenda("go go go", 8.0, 3.0)), false);
        indexador.indexar("auto02", "Auto 2", "en", null, List.of(
                new TrechoLegenda("let us go", 0.0, 3.0)), true);
        indexador.indexar("manu02", "Manual 2", "en", null, List.of(
                new TrechoLegenda("go away", 0.0, 2.0)), false);
        indexador.indexar("manu03", "Manual 3", "en", null, List.of(
                new TrechoLegenda("no thanks", 0.0, 2.0)), false);
        indexador.indexar("espa01", "Video es", "es", List.of(new TrechoLegenda("vamos go", 0.0, 2.0)));
    }

    @Test
    void deveDarOMesmoResultadoQueABuscaGeralParaUmaPalavra() {
        assertThat(buscaRepository.buscarPalavra("go", "en", 50))
                .isNotEmpty()
                .isEqualTo(buscaRepository.buscarSequencia(List.of("go"), "en", 50));
    }

    @Test
    void deveDarOMesmoResultadoNoCorteDoLimite() {
        // o corte cai no meio da 2ª ocorrência dos vídeos manuais
        assertThat(buscaRepository.buscarPalavra("go", "en", 3))
                .hasSize(3)
                .isEqualTo(buscaRepository.buscarSequencia(List.of("go"), "en", 3));
    }

    @Test
    void deveTrazerNoMaximoDuasPorVideoESoDoIdioma() {
        List<BuscaRepository.Acerto> acertos = buscaRepository.buscarPalavra("go", "en", 50);

        Map<String, Long> porVideo = acertos.stream()
                .collect(Collectors.groupingBy(BuscaRepository.Acerto::youtubeId, Collectors.counting()));
        assertThat(porVideo).doesNotContainKey("espa01").doesNotContainKey("manu03");
        assertThat(porVideo.values()).allMatch(n -> n <= 2);
        assertThat(porVideo.get("manu01")).isEqualTo(2);
        // legenda manual primeiro
        assertThat(acertos.get(0).legendaAutomatica()).isFalse();
    }
}
