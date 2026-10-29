package com.lucasmanoel.ouvirfrase.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforma texto em tokens. A mesma função serve para o índice e para a frase digitada.
 *
 * Regras por tipo de escrita:
 *  - letras, marcas combinadas (hindi, árabe...) e dígitos formam a palavra;
 *  - chinês, japonês (kana) e kanji não têm espaço entre palavras: cada caractere vira um token,
 *    o que torna a busca da frase uma busca de sequência de caracteres;
 *  - só o inglês mantém o apóstrofo dentro da palavra (don't). Nos demais idiomas ele separa
 *    (l'homme -> l, homme), senão "homme" nunca acharia "l'homme".
 */
@Component
public class Normalizador {

    private static final String IDIOMA_PADRAO = "en";

    private static final Pattern MARCACAO_DE_SOM =
            Pattern.compile("\\[[^\\]]*\\]|\\([^)]*\\)");

    private static final String CARACTERE_SOLTO = "\\p{IsHan}|\\p{IsHiragana}|\\p{IsKatakana}";
    private static final String PALAVRA =
            "[\\p{L}\\p{M}\\p{N}&&[^\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]]+";

    private static final Pattern TOKEN_COM_APOSTROFO =
            Pattern.compile(CARACTERE_SOLTO + "|" + PALAVRA + "(?:'" + PALAVRA + ")*");
    private static final Pattern TOKEN_SEM_APOSTROFO =
            Pattern.compile(CARACTERE_SOLTO + "|" + PALAVRA);

    public List<String> tokenizar(String texto) {
        return tokenizar(texto, IDIOMA_PADRAO);
    }

    public List<String> tokenizar(String texto, String idioma) {
        if (texto == null || texto.isEmpty()) {
            return List.of();
        }

        String limpo = Normalizer.normalize(texto, Normalizer.Form.NFKC)
                .replace('’', '\'')   // ’
                .replace('‘', '\'')   // ‘
                .replace('ʼ', '\'')   // ʼ
                .toLowerCase(Locale.ROOT);

        limpo = MARCACAO_DE_SOM.matcher(limpo).replaceAll(" ");

        Pattern token = mantemApostrofo(idioma) ? TOKEN_COM_APOSTROFO : TOKEN_SEM_APOSTROFO;
        List<String> tokens = new ArrayList<>();
        Matcher matcher = token.matcher(limpo);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private boolean mantemApostrofo(String idioma) {
        return idioma == null || idioma.toLowerCase(Locale.ROOT).startsWith("en");
    }
}
