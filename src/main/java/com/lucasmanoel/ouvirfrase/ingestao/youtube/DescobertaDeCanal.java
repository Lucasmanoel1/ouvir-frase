package com.lucasmanoel.ouvirfrase.ingestao.youtube;

import com.lucasmanoel.ouvirfrase.ingestao.catalogo.CatalogoDeVideos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;

/**
 * Dado o link/handle de um canal, acha os vídeos candidatos e acrescenta em
 * {@code <raiz>/<idioma>/<canal>.csv}. Não indexa nada: o Lucas revisa o CSV e depois roda a ingestão.
 */
@Component
@Profile("ingestao")
@RequiredArgsConstructor
@Slf4j
public class DescobertaDeCanal {

    private static final CSVFormat FORMATO = CSVFormat.DEFAULT.builder().setRecordSeparator('\n').get();

    private final YouTubeCanalClient client;
    private final CatalogoDeVideos catalogo;

    public record Resultado(Path arquivo, int gravados) {
    }

    public Resultado descobrir(Path raiz, String canalEntrada, String idioma, int max) {
        YouTubeCanalClient.Canal canal = client.resolverCanal(canalEntrada);
        Path arquivo = raiz.resolve(idioma).resolve(canal.slug() + ".csv");
        log.info("Canal '{}' -> {}", canal.titulo(), arquivo);

        Set<String> conhecidos = catalogo.idsConhecidos(raiz);
        List<YouTubeCanalClient.VideoDoCanal> novos = client.listarVideos(canal.uploadsPlaylistId(), conhecidos, max);
        if (novos.isEmpty()) {
            return new Resultado(arquivo, 0);
        }

        try {
            Files.createDirectories(arquivo.getParent());
            boolean novoArquivo = Files.notExists(arquivo);
            try (Writer w = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                 CSVPrinter printer = new CSVPrinter(w, FORMATO)) {
                if (novoArquivo) {
                    printer.printRecord("youtube_id", "idioma", "titulo");
                }
                for (YouTubeCanalClient.VideoDoCanal v : novos) {
                    printer.printRecord(v.youtubeId(), idioma, v.titulo());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar " + arquivo, e);
        }
        return new Resultado(arquivo, novos.size());
    }
}
