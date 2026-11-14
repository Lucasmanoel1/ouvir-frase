package com.lucasmanoel.idiomando.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

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

    static Stream<Arguments> tokensParaJuntar() {
        return Stream.of(
                Arguments.of(List.of("a", "lot", "of"), "a lot of"),
                Arguments.of(List.of("\u4f60", "\u597d"), "\u4f60\u597d"),             // dois caracteres soltos: sem espaço
                Arguments.of(List.of("\u6211", "love"), "\u6211 love"));                  // um caractere e uma palavra: com espaço
    }

    @ParameterizedTest
    @MethodSource("tokensParaJuntar")
    void deveJuntarTokensSoComEspacoOndeHaPalavra(List<String> tokens, String esperado) {
        assertThat(normalizador.juntar(tokens)).isEqualTo(esperado);
    }

}
