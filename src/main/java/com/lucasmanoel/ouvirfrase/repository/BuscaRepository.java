package com.lucasmanoel.ouvirfrase.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
@RequiredArgsConstructor
public class BuscaRepository {

    private final EntityManager em;

    public record Acerto(String youtubeId, String titulo, double inicioSeg, String legenda) {
    }

    @SuppressWarnings("unchecked")
    public List<Acerto> buscarSequencia(List<String> palavras, int limite) {
        int n = palavras.size();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT v.youtube_id, v.titulo, o1.inicio_seg, ")
           .append("(SELECT string_agg(x.texto, ' ' ORDER BY x.ordem) FROM trecho x ")
           .append("  WHERE x.video_id = o1.video_id AND x.ordem BETWEEN t1.ordem AND tf.ordem) AS legenda ")
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
           .append("WHERE o1.palavra = ?1 ")
           .append("ORDER BY v.id, o1.posicao");

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
