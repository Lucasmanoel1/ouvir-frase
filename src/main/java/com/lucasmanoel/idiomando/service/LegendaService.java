package com.lucasmanoel.idiomando.service;

import com.lucasmanoel.idiomando.dto.LegendaTrecho;
import com.lucasmanoel.idiomando.repository.TrechoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LegendaService {

    private final TrechoRepository trechoRepository;

    @Transactional(readOnly = true)
    public List<LegendaTrecho> legendaDoVideo(String youtubeId) {
        return trechoRepository.findByVideoYoutubeIdOrderByOrdem(youtubeId).stream()
                .map(t -> new LegendaTrecho(t.getTexto(), t.getInicioSeg(), t.getDuracaoSeg()))
                .toList();
    }
}
