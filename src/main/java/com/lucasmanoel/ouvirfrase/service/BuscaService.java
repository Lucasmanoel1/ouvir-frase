package com.lucasmanoel.ouvirfrase.service;

import com.lucasmanoel.ouvirfrase.dto.RespostaBusca;
import com.lucasmanoel.ouvirfrase.dto.ResultadoBusca;
import com.lucasmanoel.ouvirfrase.model.Video;
import com.lucasmanoel.ouvirfrase.repository.BuscaRepository;
import com.lucasmanoel.ouvirfrase.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(BuscaProperties.class)
public class BuscaService {

    private static final Logger log = LoggerFactory.getLogger(BuscaService.class);

    static final int LIMITE_RESULTADOS = 50;
    static final int LIMITE_CANDIDATAS = 20_000;
    static final int MAX_APROXIMADOS_POR_VIDEO = 2;
    /** Cada palavra da frase vira um JOIN na busca exata; este teto mantém a consulta barata. */
    static final int MAX_PALAVRAS = 15;

    private final Normalizador normalizador;
    private final BuscaRepository buscaRepository;
    private final CasadorPorLacunas casador;
    private final VideoRepository videoRepository;
    private final Transliterador transliterador;
    private final BuscaProperties propriedades;

    /**
     * Busca no idioma escolhido; se a frase não existe nele (ou está em outra escrita), procura nos outros
     * idiomas indexados que escrevem assim. Quando a frase existe nos dois, vale o escolhido.
     * <p>
     * A busca inteira tem um orçamento de tempo ({@link BuscaProperties#orcamento()}): cada consulta pesada roda com
     * o que sobrou dele e, quando acaba, a busca para e devolve o que já achou (as exatas) ou nada.
     */
    @Transactional(readOnly = true)
    public RespostaBusca buscar(String frase, String idioma) {
        List<String> tokens = normalizador.tokenizar(frase, idioma);
        if (tokens.isEmpty()) {
            return new RespostaBusca(idioma, List.of(), null);
        }
        if (tokens.size() > MAX_PALAVRAS) {
            throw new FraseLongaDemaisException(MAX_PALAVRAS);
        }

        Prazo prazo = Prazo.de(propriedades.orcamento());
        Set<Character.UnicodeScript> escritas = Escritas.de(frase);
        if (Escritas.compativel(idioma, escritas)) {
            List<ResultadoBusca> resultados = buscarEm(frase, idioma, prazo);
            if (!resultados.isEmpty()) {
                return resposta(frase, idioma, resultados);
            }
        }
        if (prazo.esgotado()) { // a transação pode estar abortada: nenhuma consulta a mais
            return resposta(frase, idioma, List.of());
        }

        // idiomasDisponiveis() já vem do maior índice para o menor
        List<String> outros = videoRepository.idiomasDisponiveis().stream()
                .filter(i -> !i.equals(idioma) && Escritas.compativel(i, escritas))
                .toList();
        // primeiro quem tem a frase exata (reaproveita os exatos achados, sem repetir a consulta)
        for (String outro : outros) {
            List<String> palavras = palavrasNoLimite(frase, outro);
            List<ResultadoBusca> achados = exatos(palavras, outro, prazo);
            if (!achados.isEmpty()) {
                return resposta(frase, outro, completarComAproximados(achados, palavras, outro, prazo));
            }
            if (prazo.esgotado()) {
                return resposta(frase, idioma, List.of());
            }
        }
        for (String outro : outros) {
            List<ResultadoBusca> resultados = buscarEm(frase, outro, prazo);
            if (!resultados.isEmpty()) {
                return resposta(frase, outro, resultados);
            }
            if (prazo.esgotado()) {
                return resposta(frase, idioma, List.of());
            }
        }
        return resposta(frase, idioma, List.of());
    }

    /** Primeiro as frases exatas; se sobrar espaço, as aproximadas (>= 70% das palavras, na ordem). */
    private List<ResultadoBusca> buscarEm(String frase, String idioma, Prazo prazo) {
        List<String> palavras = palavrasNoLimite(frase, idioma);
        return completarComAproximados(exatos(palavras, idioma, prazo), palavras, idioma, prazo);
    }

    /** Palavras da frase no idioma; vazio se não houver nenhuma ou se passar do limite (o idioma é pulado). */
    private List<String> palavrasNoLimite(String frase, String idioma) {
        List<String> palavras = normalizador.tokenizar(frase, idioma);
        return palavras.size() > MAX_PALAVRAS ? List.of() : palavras;
    }

    private List<ResultadoBusca> completarComAproximados(List<ResultadoBusca> exatos, List<String> palavras,
                                                         String idioma, Prazo prazo) {
        List<ResultadoBusca> resultados = new ArrayList<>(exatos);
        if (palavras.size() >= 2 && resultados.size() < LIMITE_RESULTADOS && !prazo.esgotado()) {
            resultados.addAll(aproximados(palavras, idioma, LIMITE_RESULTADOS - resultados.size(), prazo));
        }
        return resultados;
    }

    private RespostaBusca resposta(String frase, String idioma, List<ResultadoBusca> resultados) {
        return new RespostaBusca(idioma, resultados, transliterador.transliterar(frase, idioma));
    }

