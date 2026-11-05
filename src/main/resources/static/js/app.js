// OuvirFrase: orquestra a busca. Cada parte da tela fica no seu módulo.
import { $, tela } from './util.js';
import { busca } from './estado.js';
import { buscarFrases, ERRO_PADRAO } from './api.js';
import { iniciarPlayer, getPlayer, pausar } from './player.js';
import { aoTempo, retraduzirFalaAtual } from './legenda.js';
import { iniciarTraducao, prepararTraducao } from './traducao.js';
import { iniciarHistorico, guardarHistorico, renderChips } from './historico.js';
import { iniciarIdiomas, aplicarIdiomaDetectado, limparSinal } from './idiomas.js';
import { montarLista, mostrar, navegar, alvoMudou } from './estudio.js';
import { mostrarStatus } from './status.js';
import * as Ferramentas from './ferramentas.js';
import * as Pronuncia from './pronuncia.js';

const form = $('form-busca'), campo = $('frase'), idiomaSel = $('idioma');

function atualizarUrl() {
  history.replaceState(null, '', '?q=' + encodeURIComponent(busca.frase) + '&l=' + encodeURIComponent(busca.idioma));
}

async function buscar(texto) {
  busca.frase = texto.trim();
  if (!busca.frase) return;
  limparSinal();
  atualizarUrl();
  prepararTraducao(busca.idioma, !!(navigator.userActivation && navigator.userActivation.isActive));
  const mantemEstudio = document.body.dataset.estado === 'resultados';
  if (mantemEstudio) {
    document.body.classList.add('ocupado');
  } else {
    tela('buscando');
    mostrarStatus('buscando', 'Buscando…');
  }

  let resultados, fraseTransliterada, idiomaUsado;
  try {
    ({ resultados, fraseTransliterada, idioma: idiomaUsado } = await buscarFrases(busca.frase, busca.idioma));
  } catch (e) {
    document.body.classList.remove('ocupado');
    pausar();
    Ferramentas.trechoMudou(null);
    tela('erro');
    mostrarStatus('erro', 'Algo deu errado', e instanceof TypeError ? ERRO_PADRAO : e.message);
    return;
  }
  document.body.classList.remove('ocupado');

  busca.resultados = resultados;
  busca.fraseTransliterada = fraseTransliterada;
  if (resultados.length === 0) {
    pausar();
    Ferramentas.trechoMudou(null);
    Pronuncia.limpar();
    tela('vazio');
    mostrarStatus('vazio', 'Não encontrei essa frase', 'Tente menos palavras ou outro idioma.');
    return;
  }

  // o servidor achou a frase em outro idioma: o seletor, a URL e o histórico seguem o idioma usado
  if (idiomaUsado && idiomaUsado !== busca.idioma) {
    aplicarIdiomaDetectado(idiomaUsado);
    atualizarUrl();
    prepararTraducao(idiomaUsado, !!(navigator.userActivation && navigator.userActivation.isActive));
  }

  guardarHistorico(busca.frase);
  document.title = busca.frase + ' · OuvirFrase';
  tela('resultados');
  busca.indice = 0;
  montarLista();
  mostrar();
}

async function copiarLink() {
  try {
    await navigator.clipboard.writeText(location.href);
  } catch (e) {
    const area = document.createElement('textarea');
    area.value = location.href;
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.append(area);
    area.select();
    let copiou = false;
    try { copiou = document.execCommand('copy'); } catch (e2) { /* sem fallback */ }
    area.remove();
    if (!copiou) return;
  }
  $('ic-copiar').setAttribute('href', 'icons.svg#i-check');
  $('copiar').title = 'Link copiado';
  setTimeout(() => { $('ic-copiar').setAttribute('href', 'icons.svg#i-link'); $('copiar').title = 'Copiar link'; }, 1600);
}

form.addEventListener('submit', (e) => { e.preventDefault(); buscar(campo.value); });
$('anterior').addEventListener('click', () => navegar(-1));
$('proximo').addEventListener('click', () => navegar(1));
$('copiar').addEventListener('click', copiarLink);
document.addEventListener('keydown', (e) => {
  if (document.body.dataset.estado !== 'resultados') return;
  if (/^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName)) return;
  if (e.key === 'ArrowLeft') navegar(-1);
  if (e.key === 'ArrowRight') navegar(1);
});
idiomaSel.addEventListener('change', () => {
  busca.idioma = idiomaSel.value;
  renderChips();
  if (campo.value.trim()) buscar(campo.value);
});

iniciarPlayer((estado) => Ferramentas.aoMudarEstado(estado));
iniciarTraducao(retraduzirFalaAtual);
iniciarHistorico(buscar);
Ferramentas.iniciar({ getPlayer, estudoAberto: () => document.body.dataset.estado === 'resultados', aoTempo });
Pronuncia.iniciar({
  getAlvo: (tipo) => {
    const r = busca.resultados[busca.indice] || {};
    return tipo === 'trecho'
      ? { texto: r.legenda, latino: r.legendaTransliterada }
      : { texto: busca.frase, latino: busca.fraseTransliterada };
  },
  getIdioma: () => busca.idioma,
  pausar,
  aoMudarAlvo: alvoMudou,
});

const params = new URLSearchParams(location.search);
await iniciarIdiomas(params);
renderChips();
const q = params.get('q');
if (q) { campo.value = q; buscar(q); }
