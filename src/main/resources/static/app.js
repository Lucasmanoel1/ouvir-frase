(() => {
  const $ = (id) => document.getElementById(id);
  const form = $('form-busca'), campo = $('frase'), status = $('status'), secao = $('resultado');
  const selo = $('selo'), legenda = $('legenda'), titulo = $('titulo'), contador = $('contador');
  const btnAnterior = $('anterior'), btnProximo = $('proximo');

  let resultados = [], indice = 0, frase = '';
  let player = null, ytPronto = false, pendente = null;

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
        playerVars: { start: inicio, autoplay: 1, rel: 0, cc_load_policy: 0 },
      });
    } else {
      player.loadVideoById({ videoId: r.youtubeId, startSeconds: inicio });
    }
  }

  // --- Busca ---
  async function buscar(texto) {
    frase = texto.trim();
    if (!frase) return;
    history.replaceState(null, '', '?q=' + encodeURIComponent(frase));
    status.textContent = 'Buscando…';
    secao.hidden = true;
    try {
      const resp = await fetch('/api/busca?frase=' + encodeURIComponent(frase));
      if (!resp.ok) {
        const corpo = await resp.json().catch(() => ({}));
        throw new Error(corpo.mensagem || 'Erro ao buscar. Tente novamente em instantes.');
      }
      const dados = await resp.json();
      resultados = dados.resultados || [];
    } catch (e) {
      status.textContent = e instanceof TypeError
        ? 'Erro ao buscar. Tente novamente em instantes.'
        : e.message;
      return;
    }
    if (resultados.length === 0) {
      status.textContent = 'Não encontrei essa frase nos vídeos indexados.';
      if (player) player.pauseVideo();
      return;
    }
    status.textContent = '';
    secao.hidden = false;
    indice = 0;
    mostrar();
  }

  // --- Exibição ---
  function mostrar() {
    const r = resultados[indice];
    tocar(r);
    selo.hidden = r.exata !== false;
    selo.textContent = 'Frase aproximada · ' + Math.round((r.pontuacao || 0) * 100) + '% das palavras';
    renderLegenda(r.legenda || '');
    titulo.textContent = r.titulo || '';
    contador.textContent = (indice + 1) + ' / ' + resultados.length;
    btnAnterior.disabled = indice === 0;
    btnProximo.disabled = indice === resultados.length - 1;
  }

  function escaparRegex(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }

  // Destaca a frase na legenda ignorando pontuação/caixa entre as palavras.
  function renderLegenda(texto) {
    legenda.textContent = '';
    const palavras = frase.toLowerCase().match(/[\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)*/gu) || [];
    if (palavras.length === 0) { legenda.textContent = texto; return; }
    const sep = "[^\\p{L}\\p{N}]+";
    const re = new RegExp(palavras.map((p) => escaparRegex(p).replace(/'/g, "['’]")).join(sep), 'iu');
    const m = re.exec(texto);
    if (!m) { destacarPalavras(texto, palavras); return; }
    const marca = document.createElement('mark');
    marca.textContent = m[0];
    legenda.append(texto.slice(0, m.index), marca, texto.slice(m.index + m[0].length));
  }

  // Frase aproximada: não há trecho contínuo, então destaca cada palavra da frase que aparece.
  function destacarPalavras(texto, palavras) {
    const alternativas = [...new Set(palavras)].map((p) => escaparRegex(p).replace(/'/g, "['’]")).join('|');
    const re = new RegExp("(?<![\\p{L}\\p{N}'’])(?:" + alternativas + ")(?![\\p{L}\\p{N}'’])", 'giu');
    let ultimo = 0;
    for (const m of texto.matchAll(re)) {
      legenda.append(texto.slice(ultimo, m.index));
      const marca = document.createElement('mark');
      marca.textContent = m[0];
      legenda.append(marca);
      ultimo = m.index + m[0].length;
    }
    legenda.append(texto.slice(ultimo));
  }

  function navegar(delta) {
    const novo = indice + delta;
    if (novo < 0 || novo >= resultados.length) return;
    indice = novo;
    mostrar();
  }

  form.addEventListener('submit', (e) => { e.preventDefault(); buscar(campo.value); });
  btnAnterior.addEventListener('click', () => navegar(-1));
  btnProximo.addEventListener('click', () => navegar(1));
  document.addEventListener('keydown', (e) => {
    if (document.activeElement === campo || secao.hidden) return;
    if (e.key === 'ArrowLeft') navegar(-1);
    if (e.key === 'ArrowRight') navegar(1);
  });

  const q = new URLSearchParams(location.search).get('q');
  if (q) { campo.value = q; buscar(q); }
})();
