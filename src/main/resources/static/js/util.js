// Utilidades pequenas, usadas por vários módulos.

export const $ = (id) => document.getElementById(id);

/** Muda a tela principal: inicial, buscando, resultados, vazio ou erro (o CSS escolhe o que mostrar). */
export const tela = (nome) => { document.body.dataset.estado = nome; };

export function icone(nome) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', 'ic');
  svg.setAttribute('aria-hidden', 'true');
  const uso = document.createElementNS('http://www.w3.org/2000/svg', 'use');
  uso.setAttribute('href', 'icons.svg#i-' + nome);
  svg.append(uso);
  return svg;
}

// localStorage pode lançar erro (navegação privada, dados bloqueados): a página funciona sem ele.
export function lerPreferencia(chave) {
  try { return localStorage.getItem(chave); } catch (e) { return null; }
}

export function gravarPreferencia(chave, valor) {
  try { localStorage.setItem(chave, valor); } catch (e) { /* ignora */ }
}

/** Milissegundos que faltam para completar `minimo` desde `inicio` (0 se já passou). */
export function faltaEsperar(inicio, agora, minimo) {
  return Math.max(0, minimo - (agora - inicio));
}

/** Até `n` itens da lista a partir de `inicio` (módulo o tamanho), dando a volta no fim e sem repetir item. */
export function fatiaCircular(lista, inicio, n) {
  if (lista.length === 0) return [];
  return Array.from({ length: Math.min(n, lista.length) }, (_, i) => lista[(inicio + i) % lista.length]);
}
