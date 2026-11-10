package com.lucasmanoel.ouvirfrase.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Busca por lacunas dentro de UM vídeo: acha onde as palavras da frase aparecem na ordem
 * (a maior subsequência em comum, ou LCS) dentro de uma janela de posições.
 *
 * Regras: casadas/N >= 70% e janela de no máximo 2*N posições.
 * Classe pura (sem banco): recebe só as ocorrências das palavras da frase, ordenadas por posição.
 */
@Component
public class CasadorPorLacunas {

    public record Candidata(String palavra, int posicao, long trechoId, double inicioSeg) {
    }

    public record Casamento(int casadas, double score, Candidata primeira, Candidata ultima, int span) {
    }

    /** ceil(0,7 * n) em inteiros: n=4 -> 3, n=5 -> 4, n=10 -> 7. */
    public int minimoDeCasadas(int n) {
        return Math.max(1, (7 * n + 9) / 10);
    }

    public List<Casamento> casar(List<String> frase, List<Candidata> doVideo, int maxResultados) {
        int n = frase.size();
        int minimo = minimoDeCasadas(n);
        int janela = 2 * n;

        List<Casamento> todos = new ArrayList<>();
        for (int i = 0; i < doVideo.size(); i++) {
            int limite = doVideo.get(i).posicao() + janela;
            int fim = i;
            while (fim < doVideo.size() && doVideo.get(fim).posicao() < limite) {
                fim++;
            }
            Casamento c = melhorSubsequencia(frase, doVideo.subList(i, fim));
            if (c != null && c.casadas() >= minimo) {
                todos.add(c);
            }
        }

        // mais palavras casadas primeiro; empate: trecho mais compacto; depois o mais cedo no vídeo
        todos.sort(Comparator.<Casamento>comparingInt(Casamento::casadas).reversed()
                .thenComparingInt(Casamento::span)
                .thenComparingInt(c -> c.primeira().posicao()));

        // janelas vizinhas acham o mesmo trecho várias vezes: fica só o melhor de cada região
        List<Casamento> escolhidos = new ArrayList<>();
        for (Casamento c : todos) {
            if (escolhidos.size() >= maxResultados) {
                break;
            }
            boolean sobrepoe = escolhidos.stream().anyMatch(e ->
                    c.primeira().posicao() <= e.ultima().posicao() && e.primeira().posicao() <= c.ultima().posicao());
            if (!sobrepoe) {
                escolhidos.add(c);
            }
        }
        return escolhidos;
    }

    private Casamento melhorSubsequencia(List<String> frase, List<Candidata> janela) {
        int n = frase.size();
        int m = janela.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int a = 1; a <= n; a++) {
            for (int b = 1; b <= m; b++) {
                dp[a][b] = frase.get(a - 1).equals(janela.get(b - 1).palavra())
                        ? dp[a - 1][b - 1] + 1
                        : Math.max(dp[a - 1][b], dp[a][b - 1]);
            }
        }
        int casadas = dp[n][m];
        if (casadas == 0) {
            return null;
        }

        int a = n;
        int b = m;
        int primeiro = -1;
        int ultimo = -1;
        while (a > 0 && b > 0) {
            if (frase.get(a - 1).equals(janela.get(b - 1).palavra()) && dp[a][b] == dp[a - 1][b - 1] + 1) {
                if (ultimo < 0) {
                    ultimo = b - 1;
                }
                primeiro = b - 1;
                a--;
                b--;
            } else if (dp[a - 1][b] >= dp[a][b - 1]) {
                a--;
            } else {
                b--;
            }
        }

        Candidata p = janela.get(primeiro);
        Candidata u = janela.get(ultimo);
        return new Casamento(casadas, (double) casadas / n, p, u, u.posicao() - p.posicao() + 1);
    }
}
