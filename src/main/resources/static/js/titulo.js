// Título animado da tela inicial: "Ouça como a frase é falada de verdade." -> "Aprenda idiomas idiomando."
// A marca (logo + nome) desliza do centro para o canto, ao lado do botão de idioma.
// Tudo é Web Animations API com `delay` a partir de t = 0, então uma única linha do tempo serve para pausar
// e retomar. Distâncias em `em`: o movimento acompanha o tamanho do título (desktop e celular).
import { prepararSom } from './som.js';

const raiz = document.getElementById('titulo');
const marca = document.querySelector('.marca');
const pilula = document.getElementById('pilula-idioma');

// ms desde o início. A frase 1 fica parada até o pulso; o 1º salto cai em 3,96 s e o 2º 150 ms depois.
const T = { pulso: 2500, saida: 2700, marca: 2700, entrada: 3050, pingo1: 3960, pingo2: 4110, subtitulo: 4550 };
const MARCA_ESCALA = .75, MARCA_MS = 900;
const EASE_ENTRADA = 'cubic-bezier(.22,1,.36,1)';
const reduzido = matchMedia('(prefers-reduced-motion: reduce)').matches;

let deslize = null; // animação da marca (fora de `raiz`)
let pausadas = [];

function anima(el, quadros, opcoes) {
  return el.animate(quadros, { fill: 'both', ...opcoes });
}

// quanto a marca precisa andar para ficar na mesma margem lateral e na mesma altura central da pílula de idioma
function alvoDaMarca() {
  const tinha = document.body.dataset.marca;
  delete document.body.dataset.marca; // medir a posição natural, sem a transformação
  const r = marca.getBoundingClientRect();
  const p = pilula.getBoundingClientRect();
  const margem = parseFloat(getComputedStyle(pilula).right) || 24;
  if (tinha) document.body.dataset.marca = tinha;
  return {
    dx: margem - r.left,
    dy: p.top + p.height / 2 - (r.top + (r.height * MARCA_ESCALA) / 2),
  };
}

// estado final: a marca fica no canto por CSS (acompanha o redimensionamento) e a animação sai de cena
function fixarMarca() {
  const { dx, dy } = alvoDaMarca();
  marca.style.setProperty('--marca-dx', dx + 'px');
  marca.style.setProperty('--marca-dy', dy + 'px');
  marca.style.setProperty('--marca-escala', MARCA_ESCALA);
  document.body.dataset.marca = 'canto';
}

function deslizarMarca() {
  const { dx, dy } = alvoDaMarca();
  deslize = marca.animate(
    [{ transform: 'none' }, { transform: `translate(${dx}px, ${dy}px) scale(${MARCA_ESCALA})` }],
    { delay: T.marca, duration: MARCA_MS, easing: EASE_ENTRADA, fill: 'both' },
  );
  deslize.onfinish = () => { deslize.cancel(); fixarMarca(); };
}

