// Regras puras da seção "Sugestões de exemplos" do painel (sem DOM; o DOM fica em painel.js).

const normalizar = (frase) => frase.toLowerCase().replace(/’/g, "'");

export const quantos = (n, singular, plural) => n + ' ' + (n === 1 ? singular : plural);

// Separa o que já está em exemplos.js (puladas) do que ainda é novidade. Idioma sem lista = nada pulado.
export function separarConhecidas(sugestoes, exemplos = []) {
  const conhecidas = new Set(exemplos.map(normalizar));
  const novas = [];
  const puladas = [];
  for (const s of sugestoes) (conhecidas.has(normalizar(s.frase)) ? puladas : novas).push(s);
  return { novas, puladas };
}

// Uma por linha, pronta para colar no chat: "at least · 41 vídeos".
export function textoParaCopiar(sugestoes) {
  return sugestoes.map((s) => s.frase + ' · ' + quantos(s.videos, 'vídeo', 'vídeos')).join('\n');
}
