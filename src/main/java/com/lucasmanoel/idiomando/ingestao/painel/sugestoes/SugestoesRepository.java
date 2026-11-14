package com.lucasmanoel.idiomando.ingestao.painel.sugestoes;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Contagem de frases do índice para o gerador de sugestões. Só o Postgres conta (janela + agregação):
 * contar em Java pediria milhões de chaves no heap. O ranking fica em {@link RankingDeSugestoes}.
 */
@Repository
@RequiredArgsConstructor
public class SugestoesRepository {

    /** frase = palavras normalizadas separadas por um espaço. */
    public record Contagem(String frase, long vezes, int videos, int canais) {
    }

    public record Totais(long palavras, int canais) {
    }

    /**
     * lead(trecho_id, k) = trecho_id: a k-ésima palavra seguinte está no mesmo trecho (as posições de um
     * trecho são seguidas, então p3 não nulo garante p2 não nulo). por_video agrupa antes, para que o
     * count(*) de fora seja de vídeos e o count(DISTINCT canal), de canais. Vídeo sem canal vira o canal ''.
     */
    private static final String CONTAGEM = """
            WITH seq AS (
                SELECT o.video_id, coalesce(v.canal, '') AS canal, o.palavra AS p1,
                       CASE WHEN lead(o.trecho_id, 1) OVER w = o.trecho_id THEN lead(o.palavra, 1) OVER w END AS p2,
                       CASE WHEN lead(o.trecho_id, 2) OVER w = o.trecho_id THEN lead(o.palavra, 2) OVER w END AS p3,
                       CASE WHEN lead(o.trecho_id, 3) OVER w = o.trecho_id THEN lead(o.palavra, 3) OVER w END AS p4,
                       CASE WHEN lead(o.trecho_id, 4) OVER w = o.trecho_id THEN lead(o.palavra, 4) OVER w END AS p5
                FROM ocorrencia o
                JOIN video v ON v.id = o.video_id
                WHERE v.idioma = :idioma
                WINDOW w AS (PARTITION BY o.video_id ORDER BY o.posicao)
            ),
            frases AS (
                SELECT video_id, canal, p1 AS frase FROM seq
                UNION ALL SELECT video_id, canal, p1 || ' ' || p2 FROM seq WHERE p2 IS NOT NULL
                UNION ALL SELECT video_id, canal, p1 || ' ' || p2 || ' ' || p3 FROM seq WHERE p3 IS NOT NULL
                UNION ALL SELECT video_id, canal, p1 || ' ' || p2 || ' ' || p3 || ' ' || p4 FROM seq WHERE p4 IS NOT NULL
                UNION ALL SELECT video_id, canal, p1 || ' ' || p2 || ' ' || p3 || ' ' || p4 || ' ' || p5 FROM seq WHERE p5 IS NOT NULL
            ),
            por_video AS (
                SELECT frase, video_id, canal, count(*) AS vezes
                FROM frases
                GROUP BY frase, video_id, canal
            )
            SELECT frase, sum(vezes) AS vezes, count(*) AS videos, count(DISTINCT canal) AS canais
            FROM por_video
            GROUP BY frase
            HAVING count(*) >= :minimo
            """;

    private static final String TOTAIS = """
            SELECT (SELECT count(*) FROM ocorrencia o JOIN video v ON v.id = o.video_id WHERE v.idioma = :idioma),
                   (SELECT count(DISTINCT coalesce(canal, '')) FROM video WHERE idioma = :idioma)
            """;

    private final EntityManager em;

    /** Frases de 1 a 5 palavras seguidas dentro do mesmo trecho, em pelo menos 'minimo' vídeos. Sem ordem. */
    @SuppressWarnings("unchecked")
    public List<Contagem> contar(String idioma, int minimo) {
        List<Object[]> linhas = em.createNativeQuery(CONTAGEM)
                .setParameter("idioma", idioma)
                .setParameter("minimo", minimo)
                .getResultList();
        // sum volta como BigDecimal e count como Long: lê tudo por Number
        return linhas.stream()
                .map(r -> new Contagem((String) r[0], ((Number) r[1]).longValue(),
                        ((Number) r[2]).intValue(), ((Number) r[3]).intValue()))
                .toList();
    }

    /** Total de palavras do idioma (o N do NPMI) e quantos canais ele tem. */
    public Totais totais(String idioma) {
        Object[] r = (Object[]) em.createNativeQuery(TOTAIS)
                .setParameter("idioma", idioma)
                .getSingleResult();
        return new Totais(((Number) r[0]).longValue(), ((Number) r[1]).intValue());
    }
}
