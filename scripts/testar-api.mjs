import assert from 'node:assert/strict';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const base = (process.argv[2] || 'http://localhost:8080').replace(/\/$/, '');
const mode = process.argv[3] || 'crud';
const stateFile = new URL('../target/devops/persistencia.json', import.meta.url);
const results = [];

async function request(method, path, expected, body, token) {
  const response = await fetch(base + path, {
    method,
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(15000),
  });
  assert.equal(response.status, expected, `${method} ${path}: esperado ${expected}, recebido ${response.status}`);
  results.push({ metodo: method, caminho: path, status: response.status });
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

const point = {
  nome: 'Ponto de teste DevOps', logradouro: 'Rua Demonstrativa', numero: '100',
  bairro: 'Bairro DevOps', cidade: 'Cidade Demonstrativa', uf: 'SP', horarioTexto: '08:00-18:00', status: 'ATIVO',
};

try {
  const destination = new URL(base);
  if (destination.protocol === 'http:' && !['localhost', '127.0.0.1', '[::1]'].includes(destination.hostname)) {
    throw new Error('Use HTTPS ou tunel SSH para autenticar fora do loopback');
  }
  assert.equal((await request('GET', '/actuator/health', 200)).status, 'UP');
  const manager = await request('POST', '/api/auth/login', 200, { email: 'gestor09@example.test', senha: 'GestorDemo2026!' });
  const token = manager.accessToken;
  assert.ok(token);
  if (mode === 'preparar-persistencia') {
    const created = await request('POST', '/api/pontos', 201, { ...point, codigoPublico: 'REF-990', nome: 'Persistencia DevOps' }, token);
    const resource = await request('POST', '/api/recursos', 201, { pontoId: created.id, tipo: 'AGUA', disponivel: true }, token);
    mkdirSync(new URL('../target/devops/', import.meta.url), { recursive: true });
    writeFileSync(stateFile, JSON.stringify({ base, pontoId: created.id, recursoId: resource.id, codigoPublico: created.codigoPublico }));
  } else if (mode === 'verificar-persistencia') {
    const state = JSON.parse(readFileSync(stateFile, 'utf8'));
    assert.equal(state.base, base, 'Use o mesmo ambiente do preparo');
    const saved = await request('GET', `/api/pontos/${state.pontoId}`, 200);
    assert.equal(saved.codigoPublico, state.codigoPublico);
    assert.equal(saved.nome, 'Persistencia DevOps');
    const resources = await request('GET', `/api/pontos/${state.pontoId}/recursos`, 200);
    assert.ok(JSON.stringify(resources).includes(`"id":${state.recursoId}`));
  } else if (mode === 'crud') {
    const citizen = await request('POST', '/api/auth/login', 200, { email: 'cidadao01@example.test', senha: 'CidadaoDemo2026!' });
    await request('POST', '/api/pontos', 401, { ...point, codigoPublico: 'REF-989' });
    await request('POST', '/api/pontos', 403, { ...point, codigoPublico: 'REF-989' }, citizen.accessToken);
    await request('POST', '/api/pontos', 400, { ...point, codigoPublico: 'invalido' }, token);
    await request('GET', '/api/pontos/999999999', 404);
    await request('GET', '/api/pontos', 200);
    await request('GET', '/actuator/info', 200);
    await request('GET', '/actuator/metrics', 401);
    await request('GET', '/actuator/metrics', 403, undefined, citizen.accessToken);
    await request('GET', '/actuator/metrics', 200, undefined, token);
    const spec = await request('GET', '/v3/api-docs', 200);
    assert.ok(spec.paths['/api/pontos']);
    assert.ok(spec.paths['/api/recursos']);
    const created = await request('POST', '/api/pontos', 201, { ...point, codigoPublico: 'REF-989' }, token);
    await request('POST', '/api/pontos', 409, { ...point, codigoPublico: 'REF-989' }, token);
    const resource = await request('POST', '/api/recursos', 201, { pontoId: created.id, tipo: 'SOMBRA', disponivel: true }, token);
    await request('GET', `/api/pontos/${created.id}`, 200);
    await request('GET', `/api/pontos/${created.id}/recursos`, 200);
    await request('PUT', `/api/pontos/${created.id}`, 204, { ...point, nome: 'Ponto DevOps atualizado' }, token);
    assert.equal((await request('GET', `/api/pontos/${created.id}`, 200)).nome, 'Ponto DevOps atualizado');
    await request('PUT', `/api/recursos/${resource.id}`, 204, { pontoId: created.id, tipo: 'SOMBRA', disponivel: false }, token);
    const detail = await request('GET', `/api/pontos/${created.id}`, 200);
    assert.ok(JSON.stringify(detail).includes('"disponivel":false'));
    await request('DELETE', `/api/pontos/${created.id}`, 409, undefined, token);
    await request('DELETE', `/api/recursos/${resource.id}`, 204, undefined, token);
    const after = await request('GET', `/api/pontos/${created.id}/recursos`, 200);
    assert.ok(!JSON.stringify(after).includes(`"id":${resource.id}`));
    await request('DELETE', `/api/pontos/${created.id}`, 204, undefined, token);
    await request('GET', `/api/pontos/${created.id}`, 404);
  } else {
    throw new Error('Modo aceito: crud, preparar-persistencia ou verificar-persistencia');
  }
  console.log(JSON.stringify({ resultado: 'APROVADO', base, modo: mode, verificacoes: results.length, respostas: results }, null, 2));
} catch (error) {
  console.error(`FALHOU: ${error.message}`);
  console.error('Dados criados antes da falha podem permanecer; nao remova volumes para corrigir.');
  process.exitCode = 1;
}
