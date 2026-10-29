// Dock do player: play/pause, voltar/avançar, loop do trecho, velocidade e barra de progresso do trecho.
// Só conversa com o player do YouTube (IFrame API) por meio de getPlayer().
const Ferramentas = (() => {
  const $ = (id) => document.getElementById(id);
  const VELOCIDADES = [0.25, 0.5, 0.75, 1, 1.25, 1.5];
  const SALTO_SEG = 2;
  const DURACAO_PADRAO_SEG = 8; // se a API não mandar o fim do trecho
  const TICK_MS = 100;
  const CHAVE_VELOCIDADE = 'ouvirfrase.velocidade';
  // YT.PlayerState (a constante YT só existe depois da API carregar)
  const ENCERRADO = 0, TOCANDO = 1, PAUSADO = 2, EM_FILA = 5, NAO_INICIADO = -1;

  let getPlayer = () => null, estudoAberto = () => false, aoTempo = () => {};
  let estadoAtual = NAO_INICIADO;
  let trecho = null, loop = false, velocidade = lerVelocidade(), timer = null;
  const opcoes = new Map();

  function lerVelocidade() {
    try {
      const v = parseFloat(localStorage.getItem(CHAVE_VELOCIDADE));
      if (VELOCIDADES.includes(v)) return v;
    } catch (e) { /* sem localStorage: usa 1× */ }
    return 1;
  }

  function guardarVelocidade() {
    try { localStorage.setItem(CHAVE_VELOCIDADE, String(velocidade)); } catch (e) { /* ignora */ }
  }

  const rotuloVelocidade = (v) => String(v).replace('.', ',') + '×';

  // --- trecho atual ---
  function trechoMudou(r) {
    $('progresso').style.width = '0';
    if (!r) {
      trecho = null;
      if (timer) { clearInterval(timer); timer = null; }
      return;
    }
    const fim = r.fimSeg > r.inicioSeg ? r.fimSeg : r.inicioSeg + DURACAO_PADRAO_SEG;
    trecho = { youtubeId: r.youtubeId, inicio: r.inicioSeg, fim };
    // o relógio roda com o estudo aberto (não só tocando): a legenda também acompanha pausa e arrasto na barra do YouTube
    if (!timer) timer = setInterval(tick, TICK_MS);
  }

  // Evita agir com o tempo do vídeo anterior enquanto o novo ainda carrega.
  function ehOVideoDoTrecho(p) {
    try { return !!trecho && p.getVideoUrl().includes('v=' + trecho.youtubeId); } catch (e) { return false; }
  }

  // --- estado do player ---
  function mostrarTocando(tocando) {
    $('ic-tocar').setAttribute('href', 'icons.svg#i-' + (tocando ? 'pause' : 'play'));
    const rotulo = tocando ? 'Pausar' : 'Reproduzir';
    $('tocar').setAttribute('aria-label', rotulo);
    $('tocar').title = rotulo;
  }

  function aoMudarEstado(estado) {
    estadoAtual = estado;
    if (estado === TOCANDO) {
      mostrarTocando(true);
      aplicarVelocidade();
    } else if (estado === PAUSADO || estado === ENCERRADO || estado === EM_FILA || estado === NAO_INICIADO) {
      mostrarTocando(false);
    }
    if (estado === ENCERRADO && loop) reiniciar();
  }

  function tick() {
    const p = getPlayer();
    if (!p || !p.getCurrentTime || !ehOVideoDoTrecho(p)) return;
    const t = p.getCurrentTime();
    const fracao = (t - trecho.inicio) / (trecho.fim - trecho.inicio);
    $('progresso').style.width = Math.round(Math.min(1, Math.max(0, fracao)) * 100) + '%';
    if (loop && estadoAtual === TOCANDO && t >= trecho.fim) p.seekTo(trecho.inicio, true);
    aoTempo(t);
  }

  // --- ações ---
  function alternarTocar() {
    const p = getPlayer();
    if (!p || !p.getPlayerState) return;
    if (p.getPlayerState() === TOCANDO) p.pauseVideo(); else p.playVideo();
  }

  function definirLoop(ativo) {
    loop = ativo;
    $('loop').setAttribute('aria-pressed', String(ativo));
  }

  function definirVelocidade(v) {
    velocidade = v;
    guardarVelocidade();
    opcoes.forEach((botao, valor) => botao.setAttribute('aria-checked', String(valor === v)));
    $('vel-texto').textContent = rotuloVelocidade(v);
    aplicarVelocidade();
  }

  function aplicarVelocidade() {
    const p = getPlayer();
    if (p && p.setPlaybackRate && p.getPlaybackRate() !== velocidade) p.setPlaybackRate(velocidade);
  }

  function pular(delta) {
    const p = getPlayer();
    if (p && p.seekTo) p.seekTo(Math.max(0, p.getCurrentTime() + delta), true);
  }

  function reiniciar() {
    const p = getPlayer();
    if (!p || !trecho || !ehOVideoDoTrecho(p)) return;
    p.seekTo(trecho.inicio, true);
    p.playVideo();
  }

  function mudarVelocidade(passo) {
    const i = VELOCIDADES.indexOf(velocidade) + passo;
    if (i >= 0 && i < VELOCIDADES.length) definirVelocidade(VELOCIDADES[i]);
  }

  // --- popover de velocidade ---
  function abrirVelocidades(abrir) {
    $('velocidades').hidden = !abrir;
    $('vel-botao').setAttribute('aria-expanded', String(abrir));
  }

  // --- montagem ---
  function iniciar(config) {
    getPlayer = config.getPlayer;
    estudoAberto = config.estudoAberto;
    if (config.aoTempo) aoTempo = config.aoTempo;

    for (const v of VELOCIDADES) {
      const botao = document.createElement('button');
      botao.type = 'button';
      botao.setAttribute('role', 'menuitemradio');
      const rotulo = document.createElement('span');
      rotulo.textContent = rotuloVelocidade(v);
      const marca = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      marca.setAttribute('class', 'ic');
      marca.setAttribute('aria-hidden', 'true');
      const uso = document.createElementNS('http://www.w3.org/2000/svg', 'use');
      uso.setAttribute('href', 'icons.svg#i-check');
      marca.append(uso);
      botao.append(rotulo, marca);
      botao.addEventListener('click', () => { definirVelocidade(v); abrirVelocidades(false); $('vel-botao').focus(); });
      opcoes.set(v, botao);
      $('velocidades').append(botao);
    }
    definirVelocidade(velocidade);

    $('tocar').addEventListener('click', alternarTocar);
    $('loop').addEventListener('click', () => definirLoop(!loop));
    $('recuar').addEventListener('click', () => pular(-SALTO_SEG));
    $('avancar').addEventListener('click', () => pular(SALTO_SEG));
    $('vel-botao').addEventListener('click', () => abrirVelocidades($('velocidades').hidden));
    document.addEventListener('click', (e) => {
      if (!$('velocidades').hidden && !e.target.closest('.vel')) abrirVelocidades(false);
    });

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && !$('velocidades').hidden) { abrirVelocidades(false); $('vel-botao').focus(); return; }
      if (!estudoAberto() || e.ctrlKey || e.metaKey || e.altKey) return;
      if (/^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName)) return;
      switch (e.key.toLowerCase()) {
        case 'l': definirLoop(!loop); break;
        case 'r': reiniciar(); break;
        case ',': pular(-SALTO_SEG); break;
        case '.': pular(SALTO_SEG); break;
        case '[': mudarVelocidade(-1); break;
        case ']': mudarVelocidade(1); break;
      }
    });
  }

  return { iniciar, trechoMudou, aoMudarEstado };
})();
