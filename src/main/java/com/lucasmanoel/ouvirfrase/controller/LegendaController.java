package com.lucasmanoel.ouvirfrase.controller;

import com.lucasmanoel.ouvirfrase.dto.LegendaTrecho;
import com.lucasmanoel.ouvirfrase.service.LegendaService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LegendaController {

    private final LegendaService legendaService;

    // A legenda completa do vídeo, em ordem, para o front acompanhar o vídeo. O dado quase não muda, então o navegador guarda.
    @GetMapping("/videos/{youtubeId}/legenda")
    public ResponseEntity<List<LegendaTrecho>> legenda(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{6,20}") String youtubeId) {
        List<LegendaTrecho> legenda = legendaService.legendaDoVideo(youtubeId);
        // vídeo desconhecido (lista vazia) não é guardado, para não esconder uma indexação futura
        CacheControl cache = legenda.isEmpty() ? CacheControl.noStore() : CacheControl.maxAge(Duration.ofDays(1)).cachePublic();
        return ResponseEntity.ok().cacheControl(cache).body(legenda);
    }
}
