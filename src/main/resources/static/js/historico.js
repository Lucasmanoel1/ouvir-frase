// Buscas recentes (guardadas só no navegador) e frases de exemplo.
import { $, icone, lerPreferencia, gravarPreferencia, fatiaCircular } from './util.js';
import { busca } from './estado.js';
import { EXEMPLOS } from './exemplos.js';

const CHAVE = 'idiomando.historico';
const POR_VEZ = 3;
const paginasRecentes = new Map(); // idioma -> página dos recentes (3 por vez)
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
  paginasRecentes.clear();
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

function chipProximos(idioma, proxima) {
  const botao = chip('', () => {
    paginasRecentes.set(idioma, proxima);
    renderChips();
    $('historico').querySelector('.chip-proximos')?.focus();
  });
  botao.classList.add('chip-trocar', 'chip-proximos');
  botao.title = 'Mais recentes';
  botao.setAttribute('aria-label', 'Mais recentes');
  botao.append(icone('caret-right'));
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

  // só 3 recentes por vez; com mais que isso, um botão passa para os próximos (e volta ao começo no fim)
  const pagina = doIdioma.length > POR_VEZ ? (paginasRecentes.get(idioma) || 0) % Math.ceil(doIdioma.length / POR_VEZ) : 0;
  const historico = doIdioma.slice(pagina * POR_VEZ, (pagina + 1) * POR_VEZ)
    .map((x) => chip(x.f, () => { campo.value = x.f; buscar(x.f); }));
  if (doIdioma.length > POR_VEZ) historico.push(chipProximos(idioma, pagina + 1));
  preencherChips($('historico'), 'Recentes', 'clock-counter-clockwise', historico);
}
