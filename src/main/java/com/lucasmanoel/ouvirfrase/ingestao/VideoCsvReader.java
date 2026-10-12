package com.lucasmanoel.ouvirfrase.ingestao;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;


@Component
public class VideoCsvReader {

    private static final Pattern ID_YOUTUBE = Pattern.compile("[A-Za-z0-9_-]{11}");

    private static final CSVFormat FORMATO = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setCommentMarker('#')
            .get();

    public List<VideoSemente> ler(Path arquivo) {
        List<VideoSemente> sementes = new ArrayList<>();
        Set<String> vistos = new HashSet<>();

        try (Reader reader = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8);
             CSVParser parser = FORMATO.parse(reader)) {

            for (CSVRecord linha : parser) {
                String id = campo(linha, "youtube_id");
                String idioma = campo(linha, "idioma");
                String titulo = campo(linha, "titulo");

                if (!ID_YOUTUBE.matcher(id).matches()) {
                    throw new IllegalArgumentException("youtube_id inválido: " + id);
                }
                if (!vistos.add(id)) {
                    throw new IllegalArgumentException("youtube_id repetido: " + id);
                }
                sementes.add(new VideoSemente(id, idioma, titulo));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler " + arquivo, e);
        }
        return sementes;
    }

    private String campo(CSVRecord linha, String nome) {
        String valor = linha.get(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "Registro " + linha.getRecordNumber() + ": campo '" + nome + "' vazio");
        }
        return valor;
    }
}
