package com.lucasmanoel.idiomando.service;

import java.lang.Character.UnicodeScript;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Descobre em que escritas (latina, cirílica, hangul...) um texto está. */
public final class Escritas {

    private static final Set<UnicodeScript> LATINA = EnumSet.of(UnicodeScript.LATIN);

    /** Escritas de cada idioma indexado (código da pasta em data/). Idioma fora daqui não é filtrado. */
    private static final Map<String, Set<UnicodeScript>> POR_IDIOMA = Map.of(
            "en", LATINA, "es", LATINA, "fr", LATINA, "de", LATINA, "it", LATINA, "pt", LATINA,
            "ru", EnumSet.of(UnicodeScript.CYRILLIC),
            "ko", EnumSet.of(UnicodeScript.HANGUL),
            "ja", EnumSet.of(UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA, UnicodeScript.HAN),
            "zh-CN", EnumSet.of(UnicodeScript.HAN));

    private Escritas() {
    }

    /** Escritas das letras do texto; número, pontuação e espaço não contam. Sem letras: conjunto vazio. */
    public static Set<Character.UnicodeScript> de(String texto) {
        Set<Character.UnicodeScript> escritas = EnumSet.noneOf(Character.UnicodeScript.class);
        if (texto == null) {
            return escritas;
        }
        texto.codePoints()
                .filter(Character::isLetter)
                .mapToObj(Character.UnicodeScript::of)
                .filter(s -> s != Character.UnicodeScript.COMMON && s != Character.UnicodeScript.INHERITED)
                .forEach(escritas::add);
        return escritas;
    }

    /** O idioma escreve com todas essas escritas? Sem letras ou idioma desconhecido: sim (nada a descartar). */
    public static boolean compativel(String idioma, Set<UnicodeScript> escritas) {
        Set<UnicodeScript> doIdioma = POR_IDIOMA.get(idioma);
        return escritas.isEmpty() || doIdioma == null || doIdioma.containsAll(escritas);
    }
}
