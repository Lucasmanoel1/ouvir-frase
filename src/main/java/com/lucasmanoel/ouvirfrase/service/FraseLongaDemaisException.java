package com.lucasmanoel.ouvirfrase.service;

/** A frase tem mais palavras do que a busca aceita (cada palavra vira um JOIN no SQL). */
public class FraseLongaDemaisException extends RuntimeException {

    public FraseLongaDemaisException(int limite) {
        super("Use até " + limite + " palavras.");
    }
}
