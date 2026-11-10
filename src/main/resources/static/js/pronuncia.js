// Pronúncia: o usuário repete a frase no microfone (Web Speech API) e comparamos palavra por palavra.
// O OuvirFrase não recebe o áudio: quem reconhece a fala é o serviço de voz do navegador.
import { $ } from './util.js';
import { comparar, tokens, deveMostrarOuvido, alternativasDe } from './comparacao.js';

function rotulo(p) {
  if (p >= 0.9) return 'Excelente!';
  if (p >= 0.7) return 'Bom!';
  if (p >= 0.4) return 'Quase lá';
  return 'Tente de novo';
}

// ---------- reconhecimento de fala ----------
const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
const LOCALE = {
  en: 'en-US', es: 'es-ES', pt: 'pt-BR', fr: 'fr-FR', de: 'de-DE', it: 'it-IT',
  ja: 'ja-JP', ko: 'ko-KR', ru: 'ru-RU', ar: 'ar-SA', hi: 'hi-IN', 'zh-CN': 'zh-CN',
};
const ERROS = {
  'not-allowed': 'Permissão do microfone negada. Libere o microfone nas configurações do site.',
  'service-not-allowed': 'O navegador bloqueou o reconhecimento de voz neste site.',
  'no-speech': 'Não ouvi nada. Tente de novo, falando um pouco mais alto.',
  'audio-capture': 'Nenhum microfone encontrado.',
  'network': 'Sem conexão com o serviço de voz do navegador.',
  'language-not-supported': 'O navegador não reconhece fala nesse idioma.',
};
const SEM_SUPORTE = 'Este navegador não tem reconhecimento de voz. Use o Chrome ou o Edge.';

const SILENCIO_MS = 1500;        // calado por este tempo depois de falar: encerra
const ESPERA_INICIAL_MS = 8000;  // ninguém falou ainda (a especificação não define o tempo do "no-speech")
const TETO_MS = 30000;           // limite de uma tentativa

let config = null, rec = null, ouvindo = false, alvo = 'frase';
const relogios = { silencio: 0, teto: 0 };

function pararRelogios() {
  clearTimeout(relogios.silencio);
  clearTimeout(relogios.teto);
}

const dica = () => 'Toque no microfone e repita ' + (alvo === 'frase' ? 'a frase.' : 'o trecho.');
const mensagem = (texto) => { $('mic-status').textContent = texto; };

function botaoParado() {
  ouvindo = false;
  $('mic').classList.remove('escutando');
  $('ic-mic').setAttribute('href', 'icons.svg#i-microphone');
  $('mic-texto').textContent = 'Falar';
  $('mic').setAttribute('aria-label', 'Falar no microfone');
}

// Descarta qualquer reconhecimento em andamento (os handlers dele passam a ser ignorados).
function limpar() {
  const antigo = rec;
  rec = null;
  if (antigo) { try { antigo.abort(); } catch (e) { /* já parou */ } }
  pararRelogios();
  botaoParado();
  $('mic-resultado').hidden = true;
  mostrarAlvo();
  mensagem(SR ? dica() : SEM_SUPORTE);
}

// Mostra o que será repetido em letras latinas, quando o texto está em outra escrita (russo, coreano...).
function mostrarAlvo() {
  const { texto, latino } = config.getAlvo(alvo);
  $('pratica-alvo').hidden = !latino;
  $('alvo-original').textContent = latino ? texto : '';
  $('alvo-latino').textContent = latino || '';
}

function mostrarResultado(esperado, alternativas) {
  // Das alternativas que o navegador devolve, vale a que mais se aproxima do esperado.
  let melhor = null, ouvido = '';
  for (const alt of alternativas) {
    const c = comparar(esperado, alt);
    if (!melhor || c.pontuacao > melhor.pontuacao) { melhor = c; ouvido = alt; }
  }
  const pct = Math.round(melhor.pontuacao * 100);
  $('mic-nota').textContent = pct + '% · ' + rotulo(melhor.pontuacao);
  const palavras = $('mic-palavras');
  palavras.textContent = '';
  for (const p of melhor.palavras) {
    const span = document.createElement('span');
    span.className = 'palavra ' + (p.ok ? 'ok' : 'falta');
    span.textContent = p.texto;
    span.title = p.ok ? 'Entendi' : 'Não entendi esta palavra';
    palavras.append(span);
  }
  $('mic-ouvido').textContent = 'Entendi: "' + ouvido + '"';
  $('mic-ouvido').hidden = !deveMostrarOuvido(melhor);
  $('mic-resultado').hidden = false;
  mensagem('');
}

