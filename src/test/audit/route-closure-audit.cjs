'use strict';
// Run after local-api-audit.cjs and before cleaning its fresh synthetic fixture roots.
// Reads the current local test key only in memory, and targets the audit account only.
const fs=require('node:fs'),crypto=require('node:crypto');
if(!process.argv.includes('--authorized-local-test-db'))throw Error('Explicit authorization required');
const roots=JSON.parse(fs.readFileSync('target/audit/fixture-roots.json','utf8'));
if(!/^AUDIT_20261030_[a-z0-9]+$/.test(roots.run))throw Error('Audit namespace required');
const secret=fs.readFileSync('src/main/resources/application.properties','utf8').match(/^app\.jwt\.secret=(.*)$/m)[1].trim();
const checks=process.argv.includes('--read-only')?JSON.parse(fs.readFileSync('target/audit/route-closure-results.json','utf8')).checks:[];
function token(suffix){const h=Buffer.from(JSON.stringify({alg:'HS256',typ:'JWT'})).toString('base64url');const now=Math.floor(Date.now()/1000);const b=Buffer.from(JSON.stringify({sub:roots.run+'_'+suffix,iat:now,exp:now+300})).toString('base64url');return h+'.'+b+'.'+crypto.createHmac('sha256',secret).update(h+'.'+b).digest('base64url');}
async function req(name,method,route,body,expected=200,actor='admin'){
 const headers={Authorization:'Bearer '+token(actor)};if(body)headers['Content-Type']='application/json';
 const r=await fetch('http://127.0.0.1:8080'+route,{method,headers,body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(15000)});
 const text=await r.text();let data;try{data=JSON.parse(text)}catch{data={}};
 checks.push({name,method,route,expected,actual:r.status,result:r.status===expected?'PASS':'FAIL'});
 if(r.status!==expected)throw Error('Unexpected response in '+name+': '+r.status);
 return data;
}
(async()=>{
 try{
  await req('read project catalogue','GET','/api/projects');
  await req('read all active pieces','GET','/api/pieces/all');
  await req('read melt passport report list','GET','/api/melt-passports');
  if(process.argv.includes('--read-only')){console.log('Three remaining read routes passed');return;}
  const project=roots.projects[0];
  const definitions=await req('lookup audit definitions','GET','/api/piece-definitions?projectId='+project);
  const definition=definitions.find(d=>d.rowNum===1),blocked=definitions.find(d=>d.rowNum===99);
  const pieces=await req('read pieces by definition','GET','/api/pieces?pieceDefinitionId='+definition.id);
  const p=pieces.find(p=>p.pieceNumber===11);
  await req('read piece by reference','GET','/api/pieces/by-ref?code='+encodeURIComponent(roots.run+'-1-11'));
  await req('legacy piece search','GET','/api/pieces/search?q='+roots.run);
  await req('tensile allowed sample labels','GET','/api/tensile-tests/sample-labels');
  await req('application local melt date','GET','/api/melt-passports/today');
  await req('ingot type lookup','GET','/api/melt-passports/ingot-types');
  const workerId=roots.users[2];
  const periods=await req('lookup own audit periods','GET','/api/users/'+workerId+'/performance/periods');
  await req('read evaluation period detail','GET','/api/users/'+workerId+'/performance/periods/'+periods.items[0].id);
  const radio=pieces.find(p=>p.pieceNumber===2);
  await req('piece workflow state','GET','/api/radiography/pieces/'+radio.id+'/workflow');
  const histories=await req('read audit stage histories','GET','/api/pieces/'+p.id+'/stage-history');
  await req('limited stage history delete denied','DELETE','/api/pieces/'+p.id+'/stage-history/'+histories[0].id,null,403,'limited');
  await req('admin deletes synthetic stage history','DELETE','/api/pieces/'+p.id+'/stage-history/'+histories[0].id,null,204);
  const sub=await req('read audit substage histories','GET','/api/pieces/'+p.id+'/substage-history');
  await req('admin deletes synthetic substage history','DELETE','/api/pieces/'+p.id+'/substage-history/'+sub[0].id,null,204);
  const bp=await req('read retained audit piece','GET','/api/pieces?pieceDefinitionId='+blocked.id);
  const melts=await req('read retained audit melt','GET','/api/melt-passports/piece/'+bp[0].id);
  const melt=melts[0];
  await req('assign existing passport to another operator','POST','/api/melt-passports/'+melt.id+'/assignment',{assigneeId:workerId,version:melt.version});
  await req('archive all pieces in definition','POST','/api/piece-definitions/'+definition.id+'/archive-pieces');
  await req('restore archived definition fixture','POST','/api/pieces/bulk/restore',{pieceIds:pieces.map(p=>p.id)});
  await req('archive all pieces in project','POST','/api/projects/'+project+'/archive-pieces');
  await req('restore archived project fixtures','POST','/api/pieces/bulk/restore',{pieceIds:[...pieces,...bp].map(p=>p.id)});
 }finally{fs.writeFileSync('target/audit/route-closure-results.json',JSON.stringify({run:roots.run,checks},null,2));}
 console.log(JSON.stringify({checks:checks.length,passed:checks.filter(c=>c.result==='PASS').length,failed:checks.filter(c=>c.result==='FAIL').length}));
})().catch(e=>{console.error(e.message);process.exitCode=1;});
