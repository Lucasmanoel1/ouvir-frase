package com.lucasmanoel.idiomando.repository;

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
 * Ordem: vídeos de legenda manual antes dos de legenda automática. Variedade: a consulta de dentro numera os acertos de cada vídeo (row_number) e a de fora
 * guarda só os primeiros MAX_POR_VIDEO de cada um, ordenando por essa numeração. Assim os
 * resultados alternam entre vídeos em vez de vir todos do primeiro vídeo que casou.
 */
@Repository
@RequiredArgsConstructor
public class BuscaRepository {

    static final int MAX_POR_VIDEO = 2;

    private final EntityManager em;

    /**
     * Limita, em milissegundos, cada consulta do restante desta transação (statement_timeout). Vale só até o fim
     * da transação. O valor vai como parâmetro de set_config, nunca no texto do SQL. Use um valor maior que zero:
     * 0 desligaria o limite, inclusive o que o papel do banco já tem em produção.
     */
    public void limitarTempo(long ms) {
        em.createNativeQuery("SELECT set_config('statement_timeout', ?1, true)")
                .setParameter(1, Long.toString(ms))
                .getSingleResult();
    }

    /** Volta ao statement_timeout de antes de limitarTempo (o do papel ou do banco), para o resto da transação. */
    public void restaurarTempo() {
        em.createNativeQuery("SET LOCAL statement_timeout TO DEFAULT").executeUpdate();
    }

    public record Acerto(String youtubeId, String titulo, double inicioSeg, double ultimaPalavraSeg,
                         double falaInicioSeg, double falaFimSeg, String legenda, boolean legendaAutomatica) {
    }

    @SuppressWarnings("unchecked")
    public List<Acerto> buscarSequencia(List<String> palavras, String idioma, int limite) {
        int n = palavras.size();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT q.youtube_id, q.titulo, q.inicio_seg, q.ultima_seg, q.fala_inicio_seg, q.fala_fim_seg, q.legenda, q.legenda_automatica FROM (")
           .append("SELECT v.id AS video_id, o1.posicao AS posicao, v.youtube_id, v.titulo, v.legenda_automatica, o1.inicio_seg, ")
           .append("o").append(n).append(".inicio_seg AS ultima_seg, t1.inicio_seg AS fala_inicio_seg, ")
           // na legenda automática a duração invade a fala seguinte: o fim vale até o início dela (least ignora o NULL do último)
           .append("least(tf.inicio_seg + tf.duracao_seg, (SELECT p.inicio_seg FROM trecho p ")
           .append("  WHERE p.video_id = tf.video_id AND p.ordem = tf.ordem + 1)) AS fala_fim_seg, ")
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
           .append("ORDER BY q.legenda_automatica, q.rn, q.video_id, q.posicao");

        Query query = em.createNativeQuery(sql.toString());
        for (int k = 1; k <= n; k++) {
            query.setParameter(k, palavras.get(k - 1));
        }
        query.setParameter(n + 1, idioma);
        query.setMaxResults(limite);

        return ((List<Object[]>) query.getResultList()).stream()
                .map(r -> new Acerto((String) r[0], (String) r[1], ((Number) r[2]).doubleValue(),
                        ((Number) r[3]).doubleValue(), ((Number) r[4]).doubleValue(), ((Number) r[5]).doubleValue(),
                        (String) r[6], (Boolean) r[7]))
                .toList();
    }

    /**
     * Busca de uma palavra só. Dá o mesmo resultado de buscarSequencia com uma palavra, mas sem a janela
     * row_number() sobre todas as ocorrências (numa palavra comum, como "the", seriam milhões de linhas):
     * escolhe primeiro os 'limite' vídeos que têm a palavra (manual antes de automática, depois id) e só então
     * pega até MAX_POR_VIDEO ocorrências de cada um com LATERAL. A ordem final é a mesma de buscarSequencia.
     * Com uma palavra, o fim da frase é a própria palavra e a legenda é o texto do próprio trecho.
     */
    @SuppressWarnings("unchecked")
    public List<Acerto> buscarPalavra(String palavra, String idioma, int limite) {
        String sql = "SELECT v.youtube_id, v.titulo, o.inicio_seg, o.inicio_seg AS ultima_seg, "
                + "t.inicio_seg AS fala_inicio_seg, "
                + "least(t.inicio_seg + t.duracao_seg, (SELECT p.inicio_seg FROM trecho p "
                + "  WHERE p.video_id = t.video_id AND p.ordem = t.ordem + 1)) AS fala_fim_seg, "
                + "t.texto AS legenda, v.legenda_automatica "
                + "FROM (SELECT v.id, v.youtube_id, v.titulo, v.legenda_automatica FROM video v "
                + "  WHERE v.idioma = :idioma "
                + "  AND EXISTS (SELECT 1 FROM ocorrencia e WHERE e.palavra = :palavra AND e.video_id = v.id) "
                + "  ORDER BY v.legenda_automatica, v.id LIMIT :limite) v "
                + "CROSS JOIN LATERAL (SELECT x.inicio_seg, x.trecho_id, x.posicao, "
                + "    row_number() OVER (ORDER BY x.posicao) AS rn "
                + "  FROM ocorrencia x WHERE x.palavra = :palavra AND x.video_id = v.id "
                + "  ORDER BY x.posicao LIMIT " + MAX_POR_VIDEO + ") o "
                + "JOIN trecho t ON t.id = o.trecho_id "
                + "ORDER BY v.legenda_automatica, o.rn, v.id, o.posicao "
                + "LIMIT :limite";
        Query query = em.createNativeQuery(sql);
        query.setParameter("palavra", palavra);
        query.setParameter("idioma", idioma);
        query.setParameter("limite", limite);

        return ((List<Object[]>) query.getResultList()).stream()
                .map(r -> new Acerto((String) r[0], (String) r[1], ((Number) r[2]).doubleValue(),
                        ((Number) r[3]).doubleValue(), ((Number) r[4]).doubleValue(), ((Number) r[5]).doubleValue(),
                        (String) r[6], (Boolean) r[7]))
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

    public record Falas(String texto, double inicioSeg, double fimSeg) {
    }

    /**
     * Texto dos trechos entre o primeiro e o último (inclusive), o início do primeiro e o fim do último
     * (sem passar do início do trecho seguinte, como em buscarSequencia).
     */
    public Falas falas(long videoId, long trechoInicio, long trechoFim) {
        // min/max só escolhem o único valor que passa no FILTER
        Query query = em.createNativeQuery(
                "SELECT string_agg(x.texto, ' ' ORDER BY x.ordem), "
                        + "min(x.inicio_seg) FILTER (WHERE x.id = ?2), "
                        + "least(max(x.inicio_seg + x.duracao_seg) FILTER (WHERE x.id = ?3), "
                        + "(SELECT p.inicio_seg FROM trecho p WHERE p.video_id = ?1 "
                        + "AND p.ordem = (SELECT ordem FROM trecho WHERE id = ?3) + 1)) "
                        + "FROM trecho x WHERE x.video_id = ?1 "
                        + "AND x.ordem BETWEEN (SELECT ordem FROM trecho WHERE id = ?2) "
                        + "AND (SELECT ordem FROM trecho WHERE id = ?3)");
        query.setParameter(1, videoId);
        query.setParameter(2, trechoInicio);
        query.setParameter(3, trechoFim);
        Object[] r = (Object[]) query.getSingleResult();
        return new Falas((String) r[0], ((Number) r[1]).doubleValue(), ((Number) r[2]).doubleValue());
    }
}
