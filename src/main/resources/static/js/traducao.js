// Tradução da legenda no navegador, pela Translator API do Chrome (local, sem servidor).
// O alvo é sempre o português. estado: indisponivel | inativo | baixando | pronto
import { $, lerPreferencia, gravarPreferencia } from './util.js';

const CHAVE = 'ouvirfrase.traducao';
const IDIOMA_ALVO = 'pt';

let ligada = lerPreferencia(CHAVE) !== 'nao';
let aoMudar = () => {}; // quem mostra a fala atual refaz a tradução quando o estado muda
const trad = { estado: 'indisponivel', origem: null, instancia: null, progresso: 0, consultando: null, gesto: false };
const traducoes = new Map(); // 'origem|texto' -> Promise da tradução

const origemDe = (cod) => (cod === 'zh-CN' ? 'zh' : cod.split('-')[0]);

export function iniciarTraducao(aoMudarEstado) {
  aoMudar = aoMudarEstado;
  $('traducao-botao').addEventListener('click', () => {
    if (trad.estado === 'inativo') {
      ligada = true;
      criarTradutor(trad.origem); // direto do clique: o Chrome exige para baixar o modelo
    } else if (trad.estado === 'pronto') {
      ligada = !ligada;
    } else {
      return;
    }
    gravarPreferencia(CHAVE, ligada ? 'sim' : 'nao');
    atualizar();
    aoMudar();
  });
}

function atualizar() {
  const botao = $('traducao-botao'), linha = $('traducao');
  const { estado } = trad;
  const pronto = estado === 'pronto';
  botao.hidden = estado === 'indisponivel';
  botao.disabled = estado === 'baixando';
  botao.classList.toggle('com-rotulo', estado === 'inativo');
  $('traducao-rotulo').hidden = estado !== 'inativo';
  botao.setAttribute('aria-pressed', String(pronto && ligada));
  const rotulo = estado === 'inativo' ? 'Ativar tradução'
    : estado === 'baixando' ? 'Baixando o modelo de tradução'
    : ligada ? 'Ocultar a tradução' : 'Mostrar a tradução';
  botao.setAttribute('aria-label', rotulo);
  botao.title = rotulo;
  linha.hidden = !(estado === 'baixando' || (pronto && ligada));
  if (estado === 'baixando') linha.textContent = 'Baixando o modelo… ' + Math.round(trad.progresso * 100) + '%';
}

/** Esvazia a linha de tradução (menos durante o download, que usa a linha para mostrar o progresso). */
export function limparTraducao() {
  if (trad.estado !== 'baixando') $('traducao').textContent = '';
}

// Vê se o par origem->português existe neste navegador; com o modelo já baixado e a tradução ligada, cria sozinho.
// 'gesto' = veio de um clique ou Enter do usuário, o que permite baixar o modelo.
export async function prepararTraducao(codigo, gesto = false) {
  const origem = origemDe(codigo);
  if (trad.origem === origem && (trad.estado === 'pronto' || trad.estado === 'baixando')) return;
  if (trad.consultando === origem) { if (gesto) trad.gesto = true; return; } // já perguntando ao Chrome
  trad.origem = origem;
  trad.gesto = gesto;
  trad.instancia = null;
  trad.estado = 'indisponivel';
  atualizar();
  if (!('Translator' in self) || origem === IDIOMA_ALVO) return;
  trad.consultando = origem;
  try {
    const a = await Translator.availability({ sourceLanguage: origem, targetLanguage: IDIOMA_ALVO });
    if (trad.consultando === origem) trad.consultando = null;
    if (trad.origem !== origem || trad.estado !== 'indisponivel') return;
    if (a === 'unavailable') return;
    trad.estado = 'inativo';
    atualizar();
    // baixar o modelo exige um gesto do usuário: a busca (Enter ou clique) já conta e adianta o download
    if (ligada && (a === 'available' || (a === 'downloadable' && trad.gesto))) criarTradutor(origem);
  } catch (e) { if (trad.consultando === origem) trad.consultando = null; /* fica indisponível */ }
}

async function criarTradutor(origem) {
  trad.estado = 'baixando';
  trad.progresso = 0;
  atualizar();
  try {
    const instancia = await Translator.create({
      sourceLanguage: origem, targetLanguage: IDIOMA_ALVO,
      monitor(m) {
        m.addEventListener('downloadprogress', (e) => {
          trad.progresso = e.loaded;
          if (trad.estado === 'baixando' && trad.origem === origem) atualizar();
        });
      },
    });
    if (trad.origem !== origem) { if (instancia.destroy) instancia.destroy(); return; }
    trad.instancia = instancia;
    trad.estado = 'pronto';
  } catch (e) {
    if (trad.origem === origem) { trad.estado = 'inativo'; trad.instancia = null; }
  }
  atualizar();
  aoMudar();
}

function falhou() {
  if (trad.estado !== 'pronto') return;
  trad.estado = 'inativo';
  trad.instancia = null;
  traducoes.clear();
  $('traducao').textContent = '';
  atualizar();
}

function traduzirTexto(texto) {
  const chave = trad.origem + '|' + texto;
  if (!traducoes.has(chave)) {
    traducoes.set(chave, trad.instancia.translate(texto).catch((e) => {
      traducoes.delete(chave);
      falhou();
      throw e;
    }));
  }
  return traducoes.get(chave);
}

/**
 * Mostra a tradução da primeira fala de 'textos' e já deixa as seguintes na fila (a API traduz uma de cada vez).
 * 'aindaAtual' diz se a fala ainda é a do momento, para uma tradução atrasada não aparecer sobre outra fala.
 */
export function mostrarTraducao(textos, aindaAtual) {
  const linha = $('traducao');
  if (trad.estado !== 'pronto') return;
  linha.textContent = '';
  if (!ligada || !textos || textos.length === 0) return;
  const origem = trad.origem;
  traduzirTexto(textos[0]).then((t) => {
    if (aindaAtual() && trad.origem === origem && trad.estado === 'pronto') linha.textContent = t;
  }, () => {});
  for (const proxima of textos.slice(1)) traduzirTexto(proxima).catch(() => {});
}
