package com.lucasmanoel.ouvirfrase.exception;

public class FalhaNaBuscaDeLegendaException extends RuntimeException {
    public FalhaNaBuscaDeLegendaException(String message) {
        super(message);
    }
    public FalhaNaBuscaDeLegendaException(String message, Throwable cause) {
        super(message, cause);
    }
}
