package com.lucasmanoel.idiomando.ingestao.painel.sugestoes;

import com.lucasmanoel.idiomando.ingestao.painel.sugestoes.SugestoesRepository.Contagem;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Escolhe e ordena as frases candidatas a exemplo. Classe pura: recebe as contagens, não toca no banco.
 *
 * Filtros: 2 a 4 palavras; em pelo menos 2 canais (ou no total de canais do idioma, se ele tiver menos);
 * e não ser "pedaço": se uma única palavra completa a frase em metade das vezes ou mais, à direita ou à
 * esquerda (a lot -> a lot of), a forma longa é a boa. As frases de 5 palavras só servem para esse teste.
 *
 * Ordem: soma de dois postos, o de vídeos (a frase é comum) e o de associação (as palavras andam juntas e
 * não por acaso). A associação é o NPMI no corte mais fraco da frase.
 */
@Component
public class RankingDeSugestoes {

    static final int LIMITE = 150;
    static final double RAZAO_PEDACO = 0.5;
    private static final int MIN_PALAVRAS = 2;
    private static final int MAX_PALAVRAS = 4;

    public List<Contagem> ranquear(List<Contagem> contagens, long totalPalavras, int canaisDoIdioma) {
        Map<String, Long> vezes = new HashMap<>();
        Map<String, Long> maiorADireita = new HashMap<>();   // chave: a frase; valor: a maior frase que a estende à direita
        Map<String, Long> maiorAEsquerda = new HashMap<>();
        for (Contagem c : contagens) {
            vezes.put(c.frase(), c.vezes());
            String[] palavras = c.frase().split(" ");
            if (palavras.length >= 2) {
                maiorADireita.merge(juntar(palavras, 0, palavras.length - 1), c.vezes(), Math::max);
                maiorAEsquerda.merge(juntar(palavras, 1, palavras.length), c.vezes(), Math::max);
            }
        }

        int canaisMinimos = Math.min(2, canaisDoIdioma);
        List<Contagem> candidatas = contagens.stream()
                .filter(c -> tamanhoValido(c.frase()))
                .filter(c -> c.canais() >= canaisMinimos)
                .filter(c -> !pedaco(c, maiorADireita, maiorAEsquerda))
                .toList();

        int[] postosDeVideos = postos(candidatas.stream().mapToDouble(Contagem::videos).toArray());
        double[] associacoes = candidatas.stream()
                .mapToDouble(c -> associacao(c.frase(), vezes, totalPalavras)).toArray();
        int[] postosDeAssociacao = postos(associacoes);

        return IntStream.range(0, candidatas.size()).boxed()
                .sorted(Comparator
                        .comparingInt((Integer i) -> postosDeVideos[i] + postosDeAssociacao[i])
                        .thenComparing(i -> -candidatas.get(i).videos())
                        .thenComparing(i -> candidatas.get(i).frase()))
                .limit(LIMITE)
                .map(candidatas::get)
                .toList();
    }

    /** NPMI de Bouma no corte mais fraco: o menor valor entre todos os cortes da frase em a | b. */
    static double associacao(String frase, Map<String, Long> vezes, long totalPalavras) {
        String[] palavras = frase.split(" ");
        double n = totalPalavras;
        double c = vezes.get(frase);
        double menor = Double.MAX_VALUE;
        for (int corte = 1; corte < palavras.length; corte++) {
            double a = vezes.get(juntar(palavras, 0, corte));
            double b = vezes.get(juntar(palavras, corte, palavras.length));
            double npmi = Math.log(c * n / (a * b)) / -Math.log(c / n);
            menor = Math.min(menor, npmi);
        }
        return menor;
    }

    private static boolean tamanhoValido(String frase) {
        int palavras = frase.split(" ").length;
        return palavras >= MIN_PALAVRAS && palavras <= MAX_PALAVRAS;
    }

    private static boolean pedaco(Contagem c, Map<String, Long> maiorADireita, Map<String, Long> maiorAEsquerda) {
        long maior = Math.max(maiorADireita.getOrDefault(c.frase(), 0L), maiorAEsquerda.getOrDefault(c.frase(), 0L));
        return maior >= RAZAO_PEDACO * c.vezes();
    }

    /** Posto 1 = maior valor; valores iguais dividem o mesmo posto (1 + quantos são estritamente maiores). */
    private static int[] postos(double[] valores) {
        Integer[] ordem = IntStream.range(0, valores.length).boxed().toArray(Integer[]::new);
        Arrays.sort(ordem, (x, y) -> Double.compare(valores[y], valores[x]));
        int[] posto = new int[valores.length];
        for (int i = 0; i < ordem.length; i++) {
            boolean empate = i > 0 && valores[ordem[i]] == valores[ordem[i - 1]];
            posto[ordem[i]] = empate ? posto[ordem[i - 1]] : i + 1;
        }
        return posto;
    }

    private static String juntar(String[] palavras, int de, int ate) {
        return String.join(" ", Arrays.copyOfRange(palavras, de, ate));
    }
}
