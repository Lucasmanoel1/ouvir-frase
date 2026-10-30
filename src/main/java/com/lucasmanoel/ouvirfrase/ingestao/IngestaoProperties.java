package com.lucasmanoel.ouvirfrase.ingestao;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * Parâmetros da ingestão (flags {@code --ingestao.*}). Os padrões ficam só aqui.
 *
 * @param dados   pasta raiz dos CSVs ({@code <dados>/<idioma>/<canal>.csv})
 * @param limite  máximo de vídeos indexados por execução; 0 = sem limite
 * @param pausaMs pausa entre vídeos, em milissegundos (alivia o bloqueio de IP do YouTube)
 */
@ConfigurationProperties(prefix = "ingestao")
public record IngestaoProperties(
        @DefaultValue("data") Path dados,
        @DefaultValue("0") int limite,
        @DefaultValue("1500") long pausaMs) {
}
