// Read-only inspection/export. Never starts Spring or restores a database.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = process.argv[2] && path.resolve(process.argv[2]);
if (!output || output === root || !path.relative(root, output).startsWith('..' + path.sep)) {
  throw new Error('Supply a NEW private backup directory outside the repository.');
}
const props = Object.fromEntries(fs.readFileSync(path.join(root, 'src/main/resources/application.properties'), 'utf8')
  .split(/\r?\n/).filter(l => l && !l.startsWith('#') && l.includes('='))
  .map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
const setting = (env, key) => process.env[env] ?? (props[key]?.includes('${') ? undefined : props[key]);
const jdbc = setting('SPRING_DATASOURCE_URL', 'spring.datasource.url');
if (!jdbc?.startsWith('jdbc:mysql://')) throw new Error('Set SPRING_DATASOURCE_URL for the source to inspect/export.');
const url = new URL(jdbc.slice(5));
const database = decodeURIComponent(url.pathname.slice(1));
if (!/^[a-zA-Z0-9_]+$/.test(database) || url.username || url.password) throw new Error('Unsupported JDBC URL.');
const username = setting('SPRING_DATASOURCE_USERNAME', 'spring.datasource.username');
const password = setting('SPRING_DATASOURCE_PASSWORD', 'spring.datasource.password');
if (!username || password === undefined) throw new Error('Set source datasource credentials in environment variables.');
const bin = process.env.MYSQL_BIN ?? 'C:/Program Files/MySQL/MySQL Server 8.0/bin';
const exe = name => path.join(bin, name + (process.platform === 'win32' ? '.exe' : ''));
const env = { ...process.env, MYSQL_PWD: password };
const args = ['--no-defaults', '--protocol=TCP', `--host=${url.hostname}`, `--port=${url.port || 3306}`,
  `--user=${username}`, '--default-character-set=utf8mb4'];
const query = sql => execFileSync(exe('mysql'), [...args, '--batch', '--raw', '--skip-column-names', `--database=${database}`, '--execute', sql],
  { env, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] });
try {
  const server = query('SELECT VERSION(), @@character_set_server, @@collation_server, @@sql_mode');
  const counts = query("SELECT COUNT(*), SUM(ENGINE='InnoDB') FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'");
  fs.mkdirSync(output, { recursive: false, mode: 0o700 });
  fs.writeFileSync(path.join(output, 'server-private.tsv'), server, { mode: 0o600 });
  for (const [name, table, schemaColumn] of [
    ['tables', 'TABLES', 'TABLE_SCHEMA'], ['columns', 'COLUMNS', 'TABLE_SCHEMA'],
    ['constraints', 'TABLE_CONSTRAINTS', 'CONSTRAINT_SCHEMA'], ['keys', 'KEY_COLUMN_USAGE', 'CONSTRAINT_SCHEMA'],
    ['references', 'REFERENTIAL_CONSTRAINTS', 'CONSTRAINT_SCHEMA'], ['indexes', 'STATISTICS', 'TABLE_SCHEMA'],
    ['checks', 'CHECK_CONSTRAINTS', 'CONSTRAINT_SCHEMA'], ['triggers', 'TRIGGERS', 'TRIGGER_SCHEMA'],
    ['routines', 'ROUTINES', 'ROUTINE_SCHEMA'], ['events', 'EVENTS', 'EVENT_SCHEMA']]) {
    fs.writeFileSync(path.join(output, `${name}-private.tsv`), query(`SELECT * FROM information_schema.${table} WHERE ${schemaColumn}=DATABASE()`), { mode: 0o600 });
  }
  const history = query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='flyway_schema_history'").trim();
  fs.writeFileSync(path.join(output, 'migration-history-present.txt'), history, { mode: 0o600 });
  if (history === '1') fs.writeFileSync(path.join(output, 'migration-history-private.tsv'), query('SELECT installed_rank,version,description,type,script,success FROM flyway_schema_history ORDER BY installed_rank'), { mode: 0o600 });
  for (const [name, extra] of [['database-private.sql', []], ['schema-private.sql', ['--no-data']]]) {
    const destination = path.join(output, name);
    execFileSync(exe('mysqldump'), [...args, '--single-transaction', '--skip-lock-tables', '--no-tablespaces', '--set-gtid-purged=OFF',
      '--routines', '--events', '--triggers', '--hex-blob', ...extra, `--result-file=${destination}`, database],
      { env, stdio: ['ignore', 'pipe', 'pipe'] });
    fs.chmodSync(destination, 0o600);
    const data = fs.readFileSync(destination);
    if (!data.length || !data.toString('utf8').includes('-- Dump completed on')) throw new Error('Incomplete dump.');
    fs.writeFileSync(destination + '.sha256', createHash('sha256').update(data).digest('hex') + '\n', { mode: 0o600 });
  }
  console.log(JSON.stringify({ backupDirectory: output, serverVersion: server.split('\t')[0], baseTablesAndInnoDB: counts.trim(), flywayHistoryPresent: history === '1', status: 'export-complete; restore not verified' }));
} catch {
  console.error('Inspection/export failed. No restore or source DDL was performed. Partial files may remain in the private destination. Check local credentials, client availability and SELECT/SHOW VIEW/TRIGGER/EVENT/routine privileges; do not publish raw errors.');
  process.exitCode = 1;
}