function iniciar(som) {
  const palavras = [...raiz.querySelectorAll('.fr-1 .p')];
  const grupos = [...raiz.querySelectorAll('.fr-2 .g')];
  const sublinhado = raiz.querySelector('.sub path');
  const onda = raiz.querySelector('.onda-titulo');
  const pingos = [raiz.querySelector('#pingo-1'), raiz.querySelector('#pingo-2')];
  const subtitulo = raiz.querySelector('.sub-frase');
  const salto = parseFloat(pingos[0].closest('svg').dataset.salto || 120); // 0,12 em, em unidades do SVG (fonte de 1000)

  // sublinhado: um pulso de espessura, como se a frase ganhasse voz
  anima(sublinhado, [{ strokeWidth: '2.5px' }, { strokeWidth: '6px', offset: .4 }, { strokeWidth: '2.5px' }],
    { delay: T.pulso, duration: 420, easing: 'ease-in-out' });
  anima(sublinhado, [{ opacity: 1 }, { opacity: 0 }], { delay: T.saida + 60, duration: 330, easing: 'ease-in' });

  // a mesma onda da tela de busca ("Buscando…"), aparecendo com o pulso e saindo junto com a frase
  anima(onda, [{ opacity: 0 }, { opacity: 1, offset: .2 }, { opacity: 1, offset: .75 }, { opacity: 0 }],
    { delay: T.pulso, duration: 600, easing: 'linear' });

  // frase 1 sai para cima: ~27 px, fade, rotação de -2 graus e desfoque, uma palavra após a outra
  palavras.forEach((p, i) => anima(p, [
    { opacity: 1, transform: 'translateY(0) rotate(0deg)', filter: 'blur(0)' },
    { opacity: 0, transform: 'translateY(-.56em) rotate(-2deg)', filter: 'blur(.1em)' },
  ], { delay: T.saida + i * 30, duration: 390, easing: 'cubic-bezier(.45,0,.55,1)' }));

  // frase 2 entra do mesmo eixo, grupo a grupo; "idiomando." é maior, então a distância em em é menor
  grupos.forEach((g, i) => {
    const k = i === 2 ? 1.3 : 1;
    anima(g, [
      { opacity: 0, transform: `translateY(${.83 / k}em)`, filter: 'blur(.08em)', easing: EASE_ENTRADA },
      { opacity: 1, transform: `translateY(${-.04 / k}em)`, filter: 'blur(0)', offset: .72, easing: 'ease-in-out' },
      { opacity: 1, transform: 'translateY(0)', filter: 'blur(0)' },
    ], { delay: T.entrada + i * 95, duration: 680 });
  });

  // os pingos dos "i": só eles se mexem. Um salto cada, de 0,12 em, e voltam exatamente ao lugar.
  pingos.forEach((p, i) => {
    const inicio = i === 0 ? T.pingo1 : T.pingo2;
    anima(p, [{ opacity: 0 }, { opacity: 1 }], { delay: inicio, duration: 60 });
    const pulo = anima(p, [
      { transform: 'translateY(0)', easing: 'cubic-bezier(.15,.85,.3,1)' },
      { transform: `translateY(${-salto}px)`, offset: .36, easing: 'cubic-bezier(.45,0,.6,1)' },
      { transform: 'translateY(0)' },
    ], { delay: inicio, duration: 420 });
    // a batida do pingo toca no instante em que ele começa a saltar (da esquerda para a direita)
    Promise.all([pulo.ready, som]).then(([, s]) => {
      if (s) s.tocar(i, pulo.startTime + inicio - document.timeline.currentTime);
    });
  });

  // por último, a frase original volta como subtítulo
  anima(subtitulo, [{ opacity: 0, transform: 'translateY(.5em)' }, { opacity: 1, transform: 'translateY(0)' }],
    { delay: T.subtitulo, duration: 500, easing: EASE_ENTRADA });
}

function todas() {
  return [...raiz.getAnimations({ subtree: true }), ...(deslize ? [deslize] : [])];
}

if (reduzido) {
  // sem movimento: fica a frase inicial, parada, e a marca no centro
  raiz.dataset.estatico = '';
} else {
  const som = prepararSom();
  iniciar(som);
  deslizarMarca();

  // aba oculta: pausa só o que está rodando (e o relógio do áudio); ao voltar, retoma só essas
  const aoMudarVisibilidade = () => {
    if (document.hidden) {
      pausadas = todas().filter((a) => a.playState === 'running');
      pausadas.forEach((a) => a.pause());
      som.then((s) => s && s.pausar());
    } else {
      pausadas.forEach((a) => a.play());
      pausadas = [];
      som.then((s) => s && s.retomar());
    }
  };
  document.addEventListener('visibilitychange', aoMudarVisibilidade);
  if (document.hidden) aoMudarVisibilidade(); // a página abriu numa aba em segundo plano

  // se a busca começar antes do fim, a marca volta ao lugar normal do cabeçalho (o CSS da outra tela cuida disso)
  new MutationObserver(() => {
    if (document.body.dataset.estado === 'inicio') return;
    if (deslize) deslize.cancel();
    som.then((s) => s && s.parar()); // a animação segue ao fundo, mas o som dos pingos fica só na tela inicial
  }).observe(document.body, { attributes: true, attributeFilter: ['data-estado'] });

  // com a marca fixada no canto, recalcula a posição quando a janela muda de tamanho
  addEventListener('resize', () => {
    if (document.body.dataset.marca === 'canto') fixarMarca();
  });
}
