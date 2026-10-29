package com.lucasmanoel.ouvirfrase.service;

import com.lucasmanoel.ouvirfrase.service.CasadorPorLacunas.Candidata;
import com.lucasmanoel.ouvirfrase.service.CasadorPorLacunas.Casamento;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CasadorPorLacunasTest {

    private final CasadorPorLacunas casador = new CasadorPorLacunas();
    private final List<String> frase = List.of("a", "b", "c", "d", "e");

    private static Candidata c(String palavra, int posicao) {
        return new Candidata(palavra, posicao, 1L, posicao);
    }

    @Test
    void deveCasarQuandoFaltaUmaPalavraNoMeio() {
        List<Casamento> r = casador.casar(frase,
                List.of(c("a", 0), c("b", 1), c("d", 3), c("e", 4)), 2);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).casadas()).isEqualTo(4);
        assertThat(r.get(0).score()).isEqualTo(0.8, within(1e-9));
        assertThat(r.get(0).span()).isEqualTo(5);
    }

    @Test
    void naoDeveCasarComAsPalavrasEmOrdemTrocada() {
        List<Casamento> r = casador.casar(frase,
                List.of(c("e", 0), c("d", 1), c("c", 2), c("b", 3), c("a", 4)), 2);

        assertThat(r).isEmpty();
    }
}
