package com.lucasmanoel.ouvirfrase.ingestao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@Profile("ingestao")
@Slf4j
public class IngestaoRunner implements ApplicationRunner {

    private final IngestaoService service;
    private final Path csv;

    public IngestaoRunner(IngestaoService service, @Value("${ingestao.csv:data/videos.csv}") String csv) {
        this.service = service;
        this.csv = Path.of(csv);
    }

    @Override
    public void run(ApplicationArguments args) {
        RelatorioIngestao relatorio = service.executar(csv);
        log.info("\n{}", relatorio.resumo());
    }
}
