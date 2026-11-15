import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

test('Teste HTTP recusa enviar credenciais para HTTP publico', () => {
  const result = spawnSync(process.execPath, [fileURLToPath(new URL('../scripts/testar-api.mjs', import.meta.url)), 'http://203.0.113.1:8080', 'crud'], { encoding: 'utf8', timeout: 1500 });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /Use HTTPS ou tunel SSH para autenticar fora do loopback/);
});
