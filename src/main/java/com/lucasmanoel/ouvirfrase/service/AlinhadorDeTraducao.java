package com.lucasmanoel.ouvirfrase.service;

import com.lucasmanoel.ouvirfrase.dto.TrechoLegenda;

import java.util.ArrayList;
import java.util.List;

/**
 * Casa cada trecho já indexado com o trecho traduzido que começa mais perto dele (tolerância de 1 s).
 * Casar por tempo, e não por posição, aguenta o indexador ter descartado trechos sem palavras
 * e a tradução vir segmentada um pouco diferente.
 */
public final class AlinhadorDeTraducao {

    static final double TOLERANCIA_SEG = 1.0;
    static final int TAMANHO_MAXIMO = 1000;

    private AlinhadorDeTraducao() {
    }

    /** Devolve uma lista do mesmo tamanho de {@code inicios}; posição sem par fica {@code null}. */
    public static List<String> alinhar(List<Double> inicios, List<TrechoLegenda> traduzidos) {
        List<TrechoLegenda> ordenados = traduzidos.stream()
                .filter(t -> t.texto() != null && !t.texto().isBlank())
                .sorted((a, b) -> Double.compare(a.inicioSeg(), b.inicioSeg()))
                .toList();

        List<String> saida = new ArrayList<>(inicios.size());
        for (double inicio : inicios) {
            saida.add(maisProximo(ordenados, inicio));
        }
        return saida;
    }

    private static String maisProximo(List<TrechoLegenda> ordenados, double inicio) {
        int baixo = 0, alto = ordenados.size() - 1;
        while (baixo < alto) {
            int meio = (baixo + alto) >>> 1;
            if (ordenados.get(meio).inicioSeg() < inicio) {
                baixo = meio + 1;
            } else {
                alto = meio;
            }
        }
        TrechoLegenda melhor = null;
        for (int i = Math.max(0, baixo - 1); i <= Math.min(ordenados.size() - 1, baixo); i++) {
            TrechoLegenda candidato = ordenados.get(i);
            if (melhor == null || Math.abs(candidato.inicioSeg() - inicio) < Math.abs(melhor.inicioSeg() - inicio)) {
                melhor = candidato;
            }
        }
        if (melhor == null || Math.abs(melhor.inicioSeg() - inicio) > TOLERANCIA_SEG) {
            return null;
        }
        String texto = melhor.texto().replaceAll("\\s+", " ").trim();
        return texto.length() > TAMANHO_MAXIMO ? texto.substring(0, TAMANHO_MAXIMO) : texto;
    }
}
