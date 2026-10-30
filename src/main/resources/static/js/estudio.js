// O estúdio: lista de ocorrências, trecho em exibição e navegação entre resultados.
import { $ } from './util.js';
import { busca } from './estado.js';
import { renderLegenda } from './destaque.js';
import { tocar } from './player.js';
import { carregarLegenda } from './legenda.js';
import * as Ferramentas from './ferramentas.js';
import * as Pronuncia from './pronuncia.js';

const selo = $('selo'), seloAuto = $('selo-auto'), legenda = $('legenda'), contador = $('contador');
const lista = $('ocorrencias'), btnAnterior = $('anterior'), btnProximo = $('proximo');

export function montarLista() {
  lista.textContent = '';
  busca.resultados.forEach((r, i) => {
    const li = document.createElement('li');
    const botao = document.createElement('button');
    botao.type = 'button';
    botao.className = 'item';

    const img = new Image();
    img.className = 'thumb';
    img.alt = '';
    img.loading = 'lazy';
    img.src = 'https://i.ytimg.com/vi/' + encodeURIComponent(r.youtubeId) + '/mqdefault.jpg';

    const texto = document.createElement('span');
    texto.className = 'item-texto';
    const trecho = document.createElement('span');
    trecho.className = 'item-legenda';
    trecho.dir = 'auto';
    renderLegenda(trecho, r.legenda || '', busca.frase);
    const nome = document.createElement('span');
    nome.className = 'item-titulo';
    nome.textContent = r.titulo || '';
    texto.append(trecho, nome);
    botao.append(img, texto);

    if (r.exata === false) {
      const aprox = document.createElement('span');
      aprox.className = 'selo-aprox';
      aprox.title = 'Frase aproximada';
      aprox.textContent = Math.round((r.pontuacao || 0) * 100) + '%';
      botao.append(aprox);
    }
    botao.addEventListener('click', () => { busca.indice = i; mostrar(); });
    li.append(botao);
    lista.append(li);
  });
}

export function mostrar() {
  const { resultados, indice } = busca;
  const r = resultados[indice];
  tocar(r);
  Ferramentas.trechoMudou(r);
  Pronuncia.limpar();

  selo.hidden = r.exata !== false;
  seloAuto.hidden = !r.legendaAutomatica;
  selo.textContent = 'Frase aproximada · ' + Math.round((r.pontuacao || 0) * 100) + '% das palavras';
  renderLegenda(legenda, r.legenda || '', busca.frase);
  carregarLegenda(r);
  contador.textContent = (indice + 1) + ' de ' + resultados.length;
  btnAnterior.disabled = indice === 0;
  btnProximo.disabled = indice === resultados.length - 1;

  lista.querySelectorAll('.item').forEach((el, i) => {
    if (i === indice) {
      el.setAttribute('aria-current', 'true');
      // só rola a lista quando ela tem rolagem própria (desktop); no mobile a página não deve pular
      if (getComputedStyle(lista).overflowY === 'auto') el.scrollIntoView({ block: 'nearest' });
    } else {
      el.removeAttribute('aria-current');
    }
  });
}

export function navegar(delta) {
  const novo = busca.indice + delta;
  if (novo < 0 || novo >= busca.resultados.length) return;
  busca.indice = novo;
  mostrar();
}