    private List<ResultadoBusca> exatos(List<String> palavras, String idioma, Prazo prazo) {
        if (palavras.isEmpty()) {
            return List.of();
        }
        // uma palavra só: consulta própria, que não percorre todas as ocorrências de uma palavra comum
        List<BuscaRepository.Acerto> acertos = consultar(prazo, () -> palavras.size() == 1
                ? buscaRepository.buscarPalavra(palavras.get(0), idioma, LIMITE_RESULTADOS)
                : buscaRepository.buscarSequencia(palavras, idioma, LIMITE_RESULTADOS));
        return acertos.stream()
                .map(a -> new ResultadoBusca(a.youtubeId(), a.titulo(), a.inicioSeg(), a.ultimaPalavraSeg(),
                        a.falaInicioSeg(), a.falaFimSeg(), a.legenda(), 1.0, true,
                        a.legendaAutomatica(), transliterador.transliterar(a.legenda(), idioma)))
                .toList();
    }

    private List<ResultadoBusca> aproximados(List<String> palavras, String idioma, int limite, Prazo prazo) {
        Map<Long, List<CasadorPorLacunas.Candidata>> porVideo = carregarCandidatas(palavras, idioma, prazo);
        if (porVideo.isEmpty()) { // sem candidatas (ou o prazo acabou): nada a casar nem a carregar
            return List.of();
        }
        List<Achado> achados = casarPorVideo(palavras, porVideo);

        Map<Long, Video> videos = videoRepository
                .findAllById(achados.stream().map(Achado::videoId).distinct().toList()).stream()
                .collect(Collectors.toMap(Video::getId, v -> v));

        // mesmo score: legenda manual antes da automática (false < true), depois o trecho mais compacto
        achados.sort(Comparator.comparingDouble((Achado a) -> a.casamento().score()).reversed()
                .thenComparing((Achado a) -> videos.get(a.videoId()).isLegendaAutomatica())
                .thenComparingInt(a -> a.casamento().span()));

        return achados.stream().limit(limite)
                .map(a -> montarResultado(a.casamento(), a.videoId(), videos.get(a.videoId()), idioma))
                .toList();
    }

    /** Ocorrências das palavras da frase, agrupadas por vídeo (só vídeos que têm palavras suficientes). */
    private Map<Long, List<CasadorPorLacunas.Candidata>> carregarCandidatas(List<String> palavras, String idioma,
                                                                            Prazo prazo) {
        int n = palavras.size();
        List<String> distintas = palavras.stream().distinct().toList();
        // o vídeo pode perder no máximo (n - mínimo) palavras, então precisa ter quase todas as distintas
        int distintasMinimas = Math.max(1, distintas.size() - (n - casador.minimoDeCasadas(n)));

        Map<Long, List<CasadorPorLacunas.Candidata>> porVideo = new LinkedHashMap<>();
        for (BuscaRepository.OcorrenciaCandidata o : consultar(prazo,
                () -> buscaRepository.buscarCandidatas(distintas, idioma, distintasMinimas, LIMITE_CANDIDATAS))) {
            porVideo.computeIfAbsent(o.videoId(), k -> new ArrayList<>())
                    .add(new CasadorPorLacunas.Candidata(o.palavra(), o.posicao(), o.trechoId(), o.inicioSeg()));
        }
        return porVideo;
    }

    /** Casamentos aproximados de cada vídeo, sem repetir a frase exata (essa já veio da busca exata). */
    private List<Achado> casarPorVideo(List<String> palavras, Map<Long, List<CasadorPorLacunas.Candidata>> porVideo) {
        int n = palavras.size();
        List<Achado> achados = new ArrayList<>();
        porVideo.forEach((videoId, ocorrencias) -> {
            int aceitos = 0;
            for (CasadorPorLacunas.Casamento c : casador.casar(palavras, ocorrencias, 10)) {
                boolean exataContigua = c.casadas() == n && c.span() == n;
                if (!exataContigua && aceitos < MAX_APROXIMADOS_POR_VIDEO) {
                    achados.add(new Achado(videoId, c));
                    aceitos++;
                }
            }
        });
        return achados;
    }

    private ResultadoBusca montarResultado(CasadorPorLacunas.Casamento c, long videoId, Video v, String idioma) {
        BuscaRepository.Falas f = buscaRepository.falas(videoId, c.primeira().trechoId(), c.ultima().trechoId());
        return new ResultadoBusca(v.getYoutubeId(), v.getTitulo(), c.primeira().inicioSeg(), c.ultima().inicioSeg(),
                f.inicioSeg(), f.fimSeg(), f.texto(), c.score(), false,
                v.isLegendaAutomatica(), transliterador.transliterar(f.texto(), idioma));
    }

    /**
     * Roda uma consulta pesada com o que sobra do prazo como statement_timeout do Postgres (o banco cancela a
     * consulta, não só o Java deixa de esperar). Se estourar, o Postgres deixa a transação abortada: marca o prazo
     * como esgotado (ninguém mais consulta), manda a transação desfazer e devolve lista vazia.
     */
    private <T> List<T> consultar(Prazo prazo, Supplier<List<T>> consulta) {
        if (prazo.esgotado()) {
            return List.of();
        }
        buscaRepository.limitarTempo(prazo.restanteMs());
        try {
            List<T> resultado = consulta.get();
            buscaRepository.restaurarTempo(); // as consultas curtas que vêm depois não ficam com o resto do prazo
            return resultado;
        } catch (QueryTimeoutException e) {
            log.warn("Busca passou do orçamento de {} ms; devolvendo o que já foi achado", propriedades.orcamento().toMillis());
            prazo.estourar();
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return List.of();
        }
    }

    private record Achado(long videoId, CasadorPorLacunas.Casamento casamento) {
    }

    /** Idiomas com vídeos indexados, do que tem mais vídeos para o que tem menos. */
    @Transactional(readOnly = true)
    public List<String> idiomas() {
        return videoRepository.idiomasDisponiveis();
    }
}
