package com.lucasmanoel.ouvirfrase.ingestao.painel.sugestoes;

import com.lucasmanoel.ouvirfrase.ingestao.indexacao.IndexadorDeVideo;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.TrechoLegenda;
import com.lucasmanoel.ouvirfrase.ingestao.painel.sugestoes.SugestoesRepository.Contagem;
import com.lucasmanoel.ouvirfrase.ingestao.painel.sugestoes.SugestoesRepository.Totais;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SugestoesRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired SugestoesRepository repository;
    @Autowired IndexadorDeVideo indexador;
    @Autowired VideoRepository videoRepository;
    @Autowired TrechoRepository trechoRepository;
    @Autowired OcorrenciaRepository ocorrenciaRepository;

    @BeforeEach
    void prepararBanco() {
        ocorrenciaRepository.deleteAllInBatch();
        trechoRepository.deleteAllInBatch();
        videoRepository.deleteAllInBatch();

        indexador.indexar("v1", "Video 1", "en", "a",
                List.of(new TrechoLegenda("thank you", 0.0, 2.0), new TrechoLegenda("so what", 2.0, 2.0)), false);
        indexador.indexar("v2", "Video 2", "en", "b",
                List.of(new TrechoLegenda("thank you so much my friend", 0.0, 6.0)), false);
        indexador.indexar("v3", "Video 3", "en", List.of(new TrechoLegenda("thank you", 0.0, 2.0)));   // sem canal
        indexador.indexar("v4", "Video 4", "es", "a",
                List.of(new TrechoLegenda("thank you", 0.0, 2.0)), false);
    }

    private Map<String, Contagem> porFrase(String idioma, int minimo) {
        return repository.contar(idioma, minimo).stream()
                .collect(Collectors.toMap(Contagem::frase, Function.identity()));
    }

    @Test
    void deveContarSoDentroDoTrechoEAteCincoPalavras() {
        Map<String, Contagem> contagens = porFrase("en", 1);

        // em v1 "you" e "so" estão em trechos diferentes; só v2 tem "you so"
        assertThat(contagens.get("you so").videos()).isEqualTo(1);
        assertThat(contagens).containsKey("thank you so much my").doesNotContainKey("thank you so much my friend");

        Contagem thankYou = contagens.get("thank you");
        assertThat(thankYou.vezes()).isEqualTo(3);
        assertThat(thankYou.videos()).isEqualTo(3);
        assertThat(thankYou.canais()).isEqualTo(3);   // a, b e "sem canal"; o vídeo em espanhol não entra
    }

    @Test
    void deveCortarPeloMinimoDeVideos() {
        assertThat(porFrase("en", 2).keySet()).containsExactlyInAnyOrder("thank", "you", "thank you", "so");
    }

    @Test
    void deveTotalizarPalavrasECanaisDoIdioma() {
        Totais totais = repository.totais("en");

        assertThat(totais.palavras()).isEqualTo(12);
        assertThat(totais.canais()).isEqualTo(3);
    }
}
