// Comparação entre a frase esperada e o que o microfone ouviu (pura, sem DOM).
import { palavras } from './palavras.js';

export function tokens(texto) {
  const limpo = (texto || '').normalize('NFKC').replace(/[’‘]/g, "'").toLowerCase()
    .replace(/\[[^\]]*\]|\([^)]*\)/g, ' ');
  return palavras(limpo).map((p) => p.texto);
}

// Compara sem acento latino e sem apóstrofo: "está"/"esta" e "dont"/"don't" são erro de reconhecimento, não de pronúncia.
function chave(token) {
  return token.normalize('NFD').replace(/[̀-ͯ']/g, '').normalize('NFC');
}

// Maior subsequência comum (na ordem) entre o que era esperado e o que foi ouvido.
export function comparar(esperado, ouvido) {
  const e = tokens(esperado), o = tokens(ouvido);
  const ke = e.map(chave), ko = o.map(chave);
  const n = e.length, m = o.length;
  const t = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      t[i][j] = ke[i] === ko[j] ? t[i + 1][j + 1] + 1 : Math.max(t[i + 1][j], t[i][j + 1]);
    }
  }
  const ok = new Array(n).fill(false);
  let i = 0, j = 0;
  while (i < n && j < m) {
    if (ke[i] === ko[j]) { ok[i] = true; i++; j++; }
    else if (t[i + 1][j] >= t[i][j + 1]) i++;
    else j++;
  }
  const casadas = ok.filter(Boolean).length;
  return {
    pontuacao: n === 0 ? 0 : casadas / n,
    casadas,
    palavras: e.map((texto, k) => ({ texto, ok: ok[k] })),
    ouvido: o,
  };
}

// "Entendi" só aparece quando acrescenta algo: nota abaixo de 100% ou palavras ouvidas a mais.
export function deveMostrarOuvido(c) {
  return c.pontuacao < 1 || c.ouvido.length > c.casadas;
}

// Alternativas a comparar, a partir dos resultados do reconhecimento: [{ final, alternativas: string[] }].
// Vários finais (pausas no meio da fala) viram uma tentativa só, juntando a melhor alternativa de cada um.
export function alternativasDe(resultados) {
  const finais = resultados.filter((r) => r.final);
  if (finais.length === 0) return [];
  if (finais.length === 1) return finais[0].alternativas;
  return [finais.map((f) => f.alternativas[0]).join(' ')];
}
