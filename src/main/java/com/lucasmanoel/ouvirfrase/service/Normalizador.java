package com.lucasmanoel.ouvirfrase.service;

import org.springframework.stereotype.Component;

import javax.swing.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Normalizador {

    private static final Pattern MARCACAO_DE_SOM =
            Pattern.compile("\\[[^\\]]*\\]|\\([^)]*\\)");

    private static final Pattern TOKEN =
            Pattern.compile("[\\p{L}\\p{N}]+(?:'[\\p{L}\\p{N}]+)*");

    public List<String> tokenizar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return List.of();
        }

        String limpo = Normalizer.normalize(texto, Normalizer.Form.NFKC)
                .replace('\u2019', '\'')   // ’
                .replace('\u2018', '\'')   // ‘
                .replace('\u02BC', '\'')   // ʼ
                .toLowerCase(Locale.ROOT);

        limpo = MARCACAO_DE_SOM.matcher(limpo).replaceAll(" ");

        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(limpo);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }
}
