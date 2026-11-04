// Painel de ingestão: cola o link do canal ou da playlist, acompanha o andamento e indexa listas já cadastradas.
const API = '/api/painel/ingestao';
const API_LISTAS = '/api/painel/listas';
const SEM_PAINEL = 'Não consegui falar com o painel. Ele ainda está rodando no terminal?';

const $ = (id) => document.getElementById(id);
const campos = [$('link'), $('idioma'), $('max'), $('limite')];
const enviar = $('enviar'), andamento = $('andamento');
const listasVazio = $('cadastradas-vazio');
const TEXTO_SEM_LISTAS = listasVazio.textContent;
const TEXTO_TUDO_INDEXADO = 'Nenhuma lista com vídeos pendentes. Cole um link acima para adicionar outra.';

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

function botao(rotulo, nomeIcone, aoClicar, classe = 'btn primario') {
  const b = el('button', classe);
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
  return { situacao: 'ERRO', mensagem, relatorio: null, adiados: 0, pedido: null, lista: null };
}

// "só indexar": a lista já existe em data/, então não se descobre nada de novo no YouTube
function indexarLista(lista, limite) {
  return iniciar({ idioma: lista.idioma, lista: lista.nome, limite });
}

function preencher(pedido) {
  if (!pedido) return;
  if (pedido.link) $('link').value = pedido.link;
  if ([...$('idioma').options].some((o) => o.value === pedido.idioma)) $('idioma').value = pedido.idioma;
  $('max').value = pedido.max;
  $('limite').value = pedido.limite;
}

function linhaDaLista(l) {
  const topo = el('div', 'cadastrada-topo');
  topo.append(el('span', 'cadastrada-nome', l.nome),
    el('span', 'selo', l.playlist ? 'Playlist' : 'Canal'), el('span', 'selo', l.idioma));
  const numeros = el('p', 'cadastrada-numeros',
    l.indexados + ' de ' + l.total + ' indexados · ' + l.descartados + ' descartados · ' + l.pendentes + ' pendentes');
  const info = el('div', 'cadastrada-info');
  info.append(topo, numeros);

  const linha = el('li', 'cadastrada');
  linha.append(info);
  linha.append(botao('Ingerir lote', 'arrow-right', () => indexarLista(l, Number($('limite').value)), 'btn pequeno'));
  return linha;
}

// Só entram as listas com vídeo pendente: lista concluída sai daqui.
function mostrarListas(listas) {
  const pendentes = listas.filter((l) => l.pendentes > 0);
  const itens = $('cadastradas-itens');
  itens.textContent = '';
  pendentes.forEach((l) => itens.append(linhaDaLista(l)));
  listasVazio.textContent = listas.length === 0 ? TEXTO_SEM_LISTAS : TEXTO_TUDO_INDEXADO;
  listasVazio.hidden = pendentes.length > 0;
  bloquearListas(enviar.disabled);
}

function bloquearListas(bloqueado) {
  document.querySelectorAll('.cadastrada .btn').forEach((b) => { b.disabled = bloqueado; });
}

async function carregarListas() {
  try {
    const resposta = await fetch(API_LISTAS);
    const dados = await resposta.json();
    if (resposta.ok) {
      mostrarListas(dados);
    } else {
      $('cadastradas-itens').textContent = '';
      listasVazio.textContent = dados.mensagem || 'Não consegui ler as listas.';
      listasVazio.hidden = false;
    }
  } catch (e) {
    $('cadastradas-itens').textContent = '';
    listasVazio.textContent = SEM_PAINEL;
    listasVazio.hidden = false;
  }
}

function mostrar(a) {
  const rodando = a.situacao === 'RODANDO';
  enviar.disabled = rodando;
  campos.forEach((c) => { c.disabled = rodando; });
  bloquearListas(rodando);

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
      el('h2', null, faltam ? 'Lote concluído' : 'Lista concluída'),
      el('p', null, faltam
        ? 'Faltam ' + a.adiados + ' vídeos desta lista. Clique em Continuar para o próximo lote.'
        : (a.relatorio ? 'Não há mais vídeos pendentes nesta lista.' : a.mensagem)));
    if (a.relatorio) andamento.append(relatorio(a.relatorio));
    if (faltam && a.lista) {
      andamento.append(botao('Continuar', 'arrow-right', () => indexarLista(a.lista, a.pedido.limite)));
    }
    return;
  }

  andamento.append(icone('warning'), el('h2', null, 'Não deu certo'), el('p', null, a.mensagem));
  if (a.relatorio) andamento.append(relatorio(a.relatorio));
  if (a.pedido) {
    // a lista já é conhecida (veio da descoberta ou do pedido): tentar de novo só indexa, sem gastar cota
    const repetir = () => (a.lista ? indexarLista(a.lista, a.pedido.limite) : iniciar(a.pedido));
    andamento.append(botao('Tentar de novo', 'arrow-counter-clockwise', repetir));
  }
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
      carregarListas(); // uma vez ao abrir a página e uma vez no fim de cada lote
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
    link: $('link').value,
    idioma: $('idioma').value,
    max: Number($('max').value),
    limite: Number($('limite').value),
  });
});

atualizar();
