package com.lucasmanoel.ouvirfrase.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class NormalizadorTest {

    private final Normalizador normalizador = new Normalizador();

    @Test
    void deveManterApostrofoDentroDaPalavra() {
        assertThat(normalizador.tokenizar("Don't stop", "en"))
                .containsExactly("don't", "stop");
    }

    @Test
    void deveConverterApostrofoCurvoParaReto() {
        assertThat(normalizador.tokenizar("it\u2019s", "en"))
                .containsExactly("it's");
    }

    @Test
    void deveRemoverMarcacoesDeSom() {
        assertThat(normalizador.tokenizar("[Music] Hello (Laughter) world", "en"))
                .containsExactly("hello", "world");
    }

    @Test
    void deveSepararCaracteresChinesesEManterPalavraLatina() {
        // "eu gosto" em chinês (3 caracteres) + "apple"
        assertThat(normalizador.tokenizar("\u6211\u559c\u6b22 apple", "zh-CN"))
                .containsExactly("\u6211", "\u559c", "\u6b22", "apple");
    }

    @Test
    void deveSepararApostrofoForaDoInglesEManterMarcasCombinadas() {
        // "l'homme" em francês vira dois tokens; "hindi" em devanágari continua uma palavra só
        assertThat(normalizador.tokenizar("L'homme \u0939\u093f\u0928\u094d\u0926\u0940", "fr"))
                .containsExactly("l", "homme", "\u0939\u093f\u0928\u094d\u0926\u0940");
    }

}
