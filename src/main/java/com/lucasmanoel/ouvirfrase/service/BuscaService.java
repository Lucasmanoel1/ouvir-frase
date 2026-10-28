package com.lucasmanoel.ouvirfrase.service;

import com.lucasmanoel.ouvirfrase.dto.RespostaBusca;
import com.lucasmanoel.ouvirfrase.dto.ResultadoBusca;
import com.lucasmanoel.ouvirfrase.repository.BuscaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BuscaService {

    static final double RECUO_SEG = 3.0;
    static final int LIMITE_RESULTADOS = 50;

    private final Normalizador normalizador;
    private final BuscaRepository buscaRepository;

    @Transactional(readOnly = true)
    public RespostaBusca buscar(String frase) {
        List<String> palavras = normalizador.tokenizar(frase);
        if (palavras.isEmpty()) {
            return new RespostaBusca(List.of());
        }

        List<ResultadoBusca> resultados = buscaRepository.buscarSequencia(palavras, LIMITE_RESULTADOS).stream()
                .map(a -> new ResultadoBusca(a.youtubeId(), a.titulo(),
                        Math.max(0.0, a.inicioSeg() - RECUO_SEG), a.legenda()))
                .toList();
        return new RespostaBusca(resultados);
    }
}
