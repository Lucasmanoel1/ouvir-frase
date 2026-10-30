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
