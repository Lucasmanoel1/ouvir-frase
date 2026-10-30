// Destaca a frase buscada dentro de um texto.

function escaparRegex(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }

/** Destaca a frase no texto ignorando pontuação e caixa entre as palavras. */
export function renderLegenda(alvo, texto, frase, aceitaSoltas = true) {
  alvo.textContent = '';
  const palavras = frase.toLowerCase().match(/[\p{L}\p{M}\p{N}]+(?:['’][\p{L}\p{M}\p{N}]+)*/gu) || [];
  if (palavras.length === 0) { alvo.textContent = texto; return; }
  const sep = "[^\\p{L}\\p{M}\\p{N}]+";
  const re = new RegExp(palavras.map((p) => escaparRegex(p).replace(/'/g, "['’]")).join(sep), 'iu');
  const m = re.exec(texto);
  if (!m) {
    if (aceitaSoltas) destacarPalavras(alvo, texto, palavras); else alvo.textContent = texto;
    return;
  }
  const marca = document.createElement('mark');
  marca.textContent = m[0];
  alvo.append(texto.slice(0, m.index), marca, texto.slice(m.index + m[0].length));
}

// Frase aproximada: não há trecho contínuo, então destaca cada palavra da frase que aparece.
function destacarPalavras(alvo, texto, palavras) {
  const alternativas = [...new Set(palavras)].map((p) => escaparRegex(p).replace(/'/g, "['’]")).join('|');
  const re = new RegExp("(?<![\\p{L}\\p{M}\\p{N}'’])(?:" + alternativas + ")(?![\\p{L}\\p{M}\\p{N}'’])", 'giu');
  let ultimo = 0;
  for (const m of texto.matchAll(re)) {
    alvo.append(texto.slice(ultimo, m.index));
    const marca = document.createElement('mark');
    marca.textContent = m[0];
    alvo.append(marca);
    ultimo = m.index + m[0].length;
  }
  alvo.append(texto.slice(ultimo));
}
