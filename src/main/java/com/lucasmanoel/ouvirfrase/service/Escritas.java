package com.lucasmanoel.ouvirfrase.service;

import java.util.EnumSet;
import java.util.Set;

/** Descobre em que escritas (latina, cirílica, hangul...) um texto está. */
public final class Escritas {

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
}
