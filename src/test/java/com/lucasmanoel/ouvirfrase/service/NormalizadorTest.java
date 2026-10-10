package com.lucasmanoel.ouvirfrase.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class NormalizadorTest {

    private final Normalizador normalizador = new Normalizador();

    @Test
    void deveManterApostrofoDentroDaPalavra() {
        assertThat(normalizador.tokenizar("Don't stop"))
                .containsExactly("don't", "stop");
    }

    @Test
    void deveConverterApostrofoCurvoParaReto() {
        assertThat(normalizador.tokenizar("it\u2019s"))
                .containsExactly("it's");
    }

    @Test
    void deveRemoverMarcacoesDeSom() {
        assertThat(normalizador.tokenizar("[Music] Hello (Laughter) world"))
                .containsExactly("hello", "world");
    }

}
