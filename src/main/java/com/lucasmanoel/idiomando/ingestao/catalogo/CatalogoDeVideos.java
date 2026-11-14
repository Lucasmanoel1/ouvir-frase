package com.lucasmanoel.idiomando.ingestao.catalogo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lê a pasta de dados organizada como {@code <raiz>/<idioma>/<canal>.csv}.
 * O idioma vem da pasta, o canal vem do nome do arquivo. O CSV continua com a coluna idioma
 * (formato do {@link VideoCsvReader}), que precisa bater com a pasta.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogoDeVideos {

    private final VideoCsvReader reader;

    public List<VideoSemente> carregar(Path raiz) {
        List<VideoSemente> todos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();

        for (Path arquivo : listarCsvs(raiz)) {
            ListaDeVideos lista = ListaDeVideos.doArquivo(arquivo);

            for (VideoSemente s : reader.ler(arquivo)) {
                if (!s.idioma().equals(lista.idioma())) {
                    throw new IllegalArgumentException("%s: idioma '%s' não bate com a pasta '%s' (vídeo %s)"
                            .formatted(arquivo, s.idioma(), lista.idioma(), s.youtubeId()));
                }
                if (vistos.add(s.youtubeId())) {
                    todos.add(new VideoSemente(s.youtubeId(), s.idioma(), s.titulo(), lista.nome()));
                } else {
                    log.warn("Vídeo [{}] repetido em {}; mantida a primeira ocorrência", s.youtubeId(), arquivo);
                }
            }
        }
        return todos;
    }

    public Set<String> idsConhecidos(Path raiz) {
        Set<String> ids = new HashSet<>();
        carregar(raiz).forEach(s -> ids.add(s.youtubeId()));
        return ids;
    }

    private List<Path> listarCsvs(Path raiz) {
        try (Stream<Path> arquivos = Files.walk(raiz, 2)) {
            return arquivos
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".csv"))
                    .filter(p -> !p.getParent().equals(raiz))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler a pasta " + raiz, e);
        }
    }
}
