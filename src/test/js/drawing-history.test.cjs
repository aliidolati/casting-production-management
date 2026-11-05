const test=require('node:test');const assert=require('node:assert/strict');const vm=require('node:vm');const fs=require('node:fs');
function setup(){const els={};let html='',deleted;const el=id=>els[id]??={style:{},value:'',addEventListener(){}};
 const c={document:{getElementById:el,querySelectorAll:()=>[]},escapeHtml:s=>String(s).replaceAll('<','&lt;'),openModal:(title,body)=>{html=body;},closeModal(){},showToast(){},formatDateTime:s=>s,hasPermission:()=>false,
 ProjectAPI:{deletionPreview:async()=>({label:'<project>',canDelete:true,confirmationToken:'reviewed-token',impacts:[{label:'<pieces>',count:2,blocksDeletion:false}]}),delete:async(id,token)=>{deleted={id,token};}},PieceDefinitionAPI:{}};
 vm.createContext(c);vm.runInContext(fs.readFileSync('src/main/resources/static/js/drawing-history.js','utf8'),c);return {c,el,html:()=>html,deleted:()=>deleted};}
test('drawing filters keep points attached to their original revision',()=>{
 const x=setup();const marks=[{id:1,drawingRevisionId:'old'},{id:2,drawingRevisionId:'current'},{id:3}];assert.deepEqual(Array.from(x.c.marksForDrawingRevision(marks,'current'),m=>m.id),[2]);assert.deepEqual(Array.from(x.c.marksForDrawingRevision(marks,null),m=>m.id),[3]);
});
test('deletion preview escapes dependency labels and sends only its reviewed confirmation token',async()=>{
 const x=setup();await x.c.openCatalogDeletionPreview('projects','p',async()=>{});assert.ok(x.html().includes('&lt;project>'));assert.ok(x.html().includes('&lt;pieces>'));assert.equal(x.deleted(),undefined);
 await x.el('catalog-delete-confirm').onsubmit({preventDefault(){},submitter:{}});assert.deepEqual(x.deleted(),{id:'p',token:'reviewed-token'});
});
test('blocked preview cannot issue delete even when its handler is called directly',async()=>{
 const x=setup();x.c.ProjectAPI.deletionPreview=async()=>({label:'blocked',canDelete:false,confirmationToken:'x',impacts:[]});await x.c.openCatalogDeletionPreview('projects','p',async()=>{});await x.el('catalog-delete-confirm').onsubmit({preventDefault(){}});assert.equal(x.deleted(),undefined);
});
