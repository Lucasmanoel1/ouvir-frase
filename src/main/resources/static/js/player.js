// Player do YouTube (IFrame Player API). Um caminho só para tocar: loadVideoById, que aceita segundos fracionários.

let player = null, apiPronta = false, playerPronto = false, pedido = null, aoMudarEstado = () => {}, aoErroDoPlayer = () => {};

// Antes do onReady os métodos do player não existem.
export const getPlayer = () => (playerPronto ? player : null);

// Códigos do onError da IFrame API: 100 (removido ou privado), 101 e 150 (incorporação bloqueada).
// Ficam de fora 2 (pedido inválido), 5 (erro do HTML5) e 153 (sem Referer): o YouTube já mostra o erro e não é culpa do vídeo.
export const VIDEO_INDISPONIVEL = new Set([100, 101, 150]);
export const videoIndisponivel = (codigo) => VIDEO_INDISPONIVEL.has(codigo);

/**
 * Carrega a API do YouTube. O callback global é definido antes de injetar o script, sem corrida.
 * aoErro(codigo, youtubeId): o player recusou o vídeo do pedido corrente. aoFalharApi(): o script não carregou (bloqueador, rede).
 */
export function iniciarPlayer(aoMudar, aoErro, aoFalharApi) {
  aoMudarEstado = aoMudar;
  aoErroDoPlayer = aoErro;
  window.onYouTubeIframeAPIReady = () => {
    apiPronta = true;
    if (pedido) criarPlayer();
  };
  const script = document.createElement('script');
  script.src = 'https://www.youtube.com/iframe_api';
  script.async = true;
  script.onerror = aoFalharApi;
  document.head.append(script);
}

/** O pedido mais recente vale: se a API ou o player ainda não estão prontos, eles usam o último. */
export function tocar(youtubeId, inicioSeg) {
  pedido = { videoId: youtubeId, startSeconds: inicioSeg };
  if (playerPronto) player.loadVideoById(pedido);
  else if (apiPronta && !player) criarPlayer();
}

// Criado sem videoId (senão o vídeo carregaria duas vezes) e sem start (que só aceita inteiro).
function criarPlayer() {
  player = new YT.Player('player', {
    playerVars: { rel: 0, cc_load_policy: 0, modestbranding: 1, iv_load_policy: 3, playsinline: 1 },
    events: {
      onReady: () => { playerPronto = true; player.loadVideoById(pedido); },
      onStateChange: (e) => aoMudarEstado(e.data),
      onError: (e) => aoErroDoPlayer(e.data, pedido && pedido.videoId),
    },
  });
}

export function pausar() { if (player && player.pauseVideo) player.pauseVideo(); }
