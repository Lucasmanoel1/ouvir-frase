import test from 'node:test';
import assert from 'node:assert/strict';
import { proximoLoop } from '../../main/resources/static/js/ferramentas.js';

test('um clique liga o loop deste vídeo e desliga qualquer loop ligado', () => {
  assert.equal(proximoLoop('desligado', 'clique'), 'ligado');
  assert.equal(proximoLoop('ligado', 'clique'), 'desligado');
  assert.equal(proximoLoop('automatico', 'clique'), 'desligado');
});

test('clique duplo (click, click, dblclick) termina no automático, de qualquer estado', () => {
  for (const inicio of ['desligado', 'ligado', 'automatico']) {
    const fim = ['clique', 'clique', 'duplo'].reduce(proximoLoop, inicio);
    assert.equal(fim, 'automatico', inicio);
  }
});

test('trocar de resultado desliga o loop simples e mantém o automático', () => {
  assert.equal(proximoLoop('ligado', 'troca'), 'desligado');
  assert.equal(proximoLoop('automatico', 'troca'), 'automatico');
  assert.equal(proximoLoop('desligado', 'troca'), 'desligado');
});
