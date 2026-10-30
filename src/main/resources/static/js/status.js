// Área de status da busca: buscando, vazio, erro.
import { $, icone } from './util.js';

const status = $('status');

export function mostrarStatus(tipo, texto, detalhe) {
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
