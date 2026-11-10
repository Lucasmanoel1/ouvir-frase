package com.lucasmanoel.ouvirfrase.ingestao.painel.sugestoes;

import com.lucasmanoel.ouvirfrase.ingestao.painel.sugestoes.SugestoesRepository.Contagem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingDeSugestoesTest {

    private final RankingDeSugestoes ranking = new RankingDeSugestoes();

    private static Contagem c(String frase, long vezes, int videos, int canais) {
        return new Contagem(frase, vezes, videos, canais);
    }

    /** Palavra solta: só entra na conta da associação, nunca vira sugestão. */
    private static Contagem solta(String palavra, long vezes) {
        return c(palavra, vezes, 100, 3);
    }

    private List<String> frases(List<Contagem> contagens, long total, int canaisDoIdioma) {
        return ranking.ranquear(contagens, total, canaisDoIdioma).stream().map(Contagem::frase).toList();
    }

    @Test
    void deveDescartarOPedacoQueUmaPalavraCompletaEmMetadeDasVezesOuMais() {
        // "a lot" é seguido de "of" em 50 de 100 (o limite); "lot of" é precedido de "a" em todas as suas
        List<Contagem> contagens = List.of(
                solta("a", 1000), solta("lot", 200), solta("of", 900),
                c("a lot", 100, 20, 3), c("lot of", 50, 20, 3), c("a lot of", 50, 20, 3));

        assertThat(frases(contagens, 100_000, 3)).containsExactly("a lot of");
    }

    @Test
    void deveDescartarOPedacoQueFicaNaDireitaDaFormaLonga() {
        List<Contagem> contagens = List.of(
                solta("as", 500), solta("well", 300),
                c("as well", 49, 20, 3), c("well as", 50, 20, 3), c("as well as", 49, 20, 3));

        assertThat(frases(contagens, 100_000, 3)).containsExactly("as well as");
    }

    @Test
    void deveManterAFrasePrefixoQuandoAFormaLongaFicaAbaixoDaMetade() {
        List<Contagem> contagens = List.of(
                solta("you", 900), solta("know", 400), solta("what", 600),
                c("you know", 100, 30, 3), c("know what", 49, 20, 3), c("you know what", 49, 20, 3));

        // "you know what" é 49 de 100 (< 50%): "you know" fica; "know what" é completada por "you" em todas e sai
        assertThat(frases(contagens, 100_000, 3)).containsExactlyInAnyOrder("you know", "you know what");
    }

    @Test
    void deveMedirAAssociacaoNoPontoMaisFraco() {
        Map<String, Long> vezes = Map.of(
                "i", 50L, "don't", 20L, "know", 30L,
                "i don't", 10L, "don't know", 15L, "i don't know", 8L);

        // corte "i | don't know": ln(8·1000 / (50·15)) / -ln(8/1000) = 0,4903; o corte "i don't | know" dá 0,680
        assertThat(RankingDeSugestoes.associacao("i don't know", vezes, 1000)).isEqualTo(0.4903, within(0.0005));
    }

    @Test
    void deveOrdenarPelaSomaDosPostosDeVideosEDeAssociacao() {
        List<Contagem> contagens = List.of(
                solta("of", 600), solta("the", 900), solta("in", 500), solta("i", 300),
                solta("think", 80), solta("for", 150), solta("example", 32),
                c("of the", 400, 50, 3), c("in the", 300, 48, 3), c("i think", 60, 30, 3), c("for example", 30, 20, 3));

        // postos (vídeos + associação): of the 1+3, in the 2+4, i think 3+2, for example 4+1.
        // "i think" e "for example" empatam em 5 e o de mais vídeos vem primeiro
        assertThat(frases(contagens, 10_000, 3)).containsExactly("of the", "i think", "for example", "in the");
    }

    @Test
    void deveIgnorarFrasesDeUmaEDeCincoPalavras() {
        List<Contagem> contagens = List.of(
                solta("thank", 500), c("a b c d e", 40, 20, 3), c("thank you", 100, 30, 3), solta("you", 500));

        assertThat(frases(contagens, 100_000, 3)).containsExactly("thank you");
    }

    @Test
    void deveExigirDoisCanaisSalvoQuandoOIdiomaTemUmSo() {
        List<Contagem> contagens = List.of(solta("por", 500), solta("eso", 200), c("por eso", 100, 30, 1));

        assertThat(frases(contagens, 100_000, 3)).isEmpty();
        assertThat(frases(contagens, 100_000, 1)).containsExactly("por eso");
    }

    @Test
    void deveDevolverNoMaximoCentoECinquentaFrases() {
        List<Contagem> contagens = new ArrayList<>();
        IntStream.range(0, 151).forEach(i -> {
            contagens.add(solta("x" + i, 100));
            contagens.add(solta("y" + i, 100));
            contagens.add(c("x" + i + " y" + i, 10, 10, 3));
        });

        assertThat(ranking.ranquear(contagens, 100_000, 3)).hasSize(RankingDeSugestoes.LIMITE);
    }
}
