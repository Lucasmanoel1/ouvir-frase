// Separa as palavras de um texto, com a posição de cada uma no texto ORIGINAL (puro, sem DOM).
// Mesma regra do Normalizador do backend: letras, marcas e dígitos formam a palavra; o apóstrofo só vale
// entre caracteres (don't); chinês e japonês (Han, Hiragana, Katakana) têm um item por caractere.
const CJK = /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]/u;
const PALAVRA = /[\p{L}\p{M}\p{N}]+(?:['’][\p{L}\p{M}\p{N}]+)*/gu;

/** [{ texto, inicio, fim }]: `inicio` e `fim` são índices em `texto` (o fim não entra). */
export function palavras(texto) {
  const saida = [];
  for (const m of (texto || '').matchAll(PALAVRA)) {
    let atual = '', inicioAtual = m.index, pos = m.index;
    for (const c of m[0]) {
      if (CJK.test(c)) {
        if (atual) { saida.push({ texto: atual, inicio: inicioAtual, fim: pos }); atual = ''; }
        saida.push({ texto: c, inicio: pos, fim: pos + c.length });
      } else {
        if (!atual) inicioAtual = pos;
        atual += c;
      }
      pos += c.length;
    }
    if (atual) saida.push({ texto: atual, inicio: inicioAtual, fim: pos });
  }
  return saida;
}

/**
 * Qual palavra está sendo falada: `pesos` = letras de cada palavra (CJK = 1), `fracao` = (t - início) / duração da fala.
 * Palavra longa ocupa mais tempo. Antes do início → 0; depois do fim → a última; sem palavras → -1.
 */
export function indiceFalado(pesos, fracao) {
  if (pesos.length === 0) return -1;
  if (!(fracao > 0)) return 0;
  if (fracao >= 1) return pesos.length - 1;
  const alvo = fracao * pesos.reduce((a, b) => a + b, 0);
  let acumulado = 0;
  for (let k = 0; k < pesos.length; k++) {
    acumulado += pesos[k];
    if (alvo < acumulado) return k;
  }
  return pesos.length - 1;
}
