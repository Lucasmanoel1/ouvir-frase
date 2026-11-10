package com.lucasmanoel.ouvirfrase.ingestao.legenda;

public class FalhaNaBuscaDeLegendaException extends RuntimeException {
    public FalhaNaBuscaDeLegendaException(String message) {
        super(message);
    }
    public FalhaNaBuscaDeLegendaException(String message, Throwable cause) {
        super(message, cause);
    }
}
