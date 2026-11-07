package com.lucasmanoel.ouvirfrase.service;

import java.time.Duration;

/** Tempo que ainda sobra a uma busca. Vale para a busca inteira, somando todas as consultas, não para cada uma. */
final class Prazo {

    private final long fimNanos;
    private boolean estourado;

    private Prazo(long fimNanos) {
        this.fimNanos = fimNanos;
    }

    static Prazo de(Duration orcamento) {
        return new Prazo(System.nanoTime() + orcamento.toNanos());
    }

    /** Milissegundos que sobram; 0 só quando acabou (no Postgres, 0 desligaria o limite, então nunca sobra 0 antes). */
    long restanteMs() {
        long nanos = fimNanos - System.nanoTime();
        return nanos <= 0 ? 0 : Math.max(1, nanos / 1_000_000);
    }

    /** O tempo acabou ou o banco já cancelou uma consulta (a transação ficou inutilizável). */
    boolean esgotado() {
        return estourado || restanteMs() == 0;
    }

    void estourar() {
        estourado = true;
    }
}
