'use strict';
// Explicit opt-in, loopback-only. Uses and deletes exclusively fresh audit accounts.
const fs=require('node:fs'),crypto=require('node:crypto'),{spawnSync}=require('node:child_process');
if(!process.argv.includes('--authorized-local-test-db'))throw Error('Explicit authorization required');
const run='AUDIT_20261030_'+crypto.randomBytes(4).toString('hex');
const cp=fs.readFileSync(fs.existsSync('target/audit/classpath.txt')?'target/audit/classpath.txt':'target/melt-workflow-classpath.txt','utf8').trim();
const password=crypto.randomBytes(24).toString('base64url')+'Aa!7';
const checks=[];
function db(mode,input){const r=spawnSync('java',['--class-path',cp,'src/test/audit/LocalAuditDatabase.java','--authorized-local-test-db',mode],{input:JSON.stringify(input),encoding:'utf8',timeout:30000});if(r.status!==0)throw Error('Database helper failed');return JSON.parse(r.stdout);}
async function request(name,method,route,body,token,expected){
 const headers={};if(token)headers.Authorization='Bearer '+token;
 if(body)headers['Content-Type']='application/json';
 const r=await fetch('http://127.0.0.1:8080'+route,{method,headers,body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(10000)});
 const text=await r.text();let result;try{result=JSON.parse(text)}catch{result={}};
 checks.push({name,method,route,expected,actual:r.status,result:r.status===expected?'PASS':'FAIL'});
 return result;
}
function forged(secret,username,role='ADMIN') {
 const header=Buffer.from(JSON.stringify({alg:'HS256',typ:'JWT'})).toString('base64url');
 const now=Math.floor(Date.now()/1000);
 const payload=Buffer.from(JSON.stringify({sub:username,role,iat:now,exp:now+120})).toString('base64url');
 const signature=crypto.createHmac('sha256',secret).update(header+'.'+payload).digest('base64url');
 return header+'.'+payload+'.'+signature;
}
(async()=>{
 let seeded=false;
 try{
  const admin=db('seed',{username:run+'_admin',password});seeded=true;
  const login=await request('fresh audit admin login','POST','/api/auth/login',{username:admin.username,password},null,200);
  const token=login.token;
  const properties=fs.readFileSync('src/main/resources/application.properties','utf8');
  const secret=properties.match(/^app\.jwt\.secret=(.*)$/m)[1].trim();
  await request('SECURITY running app must not trust checked-in JWT signing key','GET','/api/auth/me',null,forged(secret,admin.username),401);
  const uname=run+'_reuse';
  let created=await request('create original operator account','POST','/api/users',{username:uname,password,fullName:'حساب موقت چرخه توکن',role:'OPERATOR',permissions:[]},token,201);
  const old=await request('original operator login','POST','/api/auth/login',{username:uname,password},null,200);
  await request('old operator JWT cannot list users','GET','/api/users',null,old.token,403);
  await request('delete original operator','DELETE','/api/users/'+created.id,null,token,204);
  await request('deleted operator JWT rejected','GET','/api/auth/me',null,old.token,401);
  created=await request('recreate same username as fresh admin','POST','/api/users',{username:uname,password:password+'new',fullName:'حساب جدید موقت',role:'ADMIN',permissions:[]},token,201);
  await request('SECURITY deleted-account JWT must stay invalid when username reused','GET','/api/users',null,old.token,401);
  const weak=await request('SECURITY six-character password should be rejected','POST','/api/users',{username:run+'_weak',password:'123456',fullName:'حساب رمز آزمایشی',role:'OPERATOR',permissions:[]},token,400);
  const attempts=[];
  for(let i=0;i<12;i++){
   const r=await fetch('http://127.0.0.1:8080/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:admin.username,password:'invalid-audit-only'}),signal:AbortSignal.timeout(10000)});
   attempts.push(r.status);await r.arrayBuffer();
  }
  checks.push({name:'login throttling observation (12 failed fixture attempts)',result:'OBSERVATION',attempts,throttled:attempts.includes(429)});
 }finally{
  fs.writeFileSync('target/audit/token-lifecycle-results.json',JSON.stringify({run,checks},null,2));
  if(seeded)db('cleanup',{run});
 }
 console.log(JSON.stringify({checks:checks.length,passed:checks.filter(c=>c.result==='PASS').length,failed:checks.filter(c=>c.result==='FAIL').length,observations:checks.filter(c=>c.result==='OBSERVATION').length}));
})().catch(e=>{console.error('Audit failed: '+e.name);process.exitCode=1;});
