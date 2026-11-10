// O estúdio: lista de ocorrências, trecho em exibição e navegação entre resultados.
import { $, proximoIndice } from './util.js';
import { busca } from './estado.js';
import { renderLegenda } from './destaque.js';
import { tocar, pausar, videoIndisponivel } from './player.js';
import { janelaDoTrecho } from './janela.js';
import { carregarLegenda } from './legenda.js';
import * as Ferramentas from './ferramentas.js';
import * as Pronuncia from './pronuncia.js';

const selo = $('selo'), seloAuto = $('selo-auto'), legenda = $('legenda'), contador = $('contador');
const lista = $('ocorrencias'), btnAnterior = $('anterior'), btnProximo = $('proximo'), aviso = $('aviso-video');

const MSG_INDISPONIVEL = 'Este vídeo não está mais disponível. Fui para o próximo.';
const MSG_NENHUM = 'Nenhum vídeo desta busca está disponível agora.';
const MSG_PLAYER = 'Não consegui carregar o player do YouTube. Confira bloqueadores de conteúdo.';

let playerBloqueado = false;

// O YouTube recusou o vídeo (removido, privado, incorporação bloqueada): o item fica marcado e a navegação o pula.
const pula = (i) => busca.indisponiveis.has(busca.resultados[i].youtubeId);

function avisar(texto) {
  aviso.textContent = texto;
  aviso.hidden = !texto;
}

function atualizarSetas() {
  const { resultados, indice } = busca;
  btnAnterior.disabled = proximoIndice(resultados.length, indice, -1, pula) === -1;
  btnProximo.disabled = proximoIndice(resultados.length, indice, 1, pula) === -1;
}

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
    botao.addEventListener('click', () => { if (pula(i)) return; busca.indice = i; mostrar(); });
    li.append(botao);
    lista.append(li);
  });
}

/** Mostra o resultado atual. `textoAviso` é a mensagem que explica por que o resultado mudou (vídeo indisponível). */
export function mostrar(textoAviso = '') {
  const { resultados, indice } = busca;
  const r = resultados[indice];
  const janela = janelaDoTrecho(r, Pronuncia.alvoAtual());
  tocar(r.youtubeId, janela.inicio);
  Ferramentas.trechoMudou(r.youtubeId, janela);
  Pronuncia.limpar();

  selo.hidden = r.exata !== false;
  seloAuto.hidden = !r.legendaAutomatica;
  selo.textContent = 'Frase aproximada · ' + Math.round((r.pontuacao || 0) * 100) + '% das palavras';
  renderLegenda(legenda, r.legenda || '', busca.frase);
  carregarLegenda(r);
  contador.textContent = (indice + 1) + ' de ' + resultados.length;
  atualizarSetas();
  avisar(textoAviso || (playerBloqueado ? MSG_PLAYER : ''));

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

/** A prática trocou entre Frase e Trecho: o loop passa a cobrir o que vai ser repetido. */
export function alvoMudou() {
  const r = busca.resultados[busca.indice];
  if (r) Ferramentas.ajustarJanela(janelaDoTrecho(r, Pronuncia.alvoAtual()));
}

export function navegar(delta) {
  const novo = proximoIndice(busca.resultados.length, busca.indice, delta, pula);
  if (novo === -1) return;
  busca.indice = novo;
  mostrar();
}

/** O player recusou o vídeo. Só os códigos de vídeo indisponível pulam; nos outros o YouTube mostra o próprio erro. */
export function videoFalhou(codigo, youtubeId) {
  if (!videoIndisponivel(codigo) || !youtubeId) return;
  busca.indisponiveis.add(youtubeId);
  lista.querySelectorAll('.item').forEach((el, i) => {
    if (busca.resultados[i].youtubeId !== youtubeId) return;
    el.dataset.indisponivel = '';
    el.setAttribute('aria-disabled', 'true');
  });
  const atual = busca.resultados[busca.indice];
  if (!atual || atual.youtubeId !== youtubeId) { atualizarSetas(); return; } // pedido antigo: só marca

  const total = busca.resultados.length;
  let novo = proximoIndice(total, busca.indice, 1, pula);
  if (novo === -1) novo = proximoIndice(total, busca.indice, -1, pula);
  if (novo === -1) {
    pausar();
    atualizarSetas();
    avisar(MSG_NENHUM);
    return;
  }
  busca.indice = novo;
  mostrar(MSG_INDISPONIVEL);
}

/** O script da API do YouTube não carregou (bloqueador de conteúdo, rede). O aviso aparece junto do resultado. */
export function playerFalhou() {
  playerBloqueado = true;
  if (document.body.dataset.estado === 'resultados') avisar(MSG_PLAYER);
}
