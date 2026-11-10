package com.lucasmanoel.ouvirfrase.ingestao.painel.sugestoes;

import com.lucasmanoel.ouvirfrase.ingestao.painel.sugestoes.SugestoesRepository.Totais;
import com.lucasmanoel.ouvirfrase.service.Normalizador;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Orquestra o painel de sugestões: conta no banco, ranqueia e devolve as frases prontas para mostrar. */
@Service
@Profile("painel")
@RequiredArgsConstructor
public class GeradorDeSugestoes {

    private final SugestoesRepository repository;
    private final RankingDeSugestoes ranking;
    private final Normalizador normalizador;

    @Transactional(readOnly = true)
    public List<Sugestao> gerar(String idioma, int minimo) {
        Totais totais = repository.totais(idioma);
        return ranking.ranquear(repository.contar(idioma, minimo), totais.palavras(), totais.canais()).stream()
                .map(c -> new Sugestao(normalizador.juntar(List.of(c.frase().split(" "))), c.videos(), c.vezes()))
                .toList();
    }
}
