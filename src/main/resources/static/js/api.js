// Conversa com o backend.

export const ERRO_PADRAO = 'Não consegui buscar agora. Tente de novo em instantes.';

/** Resultados da busca. Falha de rede lança TypeError; erro do servidor lança Error com a mensagem dele. */
export async function buscarFrases(frase, idioma) {
  const resp = await fetch('/api/busca?frase=' + encodeURIComponent(frase) + '&idioma=' + encodeURIComponent(idioma));
  if (!resp.ok) {
    const corpo = await resp.json().catch(() => ({}));
    throw new Error(corpo.mensagem || ERRO_PADRAO);
  }
  const dados = await resp.json();
  return dados.resultados || [];
}

export async function listarIdiomas() {
  const resp = await fetch('/api/idiomas');
  return resp.ok ? resp.json() : [];
}

const legendas = new Map(); // youtubeId -> Promise da legenda completa do vídeo

/** Legenda completa do vídeo (ou null se falhar; a próxima chamada tenta de novo). */
export function buscarLegenda(id) {
  if (!legendas.has(id)) {
    legendas.set(id, fetch('/api/videos/' + encodeURIComponent(id) + '/legenda')
      .then((resp) => (resp.ok ? resp.json() : null))
      .catch(() => null)
      .then((l) => {
        const ok = Array.isArray(l) && l.length > 0;
        if (!ok) legendas.delete(id);
        return ok ? l : null;
      }));
  }
  return legendas.get(id);
}
