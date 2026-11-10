import test from 'node:test';
import assert from 'node:assert/strict';
import { criarRelogio } from '../../main/resources/static/js/relogio.js';

const perto = (a, b) => assert.ok(Math.abs(a - b) < 1e-9, `${a} != ${b}`);

test('a primeira leitura é o tempo do player', () => {
  assert.equal(criarRelogio().ler(10, 0, 1, true), 10);
});

test('entre as atualizações do player, anda com a velocidade', () => {
  for (const taxa of [0.5, 1, 1.5]) {
    const r = criarRelogio();
    r.ler(10, 0, taxa, true);
    perto(r.ler(10, 100, taxa, true), 10 + 0.1 * taxa * 0.9); // player parado em 10: corrige só 10% da diferença
  }
});

test('segue o player sem erro quando ele está certo', () => {
  const r = criarRelogio();
  r.ler(10, 0, 1.5, true);
  perto(r.ler(10.3, 200, 1.5, true), 10.3);
});

test('uma correção pequena para trás do player não faz o tempo voltar', () => {
  const r = criarRelogio();
  r.ler(10, 0, 1, true);
  const antes = r.ler(10.2, 200, 1, true);
  assert.ok(r.ler(10.13, 210, 1, true) >= antes);
});

test('salto maior que a tolerância (seek) vai direto para o player', () => {
  const r = criarRelogio();
  r.ler(10, 0, 1, true);
  assert.equal(r.ler(8, 100, 1, true), 8);
  assert.equal(r.ler(30, 200, 1, true), 30);
});

test('pausado devolve o tempo do player, inclusive para trás', () => {
  const r = criarRelogio();
  r.ler(10, 0, 1, true);
  r.ler(10.5, 500, 1, true);
  assert.equal(r.ler(10.45, 600, 1, false), 10.45);
  assert.equal(r.ler(10.45, 900, 1, false), 10.45);
});

test('troca de velocidade recomeça do tempo do player', () => {
  const r = criarRelogio();
  r.ler(10, 0, 1, true);
  assert.equal(r.ler(10.1, 100, 1.5, true), 10.1);
  perto(r.ler(10.25, 200, 1.5, true), 10.25);
});

test('zerar faz a próxima leitura recomeçar do player', () => {
  const r = criarRelogio();
  r.ler(10, 0, 1, true);
  r.zerar();
  assert.equal(r.ler(10.1, 100, 1, true), 10.1);
});
