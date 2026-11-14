package com.lucasmanoel.idiomando.service;

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

    static Stream<Arguments> compatibilidade() {
        return Stream.of(
                Arguments.of("es", Set.of(LATIN), true),
                Arguments.of("ru", Set.of(LATIN), false),
                Arguments.of("ja", Set.of(HAN, HIRAGANA), true),
                Arguments.of("zh-CN", Set.of(HAN, HIRAGANA), false),
                Arguments.of("ar", Set.of(LATIN), true),
                Arguments.of("ru", Set.of(), true));
    }

    @ParameterizedTest
    @MethodSource("compatibilidade")
    void deveDizerSeOIdiomaEscreveComAsEscritas(String idioma, Set<Character.UnicodeScript> escritas, boolean esperado) {
        assertThat(Escritas.compativel(idioma, escritas)).isEqualTo(esperado);
    }
}
