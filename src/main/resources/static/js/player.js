// Player do YouTube (IFrame Player API).

let player = null, pronto = false, pendente = null, aoMudarEstado = () => {};

export const getPlayer = () => player;

/** Carrega a API do YouTube. O callback global é definido antes de injetar o script, sem corrida. */
export function iniciarPlayer(aoMudar) {
  aoMudarEstado = aoMudar;
  window.onYouTubeIframeAPIReady = () => {
    pronto = true;
    if (pendente) { tocar(pendente); pendente = null; }
  };
  const script = document.createElement('script');
  script.src = 'https://www.youtube.com/iframe_api';
  script.async = true;
  document.head.append(script);
}

export function tocar(r) {
  if (!pronto) { pendente = r; return; }
  const inicio = Math.max(0, Math.floor(r.inicioSeg));
  if (!player) {
    player = new YT.Player('player', {
      videoId: r.youtubeId,
      playerVars: { start: inicio, autoplay: 1, rel: 0, cc_load_policy: 0, modestbranding: 1, iv_load_policy: 3, playsinline: 1 },
      events: { onStateChange: (e) => aoMudarEstado(e.data) },
    });
  } else {
    player.loadVideoById({ videoId: r.youtubeId, startSeconds: inicio });
  }
}

export function pausar() { if (player && player.pauseVideo) player.pauseVideo(); }
