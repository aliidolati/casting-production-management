const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../main/resources/static/js');

function browser() {
    const nodes = new Map(), timers = new Map();
    let nextTimer = 0;
    const element = id => {
        if (!nodes.has(id)) {
            const el = { value: '', textContent: '', innerHTML: '', className: '', attributes: {}, listeners: {},
                setAttribute(k,v) { this.attributes[k] = v; },
                addEventListener(k,fn) { this.listeners[k] = fn; },
                querySelectorAll: () => [], isConnected: true };
            el.classList = { remove(name) { el.className = el.className.split(' ').filter(x => x !== name).join(' '); } };
            nodes.set(id,el);
        }
        return nodes.get(id);
    };
    const context = vm.createContext({ console, Date, URLSearchParams, hasPermission: () => false,
        document: { getElementById: element, querySelectorAll: () => [], addEventListener() {} },
        window: { location: { origin: 'http://synthetic.test' } }, localStorage: { getItem: () => null },
        setTimeout(fn,delay) { const id=++nextTimer;timers.set(id,{fn,delay});return id; },
        clearTimeout(id) { timers.delete(id); } });
    for(const script of ['api.js','app.js','melt-passport.js']) vm.runInContext(fs.readFileSync(path.join(root,script),'utf8'),context,{filename:script});
    return { context, element, timers, run: code => vm.runInContext(code,context) };
}

test('new toast cancels the previous timeout and keeps legacy positioning defaults', () => {
    const b=browser();b.run('showToast("قدیمی")');
    assert.equal([...b.timers.values()][0].delay,3000);
    const previous=[...b.timers.keys()][0];
    b.run('showToast("هشدار تازه", "warning", {position:"top",durationMs:6000})');
    assert.equal(b.timers.has(previous),false);assert.equal(b.timers.size,1);
    assert.equal([...b.timers.values()][0].delay,6000);
    assert.match(b.element('toast').className,/toast-top/);assert.equal(b.element('toast').attributes.role,'alert');
    b.run('showToast("پیام عادی")');assert.doesNotMatch(b.element('toast').className,/toast-top/);
    assert.equal(b.element('toast').attributes.role,'status');
});

test('API field errors appear as safe floating text and repeated errors are coalesced', async () => {
    const b=browser();
    b.context.fetch=async()=>({status:400,ok:false,json:async()=>({message:'اطلاعات معتبر نیست',errors:{furnaceNumber:'شماره کوره صحیح نیست',notes:'<img src=x onerror=alert(1)>'}})});
    await b.run('apiRequest("/melt-passports","POST",{}).catch(meltShowError)');
    assert.match(b.element('toast').textContent,/شماره کوره صحیح نیست/);
    assert.match(b.element('toast').textContent,/<img src=x/);assert.equal(b.element('toast').innerHTML,'');
    assert.equal([...b.timers.values()][0].delay,6000);
    const timer=[...b.timers.keys()][0];
    await b.run('apiRequest("/melt-passports","POST",{}).catch(meltShowError)');
    assert.equal([...b.timers.keys()][0],timer);
    b.run('showToast("پیام دیگر")');
    await b.run('apiRequest("/melt-passports","POST",{}).catch(meltShowError)');
    assert.match(b.element('toast').textContent,/شماره کوره صحیح نیست/);
    assert.notEqual([...b.timers.keys()][0],timer);
    assert.equal(b.element('melt-furnace').value,'');
});

test('editing a calculated magnesium input warns once and does not calculate or alter process values', () => {
    const b=browser();
    b.element('melt-process-rotaryDuration').value='999';b.element('melt-primary-weight').value='70';
    b.run('globalThis.meltTestState = {m:{magnesiumResult:453.2},form:{isConnected:true},catalog:[]};attachMeltEditEvents(meltTestState)');
    b.element('melt-mg-mg').listeners.input();
    assert.match(b.element('toast').textContent,/محاسبه مجدد/);assert.match(b.element('melt-magnesium-result').textContent,/محاسبه مجدد/);
    const timer=[...b.timers.keys()][0];b.element('melt-mg-ar').listeners.input();
    assert.equal([...b.timers.keys()][0],timer);assert.equal(b.element('melt-process-rotaryDuration').value,'999');assert.equal(b.element('melt-primary-weight').value,'70');
    assert.equal(b.run('meltTestState.m.magnesiumResult'),453.2);
});

