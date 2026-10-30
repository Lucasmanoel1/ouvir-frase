// Painel de ingestão: cola o link do canal e acompanha o andamento.
const API = '/api/painel/ingestao';
const SEM_PAINEL = 'Não consegui falar com o painel. Ele ainda está rodando no terminal?';

const $ = (id) => document.getElementById(id);
const campos = [$('canal'), $('idioma'), $('max'), $('limite')];
const enviar = $('enviar'), andamento = $('andamento');

let consulta = null;
let digitou = false;
campos.forEach((c) => c.addEventListener('input', () => { digitou = true; }));

function el(tag, classe, texto) {
  const e = document.createElement(tag);
  if (classe) e.className = classe;
  if (texto) e.textContent = texto;
  return e;
}

function icone(nome) {
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  svg.setAttribute('class', 'ic');
  svg.setAttribute('aria-hidden', 'true');
  const uso = document.createElementNS(ns, 'use');
  uso.setAttribute('href', 'icons.svg#i-' + nome);
  svg.append(uso);
  return svg;
}

function ondas() {
  const caixa = el('div', 'ondas');
  caixa.setAttribute('aria-hidden', 'true');
  for (let i = 0; i < 5; i++) caixa.append(document.createElement('i'));
  return caixa;
}

function botao(rotulo, nomeIcone, aoClicar) {
  const b = el('button', 'btn primario');
  b.type = 'button';
  b.append(el('span', null, rotulo), icone(nomeIcone));
  b.addEventListener('click', aoClicar);
  return b;
}

function relatorio(texto) {
  const caixa = el('details', 'relatorio');
  caixa.open = true;
  caixa.append(el('summary', null, 'Relatório'), el('pre', null, texto));
  return caixa;
}

function erroLocal(mensagem) {
  return { situacao: 'ERRO', mensagem, relatorio: null, adiados: 0, pedido: null };
}

function preencher(pedido) {
  if (!pedido) return;
  $('canal').value = pedido.canal;
  if ([...$('idioma').options].some((o) => o.value === pedido.idioma)) $('idioma').value = pedido.idioma;
  $('max').value = pedido.max;
  $('limite').value = pedido.limite;
}

function mostrar(a) {
  const rodando = a.situacao === 'RODANDO';
  enviar.disabled = rodando;
  campos.forEach((c) => { c.disabled = rodando; });

  andamento.textContent = '';
  andamento.hidden = a.situacao === 'PARADO';
  andamento.dataset.situacao = a.situacao;
  if (a.situacao === 'PARADO') return;

  if (rodando) {
    andamento.append(ondas(), el('h2', null, a.mensagem),
      el('p', null, 'Pode levar alguns minutos. Deixe o terminal do painel aberto.'));
    return;
  }

  if (a.situacao === 'CONCLUIDO') {
    const faltam = a.adiados > 0;
    andamento.append(icone('check'),
      el('h2', null, faltam ? 'Lote concluído' : 'Canal concluído'),
      el('p', null, faltam
        ? 'Faltam ' + a.adiados + ' vídeos deste canal. Clique em Continuar para o próximo lote.'
        : 'Não há mais vídeos pendentes neste canal.'));
    if (a.relatorio) andamento.append(relatorio(a.relatorio));
    if (faltam) andamento.append(botao('Continuar', 'arrow-right', () => iniciar(a.pedido)));
    return;
  }

  andamento.append(icone('warning'), el('h2', null, 'Não deu certo'), el('p', null, a.mensagem));
  if (a.relatorio) andamento.append(relatorio(a.relatorio));
  if (a.pedido) andamento.append(botao('Tentar de novo', 'arrow-counter-clockwise', () => iniciar(a.pedido)));
}

function parar() {
  clearInterval(consulta);
  consulta = null;
}

function acompanhar() {
  if (!consulta) consulta = setInterval(atualizar, 2000);
}

async function atualizar() {
  try {
    const resposta = await fetch(API);
    const a = await resposta.json();
    // ao abrir ou recarregar, o formulário volta a mostrar o que está rodando (ou rodou por último)
    if (a.situacao === 'RODANDO' || !digitou) preencher(a.pedido);
    if (a.situacao === 'RODANDO') {
      acompanhar();
    } else {
      parar();
    }
    mostrar(a);
  } catch (e) {
    parar();
    mostrar(erroLocal(SEM_PAINEL));
  }
}

async function iniciar(pedido) {
  try {
    const resposta = await fetch(API, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(pedido),
    });
    const dados = await resposta.json();
    if (resposta.status === 202) {
      mostrar(dados);
      acompanhar();
    } else if (resposta.status === 409) {
      await atualizar();
    } else {
      mostrar(erroLocal(dados.mensagem || 'Pedido inválido.'));
    }
  } catch (e) {
    mostrar(erroLocal(SEM_PAINEL));
  }
}

$('form').addEventListener('submit', (e) => {
  e.preventDefault();
  iniciar({
    canal: $('canal').value,
    idioma: $('idioma').value,
    max: Number($('max').value),
    limite: Number($('limite').value),
  });
});

atualizar();
