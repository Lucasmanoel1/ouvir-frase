// Pronúncia: o usuário repete a frase no microfone (Web Speech API) e comparamos palavra por palavra.
// O OuvirFrase não recebe o áudio: quem reconhece a fala é o serviço de voz do navegador.
const Pronuncia = (() => {
  const $ = (id) => document.getElementById(id);

  // ---------- comparação (pura, sem DOM) ----------
  // Mesma ideia do Normalizador do backend: palavras com letras/marcas/dígitos; chinês e japonês, um caractere por token.
  const CJK = /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]/u;
  const PALAVRA = /[\p{L}\p{M}\p{N}]+(?:'[\p{L}\p{M}\p{N}]+)*/gu;

  function tokens(texto) {
    const limpo = (texto || '').normalize('NFKC').replace(/[’‘]/g, "'").toLowerCase()
      .replace(/\[[^\]]*\]|\([^)]*\)/g, ' ');
    const saida = [];
    for (const m of limpo.match(PALAVRA) || []) {
      let atual = '';
      for (const c of m) {
        if (CJK.test(c)) {
          if (atual) { saida.push(atual); atual = ''; }
          saida.push(c);
        } else {
          atual += c;
        }
      }
      if (atual) saida.push(atual);
    }
    return saida;
  }

  // Compara sem acento latino e sem apóstrofo: "está"/"esta" e "dont"/"don't" são erro de reconhecimento, não de pronúncia.
  function chave(token) {
    return token.normalize('NFD').replace(/[̀-ͯ']/g, '').normalize('NFC');
  }

  // Maior subsequência comum (na ordem) entre o que era esperado e o que foi ouvido.
  function comparar(esperado, ouvido) {
    const e = tokens(esperado), o = tokens(ouvido);
    const ke = e.map(chave), ko = o.map(chave);
    const n = e.length, m = o.length;
    const t = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
    for (let i = n - 1; i >= 0; i--) {
      for (let j = m - 1; j >= 0; j--) {
        t[i][j] = ke[i] === ko[j] ? t[i + 1][j + 1] + 1 : Math.max(t[i + 1][j], t[i][j + 1]);
      }
    }
    const ok = new Array(n).fill(false);
    let i = 0, j = 0;
    while (i < n && j < m) {
      if (ke[i] === ko[j]) { ok[i] = true; i++; j++; }
      else if (t[i + 1][j] >= t[i][j + 1]) i++;
      else j++;
    }
    const casadas = ok.filter(Boolean).length;
    return {
      pontuacao: n === 0 ? 0 : casadas / n,
      palavras: e.map((texto, k) => ({ texto, ok: ok[k] })),
      ouvido: o,
    };
  }

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

  let config = null, rec = null, ouvindo = false;

  function mensagem(texto) { $('mic-status').textContent = texto; }

  function botaoParado() {
    ouvindo = false;
    $('mic').textContent = '🎤 Falar';
  }

  // Descarta qualquer reconhecimento em andamento (os handlers dele passam a ser ignorados).
  function limpar() {
    const antigo = rec;
    rec = null;
    if (antigo) { try { antigo.abort(); } catch (e) { /* já parou */ } }
    botaoParado();
    $('mic-resultado').hidden = true;
    mensagem(SR ? '' : SEM_SUPORTE);
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
      palavras.append(span, ' ');
    }
    $('mic-ouvido').textContent = 'Entendi: "' + ouvido + '"';
    $('mic-resultado').hidden = false;
    mensagem('');
  }

  function ouvir() {
    if (ouvindo) { rec.stop(); return; }
    const esperado = config.getAlvo($('alvo').value);
    if (tokens(esperado).length === 0) { mensagem('Não há o que repetir ainda. Faça uma busca primeiro.'); return; }

    config.pausar();
    $('mic-resultado').hidden = true;
    let recebeu = false, falhou = false;

    const minha = new SR();
    rec = minha;
    minha.lang = LOCALE[config.getIdioma()] || config.getIdioma();
    minha.continuous = false;
    minha.interimResults = false;
    minha.maxAlternatives = 3;
    minha.onresult = (ev) => {
      if (rec !== minha) return;
      recebeu = true;
      mostrarResultado(esperado, Array.from(ev.results[0], (a) => a.transcript));
    };
    minha.onerror = (ev) => {
      if (rec !== minha || ev.error === 'aborted') return;
      falhou = true;
      mensagem(ERROS[ev.error] || 'Não consegui reconhecer a fala (' + ev.error + ').');
    };
    minha.onend = () => {
      if (rec !== minha) return;
      botaoParado();
      if (!recebeu && !falhou) mensagem('Não ouvi nada. Tente de novo.');
    };

    ouvindo = true;
    $('mic').textContent = '⏹ Parar';
    mensagem('Ouvindo… fale agora.');
    try {
      minha.start();
    } catch (e) {
      botaoParado();
      mensagem('Não consegui iniciar o microfone.');
    }
  }

  // opcoes: getAlvo(tipo) -> texto a repetir; getIdioma() -> código do idioma; pausar() -> pausa o vídeo
  function iniciar(opcoes) {
    config = opcoes;
    if (!SR) {
      $('mic').disabled = true;
      mensagem(SEM_SUPORTE);
      return;
    }
    $('mic').addEventListener('click', ouvir);
  }

  return { iniciar, limpar, tokens, comparar };
})();
