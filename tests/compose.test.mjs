import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);

test('Compose integra API e banco com persistencia, saude e nomes previsiveis', () => {
  const config = JSON.parse(execFileSync('docker', ['compose', '--env-file', '.env.example', 'config', '--format', 'json'], {
    cwd: root,
    env: { ...process.env, POSTGRES_PASSWORD: 'somente-teste-descartavel', JWT_SECRET: Buffer.alloc(48, 7).toString('base64') },
    encoding: 'utf8',
  }));
  const { api, db } = config.services;
  assert.equal(api.container_name, 'refugio-api');
  assert.equal(db.container_name, 'refugio-db');
  assert.equal(db.image, 'postgres:16');
  assert.equal(api.restart, 'unless-stopped');
  assert.equal(db.restart, 'unless-stopped');
  assert.equal(api.depends_on.db.condition, 'service_healthy');
  assert.match(api.healthcheck.test.join(' '), /curl.*actuator\/health/);
  assert.match(db.healthcheck.test.join(' '), /pg_isready/);
  assert.equal(api.ports[0].host_ip, '127.0.0.1');
  assert.equal(Number(api.ports[0].published), 8080);
  assert.equal(db.ports[0].host_ip, '127.0.0.1');
  assert.equal(Number(db.ports[0].published), 5432);
  assert.equal(api.environment.SPRING_DATASOURCE_URL, 'jdbc:postgresql://db:5432/refugio');
  assert.equal(api.environment.SPRING_DATASOURCE_USERNAME, db.environment.POSTGRES_USER);
  assert.equal(api.environment.SPRING_DATASOURCE_PASSWORD, db.environment.POSTGRES_PASSWORD);
  assert.equal(config.networks['refugio-net'].name, 'refugio-net');
  assert.equal(config.volumes['refugio-pgdata'].name, 'refugio-pgdata');
  assert.equal(db.volumes[0].target, '/var/lib/postgresql/data');
  const dockerfile = readFileSync(new URL('../Dockerfile', import.meta.url), 'utf8');
  assert.match(dockerfile, /FROM eclipse-temurin:25-jdk-jammy AS build/);
  assert.match(dockerfile, /FROM eclipse-temurin:25-jre-jammy/);
  assert.match(dockerfile, /WORKDIR \/app/);
  assert.match(dockerfile, /USER 10001:10001/);
  assert.match(dockerfile, /apt-get install[^\n]*curl/);
  assert.match(dockerfile, /ENTRYPOINT \["java", "-jar", "\/app\/app.jar"\]/);
});
