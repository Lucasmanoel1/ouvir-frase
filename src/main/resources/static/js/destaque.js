// Destaca a frase buscada dentro de um texto e devolve um <span class="w"> por palavra (o karaokê usa esses spans).
import { palavras } from './palavras.js';

const chave = (s) => s.toLowerCase().replace(/’/g, "'");

// Faixas [primeira, última] de palavras do texto a marcar: a frase inteira em sequência, ou (aproximada) cada palavra solta da frase.
function faixas(textoChaves, fraseChaves, aceitaSoltas) {
  const n = fraseChaves.length;
  for (let i = 0; n > 0 && i + n <= textoChaves.length; i++) {
    if (fraseChaves.every((p, j) => p === textoChaves[i + j])) return [[i, i + n - 1]];
  }
  if (!aceitaSoltas) return [];
  const buscadas = new Set(fraseChaves);
  return textoChaves.flatMap((p, k) => (buscadas.has(p) ? [[k, k]] : []));
}

/** Destaca a frase no texto ignorando pontuação e caixa entre as palavras. Devolve os spans das palavras, na ordem. */
export function renderLegenda(alvo, texto, frase, aceitaSoltas = true) {
  alvo.textContent = '';
  const ps = palavras(texto);
  if (ps.length === 0) { alvo.textContent = texto || ''; return []; }
  const marcas = faixas(ps.map((p) => chave(p.texto)), palavras(frase).map((p) => chave(p.texto)), aceitaSoltas);
  const spans = [];
  let destino = alvo, ate = -1, pos = 0, m = 0;
  ps.forEach((p, k) => {
    if (k > ate) destino = alvo;
    if (p.inicio > pos) destino.append(texto.slice(pos, p.inicio));
    if (marcas[m] && marcas[m][0] === k) {
      ate = marcas[m][1];
      m++;
      destino = document.createElement('mark');
      alvo.append(destino);
    }
    const s = document.createElement('span');
    s.className = 'w';
    s.textContent = p.texto;
    destino.append(s);
    spans.push(s);
    pos = p.fim;
  });
  if (pos < texto.length) alvo.append(texto.slice(pos));
  return spans;
}