function ouvir() {
  if (ouvindo) { rec.stop(); return; }
  const esperado = config.getAlvo(alvo).texto;
  if (tokens(esperado).length === 0) { mensagem('Não há o que repetir ainda. Faça uma busca primeiro.'); return; }

  config.pausar();
  $('mic-resultado').hidden = true;
  let resultados = [], falhou = false;

  const minha = new SR();
  rec = minha;
  minha.lang = LOCALE[config.getIdioma()] || config.getIdioma();
  minha.continuous = true;
  minha.interimResults = true;
  minha.maxAlternatives = 3;
  // stop() (e não abort()) entrega o resultado do áudio já captado
  const encerrar = () => { try { minha.stop(); } catch (e) { /* já parou */ } };
  const armarSilencio = (ms) => {
    clearTimeout(relogios.silencio);
    relogios.silencio = setTimeout(encerrar, ms);
  };
  minha.onresult = (ev) => {
    if (rec !== minha) return;
    armarSilencio(SILENCIO_MS);
    // o navegador reaproveita os objetos de ev.results: guardamos uma cópia
    resultados = Array.from(ev.results, (r) => ({
      final: r.isFinal,
      alternativas: Array.from(r, (a) => a.transcript),
    }));
  };
  minha.onerror = (ev) => {
    if (rec !== minha || ev.error === 'aborted') return;
    falhou = true;
    mensagem(ERROS[ev.error] || 'Não consegui reconhecer a fala (' + ev.error + ').');
  };
  minha.onend = () => {
    if (rec !== minha) return;
    pararRelogios();
    botaoParado();
    const alternativas = alternativasDe(resultados);
    if (alternativas.length > 0) mostrarResultado(esperado, alternativas);
    else if (!falhou) mensagem('Não ouvi nada. Tente de novo.');
  };

  ouvindo = true;
  $('mic').classList.add('escutando');
  $('ic-mic').setAttribute('href', 'icons.svg#i-stop');
  $('mic-texto').textContent = 'Parar';
  $('mic').setAttribute('aria-label', 'Parar de gravar');
  mensagem('Ouvindo… fale agora.');
  try {
    minha.start();
    armarSilencio(ESPERA_INICIAL_MS);
    relogios.teto = setTimeout(encerrar, TETO_MS);
  } catch (e) {
    botaoParado();
    mensagem('Não consegui iniciar o microfone.');
  }
}

function definirAlvo(novo) {
  alvo = novo;
  $('alvo-frase').setAttribute('aria-pressed', String(novo === 'frase'));
  $('alvo-trecho').setAttribute('aria-pressed', String(novo === 'trecho'));
  limpar();
  if (config.aoMudarAlvo) config.aoMudarAlvo();
}

/** 'frase' ou 'trecho': o que o usuário escolheu repetir (o loop do player acompanha). */
const alvoAtual = () => alvo;

// config: getAlvo(tipo) -> { texto a repetir, latino (transliteração ou null) }; getIdioma() -> código do idioma; pausar() -> pausa o vídeo; aoMudarAlvo() -> opcional, avisa a troca entre Frase e Trecho
function iniciar(opcoes) {
  config = opcoes;
  $('alvo-frase').addEventListener('click', () => definirAlvo('frase'));
  $('alvo-trecho').addEventListener('click', () => definirAlvo('trecho'));
  mensagem(SR ? dica() : SEM_SUPORTE);
  if (!SR) { $('mic').disabled = true; return; }
  $('mic').addEventListener('click', ouvir);
}

export { iniciar, limpar, alvoAtual };
