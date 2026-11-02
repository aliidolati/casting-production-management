const test=require('node:test');const assert=require('node:assert/strict');const vm=require('node:vm');const fs=require('node:fs');
function setup(){const els={};let html='',saved,updates=[];const el=id=>els[id]??={style:{},value:'',checked:false,addEventListener(){}};
 const b={id:'batch',version:2,batchCode:'B1',treatmentDate:'2026-10-02',pieces:[{pieceId:'p1',pieceName:'<piece>',pieceNumber:1}]};
 const c={document:{getElementById:el},openModal:(title,body)=>{html=body;},openNestedModal:(title,body)=>{html=body;},closeNestedModal(){},showToast(){},escapeHtml:s=>String(s).replaceAll('<','&lt;'),formatDate:s=>s,formatDateTime:s=>s,pieceRefLabel:p=>String(p.pieceNumber),FURNACE_SIZE_LABELS:{},OperationAPI:{batch:async()=>b},HeatTreatmentAPI:{updateBatch:async(id,body)=>{updates.push(body);throw Error('conflict');}}};vm.createContext(c);vm.runInContext(fs.readFileSync('src/main/resources/static/js/operational-controls.js','utf8'),c);
 return {c,el,html:()=>html,updates,b,onSave:async reason=>{saved=reason;},saved:()=>saved};}
test('welding cancellation requires explicit no-work confirmation and keeps reason on failure',async()=>{
 const x=setup();x.c.openOperationCancellation('cancel',true,x.onSave);x.el('operation-cancel-reason').value='reason';await x.el('operation-cancel-form').onsubmit({preventDefault(){}});assert.equal(x.saved(),undefined);
 x.el('operation-no-work').checked=true;await x.el('operation-cancel-form').onsubmit({preventDefault(){}});assert.equal(x.saved(),'reason');
});
test('batch preview shows every affected member and submits reviewed IDs with version and reason',async()=>{
 const x=setup();await x.c.previewHeatBatchChange('batch',{treatmentDate:'2026-11-02',temperatureC:165},2);assert.ok(x.html().includes('&lt;piece>'));assert.ok(!x.html().includes('<piece>'));
 x.el('batch-confirm-reason').value='cycle correction';await x.el('batch-confirm-form').onsubmit({preventDefault(){},submitter:{}});
 const body=JSON.parse(JSON.stringify(x.updates[0]));assert.equal(body.version,2);assert.deepEqual(body.confirmedPieceIds,['p1']);assert.equal(body.reason,'cycle correction');assert.equal(x.el('batch-confirm-error').textContent,'conflict');
});
test('stale cycle cannot proceed to confirmation or submit an update',async()=>{
 const x=setup();await x.c.previewHeatBatchChange('batch',{treatmentDate:'2026-11-02'},1);assert.equal(x.html(),'');assert.equal(x.updates.length,0);
});

test('batch-only editors can reach the archive without per-piece heat-entry permission',async()=>{
 let archiveOpened=false,hidden=true;const c={hasPermission:p=>p==='EDIT_HEAT_TREATMENT_BATCH',document:{addEventListener(){},getElementById:()=>({classList:{toggle:(name,value)=>{hidden=value;}}})},showToast:()=>{throw Error('unexpected rejection');},openHeatBatchArchive:async()=>{archiveOpened=true;}};
 vm.createContext(c);vm.runInContext(fs.readFileSync('src/main/resources/static/js/heat-treatment-log.js','utf8'),c);c.syncDashboardFurnaceShortcut();assert.equal(hidden,false);await c.openFurnaceDashboardModal();assert.equal(archiveOpened,true);
});
