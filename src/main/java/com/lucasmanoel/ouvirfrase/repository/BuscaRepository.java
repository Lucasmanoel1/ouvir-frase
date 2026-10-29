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

    public record Acerto(String youtubeId, String titulo, double inicioSeg, double fimSeg, String legenda) {
    }

    @SuppressWarnings("unchecked")
    public List<Acerto> buscarSequencia(List<String> palavras, String idioma, int limite) {
        int n = palavras.size();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT q.youtube_id, q.titulo, q.inicio_seg, q.fim_seg, q.legenda FROM (")
           .append("SELECT v.id AS video_id, o1.posicao AS posicao, v.youtube_id, v.titulo, o1.inicio_seg, ")
           .append("(tf.inicio_seg + tf.duracao_seg) AS fim_seg, ")
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
           .append("WHERE o1.palavra = ?1 AND v.idioma = ?").append(n + 1).append(") q ")
           .append("WHERE q.rn <= ").append(MAX_POR_VIDEO).append(' ')
           .append("ORDER BY q.rn, q.video_id, q.posicao");

        Query query = em.createNativeQuery(sql.toString());
        for (int k = 1; k <= n; k++) {
            query.setParameter(k, palavras.get(k - 1));
        }
        query.setParameter(n + 1, idioma);
        query.setMaxResults(limite);

        return ((List<Object[]>) query.getResultList()).stream()
                .map(r -> new Acerto((String) r[0], (String) r[1], ((Number) r[2]).doubleValue(),
                        ((Number) r[3]).doubleValue(), (String) r[4]))
                .toList();
    }

    public record OcorrenciaCandidata(long videoId, String palavra, int posicao, long trechoId, double inicioSeg) {
    }

    /**
     * Candidatas da busca por lacunas: ocorrências das palavras da frase, só nos vídeos que contêm
     * pelo menos 'distintasMinimas' dessas palavras. Ordenadas por vídeo e posição.
     * 'limite' protege contra palavras muito comuns (o corte descarta os últimos vídeos).
     */
    @SuppressWarnings("unchecked")
    public List<OcorrenciaCandidata> buscarCandidatas(List<String> palavras, String idioma,
                                                      int distintasMinimas, int limite) {
        String sql = "SELECT o.video_id, o.palavra, o.posicao, o.trecho_id, o.inicio_seg FROM ocorrencia o "
                + "WHERE o.palavra IN (:palavras) AND o.video_id IN (SELECT id FROM video WHERE idioma = :idioma) AND o.video_id IN ("
                + "SELECT c.video_id FROM ocorrencia c WHERE c.palavra IN (:palavras) "
                + "AND c.video_id IN (SELECT id FROM video WHERE idioma = :idioma) "
                + "GROUP BY c.video_id HAVING count(DISTINCT c.palavra) >= " + distintasMinimas + ") "
                + "ORDER BY o.video_id, o.posicao";
        Query query = em.createNativeQuery(sql);
        query.setParameter("palavras", palavras);
        query.setParameter("idioma", idioma);
        query.setMaxResults(limite);

        return ((List<Object[]>) query.getResultList()).stream()
                .map(r -> new OcorrenciaCandidata(((Number) r[0]).longValue(), (String) r[1],
                        ((Number) r[2]).intValue(), ((Number) r[3]).longValue(), ((Number) r[4]).doubleValue()))
                .toList();
    }

    /** Instante em que o trecho termina (início + duração). */
    public double fimDoTrecho(long trechoId) {
        Query query = em.createNativeQuery("SELECT inicio_seg + duracao_seg FROM trecho WHERE id = ?1");
        query.setParameter(1, trechoId);
        return ((Number) query.getSingleResult()).doubleValue();
    }

    /** Texto dos trechos entre o primeiro e o último (inclusive) de um vídeo. */
    public String legenda(long videoId, long trechoInicio, long trechoFim) {
        Query query = em.createNativeQuery(
                "SELECT string_agg(x.texto, ' ' ORDER BY x.ordem) FROM trecho x WHERE x.video_id = ?1 "
                        + "AND x.ordem BETWEEN (SELECT ordem FROM trecho WHERE id = ?2) "
                        + "AND (SELECT ordem FROM trecho WHERE id = ?3)");
        query.setParameter(1, videoId);
        query.setParameter(2, trechoInicio);
        query.setParameter(3, trechoFim);
        return (String) query.getSingleResult();
    }
}
