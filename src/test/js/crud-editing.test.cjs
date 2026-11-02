const test=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const vm=require('node:vm');
function setup(){const els={};let html='',sent,done=0;
 const element=id=>els[id]??=( {value:'',style:{},addEventListener(type,fn){this[type]=fn;},querySelector:element});
 const ctx={document:{getElementById:element},openModal:(title,body)=>{html=body;},escapeHtml:s=>String(s).replaceAll('<','&lt;').replaceAll('"','&quot;'),showToast:()=>{},formatDateTime:s=>s,
 RecordEditAPI:{update:async(kind,id,body)=>{sent={kind,id,body};},history:async()=>({items:[{actorUsername:'<user>',occurredAt:'now',beforeState:'<script>',afterState:'after'}],totalPages:1})}};
 vm.createContext(ctx);vm.runInContext(fs.readFileSync('src/main/resources/static/js/record-edit.js','utf8'),ctx);
 return {ctx,els,element,html:()=>html,sent:()=>sent,done:()=>done,onDone:()=>{done++;}};
}
test('correction form escapes stored content and sends only selected fields without moving a piece',async()=>{
 const x=setup();x.ctx.openRecordEdit('notes',{id:'one',content:'<script>',currentStage:'CASTING'},[{key:'content',label:'متن',required:true}],x.onDone);
 assert.ok(x.html().includes('&lt;script>'));assert.ok(!x.html().includes('<script>'));
 x.element('edit-content').value='new note';await x.element('record-edit-form').submit({preventDefault(){},submitter:{}});
 assert.deepEqual(JSON.parse(JSON.stringify(x.sent())),{kind:'notes',id:'one',body:{content:'new note'}});assert.equal(x.done(),1);
});
test('invalid numeric correction is rejected and keeps the form open',async()=>{
 const x=setup();x.ctx.openRecordEdit('heat-treatments',{id:'one'},[{key:'testPlateHardnessHb',label:'HB',number:true}],x.onDone);
 x.element('edit-testPlateHardnessHb').value='oops';const button={};await x.element('record-edit-form').submit({preventDefault(){},submitter:button});
 assert.equal(x.sent(),undefined);assert.equal(x.done(),0);assert.equal(button.disabled,false);assert.match(x.element('record-edit-error').textContent,/عددی/);
});
test('history escapes actor and snapshot values',async()=>{
 const x=setup();await x.ctx.loadRecordEditHistory('notes','one',0);assert.ok(x.element('record-edit-history-list').innerHTML.includes('&lt;script>'));assert.ok(!x.element('record-edit-history-list').innerHTML.includes('<user>'));
});
