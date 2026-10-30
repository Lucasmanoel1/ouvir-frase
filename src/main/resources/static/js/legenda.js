// Legenda que acompanha o vídeo: mostra a fala do instante atual, com a frase buscada destacada.
import { $ } from './util.js';
import { busca } from './estado.js';
import { buscarLegenda } from './api.js';
import { renderLegenda } from './destaque.js';
import { prepararTraducao, limparTraducao, mostrarTraducao } from './traducao.js';

const RECUO_SEG = 3; // quanto o backend recua o início do resultado

let cues = null, cueAtual = -2;

// Última fala que começou até o instante t; passado um instante do fim dela, nenhuma (música, silêncio).
function cueEm(t) {
  let baixo = 0, alto = cues.length - 1, achado = -1;
  while (baixo <= alto) {
    const meio = (baixo + alto) >> 1;
    if (cues[meio].inicioSeg <= t + 0.1) { achado = meio; baixo = meio + 1; } else { alto = meio - 1; }
  }
  if (achado >= 0 && t > cues[achado].inicioSeg + cues[achado].duracaoSeg + 0.6) return -1;
  return achado;
}

export function aoTempo(t) {
  if (!cues) return;
  const i = cueEm(t);
  if (i === cueAtual) return;
  cueAtual = i;
  const c = i >= 0 ? cues[i] : null;
  const r = busca.resultados[busca.indice];
  // palavras soltas (frase aproximada) só dentro do trecho do resultado, para não marcar "the" em toda fala
  const soltas = !!c && r.exata === false && c.inicioSeg >= r.inicioSeg + RECUO_SEG - 0.1 && c.inicioSeg < r.fimSeg;
  renderLegenda($('legenda'), c ? c.texto : '', busca.frase, soltas);
  traduzirFala(i);
}

// A fala atual e as duas seguintes, para a tradução já ficar pronta quando elas chegarem.
function traduzirFala(i) {
  const textos = cues && i >= 0 ? cues.slice(i, i + 3).map((c) => c.texto) : null;
  mostrarTraducao(textos, () => cueAtual === i);
}

/** Chamada quando a tradução fica pronta ou é ligada/desligada. */
export function retraduzirFalaAtual() { traduzirFala(cueAtual); }

export function carregarLegenda(r) {
  cues = null;
  cueAtual = -2;
  limparTraducao();
  prepararTraducao(busca.idioma);
  buscarLegenda(r.youtubeId).then((l) => {
    const atual = busca.resultados[busca.indice];
    if (!l || !atual || atual.youtubeId !== r.youtubeId) return; // falhou (fica o trecho estático) ou o usuário já trocou
    cues = l;
    cueAtual = -2;
  });
}
