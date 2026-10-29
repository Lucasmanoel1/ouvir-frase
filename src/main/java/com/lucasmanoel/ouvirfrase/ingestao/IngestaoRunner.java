package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.exception.IngestaoInterrompidaException;
import com.lucasmanoel.ouvirfrase.exception.YouTubeApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Dois modos, ambos no profile "ingestao":
 *  - sem argumentos: indexa todos os CSVs de data/{idioma}/{canal}.csv;
 *  - com --canal=@handle [--idioma=en] [--max=100]: só descobre vídeos do canal e grava o CSV.
 */
@Component
@Profile("ingestao")
@Slf4j
public class IngestaoRunner implements ApplicationRunner {

    private final IngestaoService ingestao;
    private final DescobertaDeCanal descoberta;
    private final ApplicationContext contexto;
    private final Path dados;

    public IngestaoRunner(IngestaoService ingestao, DescobertaDeCanal descoberta, ApplicationContext contexto,
                          @Value("${ingestao.dados:data}") String dados) {
        this.ingestao = ingestao;
        this.descoberta = descoberta;
        this.contexto = contexto;
        this.dados = Path.of(dados);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (args.containsOption("canal")) {
                descobrir(args);
            } else {
                log.info("\n{}", ingestao.executar(dados).resumo());
            }
        } catch (IngestaoInterrompidaException e) {
            log.error("Ingestão interrompida por falha de infraestrutura: {}\n{}",
                    e.getCause().getMessage(), e.getRelatorioParcial().resumo());
            sair(1);
        } catch (YouTubeApiException | IllegalArgumentException e) {
            log.error("Falha: {}", e.getMessage());
            sair(1);
        }
    }

    private void descobrir(ApplicationArguments args) {
        String canal = valor(args, "canal", null);
        String idioma = valor(args, "idioma", "en");
        int max = Integer.parseInt(valor(args, "max", "100"));

        DescobertaDeCanal.Resultado r = descoberta.descobrir(dados, canal, idioma, max);
        log.info("{} vídeos novos gravados em {}. Revise o arquivo e rode a ingestão (sem --canal).",
                r.gravados(), r.arquivo());
    }

    private String valor(ApplicationArguments args, String nome, String padrao) {
        List<String> valores = args.getOptionValues(nome);
        return valores == null || valores.isEmpty() || valores.get(0).isBlank() ? padrao : valores.get(0);
    }

    private void sair(int codigo) {
        System.exit(SpringApplication.exit(contexto, () -> codigo));
    }
}
