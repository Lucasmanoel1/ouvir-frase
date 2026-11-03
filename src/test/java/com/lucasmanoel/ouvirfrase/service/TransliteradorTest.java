package com.lucasmanoel.ouvirfrase.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransliteradorTest {

    private final Transliterador transliterador = new Transliterador();

    @Test
    void deveTransliterarRussoNoPadraoBgn() {
        assertThat(transliterador.transliterar("Привет", "ru")).isEqualTo("Privet");
        // o padrão BGN usa o primo (U+02B9) para o sinal brando
        assertThat(transliterador.transliterar("Спасибо большое", "ru")).isEqualTo("Spasibo bol\u02b9shoye");
    }

    @Test
    void deveDevolverNullParaTextoJaEmLatim() {
        assertThat(transliterador.transliterar("hello", "en")).isNull();
        assertThat(transliterador.transliterar("café", "es")).isNull();
    }

    @Test
    void deveDevolverNullQuandoNaoHaLetras() {
        assertThat(transliterador.transliterar(null, "ru")).isNull();
        assertThat(transliterador.transliterar("", "ru")).isNull();
        assertThat(transliterador.transliterar("?! 123", "ru")).isNull();
    }

    @Test
    void deveUsarAnyLatinParaIdiomaSemRegraPropria() {
        assertThat(transliterador.transliterar("안녕하세요", "ko")).isEqualTo("annyeonghaseyo");
    }

    @Test
    void deveTransliterarSoAParteCirilicaDeUmTextoMisto() {
        assertThat(transliterador.transliterar("Hello, Привет", "ru")).isEqualTo("Hello, Privet");
    }
}
