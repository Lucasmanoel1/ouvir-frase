package com.lucasmanoel.ouvirfrase.ingestao.indexacao;

import com.lucasmanoel.ouvirfrase.ingestao.legenda.TrechoLegenda;
import com.lucasmanoel.ouvirfrase.model.Ocorrencia;
import com.lucasmanoel.ouvirfrase.model.Trecho;
import com.lucasmanoel.ouvirfrase.model.Video;
import com.lucasmanoel.ouvirfrase.repository.OcorrenciaRepository;
import com.lucasmanoel.ouvirfrase.repository.TrechoRepository;
import com.lucasmanoel.ouvirfrase.repository.VideoRepository;
import com.lucasmanoel.ouvirfrase.service.Normalizador;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IndexadorDeVideo {

    private final VideoRepository videoRepository;
    private final TrechoRepository trechoRepository;
    private final OcorrenciaRepository ocorrenciaRepository;
    private final Normalizador normalizador;
    private final EstimadorDeTempo estimadorDeTempo;

    @Transactional
    public int indexar(String youtubeId, String titulo, String idioma,
                       List<TrechoLegenda> trechos) {
        return indexar(youtubeId, titulo, idioma, null, trechos, false);
    }

    @Transactional
    public int indexar(String youtubeId, String titulo, String idioma, String canal,
                       List<TrechoLegenda> trechos, boolean legendaAutomatica) {

        Video video = new Video();
        video.setYoutubeId(youtubeId);
        video.setTitulo(titulo);
        video.setIdioma(idioma);
        video.setCanal(canal);
        video.setLegendaAutomatica(legendaAutomatica);
        video = videoRepository.save(video);

        List<Ocorrencia> ocorrencias = new ArrayList<>();
        int posicao = 0;
        int ordem = 0;

        for (TrechoLegenda t : trechos) {
            List<String> palavras = normalizador.tokenizar(t.texto(), idioma);
            if (palavras.isEmpty()) {
                continue;
            }

            Trecho trecho = new Trecho();
            trecho.setVideo(video);
            trecho.setOrdem(ordem++);
            trecho.setTexto(t.texto().replace('\n', ' ').trim());
            trecho.setInicioSeg(t.inicioSeg());
            trecho.setDuracaoSeg(t.duracaoSeg());
            trecho = trechoRepository.save(trecho);

            for (int i = 0; i < palavras.size(); i++) {
                Ocorrencia o = new Ocorrencia();
                o.setVideoId(video.getId());
                o.setTrechoId(trecho.getId());
                o.setPalavra(palavras.get(i));
                o.setPosicao(posicao++);
                o.setInicioSeg(estimadorDeTempo.estimarInicio(
                        t.inicioSeg(), t.duracaoSeg(), i, palavras.size()));
                ocorrencias.add(o);
            }
        }

        if (ocorrencias.isEmpty()) {
            throw new IllegalArgumentException("Legenda sem palavras utilizaveis: " + youtubeId);
        }

        ocorrenciaRepository.saveAll(ocorrencias);
        return ocorrencias.size();

    }
}
