package com.lucasmanoel.ouvirfrase.ingestao;


/** Falha de infraestrutura no meio da ingestão. Carrega o que já foi feito até ali. */
public class IngestaoInterrompidaException extends RuntimeException {

    private final transient RelatorioIngestao relatorioParcial;

    public IngestaoInterrompidaException(RelatorioIngestao relatorioParcial, Throwable causa) {
        super("Ingestão interrompida: " + causa.getMessage(), causa);
        this.relatorioParcial = relatorioParcial;
    }

    public RelatorioIngestao getRelatorioParcial() {
        return relatorioParcial;
    }
}
