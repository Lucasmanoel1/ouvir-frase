package com.lucasmanoel.ouvirfrase.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Busca exata no índice invertido: acha as ocorrências da 1ª palavra e exige que as
 * seguintes estejam em posições consecutivas (posicao + 1, + 2...) no mesmo vídeo.
 * O SQL só tem alias gerados (o1, o2...) e parâmetros posicionais: a frase do usuário nunca vai no texto do SQL.
 *
 * Variedade: a consulta de dentro numera os acertos de cada vídeo (row_number) e a de fora
 * guarda só os primeiros MAX_POR_VIDEO de cada um, ordenando por essa numeração. Assim os
 * resultados alternam entre vídeos em vez de vir todos do primeiro vídeo que casou.
 */
@Repository
@RequiredArgsConstructor
public class BuscaRepository {

    static final int MAX_POR_VIDEO = 2;

    private final EntityManager em;

    public record Acerto(String youtubeId, String titulo, double inicioSeg, String legenda) {
    }

    @SuppressWarnings("unchecked")
    public List<Acerto> buscarSequencia(List<String> palavras, int limite) {
        int n = palavras.size();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT q.youtube_id, q.titulo, q.inicio_seg, q.legenda FROM (")
           .append("SELECT v.id AS video_id, o1.posicao AS posicao, v.youtube_id, v.titulo, o1.inicio_seg, ")
           .append("(SELECT string_agg(x.texto, ' ' ORDER BY x.ordem) FROM trecho x ")
           .append("  WHERE x.video_id = o1.video_id AND x.ordem BETWEEN t1.ordem AND tf.ordem) AS legenda, ")
           .append("row_number() OVER (PARTITION BY v.id ORDER BY o1.posicao) AS rn ")
           .append("FROM ocorrencia o1 ");
        for (int k = 2; k <= n; k++) {
            sql.append("JOIN ocorrencia o").append(k)
               .append(" ON o").append(k).append(".video_id = o1.video_id")
               .append(" AND o").append(k).append(".posicao = o1.posicao + ").append(k - 1)
               .append(" AND o").append(k).append(".palavra = ?").append(k).append(' ');
        }
        sql.append("JOIN trecho t1 ON t1.id = o1.trecho_id ")
           .append("JOIN trecho tf ON tf.id = o").append(n).append(".trecho_id ")
           .append("JOIN video v ON v.id = o1.video_id ")
           .append("WHERE o1.palavra = ?1) q ")
           .append("WHERE q.rn <= ").append(MAX_POR_VIDEO).append(' ')
           .append("ORDER BY q.rn, q.video_id, q.posicao");

        Query query = em.createNativeQuery(sql.toString());
        for (int k = 1; k <= n; k++) {
            query.setParameter(k, palavras.get(k - 1));
        }
        query.setMaxResults(limite);

        return ((List<Object[]>) query.getResultList()).stream()
                .map(r -> new Acerto((String) r[0], (String) r[1], ((Number) r[2]).doubleValue(), (String) r[3]))
                .toList();
    }
}
