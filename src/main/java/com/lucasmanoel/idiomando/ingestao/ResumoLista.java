package com.lucasmanoel.idiomando.ingestao;

/**
 * O que o painel mostra de cada lista (CSV) cadastrada.
 * {@code total} conta só os vídeos da lista (vídeo repetido em outro CSV fica na primeira ocorrência).
 */
public record ResumoLista(String idioma, String nome, boolean playlist,
                          int total, int indexados, int descartados, int pendentes) {
}
