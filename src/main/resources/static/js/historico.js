// Buscas recentes (guardadas só no navegador) e frases de exemplo.
import { $, icone, lerPreferencia, gravarPreferencia } from './util.js';
import { busca } from './estado.js';

const CHAVE = 'ouvirfrase.historico';
const EXEMPLOS = { en: ['thank you very much', 'you know what', 'a lot of'] };

const campo = $('frase'), idiomaSel = $('idioma');
let buscar = () => {};

export function iniciarHistorico(aoBuscar) { buscar = aoBuscar; }

function lerHistorico() {
  try {
    const h = JSON.parse(lerPreferencia(CHAVE));
    return Array.isArray(h) ? h.filter((x) => x && typeof x.f === 'string') : [];
  } catch (e) { return []; }
}

export function guardarHistorico(f) {
  const outros = lerHistorico().filter((x) => !(x.l === busca.idioma && x.f.toLowerCase() === f.toLowerCase()));
  gravarPreferencia(CHAVE, JSON.stringify([{ f, l: busca.idioma }, ...outros].slice(0, 6)));
  renderChips();
}

function chip(texto, aoClicar) {
  const botao = document.createElement('button');
  botao.type = 'button';
  botao.className = 'chip-btn';
  botao.textContent = texto;
  botao.addEventListener('click', aoClicar);
  return botao;
}

function preencherChips(caixa, titulo, nomeIcone, botoes) {
  caixa.textContent = '';
  caixa.hidden = botoes.length === 0;
  if (botoes.length === 0) return;
  const rotulo = document.createElement('span');
  rotulo.className = 'chips-titulo';
  rotulo.append(icone(nomeIcone), titulo);
  caixa.append(rotulo, ...botoes);
}

export function renderChips() {
  const recentes = new Set(lerHistorico().filter((x) => x.l === busca.idioma).map((x) => x.f.toLowerCase()));
  const exemplos = (EXEMPLOS[busca.idioma] || []).filter((t) => !recentes.has(t.toLowerCase()))
    .map((t) => chip(t, () => { campo.value = t; buscar(t); }));
  preencherChips($('exemplos'), 'Experimente', 'arrow-right', exemplos);

  const historico = lerHistorico().map((x) => chip(x.f, () => {
    if ([...idiomaSel.options].some((o) => o.value === x.l)) { busca.idioma = x.l; idiomaSel.value = x.l; }
    campo.value = x.f;
    buscar(x.f);
  }));
  preencherChips($('historico'), 'Recentes', 'clock-counter-clockwise', historico);
}
