// Ferramentas do player: loop do trecho, velocidade e retroceder/avançar.
// Só conversa com o player do YouTube (IFrame API) por meio de getPlayer().
const Ferramentas = (() => {
  const $ = (id) => document.getElementById(id);
  const VELOCIDADES = [0.25, 0.5, 0.75, 1, 1.25, 1.5];
  const SALTO_SEG = 2;
  const DURACAO_PADRAO_SEG = 8; // se a API não mandar o fim do trecho
  const TICK_MS = 100;
  const CHAVE_VELOCIDADE = 'ouvirfrase.velocidade';
  const ESTADO_ENCERRADO = 0, ESTADO_TOCANDO = 1; // YT.PlayerState (a constante YT só existe depois da API carregar)

  let getPlayer = () => null, secaoVisivel = () => false;
  let trecho = null, loop = false, velocidade = lerVelocidade(), timer = null;
  const chips = new Map();

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

  // --- trecho atual ---
  function trechoMudou(r) {
    if (!r) { trecho = null; return; }
    const fim = r.fimSeg > r.inicioSeg ? r.fimSeg : r.inicioSeg + DURACAO_PADRAO_SEG;
    trecho = { youtubeId: r.youtubeId, inicio: r.inicioSeg, fim };
  }

  // Evita agir com o tempo do vídeo anterior enquanto o novo ainda carrega.
  function ehOVideoDoTrecho(p) {
    try { return !!trecho && p.getVideoUrl().includes('v=' + trecho.youtubeId); } catch (e) { return false; }
  }

  // --- estado do player ---
  function aoMudarEstado(estado) {
    if (estado === ESTADO_TOCANDO) {
      aplicarVelocidade();
      if (!timer) timer = setInterval(tick, TICK_MS);
    } else if (timer) {
      clearInterval(timer);
      timer = null;
    }
    if (estado === ESTADO_ENCERRADO && loop) reiniciar();
  }

  function tick() {
    const p = getPlayer();
    if (!loop || !p || !p.getCurrentTime || !ehOVideoDoTrecho(p)) return;
    if (p.getCurrentTime() >= trecho.fim) p.seekTo(trecho.inicio, true);
  }

  // --- ações ---
  function definirLoop(ativo) {
    loop = ativo;
    $('loop').setAttribute('aria-pressed', String(ativo));
  }

  function definirVelocidade(v) {
    velocidade = v;
    guardarVelocidade();
    chips.forEach((botao, valor) => botao.setAttribute('aria-pressed', String(valor === v)));
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

  // --- montagem ---
  function iniciar(opcoes) {
    getPlayer = opcoes.getPlayer;
    secaoVisivel = opcoes.secaoVisivel;

    for (const v of VELOCIDADES) {
      const botao = document.createElement('button');
      botao.type = 'button';
      botao.className = 'chip';
      botao.textContent = String(v).replace('.', ',') + '×';
      botao.addEventListener('click', () => definirVelocidade(v));
      chips.set(v, botao);
      $('velocidades').append(botao);
    }
    definirVelocidade(velocidade);

    $('loop').addEventListener('click', () => definirLoop(!loop));
    $('recuar').addEventListener('click', () => pular(-SALTO_SEG));
    $('avancar').addEventListener('click', () => pular(SALTO_SEG));
    $('reiniciar').addEventListener('click', reiniciar);

    document.addEventListener('keydown', (e) => {
      if (!secaoVisivel() || e.ctrlKey || e.metaKey || e.altKey) return;
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
