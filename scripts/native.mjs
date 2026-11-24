import { spawn, spawnSync } from 'node:child_process';
import { existsSync, readFileSync, realpathSync, readdirSync, mkdirSync, openSync, closeSync } from 'node:fs';
import { createServer } from 'node:net';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { createInterface } from 'node:readline/promises';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const windows = process.platform === 'win32';
const children = new Set();

export function readEnv(file) {
  const values = {};
  for (const raw of readFileSync(file, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const match = /^([A-Z][A-Z0-9_]*)\s*=\s*(.*)$/.exec(line);
    if (!match) throw new Error(`Invalid environment file line in ${path.basename(file)}`);
    let value = match[2];
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    values[match[1]] = value;
  }
  return values;
}

function loadConfiguration() {
  const file = path.join(root, '.env');
  if (!existsSync(file)) throw new Error('Copy .env.example to .env and configure the local database password first.');
  const values = readEnv(file);
  for (const [key, value] of Object.entries(values)) {
    if (!/^(APP_[A-Z0-9_]+|SEED_DEMO_DATA|BACKEND_URL)$/.test(key)) throw new Error('Unsupported key in .env; set runtime and administrator configuration in your terminal.');
    process.env[key] = value;
  }
  const env = process.env;
  for (const key of ['APP_DB_HOST', 'APP_DB_PORT', 'APP_DB_NAME', 'APP_DB_USER', 'APP_DB_PASSWORD', 'APP_TEST_DB_NAME']) {
    if (!env[key] || env[key] === 'replace_with_local_password') throw new Error(`Configure ${key} in the root .env file.`);
  }
  if (!['127.0.0.1', 'localhost'].includes(env.APP_DB_HOST)) throw new Error('These scripts only operate against a local PostgreSQL server.');
  for (const key of ['APP_DB_NAME', 'APP_DB_USER', 'APP_TEST_DB_NAME']) {
    if (!/^[a-z][a-z0-9_]*$/.test(env[key])) throw new Error(`${key} must use lowercase letters, numbers and underscores.`);
  }
  if (env.APP_DB_NAME === env.APP_TEST_DB_NAME || !env.APP_TEST_DB_NAME.endsWith('_test')) throw new Error('Use a separate test database ending in _test.');
  if (['postgres', 'template0', 'template1'].includes(env.APP_DB_NAME)) throw new Error('Use a dedicated application database.');
  for (const key of ['APP_DB_PORT', 'APP_BACKEND_PORT']) {
    if (key === 'APP_BACKEND_PORT') env[key] ||= '8080';
    if (!/^\d+$/.test(env[key]) || Number(env[key]) < 1 || Number(env[key]) > 65535) throw new Error(`${key} must be a valid port.`);
  }
  env.BACKEND_URL ||= `http://127.0.0.1:${env.APP_BACKEND_PORT}`;
  return env;
}

function executable(name) {
  const extensions = windows ? ['.exe', '.cmd', '.bat', ''] : [''];
  for (const directory of (process.env.PATH || '').split(path.delimiter)) {
    for (const extension of extensions) {
      const candidate = path.join(directory, name + extension);
      if (existsSync(candidate)) return candidate;
    }
  }
  if (windows && name === 'psql') {
    const base = path.join(process.env.ProgramFiles || 'C:/Program Files', 'PostgreSQL');
    if (existsSync(base)) for (const version of readdirSync(base).sort().reverse()) {
      const candidate = path.join(base, version, 'bin/psql.exe');
      if (existsSync(candidate)) return candidate;
    }
  }
  throw new Error(`Install ${name} and add it to PATH.`);
}

function runtime() {
  if (Number(process.versions.node.split('.')[0]) < 24) throw new Error('Use the Node.js 24 version pinned in frontend/.nvmrc.');
  if (!process.env.JAVA_HOME || !existsSync(path.join(process.env.JAVA_HOME, windows ? 'bin/javac.exe' : 'bin/javac'))) throw new Error('Set JAVA_HOME to a JDK 25 installation.');
  const java = path.join(process.env.JAVA_HOME, windows ? 'bin/java.exe' : 'bin/java');
  const version = spawnSync(java, ['-version'], { encoding: 'utf8', windowsHide: true });
  if (version.status !== 0 || !/version "25\./.test(version.stderr)) throw new Error('JDK 25 is required.');
  return java;
}

function run(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: 'inherit', windowsHide: true, ...options });
    child.once('error', () => reject(new Error(`Could not start ${path.basename(command)}`)));
    child.once('exit', code => code === 0 ? resolve() : reject(new Error(`${path.basename(command)} failed (exit ${code}).`)));
  });
}

