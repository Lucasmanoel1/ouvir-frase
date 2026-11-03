package com.lucasmanoel.ouvirfrase.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static java.lang.Character.UnicodeScript.CYRILLIC;
import static java.lang.Character.UnicodeScript.HAN;
import static java.lang.Character.UnicodeScript.HIRAGANA;
import static java.lang.Character.UnicodeScript.LATIN;
import static org.assertj.core.api.Assertions.assertThat;

class EscritasTest {

    static Stream<Arguments> casos() {
        return Stream.of(
                Arguments.of("hello world", Set.of(LATIN)),
                Arguments.of("Привет, 2024!", Set.of(CYRILLIC)),
                Arguments.of("日本語です", Set.of(HAN, HIRAGANA)),
                Arguments.of("123 ?!", Set.of()),
                Arguments.of(null, Set.of()));
    }

    @ParameterizedTest
    @MethodSource("casos")
    void deveIdentificarAsEscritasDasLetras(String texto, Set<Character.UnicodeScript> esperado) {
        assertThat(Escritas.de(texto)).isEqualTo(esperado);
    }
}
