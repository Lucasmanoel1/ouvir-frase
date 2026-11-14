package com.lucasmanoel.idiomando.service;

import com.ibm.icu.text.Transliterator;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Escreve em letras latinas um texto de outra escrita (russo, coreano, chinês...). Mostra as letras, não a
 * pronúncia exata. Só esta classe importa o ICU.
 *
 * O javadoc do ICU4J diz que um Transliterator não guarda estado entre chamadas, então as duas instâncias
 * são criadas uma vez e compartilhadas entre as threads.
 */
@Component
public class Transliterador {

    private final Transliterator russo = Transliterator.getInstance("Russian-Latin/BGN");
    private final Transliterator qualquer = Transliterator.getInstance("Any-Latin");

    /** Texto em letras latinas, ou null quando não há o que transliterar (vazio, só número ou já em latim). */
    public String transliterar(String texto, String idioma) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        Set<Character.UnicodeScript> escritas = Escritas.de(texto);
        if (escritas.isEmpty() || escritas.equals(Set.of(Character.UnicodeScript.LATIN))) {
            return null;
        }
        return ("ru".equalsIgnoreCase(idioma) ? russo : qualquer).transliterate(texto);
    }
}
