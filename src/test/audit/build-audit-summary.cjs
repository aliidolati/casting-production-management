'use strict';
const fs=require('node:fs'),path=require('node:path');
const main=JSON.parse(fs.readFileSync('target/audit/live-api-results.json','utf8'));
const token=JSON.parse(fs.readFileSync('target/audit/token-lifecycle-results.json','utf8'));
const closure=JSON.parse(fs.readFileSync('target/audit/route-closure-results.json','utf8'));
const checks=[...main.checks,...token.checks,...closure.checks];
const routes=[];
for(const file of fs.readdirSync('src/main/java/com/factory/productmanager/controller').filter(f=>f.endsWith('.java'))){
 const source=fs.readFileSync(path.join('src/main/java/com/factory/productmanager/controller',file),'utf8');
 const beforeClass=source.slice(0,source.indexOf('public class'));
 const prefix=beforeClass.match(/@RequestMapping\(\s*"([^"]+)"/)?.[1]||'';
 for(const m of source.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping(?:\s*\(([\s\S]*?)\))?/g)){
  const suffix=m[2]?.match(/"([^"]*)"/)?.[1]||'';
  const route=(suffix.startsWith('/api/')?suffix:prefix+suffix)||'/';
  const regex=new RegExp('^'+route.replace(/[.*+?^$()|[\]\\]/g,'\\$&').replace(/\{[^}]+\}/g,'[^/?]+')+'$');
  const found=checks.filter(c=>c.method===m[1].toUpperCase()&&regex.test((c.route||'').split('?')[0]));
  routes.push({controller:file.replace('.java',''),method:m[1].toUpperCase(),route,requested:found.length>0,
   successfulResponse:found.some(c=>c.actual>=200&&c.actual<300),statuses:[...new Set(found.map(c=>c.actual))].sort()});
 }
}
const ms=main.checks.filter(c=>typeof c.ms==='number').map(c=>c.ms).sort((a,b)=>a-b);
const summary={date:'2026-10-30',live:{checks:checks.length,passed:checks.filter(c=>c.result==='PASS').length,
 failed:checks.filter(c=>c.result==='FAIL').length,errors:checks.filter(c=>c.result==='ERROR').length,
 observations:checks.filter(c=>c.result==='OBSERVATION').length},
 routeCoverage:{declared:routes.length,requested:routes.filter(r=>r.requested).length,successfulResponse:routes.filter(r=>r.successfulResponse).length},
 timing:{p50Ms:ms[Math.floor(ms.length*.5)],p95Ms:ms[Math.floor(ms.length*.95)],maxMs:ms.at(-1),note:'Loopback smoke requests, not a load benchmark'},
 baselineJava:{total:231,passed:212,skipped:19,failures:0,errors:0},extraBrowserJava:{passed:2},extraSeedingJava:{passed:2},
 node:{passed:21,syntaxChecked:fs.readdirSync('src/main/resources/static/js').filter(f=>f.endsWith('.js')).length},
 routes};
fs.writeFileSync('target/audit/summary.json',JSON.stringify(summary,null,2));
fs.mkdirSync('docs/audits',{recursive:true});
let md='# پوشش مسیرهای HTTP در بررسی ۲۰۲۶-۱۰-۳۰\n\n';
md+='این جدول از کنترلرهای فعلی و درخواست‌های ثبت‌شدهٔ دور نهایی تست ساخته شده است. «درخواست شد» ممکن است شامل سناریوی منع دسترسی یا اعتبارسنجی باشد؛ «پاسخ موفق» فقط وجود یک پاسخ 2xx را نشان می‌دهد و تضمین صحت کامل آن مسیر نیست. بررسی همهٔ ترکیب‌های ورودی یا همهٔ رقابت‌های همزمان ادعا نمی‌شود.\n\n';
md+='| کنترلر | متد | مسیر | درخواست شد | پاسخ 2xx | کدهای مشاهده‌شده |\n|---|---|---|---|---|---|\n';
for(const r of routes)md+='| '+r.controller+' | '+r.method+' | `'+r.route+'` | '+(r.requested?'بله':'خیر')+' | '+(r.successfulResponse?'بله':'خیر')+' | '+r.statuses.join(', ')+' |\n';
fs.writeFileSync('docs/audits/2026-10-30-api-coverage.md',md);
console.log(JSON.stringify({...summary,routes:undefined}));
