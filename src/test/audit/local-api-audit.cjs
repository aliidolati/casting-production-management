/* Explicit opt-in audit of the already-running application and its LOCAL test DB.
 * Never use this against production. Creates only namespaced synthetic fixtures.
 * No tokens, passwords, signatures, drawings or existing row contents are logged.
 */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');
const assert = require('node:assert/strict');
if (!process.argv.includes('--authorized-local-test-db')) throw new Error('Explicit authorization required');
const base = 'http://127.0.0.1:8080';
const run = 'AUDIT_20261030_' + crypto.randomBytes(4).toString('hex');
const password = crypto.randomBytes(24).toString('base64url') + '!Aa7';
const out = path.resolve('target/audit');
fs.mkdirSync(out, { recursive: true });
const checks = [], actors = {}, roots = { run, projects: [], users: [], materials: [], batches: [] };
const cp = fs.readFileSync(fs.existsSync('target/audit/classpath.txt') ? 'target/audit/classpath.txt' : 'target/melt-workflow-classpath.txt', 'utf8').trim();
const db = (mode, input) => {
  const r = spawnSync('java', ['--class-path', cp, 'src/test/audit/LocalAuditDatabase.java', '--authorized-local-test-db', mode],
    { input: input ? JSON.stringify(input) : undefined, encoding: 'utf8', timeout: 30000 });
  if (r.status !== 0) throw new Error('Local database helper failed: ' + mode);
  return JSON.parse(r.stdout.trim());
};
function persist() {
  const report = JSON.stringify({ run, base, checks,
    summary: { total: checks.length, passed: checks.filter(c => c.result === 'PASS').length,
      failed: checks.filter(c => c.result === 'FAIL').length, errors: checks.filter(c => c.result === 'ERROR').length } }, null, 2);
  fs.writeFileSync(path.join(out, 'live-api-results.json'), report);
  fs.writeFileSync(path.join(out, 'results-' + run + '.json'), report);
  fs.writeFileSync(path.join(out, 'fixture-roots.json'), JSON.stringify(roots, null, 2));
}
async function req(name, method, route, data, actor = 'admin', expected = 200, extra = {}) {
  const headers = { ...extra.headers };
  if (actors[actor]?.token) headers.Authorization = 'Bearer ' + actors[actor].token;
  let body;
  if (data instanceof FormData) body = data;
  else if (data !== undefined && data !== null) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(data); }
  const t = performance.now();
  const r = await fetch(base + route, { method, body, headers, signal: AbortSignal.timeout(20000) });
  const text = await r.text();
  let value; try { value = JSON.parse(text); } catch { value = text; }
  const allowed = Array.isArray(expected) ? expected : [expected];
  const item = { name, method, route, actor, expected: allowed, actual: r.status,
    result: allowed.includes(r.status) ? 'PASS' : 'FAIL', ms: Math.round(performance.now() - t) };
  if (extra.probe) item.probe = extra.probe;
  if (item.result === 'FAIL' && value && typeof value === 'object' && value.message) {
    item.errorMessage = String(value.message).slice(0,300);
    if(value.errors) item.fieldErrors=value.errors;
  }
  checks.push(item); persist();
  if (item.result === 'FAIL') console.log('FAIL ' + name + ' expected=' + allowed + ' actual=' + r.status);
  return { ...item, body: value, headers: Object.fromEntries(r.headers) };
}
function verify(name, predicate, details = {}) {
  checks.push({ name, result: predicate ? 'PASS' : 'FAIL', ...details }); persist();
  if (!predicate) console.log('FAIL ' + name);
}
async function phase(name, f) {
  try { await f(); console.log('Phase complete: ' + name); }
  catch (e) { checks.push({ name, result: 'ERROR', error: e.name, message: String(e.message).replace(/[A-Za-z0-9_-]{35,}/g, '<redacted>').slice(0, 160) }); persist(); console.log('Phase error: ' + name + ': ' + e.name); }
}
function id(r) { if (!r.body?.id) throw new Error('Fixture creation did not return id; status=' + r.actual); return r.body.id; }
async function login(actor) {
  const a = actors[actor];
  const r = await req('login ' + actor, 'POST', '/api/auth/login', { username: a.username, password }, 'anonymous');
  if (!r.body.token) throw new Error('Fixture login failed');
  a.token = r.body.token;
}
async function user(key, permissions = [], role = 'OPERATOR') {
  const r = await req('create user ' + key, 'POST', '/api/users', { username: run + '_' + key, password, fullName: 'کاربر آزمایشی ' + key, role, permissions }, 'admin', 201);
  const a = { id: id(r), username: r.body.username }; actors[key] = a; roots.users.push(a.id); persist();
  await login(key); return a;
}
let project, definition, pieces = [], template, report, workflow, heat, task, otherTask, material;
const today = '2026-10-30';
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');
function upload(bytes = png, type = 'image/png') { const f = new FormData(); f.append('file', new Blob([bytes], { type }), 'audit.png'); return f; }
async function stage(piece, value) { return req('admin stage ' + value, 'PATCH', '/api/pieces/' + piece + '/stage', { newStage: value }); }
async function main() {
  const seed = db('seed', { username: run + '_admin', password });
  actors.admin = seed; roots.users.push(seed.id); persist();
  await login('admin');
  await user('limited', []);
  await user('worker', ['RECEIVE_TASKS','MANAGE_PIECE_NOTES','MANAGE_PIECE_ASSIGNMENT','MANAGE_HEAT_TREATMENT_LOG','MANAGE_RADIOGRAPHY_DEFECTS','MANAGE_WELDING_MAP','MOVE_PIECE_STAGE','UPDATE_RADIOGRAPHY_SUBSTAGE','UPDATE_HEAT_TREATMENT_SUBSTAGE','UPDATE_ROUGH_MACHINING_SUBSTAGE','INTERPRET_RADIOGRAPHY']);
  await user('reviewer', ['RECEIVE_TASKS','REVIEW_RADIOGRAPHY_REPORT','REVIEW_TENSILE_TEST','APPROVE_MELT_PASSPORT','VIEW_DASHBOARD','MANAGE_TASKS']);
  await user('catalog', ['MANAGE_PROJECTS','MANAGE_PIECE_DEFINITIONS']);
  await phase('authentication and users', async () => {
    for (const route of ['/api/auth/me','/api/projects','/api/pieces/all','/api/users','/api/tasks','/api/dashboard/stats','/api/melt-passports/materials','/api/notifications'])
      await req('anonymous blocked ' + route, 'GET', route, null, 'anonymous', 401);
    await req('malformed JWT blocked', 'GET', '/api/auth/me', null, 'anonymous', 401, { headers: { Authorization: 'Bearer invalid.audit.token' } });
    await req('login SQL metacharacters rejected', 'POST', '/api/auth/login', { username: "' OR '1'='1", password: 'audit-invalid' }, 'anonymous', 401);
    await req('operator user listing denied', 'GET', '/api/users', null, 'limited', 403);
    await req('operator user creation denied', 'POST', '/api/users', { username: run + '_forbidden', password, fullName: 'تست', role: 'ADMIN' }, 'limited', 403);
    await req('operator permission escalation denied', 'PATCH', '/api/users/' + actors.limited.id + '/permissions', { permissions: ['MANAGE_PROJECTS'] }, 'limited', 403);
    await req('current user', 'GET', '/api/auth/me', null, 'limited');
    await req('permission catalogue', 'GET', '/api/permissions');
    await req('user list', 'GET', '/api/users');
    await req('profile update', 'PATCH', '/api/users/' + actors.worker.id + '/profile', { department: 'بخش آزمایشی', active: true });
    await req('profile owner read', 'GET', '/api/users/' + actors.worker.id + '/profile', null, 'worker');
    await req('profile outsider hidden', 'GET', '/api/users/' + actors.worker.id + '/profile', null, 'limited', 404);
    await req('profile owner update denied', 'PATCH', '/api/users/' + actors.worker.id + '/profile', { department: 'ممنوع' }, 'worker', 403);
    await req('disable fixture user', 'PATCH', '/api/users/' + actors.limited.id + '/profile', { active: false });
    await req('disabled account old JWT rejected', 'GET', '/api/auth/me', null, 'limited', 401);
    await req('reenable fixture user', 'PATCH', '/api/users/' + actors.limited.id + '/profile', { active: true });
    await req('reset own fixture password', 'PATCH', '/api/users/' + actors.limited.id + '/password', { newPassword: password + 'x' }, 'admin', 204);
    await req('SECURITY old JWT must be revoked after password reset', 'GET', '/api/auth/me', null, 'limited', 401, { probe: 'token-revocation' });
    await req('restore fixture password', 'PATCH', '/api/users/' + actors.limited.id + '/password', { newPassword: password }, 'admin', 204);
    await req('own password wrong old password', 'PATCH', '/api/users/me/password', { currentPassword: 'wrong', newPassword: password }, 'limited', 401);
    await req('own password change', 'PATCH', '/api/users/me/password', { currentPassword: password, newPassword: password + 'y' }, 'limited', 204);
    await req('SECURITY old JWT must be revoked after own password change', 'GET', '/api/auth/me', null, 'limited', 401, { probe: 'token-revocation' });
    await req('restore fixture password again', 'PATCH', '/api/users/' + actors.limited.id + '/password', { newPassword: password }, 'admin', 204);
    await req('grant fixture project permission', 'PATCH', '/api/users/' + actors.limited.id + '/permissions', { permissions: ['MANAGE_PROJECTS'] });
    await req('revoke fixture project permission', 'PATCH', '/api/users/' + actors.limited.id + '/permissions', { permissions: [] });
    await req('live permission revocation applies to existing JWT', 'POST', '/api/projects', {code:run+'_revoked',name:'ممنوع'}, 'limited', 403);
    const disposable = await user('disposable');
    await req('delete unused user', 'DELETE', '/api/users/' + disposable.id, null, 'admin', 204);
    await req('deleted user old JWT rejected', 'GET', '/api/auth/me', null, 'disposable', 401);
    const cors = await req('SECURITY untrusted CORS origin should be rejected', 'OPTIONS', '/api/auth/me', null, 'anonymous', 403,
      { headers: { Origin: 'https://audit-untrusted.invalid', 'Access-Control-Request-Method': 'GET', 'Access-Control-Request-Headers': 'authorization' }, probe: 'cors' });
    checks.at(-1).allowOrigin = cors.headers['access-control-allow-origin'];
    const index = await req('public entry page', 'GET', '/', null, 'anonymous');
    verify('SECURITY Content-Security-Policy present', !!index.headers['content-security-policy'], { probe: 'headers' });
    verify('frame protection present', index.headers['x-frame-options'] === 'DENY');
    verify('MIME sniffing protection present', index.headers['x-content-type-options'] === 'nosniff');
    const js = await req('running JavaScript matches workspace', 'GET', '/js/api.js', null, 'anonymous');
    verify('served api.js equals current source', js.body.replace(/\r\n/g,'\n') === fs.readFileSync('src/main/resources/static/js/api.js','utf8').replace(/\r\n/g,'\n'));
  });
  await phase('projects definitions pieces CRUD', async () => {
    let r = await req('create project', 'POST', '/api/projects', { code: run, name: 'پروژه بررسی آزمایشی' }, 'admin', 201);
    project = id(r); roots.projects.push(project); persist();
    await req('read project', 'GET', '/api/projects/' + project);
    await req('update project', 'PUT', '/api/projects/' + project, { code: run, name: 'پروژه ویرایش شده آزمایشی' });
    await req('duplicate project rejected', 'POST', '/api/projects', { code: run, name: 'تکراری' }, 'admin', 409);
    await req('empty project rejected', 'POST', '/api/projects', { code: '', name: '' }, 'admin', 400);
    await req('operator project creation denied', 'POST', '/api/projects', { code: run + '_deny', name: 'ممنوع' }, 'limited', 403);
    r = await req('create definition', 'POST', '/api/piece-definitions', { projectId: project, rowNum: 1, pieceName: 'بدنه تست سامانه', minUts: 100, minYs: 50, minEl: 5 }, 'admin', 201);
    definition = id(r);
    await req('read definition', 'GET', '/api/piece-definitions/' + definition);
    await req('list project definitions', 'GET', '/api/piece-definitions?projectId=' + project);
    await req('update definition', 'PUT', '/api/piece-definitions/' + definition, { rowNum: 1, pieceName: 'بدنه آزمایشی ویرایش شده', minUts: 100, minYs: 50, minEl: 5 });
    await req('duplicate definition rejected', 'POST', '/api/piece-definitions', { projectId: project, rowNum: 1, pieceName: 'تکراری' }, 'admin', 409);
    await req('VALIDATION negative definition row should be rejected', 'POST', '/api/piece-definitions', { projectId: project, rowNum: -1, pieceName: 'ردیف نامعتبر آزمایشی' }, 'admin', 400, { probe: 'negative-number' });
    for(let i=1;i<=8;i++) pieces.push(id(await req('create piece ' + i, 'POST', '/api/pieces', { pieceDefinitionId: definition, pieceNumber: i }, 'admin', 201)));
    await req('read piece', 'GET', '/api/pieces/' + pieces[0]);
    await req('update piece', 'PUT', '/api/pieces/' + pieces[0], { pieceNumber: 11 });
    await req('duplicate piece rejected', 'POST', '/api/pieces', { pieceDefinitionId: definition, pieceNumber: 11 }, 'admin', 409);
    await req('VALIDATION negative piece number should be rejected', 'POST', '/api/pieces', { pieceDefinitionId: definition, pieceNumber: -3 }, 'admin', 400, { probe: 'negative-number' });
    const fractional = await req('VALIDATION fractional piece number should be rejected', 'POST', '/api/pieces', { pieceDefinitionId: definition, pieceNumber: 777.5 }, 'admin', 400, { probe: 'integer-coercion' });
    if(fractional.actual === 201) checks.at(-1).storedPieceNumber = fractional.body.pieceNumber;
    await req('bulk create pieces', 'POST', '/api/pieces/bulk', { pieceDefinitionId: definition, startNumber: 100, quantity: 3 }, 'admin', 201);
    await req('bulk duplicate rejected', 'POST', '/api/pieces/bulk', { pieceDefinitionId: definition, startNumber: 100, quantity: 3 }, 'admin', 409);
    await req('bulk cap enforced', 'POST', '/api/pieces/bulk', { pieceDefinitionId: definition, startNumber: 1000, quantity: 1001 }, 'admin', 400);
    await req('limited piece update denied', 'PUT', '/api/pieces/' + pieces[0], { pieceNumber: 12 }, 'limited', 403);
    await req('limited piece deletion denied', 'DELETE', '/api/pieces/' + pieces[0], null, 'limited', 403);
    await req('SECURITY limited operator must not modify description', 'PATCH', '/api/pieces/' + pieces[0] + '/description', { description: 'توضیح آزمایشی بدون مجوز' }, 'limited', 403, { probe: 'description-authorization' });
    await req('tracking filtered list', 'GET', '/api/pieces/tracking?projectCode=' + run + '&size=5');
    await req('tracking summary', 'GET', '/api/pieces/tracking/summary?projectCode=' + run);
    await req('definition status report', 'GET', '/api/piece-definitions/' + definition + '/status-report');
    await req('piece invalid UUID rejected', 'GET', '/api/pieces/not-a-uuid', null, 'admin', 400);
    await req('missing piece returns 404', 'GET', '/api/pieces/' + crypto.randomUUID(), null, 'admin', 404);
    await req('archive piece', 'DELETE', '/api/pieces/' + pieces[7], null, 'admin', 204);
    await req('archived piece mutation blocked', 'PUT', '/api/pieces/' + pieces[7], { pieceNumber: 88 }, 'admin', 400);
    await req('restore piece', 'POST', '/api/pieces/' + pieces[7] + '/restore');
    await req('bulk archive', 'POST', '/api/pieces/bulk/archive', { pieceIds: [pieces[6],pieces[7]] });
    await req('bulk restore', 'POST', '/api/pieces/bulk/restore', { pieceIds: [pieces[6],pieces[7]] });
    const p2 = id(await req('create disposable project', 'POST', '/api/projects', { code: run + '_delete', name: 'حذف آزمایشی' }, 'admin', 201)); roots.projects.push(p2);
    const d2 = id(await req('create disposable definition', 'POST', '/api/piece-definitions', { projectId: p2, rowNum: 1, pieceName: 'حذف آزمایشی' }, 'admin', 201));
    await req('delete empty definition', 'DELETE', '/api/piece-definitions/' + d2, null, 'admin', 204);
    await req('deleted definition missing', 'GET', '/api/piece-definitions/' + d2, null, 'admin', 404);
    await req('delete empty project', 'DELETE', '/api/projects/' + p2, null, 'admin', 204);
    await req('deleted project missing', 'GET', '/api/projects/' + p2, null, 'admin', 404);
  });
  await phase('drawing and notes and welding map', async () => {
    await req('upload valid synthetic drawing', 'POST', '/api/piece-definitions/' + definition + '/drawing', upload());
    await req('read synthetic drawing', 'GET', '/api/piece-definitions/' + definition + '/drawing');
    await req('invalid drawing replacement rejected', 'POST', '/api/piece-definitions/' + definition + '/drawing', upload(Buffer.from('invalid'), 'text/plain'), 'admin', 400);
    await req('INTEGRITY prior drawing survives failed replacement', 'GET', '/api/piece-definitions/' + definition + '/drawing', null, 'admin', 200, { probe: 'drawing-rollback' });
    await req('SECURITY forged image MIME should be rejected', 'POST', '/api/piece-definitions/' + definition + '/drawing', upload(Buffer.from('<svg onload="audit">not a raster</svg>'), 'image/png'), 'admin', 400, { probe: 'drawing-type' });
    await req('delete drawing', 'DELETE', '/api/piece-definitions/' + definition + '/drawing');
    await req('deleted drawing absent', 'GET', '/api/piece-definitions/' + definition + '/drawing', null, 'admin', 404);
    const n = id(await req('create note', 'POST', '/api/pieces/' + pieces[0] + '/notes', { content: 'یادداشت <تست> & "بررسی"' }, 'worker', 201));
    await req('read notes', 'GET', '/api/pieces/' + pieces[0] + '/notes', null, 'worker');
    await req('other user note deletion denied', 'DELETE', '/api/notes/' + n, null, 'limited', 403);
    await req('owner note deletion', 'DELETE', '/api/notes/' + n, null, 'worker', 204);
    await req('restore valid drawing for welding map', 'POST', '/api/piece-definitions/' + definition + '/drawing', upload());
    const m = id(await req('create welding mark', 'POST', '/api/pieces/' + pieces[0] + '/welding-marks', { xPercent: 0.2, yPercent: 0.5, defectType: 'F.M', note: 'تست' }, 'worker', 201));
    await req('read piece welding marks', 'GET', '/api/pieces/' + pieces[0] + '/welding-marks');
    await req('read definition welding marks', 'GET', '/api/piece-definitions/' + definition + '/welding-marks');
    await req('welding coordinate validation', 'POST', '/api/pieces/' + pieces[0] + '/welding-marks', { xPercent: 2, yPercent: 0.5, defectType: 'F.M' }, 'worker', 400);
    await req('delete welding mark', 'DELETE', '/api/welding-marks/' + m, null, 'worker', 204);
  });
  await phase('production movement and welding station', async () => {
    await stage(pieces[0], 'ROUGH_MACHINING');
    await req('update process date', 'PATCH', '/api/pieces/' + pieces[0] + '/process-date', { processDate: today });
    await req('update rough substage', 'PATCH', '/api/pieces/' + pieces[0] + '/rough-machining-substage', {roughMachiningSubStage:'INITIAL_CORRECTION'}, 'worker');
    await req('set stage process date', 'PATCH', '/api/pieces/' + pieces[0] + '/stage-process-dates', {stage:'ROUGH_MACHINING',processDate:today}, 'admin', 204);
    await req('set substage process date', 'PATCH', '/api/pieces/' + pieces[0] + '/sub-stage-process-dates', {kind:'ROUGH_MACHINING',subStage:'INITIAL_CORRECTION',processDate:today}, 'admin', 204);
    await req('clear substage process date', 'PATCH', '/api/pieces/' + pieces[0] + '/sub-stage-process-dates', {kind:'ROUGH_MACHINING',subStage:'INITIAL_CORRECTION',processDate:null}, 'admin', 204);
    for(const suffix of ['stage-history','substage-history','stage-process-dates','sub-stage-process-dates','welding-dispatches'])
      await req('production read ' + suffix, 'GET', '/api/pieces/' + pieces[0] + '/' + suffix);
    await req('limited movement denied', 'PATCH', '/api/pieces/' + pieces[0] + '/stage', { newStage: 'READY' }, 'limited', 403);
    await req('send piece to welding station', 'POST', '/api/pieces/' + pieces[0] + '/welding-station', { defectType: 'F.M', notes: 'ارسال آزمایشی', defectIds: [] }, 'worker', 201);
    const dispatches = await req('read open welding dispatch', 'GET', '/api/pieces/' + pieces[0] + '/welding-dispatches');
    const dispatch = dispatches.body.find(d => !d.completedAt && !d.closedAt) || dispatches.body[0];
    await req('update welding dispatch', 'PATCH', '/api/pieces/' + pieces[0] + '/welding-dispatches/' + dispatch.id, { defectType: 'F.M.M', notes: 'ویرایش آزمایشی', defectIds: [] }, 'worker');
    await req('nested welding dispatch ownership checked', 'PATCH', '/api/pieces/' + pieces[1] + '/welding-dispatches/' + dispatch.id, { defectType: 'F.M.M', defectIds: [] }, 'worker', [400,404]);
    await req('complete welding dispatch', 'POST', '/api/pieces/' + pieces[0] + '/welding-station/complete', { destination: 'PREVIOUS' }, 'worker');
    await req('advance requires process date', 'POST', '/api/pieces/' + pieces[0] + '/advance', {}, 'worker', 400);
    await req('advance with date', 'POST', '/api/pieces/' + pieces[0] + '/advance', { processDate: today }, 'worker');
    await req('bulk stage correction', 'PATCH', '/api/pieces/bulk/stage', { pieceIds: [pieces[6],pieces[7]], newStage: 'ROUGH_MACHINING' });
    await req('bulk rough substage', 'PATCH', '/api/pieces/bulk/rough-machining-substage', {pieceIds:[pieces[6],pieces[7]],roughMachiningSubStage:'MACHINING'});
    await req('bulk enter heat stage', 'PATCH', '/api/pieces/bulk/stage', {pieceIds:[pieces[6],pieces[7]],newStage:'HEAT_TREATMENT'});
    await req('bulk heat substage', 'PATCH', '/api/pieces/bulk/heat-treatment-substage', {pieceIds:[pieces[6],pieces[7]],heatTreatmentSubStage:'STAGE_1'});
    await req('bulk enter radiography', 'PATCH', '/api/pieces/bulk/stage', {pieceIds:[pieces[6],pieces[7]],newStage:'RADIOGRAPHY'});
    await req('bulk radiography substage', 'PATCH', '/api/pieces/bulk/radiography-substage', {pieceIds:[pieces[6],pieces[7]],radiographySubStage:'INTERPRETATION'});
    await req('VALIDATION unknown radiography substage rejected', 'PATCH', '/api/pieces/' + pieces[7] + '/radiography-substage', {radiographySubStage:'INVALID_AUDIT'}, 'admin', 400, {probe:'unknown-enum'});
  });
  await phase('radiography templates reports defects and approvals', async () => {
    const p = pieces[1];
    await stage(p, 'RADIOGRAPHY');
    await req('radiography set interpretation', 'PATCH', '/api/pieces/' + p + '/radiography-substage', { radiographySubStage: 'INTERPRETATION' });
    const t = await req('create radiography template', 'POST', '/api/piece-definitions/' + definition + '/radiography-templates', { name: run + '_template', lines: [{ courseCode: 'C1', imageCount: '1', th: '10' }] }, 'admin', 201);
    template = t.body; id(t);
    await req('list radiography templates', 'GET', '/api/piece-definitions/' + definition + '/radiography-templates');
    template = (await req('update unused radiography template', 'PUT', '/api/radiography-templates/' + template.id, { name: run + '_template_edit', lines: template.lines.map(l => ({ id: l.id, courseCode: l.courseCode, imageCount: '2' })) })).body;
    const reportBody = { templateId: template.id, lines: [{ templateLineId: template.lines[0].id, result: 'RS', iqiReal: '10' }] };
    report = (await req('create radiography report', 'PUT', '/api/pieces/' + p + '/radiography-reports', reportBody, 'worker')).body;
    await req('read radiography report', 'GET', '/api/pieces/' + p + '/radiography-reports');
    const dbody = { reportLineId: report.lines[0].id, defectType: 'F.M', welded: false, positions: [{ startCm: 'L4', endCm: 'L8' }] };
    const defect = id(await req('create report defect', 'POST', '/api/pieces/' + p + '/radiography-defects', dbody, 'worker', 201));
    await req('read piece defects', 'GET', '/api/pieces/' + p + '/radiography-defects');
    await req('update report defect', 'PATCH', '/api/radiography-defects/' + defect, { ...dbody, defectType: 'F.M.M' }, 'worker');
    await req('reject unknown defect type', 'PATCH', '/api/radiography-defects/' + defect, { ...dbody, defectType: 'INVALID_AUDIT' }, 'worker', 400);
    await req('defect nested report ownership checked', 'POST', '/api/pieces/' + pieces[2] + '/radiography-defects', dbody, 'worker', 400);
    await req('delete defect', 'DELETE', '/api/radiography-defects/' + defect, null, 'worker', 204);
    workflow = (await req('assign radiography interpreter', 'POST', '/api/radiography/pieces/' + p + '/workflow/assign', { interpreterId: actors.worker.id }, 'reviewer', 201)).body; id({ body: workflow });
    await req('radiography workflow read', 'GET', '/api/radiography/workflows/' + workflow.id, null, 'worker');
    await req('radiography outsider workflow hidden', 'GET', '/api/radiography/workflows/' + workflow.id, null, 'limited', 404);
    await req('radiography queue', 'GET', '/api/radiography/queue?search=' + run, null, 'reviewer');
    await req('radiography interpreter list', 'GET', '/api/radiography/interpreters', null, 'reviewer');
    await req('duplicate workflow denied', 'POST', '/api/radiography/pieces/' + p + '/workflow/assign', { interpreterId: actors.worker.id }, 'reviewer', [400,409]);
    await req('movement blocked by open workflow', 'PATCH', '/api/pieces/' + p + '/stage', { newStage: 'HEAT_TREATMENT' }, 'admin', 400);
    await req('save assigned interpreter report', 'PUT', '/api/pieces/' + p + '/radiography-reports', reportBody, 'worker');
    await req('interpreter signature upload', 'PUT', '/api/profile/signature', upload(), 'worker');
    await req('signature read', 'GET', '/api/profile/signature', null, 'worker');
    await req('signature rejects forged MIME', 'PUT', '/api/profile/signature', upload(Buffer.from('not-an-image')), 'worker', 400);
    workflow = (await req('interpreter approval', 'POST', '/api/radiography/workflows/' + workflow.id + '/interpreter-approve', { confirmed: true }, 'worker')).body;
    await req('report write locked during manager review', 'PUT', '/api/pieces/' + p + '/radiography-reports', reportBody, 'worker', 400);
    const during = await req('INTEGRITY defect creation must respect manager review lock', 'POST', '/api/pieces/' + p + '/radiography-defects', dbody, 'worker', [400,403], { probe: 'radiography-defect-lock' });
    if(during.body.id) await req('INTEGRITY defect update must respect manager review lock', 'PATCH', '/api/radiography-defects/' + during.body.id, {...dbody,defectType:'F.M.L'}, 'worker', [400,403], {probe:'radiography-defect-lock'});
    if(during.body.id) await req('INTEGRITY defect delete must respect manager review lock', 'DELETE', '/api/radiography-defects/' + during.body.id, null, 'worker', [400,403], { probe: 'radiography-defect-lock' });
    await req('manager signature upload', 'PUT', '/api/profile/signature', upload(), 'reviewer');
    workflow = (await req('manager rejection', 'POST', '/api/radiography/workflows/' + workflow.id + '/manager-reject', { reason: 'بازگشت آزمایشی' }, 'reviewer')).body;
    await req('edit after rejection', 'PUT', '/api/pieces/' + p + '/radiography-reports', reportBody, 'worker');
    await req('interpreter reapproval', 'POST', '/api/radiography/workflows/' + workflow.id + '/interpreter-approve', { confirmed: true }, 'worker');
    workflow = (await req('manager final approval', 'POST', '/api/radiography/workflows/' + workflow.id + '/manager-approve', { confirmed: true }, 'reviewer')).body;
    const moved = await req('piece state after radiography approval', 'GET', '/api/pieces/' + p);
    verify('radiography approval moves to VT', moved.body.radiographySubStage === 'VT');
    await req('INTEGRITY completed report direct edit should create controlled correction', 'PUT', '/api/pieces/' + p + '/radiography-reports', { ...reportBody, lines: [{ ...reportBody.lines[0], result: 'ACC', iqiReal: '11' }] }, 'worker', [400,403,409], { probe: 'radiography-final-edit' });
    const completed = await req('read signed workflow versions', 'GET', '/api/radiography/workflows/' + workflow.id, null, 'reviewer');
    const finalVersion=completed.body.versions.find(v=>v.kind==='MANAGER_FINAL');
    const originalVersion=completed.body.versions.find(v=>v.kind==='ORIGINAL');
    const signed=await req('read immutable signed document', 'GET', '/api/radiography/workflows/' + workflow.id + '/versions/' + finalVersion.id + '/document', null, 'reviewer');
    verify('signed snapshot preserves original result after live report edit', signed.body.reports[0].lines[0].result==='RS');
    await req('earlier document hidden from outsider', 'GET', '/api/radiography/workflows/' + workflow.id + '/versions/' + originalVersion.id + '/document', null, 'limited', 404);
    await req('final document globally readable under current contract', 'GET', '/api/radiography/workflows/' + workflow.id + '/versions/' + finalVersion.id + '/document', null, 'limited');
    await req('duplicate final approval rejected', 'POST', '/api/radiography/workflows/' + workflow.id + '/manager-approve', { confirmed: true }, 'reviewer', 400);
    await req('delete interpreter profile signature', 'DELETE', '/api/profile/signature', null, 'worker', 204);
    await req('delete reviewer profile signature', 'DELETE', '/api/profile/signature', null, 'reviewer', 204);
    // A separate draft report/template exercises actual DELETE without damaging signed fixture versions.
    const tmp = (await req('create deletable template', 'POST', '/api/piece-definitions/' + definition + '/radiography-templates', { name: run + '_delete', lines: [{ courseCode: 'D1' }] }, 'admin', 201)).body;
    const dr = (await req('create deletable report', 'PUT', '/api/pieces/' + pieces[5] + '/radiography-reports', { templateId: tmp.id, lines: [{ templateLineId: tmp.lines[0].id, result: 'ACC' }] })).body;
    await req('delete draft report', 'DELETE', '/api/radiography-reports/' + dr.id, null, 'admin', 204);
    await req('delete unused template', 'DELETE', '/api/radiography-templates/' + tmp.id, null, 'admin', 204);
    await req('INTEGRITY direct interpretation to VT without approval is allowed', 'PATCH', '/api/pieces/' + pieces[6] + '/radiography-substage', {radiographySubStage:'VT'}, 'worker', [400,403], {probe:'radiography-optional-workflow'});
    await req('second workflow interpretation setup', 'PATCH', '/api/pieces/' + pieces[7] + '/radiography-substage', {radiographySubStage:'INTERPRETATION'});
    const second=(await req('assign second workflow', 'POST', '/api/radiography/pieces/' + pieces[7] + '/workflow/assign', {interpreterId:actors.worker.id}, 'reviewer', 201)).body;
    await req('reassign open workflow', 'POST', '/api/radiography/workflows/' + second.id + '/reassign', {interpreterId:actors.admin.id}, 'reviewer');
    await req('cancel open workflow', 'POST', '/api/radiography/workflows/' + second.id + '/cancel', {reason:'اتمام آزمون تخصیص'}, 'reviewer');
  });
  await phase('heat treatment CRUD', async () => {
    const p = pieces[2]; await stage(p, 'HEAT_TREATMENT');
    await req('update heat substage', 'PATCH', '/api/pieces/' + p + '/heat-treatment-substage', {heatTreatmentSubStage:'STAGE_1'}, 'worker');
    const body = { batchCode: run + '_B1', treatmentDate: today, temperatureC: 500, durationHours: 2, treatmentStage: 'مرحله تست', testPlateHardnessHb: 90 };
    heat = (await req('create heat record and batch', 'POST', '/api/pieces/' + p + '/heat-treatments', body, 'worker', 201)).body; id({ body: heat });
    if(heat.batchId) roots.batches.push(heat.batchId);
    await req('read heat records', 'GET', '/api/pieces/' + p + '/heat-treatments');
    const batch = await req('lookup exact heat batch', 'GET', '/api/heat-treatment-batches/by-code?code=' + encodeURIComponent(body.batchCode));
    const batchId = batch.body.id || heat.batchId; if(batchId && !roots.batches.includes(batchId)) roots.batches.push(batchId);
    await req('update heat batch', 'PATCH', '/api/heat-treatment-batches/' + batchId, { treatmentDate: today, temperatureC: 510, durationHours: 2.5, notes: 'اصلاح آزمایشی' }, 'worker');
    await req('duplicate heat membership denied', 'POST', '/api/pieces/' + p + '/heat-treatments', body, 'worker', 400);
    await req('negative heat duration rejected', 'POST', '/api/pieces/' + p + '/heat-treatments', { ...body, batchCode: run + '_invalid', durationHours: -1 }, 'worker', 400);
    await req('limited heat creation denied', 'POST', '/api/pieces/' + p + '/heat-treatments', body, 'limited', 403);
    await req('delete heat record', 'DELETE', '/api/heat-treatments/' + heat.id, null, 'worker', 204);
    await req('heat batch today list', 'GET', '/api/heat-treatment-batches');
    await req('heat next batch code', 'GET', '/api/heat-treatment-batches/next-code');
    const remaining = await req('heat record absent after delete', 'GET', '/api/pieces/' + p + '/heat-treatments'); verify('heat deletion persisted', !remaining.body.some(r => r.id === heat.id));
  });
  await phase('tensile CRUD approval correction movement', async () => {
    const p = pieces[3]; await stage(p, 'TENSILE');
    const passing = { samples: [{ sampleLabel: 'C1-1', uts: 120, ys: 60, el: 6, hb: 90 }] };
    const failing = { samples: [{ sampleLabel: 'C1-1', uts: 20, ys: 10, el: 1, hb: 20 }] };
    await req('limited tensile entry denied', 'POST', '/api/pieces/' + p + '/tensile-tests', passing, 'limited', 403);
    let test = (await req('create passing tensile test', 'POST', '/api/pieces/' + p + '/tensile-tests', passing, 'worker', 201)).body; id({body:test});
    verify('passing test auto approved', test.status === 'APPROVED');
    const moved = await req('piece after tensile approval', 'GET', '/api/pieces/' + p); verify('passing test advances piece', moved.body.currentStage === 'SHOT_BLASTING');
    await req('read tensile tests', 'GET', '/api/pieces/' + p + '/tensile-tests');
    test = (await req('correct approved tensile sample to failing', 'PUT', '/api/tensile-tests/' + test.id, failing, 'worker')).body;
    verify('INTEGRITY failing correction must invalidate prior approval', test.status !== 'APPROVED', { probe: 'tensile-correction', observedStatus: test.status });
    await stage(p, 'READY');
    await req('INTEGRITY old tensile disposition must not move downstream piece again', 'PUT', '/api/tensile-tests/' + test.id + '/disposition', { dispositionDate: today, nextStage: 'RE_OPERATION', inspectionResult: 'REJECTED', notes: 'آزمون اصلاح تست قدیمی' }, 'reviewer', [400,409], { probe: 'tensile-old-disposition' });
    const after = await req('piece after old test disposition', 'GET', '/api/pieces/' + p);
    verify('INTEGRITY downstream stage preserved by historical decision', after.body.currentStage === 'READY', { probe: 'tensile-old-disposition', observedStage: after.body.currentStage });
    const p2 = pieces[4]; await stage(p2, 'TENSILE');
    const pending = (await req('create failing pending tensile test', 'POST', '/api/pieces/' + p2 + '/tensile-tests', failing, 'worker', 201)).body;
    await req('update pending tensile test', 'PUT', '/api/tensile-tests/' + pending.id, { samples: [{ sampleLabel: 'C1-2', uts: 30, ys: 10, el: 1 }] }, 'worker');
    await req('negative tensile value rejected', 'PUT', '/api/tensile-tests/' + pending.id, { samples: [{ uts: -1 }] }, 'worker', 400);
    await req('delete undecided tensile test', 'DELETE', '/api/tensile-tests/' + pending.id, null, 'worker', 204);
    await req('pending tensile queue', 'GET', '/api/tensile-tests/pending-review?projectId=' + project, null, 'reviewer');
  });
  await phase('tasks comments checklist notifications CRUD', async () => {
    const body = { title: 'وظیفه آزمایشی ' + run, description: '<b>متن امن آزمایشی</b>', assigneeId: actors.worker.id, priority: 'MEDIUM', projectId: project, partId: pieces[0] };
    task = id(await req('create task', 'POST', '/api/tasks', body, 'admin', 201));
    otherTask = id(await req('create second task', 'POST', '/api/tasks', { ...body, title: 'دوم ' + run }, 'admin', 201));
    await req('list tasks', 'GET', '/api/tasks?q=' + run);
    await req('read assigned task', 'GET', '/api/tasks/' + task, null, 'worker');
    await req('task outsider hidden', 'GET', '/api/tasks/' + task, null, 'limited', 404);
    await req('task outsider comments hidden', 'GET', '/api/tasks/' + task + '/comments', null, 'limited', 404);
    await req('update task', 'PUT', '/api/tasks/' + task, { ...body, title: 'وظیفه ویرایش شده ' + run });
    await req('change task priority', 'PATCH', '/api/tasks/' + task + '/priority', {priority:'HIGH'});
    await req('change task deadline', 'PATCH', '/api/tasks/' + task + '/deadline', {dueDate:'2026-11-15T10:00:00'});
    await req('change task assignee', 'PATCH', '/api/tasks/' + task + '/assignee', {assigneeId:actors.worker.id});
    await req('task assignee lookup', 'GET', '/api/tasks/assignees');
    await req('task part lookup', 'GET', '/api/tasks/lookup/parts?projectId='+project);
    await req('profile task list', 'GET', '/api/users/'+actors.worker.id+'/tasks', null, 'worker');
    await req('assignee cannot edit managerial task fields', 'PUT', '/api/tasks/' + task, body, 'worker', 403);
    const comment = id(await req('create comment', 'POST', '/api/tasks/' + task + '/comments', { content: 'نظر <آزمایشی>' }, 'worker', 201));
    await req('read comments', 'GET', '/api/tasks/' + task + '/comments', null, 'worker');
    await req('edit own comment', 'PUT', '/api/tasks/' + task + '/comments/' + comment, { content: 'نظر ویرایش شده' }, 'worker');
    await req('cross task comment ownership checked', 'PUT', '/api/tasks/' + otherTask + '/comments/' + comment, { content: 'ممنوع' }, 'worker', 404);
    await req('delete own comment', 'DELETE', '/api/tasks/' + task + '/comments/' + comment, null, 'worker', 204);
    const checklist = (await req('create checklist item', 'POST', '/api/tasks/' + task + '/checklist', { title: 'آیتم تست' }, 'admin', 201)).body;
    const item = checklist.items[0].id;
    await req('read checklist', 'GET', '/api/tasks/' + task + '/checklist', null, 'worker');
    await req('toggle checklist item', 'PATCH', '/api/tasks/' + task + '/checklist/' + item, { completed: true }, 'worker');
    await req('cross task checklist ownership checked', 'PATCH', '/api/tasks/' + otherTask + '/checklist/' + item, { completed: false }, 'worker', 404);
    await req('delete checklist item', 'DELETE', '/api/tasks/' + task + '/checklist/' + item);
    await req('start task', 'PATCH', '/api/tasks/' + task + '/status', { status: 'IN_PROGRESS' }, 'worker');
    await req('complete task', 'PATCH', '/api/tasks/' + task + '/status', { status: 'COMPLETED' }, 'worker');
    await req('read task activities', 'GET', '/api/tasks/' + task + '/activities', null, 'worker');
    await req('cancel task', 'DELETE', '/api/tasks/' + otherTask + '?reason=audit');
    const feed = await req('read own notifications', 'GET', '/api/notifications?limit=100', null, 'worker');
    const notification = feed.body.items?.find(i => i.taskId === task || i.taskId === otherTask);
    if(notification) {
      await req('other user notification hidden', 'POST', '/api/notifications/' + notification.id + '/read', null, 'limited', 404);
      await req('mark notification read', 'POST', '/api/notifications/' + notification.id + '/read', null, 'worker', [200,204]);
      await req('dismiss notification', 'DELETE', '/api/notifications/' + notification.id, null, 'worker', [200,204]);
    }
    await req('notification unread count', 'GET', '/api/notifications/unread-count', null, 'worker');
    await req('mark own notifications read', 'POST', '/api/notifications/read-all', null, 'worker', [200,204]);
    await req('user with task history cannot be deleted', 'DELETE', '/api/users/' + actors.worker.id, null, 'admin', 409);
  });
  await phase('employee performance CRUD and privacy', async () => {
    const root = '/api/users/' + actors.worker.id + '/performance/periods';
    let period = (await req('create evaluation period', 'POST', root, { title: 'دوره آزمایشی ' + run })).body; id({body:period});
    await req('read own evaluation periods', 'GET', root, null, 'worker');
    await req('evaluation outsider hidden', 'GET', root, null, 'limited', 404);
    await req('task manager cannot see private evaluation', 'GET', root, null, 'reviewer', 404);
    await req('owner cannot create evaluation criteria', 'POST', root + '/' + period.id + '/criteria', { version: period.version, title: 'ممنوع', maximumScore: 10, weight: 1, score: 9 }, 'worker', 403);
    period = (await req('create evaluation criterion', 'POST', root + '/' + period.id + '/criteria', { version: period.version, title: 'کیفیت آزمایشی', maximumScore: 10, weight: 2, score: 10 })).body;
    const criterion = period.criteria[0].id;
    const stale = period.version;
    period = (await req('update evaluation criterion', 'PATCH', root + '/' + period.id + '/criteria/' + criterion, { version: period.version, title: 'کیفیت ویرایش شده', maximumScore: 10, weight: 2, score: 9, reason: 'اصلاح آزمایشی نمره' })).body;
    await req('stale evaluation version rejected', 'PATCH', root + '/' + period.id + '/criteria/' + criterion, { version: stale, title: 'کهنه', maximumScore: 10, weight: 2, score: 1 }, 'admin', 409);
    await req('score beyond maximum rejected', 'PATCH', root + '/' + period.id + '/criteria/' + criterion, { version: period.version, title: 'نامعتبر', maximumScore: 10, weight: 2, score: 11 }, 'admin', 400);
    period = (await req('delete evaluation criterion', 'DELETE', root + '/' + period.id + '/criteria/' + criterion, { version: period.version })).body;
    await req('evaluation audit read', 'GET', root + '/' + period.id + '/events');
    await req('start next evaluation period', 'POST', root + '/' + period.id + '/next', { version: period.version, title: 'دوره دوم آزمایشی' });
  });
  await phase('melt materials passports assignment and approvals', async () => {
    const p = pieces[5]; await stage(p, 'CASTING');
    material = (await req('create melt material', 'POST', '/api/melt-passports/materials', { name: run + '_material', category: 'آزمایش' })).body; id({body:material}); roots.materials.push(material.id);
    await req('read melt materials', 'GET', '/api/melt-passports/materials', null, 'worker');
    await req('deactivate material', 'PATCH', '/api/melt-passports/materials/' + material.id + '/active?active=false');
    await req('reactivate material', 'PATCH', '/api/melt-passports/materials/' + material.id + '/active?active=true');
    const opMaterial = await req('operator can add material under current contract', 'POST', '/api/melt-passports/materials', { name: run + '_operator_material', category: 'آزمایش' }, 'worker');
    roots.materials.push(id(opMaterial));
    await req('operator material deactivation denied', 'PATCH', '/api/melt-passports/materials/' + material.id + '/active?active=false', null, 'worker', 403);
    const kinds = ['LANCE_SEAL','LANCE_HEAD','SMALL_SCALE','RPT_DEVICE','MN_TI_FLANGE','LAMP_CABLE'];
    const body = { version: 0, pieceIds: [p], meltDate: today, furnaceNumber: 3, ingotType: 'آلیاژ آزمایشی', primaryWeight: 70, recycledWeight: 30, injectionOperator: 'اپراتور آزمایشی', materials: [], checks: kinds.map(kind => ({ kind, status: 'ACCEPTED' })), magnesium:{mg:0.1,rotary:2,at610:3,ar:4,b:0.5,w:10} };
    let melt = (await req('assign melt entry to operator', 'POST', '/api/melt-passports/piece/' + p + '/assignment', { assigneeId: actors.worker.id })).body; id({body:melt});
    await req('read assigned melt passport', 'GET', '/api/melt-passports/' + melt.id, null, 'worker');
    await req('limited user cannot edit assigned passport', 'PUT', '/api/melt-passports/' + melt.id, { ...body, version: melt.version }, 'limited', [400,403]);
    melt = (await req('update melt passport draft', 'PUT', '/api/melt-passports/' + melt.id, { ...body, version: melt.version }, 'worker')).body;
    await req('stale melt version rejected', 'PUT', '/api/melt-passports/' + melt.id, { ...body, version: 0 }, 'worker', 409);
    const competing=await Promise.all([
      req('concurrent melt update A','PUT','/api/melt-passports/'+melt.id,{...body,version:melt.version,notes:'تست همزمان الف'},'worker',[200,409]),
      req('concurrent melt update B','PUT','/api/melt-passports/'+melt.id,{...body,version:melt.version,notes:'تست همزمان ب'},'worker',[200,409])
    ]);
    verify('concurrent melt writers yield one success and one conflict',competing.filter(r=>r.actual===200).length===1&&competing.filter(r=>r.actual===409).length===1);
    melt=(await req('refresh melt after concurrency','GET','/api/melt-passports/'+melt.id,null,'worker')).body;
    melt=(await req('calculate magnesium','POST','/api/melt-passports/'+melt.id+'/calculate-magnesium',{version:melt.version},'worker')).body;
    verify('magnesium calculation matches exact formula',Number(melt.magnesiumResult)===453.2);
    await req('read piece melt history', 'GET', '/api/melt-passports/piece/' + p, null, 'worker');
    await req('melt dashboard requires permission', 'GET', '/api/melt-passports', null, 'limited', 403);
    await req('melt candidate lookup', 'GET', '/api/melt-passports/candidates?q=' + run, null, 'worker');
    melt = (await req('submit melt passport', 'POST', '/api/melt-passports/' + melt.id + '/submit', { version: melt.version }, 'worker')).body;
    await req('nonreviewer melt approval denied', 'POST', '/api/melt-passports/' + melt.id + '/approve', { version: melt.version }, 'worker', 403);
    melt = (await req('return melt passport', 'POST', '/api/melt-passports/' + melt.id + '/return', { version: melt.version, reason: 'اصلاح آزمایشی' }, 'reviewer')).body;
    melt = (await req('resubmit melt passport', 'POST', '/api/melt-passports/' + melt.id + '/submit', { version: melt.version }, 'worker')).body;
    melt = (await req('approve melt passport', 'POST', '/api/melt-passports/' + melt.id + '/approve', { version: melt.version }, 'reviewer')).body;
    const moved = await req('piece state after melt approval', 'GET', '/api/pieces/' + p);
    verify('melt approval advances casting member', moved.body.currentStage === 'ROUGH_MACHINING');
    await req('approved melt ordinary edit blocked', 'PUT', '/api/melt-passports/' + melt.id, { ...body, version: melt.version }, 'worker', [400,403]);
    await req('duplicate melt approval denied', 'POST', '/api/melt-passports/' + melt.id + '/approve', { version: melt.version }, 'reviewer', [400,409]);
    const approved=(await req('admin approved melt correction','POST','/api/melt-passports/'+melt.id+'/corrections',{data:{...body,version:melt.version,notes:'اصلاح مصوب آزمایشی'},reason:'تست اصلاح اطلاعات',calculateMagnesium:false})).body;
    verify('approved correction retains approval',approved.status==='APPROVED');
    await req('melt consumption report', 'GET', '/api/melt-passports/consumption', null, 'reviewer');
  });
  await phase('dashboard reports access', async () => {
    for(const suffix of ['stats','alerts','overview','radiography-defect-report','stage-occupancy-report','welding-station-report','tensile-quality-report','tensile-trend-report']) {
      const query = '?projectId=' + project + '&dateFrom=2026-10-01&dateTo=2026-10-30';
      await req('dashboard ' + suffix, 'GET', '/api/dashboard/' + suffix + query, null, 'reviewer');
      await req('dashboard permission denied ' + suffix, 'GET', '/api/dashboard/' + suffix + query, null, 'limited', 403);
    }
    await req('VALIDATION missing required heat lookup parameter returns 400', 'GET', '/api/heat-treatment-batches/by-code', null, 'admin', 400, {probe:'missing-parameter'});
    await req('VALIDATION missing required report dates return 400', 'GET', '/api/dashboard/stage-occupancy-report', null, 'admin', 400, {probe:'missing-parameter'});
  });
  await phase('archive and failed deletion consistency', async () => {
    const p=pieces[7];
    const defectBody={course:'C-AUDIT',defectType:'F.M',leadsToWelding:true,startCm:'L1'};
    const d1=id(await req('create standalone welding defect','POST','/api/pieces/'+p+'/radiography-defects',defectBody,'worker',201));
    const d2=id(await req('create second standalone defect','POST','/api/pieces/'+p+'/radiography-defects',defectBody,'worker',201));
    await req('archive inspection fixture','DELETE','/api/pieces/'+p,null,'admin',204);
    await req('archived piece new defect blocked','POST','/api/pieces/'+p+'/radiography-defects',defectBody,'worker',400);
    await req('INTEGRITY archived defect welding confirmation blocked','PATCH','/api/radiography-defects/'+d1+'/confirm-welding',null,'worker',400,{probe:'archive-defect'});
    await req('INTEGRITY archived defect deletion blocked','DELETE','/api/radiography-defects/'+d2,null,'worker',400,{probe:'archive-defect'});
    await req('restore inspection fixture','POST','/api/pieces/'+p+'/restore');
    const d=id(await req('create deletion-conflict definition','POST','/api/piece-definitions',{projectId:project,rowNum:99,pieceName:'آزمایش بازگشت فایل'},'admin',201));
    const pc=id(await req('create deletion-conflict piece','POST','/api/pieces',{pieceDefinitionId:d,pieceNumber:1},'admin',201));
    await req('create task retaining definition','POST','/api/tasks',{title:'حفظ سابقه آزمایشی',assigneeId:actors.worker.id,priority:'MEDIUM',partId:pc,projectId:project},'admin',201);
    await stage(pc,'CASTING');
    await req('create draft passport retaining piece','POST','/api/melt-passports',{version:0,pieceIds:[pc],materials:[],checks:[]},'admin',201);
    await req('upload drawing before failed deletion','POST','/api/piece-definitions/'+d+'/drawing',upload());
    await req('definition deletion blocked by melt membership','DELETE','/api/piece-definitions/'+d,null,'admin',409);
    await req('definition DB row survives rollback','GET','/api/piece-definitions/'+d);
    await req('INTEGRITY drawing must survive failed definition deletion','GET','/api/piece-definitions/'+d+'/drawing',null,'admin',200,{probe:'drawing-delete-rollback'});
  });
  if(process.argv.includes('--browser')) await phase('real Chrome live database browser audit', async () => {
    const {chromium}=require('C:/Users/Asus/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
    const browser=await chromium.launch({channel:'chrome',headless:true});
    const errors=[];
    try {
      const context=await browser.newContext({viewport:{width:1440,height:1000}});
      const page=await context.newPage();page.on('pageerror',e=>errors.push(e.name+': '+e.message.slice(0,100)));
      page.setDefaultTimeout(12000);
      await page.goto(base);
      await page.locator('#input-login-username').fill(actors.admin.username);
      await page.locator('#input-login-password').fill(password);
      await page.locator('#form-login button[type=submit]').click();
      await page.locator('#app-shell').waitFor({state:'visible'});
      verify('browser real login succeeds',await page.locator('#app-shell').isVisible());
      verify('browser Persian RTL document',await page.evaluate(()=>document.documentElement.dir==='rtl'&&document.documentElement.lang==='fa'));
      await page.locator('.nav-btn[data-section="projects"]').click();
      await page.locator('#btn-add-project').click();
      await page.locator('#input-project-code').fill(run+'_browser');
      await page.locator('#input-project-name').fill('پروژه ایجاد شده از مرورگر');
      await page.locator('#form-add-project button[type=submit]').click();
      await page.locator('#form-add-project').waitFor({state:'detached'});
      const uiProject=await page.evaluate(async code=>(await ProjectAPI.getAll()).find(p=>p.code===code).id,run+'_browser');
      roots.projects.push(uiProject);persist();
      const row=page.locator('tr[data-project-id="'+uiProject+'"]');await row.waitFor();
      await row.locator('.btn-edit-project').click();
      await page.locator('#input-edit-project-name').fill('پروژه ویرایش شده از مرورگر');
      await page.locator('#form-edit-project button[type=submit]').click();
      await page.locator('#form-edit-project').waitFor({state:'detached'});
      await page.waitForFunction(id=>document.querySelector('tr[data-project-id="'+id+'"]')?.textContent.includes('ویرایش شده از مرورگر'),uiProject);
      verify('browser project create/read/update',true);
      page.once('dialog',dialog=>dialog.accept());await row.locator('.btn-delete-project').click();
      await row.waitFor({state:'detached'});verify('browser project delete',true);
      // Harmless execution sentinels: no token read, external fetch, or user data access.
      const projectPayload='"><img id="audit-xss-project" src="/audit-missing-image" onerror="window.__auditProjectXss=1"><input value="';
      const xp=id(await req('catalog operator creates stored project XSS fixture','POST','/api/projects',{code:run+'_xss',name:projectPayload},'catalog',201));
      roots.projects.push(xp);persist();
      const definitionPayload='"><img id="audit-xss-definition" src="/audit-missing-image" onerror="window.__auditDefinitionXss=1"><input value="';
      const xd=id(await req('catalog operator creates stored definition XSS fixture','POST','/api/piece-definitions',{projectId:xp,rowNum:1,pieceName:definitionPayload},'catalog',201));
      await page.locator('.nav-btn[data-section="projects"]').click();
      await page.locator('tr[data-project-id="'+xp+'"] .btn-edit-project').click();
      await page.waitForTimeout(300);
      verify('SECURITY stored project name must not execute on admin edit',!await page.evaluate(()=>window.__auditProjectXss),{probe:'stored-xss-project'});
      await page.locator('#modal-close').click();
      await page.locator('tr[data-project-id="'+xp+'"]').click();
      await page.locator('tr[data-definition-id="'+xd+'"] .btn-edit-definition').click();
      await page.waitForTimeout(300);
      verify('SECURITY stored piece name must not execute on admin edit',!await page.evaluate(()=>window.__auditDefinitionXss),{probe:'stored-xss-definition'});
      await page.locator('#modal-close').click();
      await page.evaluate(id=>openDefinitionDetail(id),definition);
      await page.locator('#pieces-table-body tr[data-piece-id]').first().waitFor();
      await page.screenshot({path:path.join(out,'live-desktop-synthetic.png'),fullPage:true});
      await page.setViewportSize({width:390,height:844});
      await page.screenshot({path:path.join(out,'live-mobile-synthetic.png'),fullPage:true});
      verify('browser mobile document contained',await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+2));
      await page.evaluate(id=>openPieceProfile(id),pieces[0]);
      await page.locator('#modal-overlay.active').waitFor();
      verify('browser mobile piece profile opens',await page.locator('#modal-overlay').isVisible());
      await page.screenshot({path:path.join(out,'live-mobile-profile-synthetic.png'),fullPage:true});
      await page.locator('#modal-close').click();
      await page.locator('#btn-logout').click();await page.locator('#login-screen').waitFor({state:'visible'});
      verify('browser logout clears session',await page.evaluate(()=>!localStorage.getItem(AUTH_TOKEN_KEY)));
      // The normal CSV quoting routine currently leaves formula prefixes intact.
      const csv=await page.evaluate(()=>csvEscape('=1+1'));
      verify('SECURITY CSV output neutralizes spreadsheet formula',!/^"?[=+@]/.test(csv),{probe:'csv-formula'});
      verify('browser has no uncaught JavaScript errors',errors.length===0,{errors});
    } finally {await browser.close();}
  });
  persist(); console.log(JSON.stringify({ run, total: checks.length, passed: checks.filter(c => c.result === 'PASS').length, failed: checks.filter(c => c.result === 'FAIL').length, errors: checks.filter(c => c.result === 'ERROR').length }));
}
main().catch(e => { console.error('Audit stopped: ' + e.name + ': ' + String(e.message).slice(0,100)); persist(); process.exitCode = 1; });
