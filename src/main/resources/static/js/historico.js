// Buscas recentes (guardadas só no navegador) e frases de exemplo.
import { $, icone, lerPreferencia, gravarPreferencia, fatiaCircular } from './util.js';
import { busca } from './estado.js';
import { EXEMPLOS } from './exemplos.js';

const CHAVE = 'ouvirfrase.historico';
const POR_VEZ = 3;
const paginas = new Map(); // idioma -> página dos exemplos (a primeira é sorteada para não mostrar sempre as mesmas)

const campo = $('frase');
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

function chipTrocar(idioma) {
  const botao = chip('', () => {
    paginas.set(idioma, paginas.get(idioma) + 1);
    renderChips();
    $('exemplos').querySelector('.chip-trocar')?.focus(); // o redesenho recria o botão: devolve o foco
  });
  botao.classList.add('chip-trocar');
  botao.title = 'Outros exemplos';
  botao.setAttribute('aria-label', 'Outros exemplos');
  botao.append(icone('arrows-clockwise'));
  return botao;
}

export function renderChips() {
  const idioma = busca.idioma;
  const doIdioma = lerHistorico().filter((x) => x.l === idioma);
  const recentes = new Set(doIdioma.map((x) => x.f.toLowerCase()));
  const disponiveis = (EXEMPLOS[idioma] || []).filter((t) => !recentes.has(t.toLowerCase()));
  if (!paginas.has(idioma)) paginas.set(idioma, Math.floor(Math.random() * Math.ceil(disponiveis.length / POR_VEZ)));

  const exemplos = fatiaCircular(disponiveis, paginas.get(idioma) * POR_VEZ, POR_VEZ)
    .map((t) => chip(t, () => { campo.value = t; buscar(t); }));
  if (disponiveis.length > POR_VEZ) exemplos.push(chipTrocar(idioma));
  preencherChips($('exemplos'), 'Experimente', 'arrow-right', exemplos);

  const historico = doIdioma.map((x) => chip(x.f, () => { campo.value = x.f; buscar(x.f); }));
  preencherChips($('historico'), 'Recentes', 'clock-counter-clockwise', historico);
}
