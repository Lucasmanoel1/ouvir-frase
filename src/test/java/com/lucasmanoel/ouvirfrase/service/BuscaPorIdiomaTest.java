package com.lucasmanoel.ouvirfrase.service;

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

@SpringBootTest
@Testcontainers
class BuscaPorIdiomaTest {

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
        // mesmas palavras nos dois idiomas: só o filtro por idioma separa os resultados
        indexador.indexar("eng0001", "Video en", "en", List.of(new TrechoLegenda("la casa grande", 4.0, 2.0)));
        indexador.indexar("spa0001", "Video es", "es", List.of(new TrechoLegenda("la casa grande", 4.0, 2.0)));
    }

    @Test
    void deveBuscarSomenteNoIdiomaPedidoEListarOsIdiomasIndexados() {
        assertThat(buscaService.buscar("casa grande", "es").resultados())
                .extracting(ResultadoBusca::youtubeId).containsExactly("spa0001");
        assertThat(buscaService.buscar("casa grande", "en").resultados())
                .extracting(ResultadoBusca::youtubeId).containsExactly("eng0001");
        assertThat(buscaService.idiomas()).containsExactlyInAnyOrder("en", "es");
    }
}