test('direct report opening is blocked in the client without dashboard permission', async () => {
    const b=browser();await b.run('openMeltPassportRegister()');
    assert.match(b.element('toast').textContent,/مجوز مشاهده داشبورد/);assert.match(b.element('toast').className,/error toast-top/);
});

test('closing a profile while a passport request is pending cannot reopen the modal', async () => {
    const b=browser();
    b.element('modal-body').firstElementChild={isConnected:true};
    let finish;
    b.context.getPendingPassport=()=>new Promise(resolve=>{finish=resolve;});
    b.run('MeltPassportAPI.get = getPendingPassport;MeltPassportAPI.materials = async()=>[];MeltPassportAPI.ingotTypes = async()=>[];MeltPassportAPI.today = async()=>({date:"2026-10-22"});');
    const pending=b.run('openMeltPassport("synthetic-id")');
    b.element('modal-body').firstElementChild.isConnected=false;
    b.context.renderMeltPassport=()=>assert.fail('Closed profile must not reopen');
    finish({id:'synthetic-id'});await pending;
});

test('approved correction uses one atomic request and never calls draft save or calculation routes', async () => {
    const b=browser(), requests=[];
    b.context.fetch=async(url,options)=>{requests.push({url,options});return {ok:true,status:200,json:async()=>({id:'approved-id',version:8,status:'APPROVED',correctable:true})};};
    b.context.collectMeltForm=()=>({version:7,pieceIds:['fixed-piece'],magnesium:{mg:0.2}});
    b.context.renderMeltPassport=state=>assert.equal(state.correcting,false);
    b.element('melt-correction-reason').value='اصلاح ساختگی';
    b.run('globalThis.correctionState={m:{id:"approved-id",version:7,status:"APPROVED",correctable:true,pieces:[{id:"fixed-piece"}]},correcting:true};');
    await b.run('runMeltAction(correctionState,"calculate-magnesium")');
    assert.equal(requests.length,1);assert.match(requests[0].url,/\/approved-id\/corrections$/);
    const body=JSON.parse(requests[0].options.body);
    assert.equal(body.data.version,7);assert.equal(body.calculateMagnesium,true);assert.equal(body.reason,'اصلاح ساختگی');
    assert.equal(b.run('correctionState.m.version'),8);
});

test('failed approved correction preserves the edit mode, entered values, reason and version', async () => {
    const b=browser();
    b.context.fetch=async()=>({ok:false,status:409,json:async()=>({message:'نسخه شناسنامه تغییر کرده است؛ دوباره باز کنید'})});
    b.context.collectMeltForm=()=>({version:7,pieceIds:['fixed-piece'],notes:'متن حفظ‌شده'});
    b.context.renderMeltPassport=()=>assert.fail('A failed correction must not replace the form');
    b.element('melt-notes').value='متن حفظ‌شده';b.element('melt-correction-reason').value='دلیل حفظ‌شده';
    b.run('globalThis.correctionState={m:{id:"approved-id",version:7,status:"APPROVED",correctable:true,pieces:[{id:"fixed-piece"}]},correcting:true};');
    await b.run('runMeltAction(correctionState,"save")');
    assert.equal(b.run('correctionState.correcting'),true);assert.equal(b.run('correctionState.m.version'),7);
    assert.equal(b.element('melt-notes').value,'متن حفظ‌شده');assert.equal(b.element('melt-correction-reason').value,'دلیل حفظ‌شده');
    assert.match(b.element('toast').textContent,/نسخه شناسنامه/);
});

test('audit renders changed values, additions, removals and untrusted text safely', () => {
    const b=browser();
    b.context.auditEvent={oldValues:{notes:'قبلی',materials:[{materialName:'ماده قدیمی',quantity:0}],checks:[{kind:'LANCE_HEAD',status:'ACCEPTED'}]},
        newValues:{notes:'<img src=x onerror=alert(1)>',materials:[],checks:[{kind:'LANCE_HEAD',status:'REJECTED'}]}};
    const html=b.run('renderMeltAuditChanges(auditEvent)');
    assert.match(html,/مقدار قبلی/);assert.match(html,/مقدار جدید/);assert.match(html,/ماده قدیمی/);assert.match(html,/>0</);
    assert.match(html,/&lt;img/);assert.doesNotMatch(html,/<img/);assert.match(html,/وضعیت کلگی/);assert.match(html,/>رد</);
    assert.equal(b.run('renderMeltAuditChanges({})'),'');
    assert.equal(b.run('renderMeltAuditChanges({oldValues:{notes:"ثابت"},newValues:{notes:"ثابت"}})'), '');
});
