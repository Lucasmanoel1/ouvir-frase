(() => {
  const $ = (id) => document.getElementById(id);
  const form = $('form-busca'), campo = $('frase'), status = $('status');
  const selo = $('selo'), seloAuto = $('selo-auto'), legenda = $('legenda'), contador = $('contador');
  const lista = $('ocorrencias'), btnAnterior = $('anterior'), btnProximo = $('proximo');
  const idiomaSel = $('idioma'), pilula = $('pilula-idioma');

  const CHAVE_HISTORICO = 'ouvirfrase.historico';
  const CHAVE_TRADUCAO = 'ouvirfrase.traducao';
  const IDIOMA_ALVO = 'pt'; // a tradução é sempre para português
  const RECUO_SEG = 3; // quanto o backend recua o início do resultado
  const EXEMPLOS = { en: ['thank you very much', 'you know what', 'a lot of'] };

  let resultados = [], indice = 0, frase = '', idioma = 'en';
  let player = null, ytPronto = false, pendente = null;
  let cues = null, cueAtual = -2, traducaoLigada = lerTraducao();
  // Tradução no navegador (Translator API do Chrome). estado: indisponivel | inativo | baixando | pronto
  const trad = { estado: 'indisponivel', origem: null, instancia: null, progresso: 0, consultando: null, gesto: false };
  const traducoes = new Map(); // 'origem|texto' -> Promise da tradução
  const legendas = new Map(); // youtubeId -> Promise da legenda completa do vídeo

  const estado = (nome) => { document.body.dataset.estado = nome; };

  function icone(nome) {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('class', 'ic');
    svg.setAttribute('aria-hidden', 'true');
    const uso = document.createElementNS('http://www.w3.org/2000/svg', 'use');
    uso.setAttribute('href', 'icons.svg#i-' + nome);
    svg.append(uso);
    return svg;
  }

  // --- YouTube IFrame Player API ---
  window.onYouTubeIframeAPIReady = () => {
    ytPronto = true;
    if (pendente) { tocar(pendente); pendente = null; }
  };

  function tocar(r) {
    if (!ytPronto) { pendente = r; return; }
    const inicio = Math.max(0, Math.floor(r.inicioSeg));
    if (!player) {
      player = new YT.Player('player', {
        videoId: r.youtubeId,
        playerVars: { start: inicio, autoplay: 1, rel: 0, cc_load_policy: 0, modestbranding: 1, iv_load_policy: 3, playsinline: 1 },
        events: { onStateChange: (e) => Ferramentas.aoMudarEstado(e.data) },
      });
    } else {
      player.loadVideoById({ videoId: r.youtubeId, startSeconds: inicio });
    }
  }

  // --- legenda que acompanha o vídeo ---
  function lerTraducao() {
    try { return localStorage.getItem(CHAVE_TRADUCAO) !== 'nao'; } catch (e) { return true; }
  }

  function buscarLegenda(id) {
    if (!legendas.has(id)) {
      legendas.set(id, fetch('/api/videos/' + encodeURIComponent(id) + '/legenda')
        .then((resp) => (resp.ok ? resp.json() : null))
        .catch(() => null)
        .then((l) => {
          const ok = Array.isArray(l) && l.length > 0;
          if (!ok) legendas.delete(id); // tenta de novo na próxima vez
          return ok ? l : null;
        }));
    }
    return legendas.get(id);
  }

  // Última fala que começou até o instante t; passado um instante do fim dela, nenhuma (música, silêncio).
  function cueEm(t) {
    let baixo = 0, alto = cues.length - 1, achado = -1;
    while (baixo <= alto) {
      const meio = (baixo + alto) >> 1;
      if (cues[meio].inicioSeg <= t + 0.1) { achado = meio; baixo = meio + 1; } else { alto = meio - 1; }
    }
    if (achado >= 0 && t > cues[achado].inicioSeg + cues[achado].duracaoSeg + 0.6) return -1;
    return achado;
  }

  function aoTempo(t) {
    if (!cues) return;
    const i = cueEm(t);
    if (i === cueAtual) return;
    cueAtual = i;
    const c = i >= 0 ? cues[i] : null;
    const r = resultados[indice];
    // palavras soltas (frase aproximada) só dentro do trecho do resultado, para não marcar "the" em toda fala
    const soltas = !!c && r.exata === false && c.inicioSeg >= r.inicioSeg + RECUO_SEG - 0.1 && c.inicioSeg < r.fimSeg;
    renderLegenda(legenda, c ? c.texto : '', soltas);
    mostrarTraducao(i);
  }

  // --- tradução no navegador ---
  const origemDe = (cod) => (cod === 'zh-CN' ? 'zh' : cod.split('-')[0]);

  function atualizarTraducao() {
    const botao = $('traducao-botao'), linha = $('traducao');
    const { estado } = trad;
    const pronto = estado === 'pronto';
    botao.hidden = estado === 'indisponivel';
    botao.disabled = estado === 'baixando';
    botao.classList.toggle('com-rotulo', estado === 'inativo');
    $('traducao-rotulo').hidden = estado !== 'inativo';
    botao.setAttribute('aria-pressed', String(pronto && traducaoLigada));
    const rotulo = estado === 'inativo' ? 'Ativar tradução'
      : estado === 'baixando' ? 'Baixando o modelo de tradução'
      : traducaoLigada ? 'Ocultar a tradução' : 'Mostrar a tradução';
    botao.setAttribute('aria-label', rotulo);
    botao.title = rotulo;
    linha.hidden = !(estado === 'baixando' || (pronto && traducaoLigada));
    if (estado === 'baixando') linha.textContent = 'Baixando o modelo… ' + Math.round(trad.progresso * 100) + '%';
  }

  // Vê se o par origem->português existe neste navegador; com o modelo já baixado e a tradução ligada, cria sozinho.
  async function prepararTraducao(codigo, gesto = false) {
    const origem = origemDe(codigo);
    if (trad.origem === origem && (trad.estado === 'pronto' || trad.estado === 'baixando')) return;
    if (trad.consultando === origem) { if (gesto) trad.gesto = true; return; } // já perguntando ao Chrome
    trad.origem = origem;
    trad.gesto = gesto;
    trad.instancia = null;
    trad.estado = 'indisponivel';
    atualizarTraducao();
    if (!('Translator' in self) || origem === IDIOMA_ALVO) return;
    trad.consultando = origem;
    try {
      const a = await Translator.availability({ sourceLanguage: origem, targetLanguage: IDIOMA_ALVO });
      if (trad.consultando === origem) trad.consultando = null;
      if (trad.origem !== origem || trad.estado !== 'indisponivel') return;
      if (a === 'unavailable') return;
      trad.estado = 'inativo';
      atualizarTraducao();
      // baixar o modelo exige um gesto do usuário: a busca (Enter ou clique) já conta e adianta o download
      if (traducaoLigada && (a === 'available' || (a === 'downloadable' && trad.gesto))) criarTradutor(origem);
    } catch (e) { if (trad.consultando === origem) trad.consultando = null; /* fica indisponível */ }
  }

  // Com o modelo por baixar, precisa ser chamado direto de um clique do usuário.
  async function criarTradutor(origem) {
    trad.estado = 'baixando';
    trad.progresso = 0;
    atualizarTraducao();
    try {
      const instancia = await Translator.create({
        sourceLanguage: origem, targetLanguage: IDIOMA_ALVO,
        monitor(m) {
          m.addEventListener('downloadprogress', (e) => {
            trad.progresso = e.loaded;
            if (trad.estado === 'baixando' && trad.origem === origem) atualizarTraducao();
          });
        },
      });
      if (trad.origem !== origem) { if (instancia.destroy) instancia.destroy(); return; }
      trad.instancia = instancia;
      trad.estado = 'pronto';
    } catch (e) {
      if (trad.origem === origem) { trad.estado = 'inativo'; trad.instancia = null; }
    }
    atualizarTraducao();
    mostrarTraducao(cueAtual);
  }

  function falhaNaTraducao() {
    if (trad.estado !== 'pronto') return;
    trad.estado = 'inativo';
    trad.instancia = null;
    traducoes.clear();
    $('traducao').textContent = '';
    atualizarTraducao();
  }

  function traduzirTexto(texto) {
    const chave = trad.origem + '|' + texto;
    if (!traducoes.has(chave)) {
      traducoes.set(chave, trad.instancia.translate(texto).catch((e) => {
        traducoes.delete(chave);
        falhaNaTraducao();
        throw e;
      }));
    }
    return traducoes.get(chave);
  }

  // Traduz a fala atual e já deixa as duas seguintes na fila (a API traduz uma de cada vez).
  function mostrarTraducao(i) {
    const linha = $('traducao');
    if (trad.estado !== 'pronto') return;
    linha.textContent = '';
    if (!traducaoLigada || !cues || i < 0 || !cues[i]) return;
    const origem = trad.origem;
    traduzirTexto(cues[i].texto).then((t) => {
      if (cueAtual === i && trad.origem === origem && trad.estado === 'pronto') linha.textContent = t;
    }, () => {});
    for (let k = 1; k <= 2; k++) {
      if (cues[i + k]) traduzirTexto(cues[i + k].texto).catch(() => {});
    }
  }

  function carregarLegendaDoVideo(r) {
    cues = null;
    cueAtual = -2;
    if (trad.estado !== 'baixando') $('traducao').textContent = '';
    prepararTraducao(idioma);
    buscarLegenda(r.youtubeId).then((l) => {
      const atual = resultados[indice];
      if (!l || !atual || atual.youtubeId !== r.youtubeId) return; // falhou (fica o trecho estático) ou o usuário já trocou
      cues = l;
      cueAtual = -2;
    });
  }

  function pausar() { if (player && player.pauseVideo) player.pauseVideo(); }

  // --- status (buscando, vazio, erro) ---
  function mostrarStatus(tipo, texto, detalhe) {
    status.textContent = '';
    if (tipo === 'buscando') {
      const ondas = document.createElement('div');
      ondas.className = 'ondas';
      ondas.setAttribute('aria-hidden', 'true');
      for (let i = 0; i < 5; i++) ondas.append(document.createElement('i'));
      status.append(ondas);
    } else {
      status.append(icone(tipo === 'erro' ? 'warning' : 'magnifying-glass'));
    }
    const h = document.createElement('h2');
    h.textContent = texto;
    status.append(h);
    if (detalhe) {
      const p = document.createElement('p');
      p.textContent = detalhe;
      status.append(p);
    }
  }

  // --- busca ---
  async function buscar(texto) {
    frase = texto.trim();
    if (!frase) return;
    history.replaceState(null, '', '?q=' + encodeURIComponent(frase) + '&l=' + encodeURIComponent(idioma));
    prepararTraducao(idioma, !!(navigator.userActivation && navigator.userActivation.isActive));
    const mantemEstudio = document.body.dataset.estado === 'resultados';
    if (mantemEstudio) {
      document.body.classList.add('ocupado');
    } else {
      estado('buscando');
      mostrarStatus('buscando', 'Buscando…');
    }

    let dados;
    try {
      const resp = await fetch('/api/busca?frase=' + encodeURIComponent(frase) + '&idioma=' + encodeURIComponent(idioma));
      if (!resp.ok) {
        const corpo = await resp.json().catch(() => ({}));
        throw new Error(corpo.mensagem || 'Não consegui buscar agora. Tente de novo em instantes.');
      }
      dados = await resp.json();
    } catch (e) {
      document.body.classList.remove('ocupado');
      pausar();
      Ferramentas.trechoMudou(null);
      estado('erro');
      mostrarStatus('erro', 'Algo deu errado',
        e instanceof TypeError ? 'Não consegui buscar agora. Tente de novo em instantes.' : e.message);
      return;
    }
    document.body.classList.remove('ocupado');

    resultados = dados.resultados || [];
    if (resultados.length === 0) {
      pausar();
      Ferramentas.trechoMudou(null);
      Pronuncia.limpar();
      estado('vazio');
      mostrarStatus('vazio', 'Não encontrei essa frase', 'Tente menos palavras ou outro idioma.');
      return;
    }

    guardarHistorico(frase);
    document.title = frase + ' · OuvirFrase';
    estado('resultados');
    indice = 0;
    montarLista();
    mostrar();
  }

  // --- lista de ocorrências ---
  function montarLista() {
    lista.textContent = '';
    resultados.forEach((r, i) => {
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
      renderLegenda(trecho, r.legenda || '');
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
      botao.addEventListener('click', () => { indice = i; mostrar(); });
      li.append(botao);
      lista.append(li);
    });
  }

  // --- exibição ---
  function mostrar() {
    const r = resultados[indice];
    tocar(r);
    Ferramentas.trechoMudou(r);
    Pronuncia.limpar();

    selo.hidden = r.exata !== false;
    seloAuto.hidden = !r.legendaAutomatica;
    selo.textContent = 'Frase aproximada · ' + Math.round((r.pontuacao || 0) * 100) + '% das palavras';
    renderLegenda(legenda, r.legenda || '');
    carregarLegendaDoVideo(r);
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

  function escaparRegex(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }

  // Destaca a frase na legenda ignorando pontuação/caixa entre as palavras.
  function renderLegenda(alvo, texto, aceitaSoltas = true) {
    alvo.textContent = '';
    const palavras = frase.toLowerCase().match(/[\p{L}\p{M}\p{N}]+(?:['’][\p{L}\p{M}\p{N}]+)*/gu) || [];
    if (palavras.length === 0) { alvo.textContent = texto; return; }
    const sep = "[^\\p{L}\\p{M}\\p{N}]+";
    const re = new RegExp(palavras.map((p) => escaparRegex(p).replace(/'/g, "['’]")).join(sep), 'iu');
    const m = re.exec(texto);
    if (!m) {
      if (aceitaSoltas) destacarPalavras(alvo, texto, palavras); else alvo.textContent = texto;
      return;
    }
    const marca = document.createElement('mark');
    marca.textContent = m[0];
    alvo.append(texto.slice(0, m.index), marca, texto.slice(m.index + m[0].length));
  }

  // Frase aproximada: não há trecho contínuo, então destaca cada palavra da frase que aparece.
  function destacarPalavras(alvo, texto, palavras) {
    const alternativas = [...new Set(palavras)].map((p) => escaparRegex(p).replace(/'/g, "['’]")).join('|');
    const re = new RegExp("(?<![\\p{L}\\p{M}\\p{N}'’])(?:" + alternativas + ")(?![\\p{L}\\p{M}\\p{N}'’])", 'giu');
    let ultimo = 0;
    for (const m of texto.matchAll(re)) {
      alvo.append(texto.slice(ultimo, m.index));
      const marca = document.createElement('mark');
      marca.textContent = m[0];
      alvo.append(marca);
      ultimo = m.index + m[0].length;
    }
    alvo.append(texto.slice(ultimo));
  }

  function navegar(delta) {
    const novo = indice + delta;
    if (novo < 0 || novo >= resultados.length) return;
    indice = novo;
    mostrar();
  }

  // --- histórico e exemplos ---
  function lerHistorico() {
    try {
      const h = JSON.parse(localStorage.getItem(CHAVE_HISTORICO));
      return Array.isArray(h) ? h.filter((x) => x && typeof x.f === 'string') : [];
    } catch (e) { return []; }
  }

  function guardarHistorico(f) {
    const outros = lerHistorico().filter((x) => !(x.l === idioma && x.f.toLowerCase() === f.toLowerCase()));
    try { localStorage.setItem(CHAVE_HISTORICO, JSON.stringify([{ f, l: idioma }, ...outros].slice(0, 6))); } catch (e) { /* ignora */ }
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

  function renderChips() {
    const recentes = new Set(lerHistorico().filter((x) => x.l === idioma).map((x) => x.f.toLowerCase()));
    const exemplos = (EXEMPLOS[idioma] || []).filter((t) => !recentes.has(t.toLowerCase()))
      .map((t) => chip(t, () => { campo.value = t; buscar(t); }));
    preencherChips($('exemplos'), 'Experimente', 'arrow-right', exemplos);

    const historico = lerHistorico().map((x) => chip(x.f, () => {
      if ([...idiomaSel.options].some((o) => o.value === x.l)) { idioma = x.l; idiomaSel.value = x.l; }
      campo.value = x.f;
      buscar(x.f);
    }));
    preencherChips($('historico'), 'Recentes', 'clock-counter-clockwise', historico);
  }

  // --- copiar link ---
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
  btnAnterior.addEventListener('click', () => navegar(-1));
  btnProximo.addEventListener('click', () => navegar(1));
  $('copiar').addEventListener('click', copiarLink);
  $('traducao-botao').addEventListener('click', () => {
    if (trad.estado === 'inativo') {
      traducaoLigada = true;
      criarTradutor(trad.origem); // direto do clique: o Chrome exige para baixar o modelo
    } else if (trad.estado === 'pronto') {
      traducaoLigada = !traducaoLigada;
    } else {
      return;
    }
    try { localStorage.setItem(CHAVE_TRADUCAO, traducaoLigada ? 'sim' : 'nao'); } catch (e) { /* ignora */ }
    atualizarTraducao();
    mostrarTraducao(cueAtual);
  });
  document.addEventListener('keydown', (e) => {
    if (document.body.dataset.estado !== 'resultados') return;
    if (/^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName)) return;
    if (e.key === 'ArrowLeft') navegar(-1);
    if (e.key === 'ArrowRight') navegar(1);
  });

  // --- idioma: sempre visível; vira botão (com seta) quando há mais de um idioma indexado ---
  async function iniciarIdiomas(params) {
    try {
      const resp = await fetch('/api/idiomas');
      const codigos = resp.ok ? await resp.json() : [];
      if (codigos.length > 0) {
        const pedido = params.get('l');
        idioma = codigos.includes(pedido) ? pedido : codigos[0];
        const local = navigator.language || 'pt-BR';
        let nomes = null;
        try { nomes = new Intl.DisplayNames([local], { type: 'language' }); } catch (e) { /* usa o código */ }
        idiomaSel.textContent = '';
        for (const codigo of codigos) {
          const opcao = document.createElement('option');
          opcao.value = codigo;
          let nome = codigo;
          try { if (nomes) nome = nomes.of(codigo) || codigo; } catch (e) { /* usa o código */ }
          opcao.textContent = nome.charAt(0).toLocaleUpperCase(local) + nome.slice(1);
          idiomaSel.append(opcao);
        }
        idiomaSel.value = idioma;
      }
      const unico = codigos.length < 2;
      idiomaSel.disabled = unico;
      pilula.toggleAttribute('data-unico', unico);
      pilula.title = unico ? 'Mais idiomas em breve' : 'Trocar o idioma da busca';
    } catch (e) { /* mantém o idioma padrão */ }
    renderChips();
  }

  async function iniciar() {
    const params = new URLSearchParams(location.search);
    idiomaSel.addEventListener('change', () => {
      idioma = idiomaSel.value;
      renderChips();
      if (campo.value.trim()) buscar(campo.value);
    });
    await iniciarIdiomas(params);
    const q = params.get('q');
    if (q) { campo.value = q; buscar(q); }
  }

  Ferramentas.iniciar({ getPlayer: () => player, estudoAberto: () => document.body.dataset.estado === 'resultados', aoTempo });
  Pronuncia.iniciar({
    getAlvo: (tipo) => (tipo === 'trecho' ? (resultados[indice] || {}).legenda : frase),
    getIdioma: () => idioma,
    pausar,
  });
  iniciar();
})();
