// Seletor de idioma: sempre visível; vira botão (com seta) quando há mais de um idioma indexado.
import { $ } from './util.js';
import { busca } from './estado.js';
import { listarIdiomas } from './api.js';

const idiomaSel = $('idioma'), pilula = $('pilula-idioma');

export async function iniciarIdiomas(params) {
  try {
    const codigos = await listarIdiomas();
    if (codigos.length > 0) {
      const pedido = params.get('l');
      busca.idioma = codigos.includes(pedido) ? pedido : codigos[0];
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
      idiomaSel.value = busca.idioma;
    }
    const unico = codigos.length < 2;
    idiomaSel.disabled = unico;
    pilula.toggleAttribute('data-unico', unico);
    pilula.title = unico ? 'Mais idiomas em breve' : 'Trocar o idioma da busca';
  } catch (e) { /* mantém o idioma padrão */ }
}
