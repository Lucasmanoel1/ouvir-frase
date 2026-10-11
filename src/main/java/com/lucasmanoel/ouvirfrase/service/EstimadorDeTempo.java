package com.lucasmanoel.ouvirfrase.service;

import org.springframework.stereotype.Component;

@Component
public class EstimadorDeTempo {
    public double estimarInicio(double inicioTrecho, double duracaoTrecho,
                                int indice, int totalPalavras){
        if (totalPalavras <= 0){
            throw new IllegalArgumentException("Total de palavras deve ser maior que zero");
        }
        if (indice < 0 || indice >= totalPalavras){
            throw new IllegalArgumentException("Indice fora do intervalo: " + indice);
        }
        if (duracaoTrecho < 0 ){
            throw new IllegalArgumentException("duracaoTrecho nao pode ser negativa");
        }
        return inicioTrecho + ((double) indice / totalPalavras) * duracaoTrecho;
    }
}

