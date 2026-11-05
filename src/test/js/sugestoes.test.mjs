import test from 'node:test';
import assert from 'node:assert/strict';
import { separarConhecidas, textoParaCopiar, quantos } from '../../main/resources/painel/sugestoes.js';

const s = (frase, videos = 10, vezes = 20) => ({ frase, videos, vezes });

test('separarConhecidas tira o que já está nos exemplos, sem ligar para maiúscula nem apóstrofo curvo', () => {
  const { novas, puladas } = separarConhecidas(
    [s('thank you'), s("don't know"), s('at least')],
    ['Thank You', 'don’t know'],
  );
  assert.deepEqual(novas.map((x) => x.frase), ['at least']);
  assert.deepEqual(puladas.map((x) => x.frase), ['thank you', "don't know"]);
});

test('separarConhecidas não pula nada quando o idioma não tem lista de exemplos', () => {
  const { novas, puladas } = separarConhecidas([s('потому что')], undefined);
  assert.equal(novas.length, 1);
  assert.equal(puladas.length, 0);
});

test('textoParaCopiar escreve uma frase por linha, com vídeo no singular quando é um só', () => {
  assert.equal(
    textoParaCopiar([s('at least', 41), s('no sé', 1)]),
    'at least · 41 vídeos\nno sé · 1 vídeo',
  );
});

test('textoParaCopiar sem sugestões devolve texto vazio', () => {
  assert.equal(textoParaCopiar([]), '');
});

test('quantos concorda o plural', () => {
  assert.equal(quantos(1, 'vez', 'vezes'), '1 vez');
  assert.equal(quantos(2, 'vez', 'vezes'), '2 vezes');
});
