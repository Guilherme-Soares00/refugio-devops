import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);

test('Build obtem a API do repositorio Java sem uma copia independente no DevOps', () => {
  const dockerfile = readFileSync(new URL('Dockerfile', root), 'utf8');
  const revision = dockerfile.match(/^ARG JAVA_REF=([a-f0-9]{40})$/m)?.[1];
  assert.ok(revision, 'O build precisa declarar o commit Java reproduzivel');
  assert.match(dockerfile, /^ADD https:\/\/github\.com\/Guilherme-Soares00\/refugio\.git#\$\{JAVA_REF\} \/build\/$/m);
  assert.match(dockerfile, /org\.opencontainers\.image\.source="https:\/\/github\.com\/Guilherme-Soares00\/refugio"/);
  assert.match(dockerfile, /org\.opencontainers\.image\.revision="\$\{JAVA_REF\}"/);
  assert.doesNotMatch(dockerfile, /^COPY (?:src|pom\.xml|mvnw|\.mvn)/m);
  for (const path of ['src', 'pom.xml', 'mvnw', 'mvnw.cmd', '.mvn']) {
    assert.equal(existsSync(new URL(path, root)), false, `Fonte Java duplicado: ${path}`);
  }
});
