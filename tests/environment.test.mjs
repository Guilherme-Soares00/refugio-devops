import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, copyFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const bash = process.env.BASH_EXE || 'bash';

test('Preparacao gera segredos fortes e nao sobrescreve ambiente existente', () => {
  const dir = mkdtempSync(join(process.env.TMPDIR || tmpdir(), 'refugio-env-test-'));
  mkdirSync(join(dir, 'scripts'));
  copyFileSync(new URL('../scripts/preparar-ambiente.sh', import.meta.url), join(dir, 'scripts/preparar-ambiente.sh'));
  const run = () => spawnSync(bash, ['scripts/preparar-ambiente.sh', '--dados-demo'], { cwd: dir, encoding: 'utf8' });
  const first = run();
  assert.equal(first.status, 0, first.stderr);
  const original = readFileSync(join(dir, '.env'), 'utf8');
  const vars = Object.fromEntries(original.trim().split(/\r?\n/).map(line => { const p = line.indexOf('='); return [line.slice(0, p), line.slice(p + 1)]; }));
  assert.equal(Buffer.from(vars.JWT_SECRET, 'base64').length, 48);
  assert.match(vars.POSTGRES_PASSWORD, /^[a-f0-9]{48}$/);
  assert.equal(vars.APP_SEED_ENABLED, 'true');
  assert.equal(vars.API_BIND_ADDRESS, '127.0.0.1');
  assert.ok(!first.stdout.includes(vars.JWT_SECRET));
  assert.ok(!first.stdout.includes(vars.POSTGRES_PASSWORD));
  assert.equal(run().status, 0);
  assert.equal(readFileSync(join(dir, '.env'), 'utf8'), original);
});