async function maven(args) {
  const cwd = path.join(root, 'backend');
  const settings = process.env.APP_MAVEN_SETTINGS;
  if (settings) {
    if (!existsSync(settings) || /["%\r\n]/.test(settings)) throw new Error('APP_MAVEN_SETTINGS must point to an existing local settings file.');
    args = ['-s', settings, ...args];
  }
  if (windows) {
    const wrapper = path.join(cwd, 'mvnw.cmd');
    await run(process.env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', `""${wrapper}" -B -ntp ${args.map(argument => `"${argument}"`).join(' ')}"`], { cwd, windowsVerbatimArguments: true });
  } else await run(path.join(cwd, 'mvnw'), ['-B', '-ntp', ...args], { cwd });
}

async function npm(args) {
  const launcher = executable('npm');
  const cli = windows ? path.join(path.dirname(launcher), 'node_modules/npm/bin/npm-cli.js') : realpathSync(launcher);
  if (!existsSync(cli)) throw new Error('Install a standard Node.js/npm distribution.');
  await run(process.execPath, [cli, ...args], { cwd: path.join(root, 'frontend') });
}

async function sql(database, query, admin = false, extra = {}) {
  const env = { ...process.env, ...extra };
  if (!admin) env.PGPASSWORD = env.APP_DB_PASSWORD;
  await run(executable('psql'), ['-X', ...(admin ? [] : ['-w']), '-v', 'ON_ERROR_STOP=1', '-h', env.APP_DB_HOST, '-p', env.APP_DB_PORT,
    '-U', admin ? (env.PGUSER || 'postgres') : env.APP_DB_USER, '-d', database, ...query], { env });
}

async function freePort(port) {
  await new Promise((resolve, reject) => {
    const server = createServer();
    server.once('error', () => reject(new Error(`Port ${port} is occupied or reserved. Configure a free APP_BACKEND_PORT and matching BACKEND_URL if needed.`)));
    server.listen(port, '127.0.0.1', () => server.close(resolve));
  });
}

async function connected(database = process.env.APP_DB_NAME) {
  await sql(database, ['-Atc', "DO $$ BEGIN IF EXISTS (SELECT FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolcreatedb OR rolcreaterole)) THEN RAISE EXCEPTION 'Use an unprivileged application role'; END IF; END $$;"]);
}

async function waitFor(url, child) {
  for (let attempt = 0; attempt < 90; attempt++) {
    if (child.exitCode !== null) throw new Error('Service exited before becoming ready. Check .logs.');
    try { const response = await fetch(url, { signal: AbortSignal.timeout(1000) }); if (response.ok) return; } catch { /* service is starting */ }
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error(`Service did not become ready at ${url}. Check .logs.`);
}

function background(command, args, name, cwd = root) {
  const file = openSync(path.join(root, '.logs', `${name}.log`), 'a');
  const child = spawn(command, args, { cwd, stdio: ['ignore', file, file], windowsHide: true });
  closeSync(file);
  child.on('error', () => { console.error(`${name} could not start. Check .logs.`); cleanup(); process.exitCode = 1; });
  children.add(child);
  return child;
}

function cleanup() { for (const child of children) if (child.exitCode === null) child.kill('SIGTERM'); }

async function start() {
  const java = runtime();
  const backend = new URL(process.env.BACKEND_URL);
  if (!['127.0.0.1', 'localhost'].includes(backend.hostname) || Number(backend.port || 80) !== Number(process.env.APP_BACKEND_PORT) || backend.pathname !== '/' || backend.protocol !== 'http:') throw new Error('BACKEND_URL must point to the configured local backend port.');
  await freePort(Number(process.env.APP_BACKEND_PORT)); await freePort(3000); await connected();
  await npm(['ci', '--no-audit']); await maven(['package', '-DskipTests']);
  mkdirSync(path.join(root, '.logs'), { recursive: true });
  const api = background(java, ['-jar', path.join(root, 'backend/target/person-management-1.0.0.jar')], 'backend');
  await waitFor(`${process.env.BACKEND_URL}/actuator/health/readiness`, api);
  const web = background(process.execPath, [path.join(root, 'frontend/node_modules/next/dist/bin/next'), 'dev', '--hostname', '127.0.0.1', '--port', '3000'], 'frontend', path.join(root, 'frontend'));
  await waitFor('http://127.0.0.1:3000/persons', web);
  console.log('Application ready: http://127.0.0.1:3000/persons (Ctrl+C to stop both applications).');
  await Promise.race([api, web].map(child => new Promise((resolve, reject) => child.once('exit', () => reject(new Error('An application stopped. Check .logs.'))))));
}

async function main() {
  loadConfiguration();
  const action = process.argv[2];
  if (action === 'setup-db') {
    const index = process.argv.indexOf('--admin-env');
    const admin = index < 0 ? {} : readEnv(path.resolve(process.argv[index + 1]));
    for (const key of Object.keys(admin)) if (!['PGUSER', 'PGPASSWORD'].includes(key)) throw new Error('Administrator file accepts only PGUSER and PGPASSWORD.');
    await sql('postgres', ['-f', path.join(root, 'infra/postgresql/setup.sql')], true, admin);
    await connected(); await connected(process.env.APP_TEST_DB_NAME);
  } else if (action === 'start') await start();
  else if (action === 'backend') { runtime(); await connected(); await maven(['spring-boot:run']); }
  else if (action === 'frontend') { runtime(); await npm(['ci', '--no-audit']); await npm(['run', 'dev']); }
  else if (action === 'test') {
    runtime(); await connected(process.env.APP_TEST_DB_NAME);
    await maven(['verify', '-Pintegration']);
    await npm(['ci', '--no-audit']); for (const script of ['test', 'typecheck', 'lint', 'build']) await npm(['run', script]);
  } else if (action === 'reset-demo') {
    if (process.env.APP_DB_NAME !== 'person_training') throw new Error('Reset is restricted to the known local person_training exercise database.');
    const prompt = createInterface({ input: process.stdin, output: process.stdout });
    const answer = await prompt.question('Type RESET person_training to replace all local demo records: '); prompt.close();
    if (answer !== 'RESET person_training') { console.log('Reset canceled.'); return; }
    await connected();
    await sql('person_training', ['-c', "BEGIN; TRUNCATE person RESTART IDENTITY; INSERT INTO person(first_name,last_name,email,date_of_birth) VALUES ('Anna','Novak','anna.novak@example.com','1994-05-12'),('Daniel','Meyer','daniel.meyer@example.com','1988-09-23'),('Sara','Ali','sara.ali@example.com','2000-02-07'); COMMIT;"]);
  } else throw new Error('Use setup-db, start, backend, frontend, test or reset-demo.');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => { cleanup(); process.exit(0); });
  process.once('exit', cleanup);
  main().catch(error => { cleanup(); console.error(error.message); process.exitCode = 1; });
}
