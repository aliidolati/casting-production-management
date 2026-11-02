const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
function browser() {
    const nodes=new Map();
    const element=id=>{
        if(!nodes.has(id))nodes.set(id,{value:'',style:{},listeners:{},innerHTML:'',textContent:'',
            addEventListener(type,fn){this.listeners[type]=fn;},classList:{add(){},remove(){},toggle(){}}});
        return nodes.get(id);
    };
    const context=vm.createContext({document:{getElementById:element,addEventListener(){},querySelectorAll(){return [];}},
        window:{addEventListener(){}},localStorage:{},console,URLSearchParams,
        escapeHtml:value=>String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;'),
        stageLabel:stage=>stage,RADIOGRAPHY_SUBSTAGE_LABELS:{SHOT:'شوت',VT:'بازرسی'},HEAT_TREATMENT_SUBSTAGE_LABELS:{},ROUGH_MACHINING_SUBSTAGE_LABELS:{}});
    for(const file of ['auth.js','tracking.js'])vm.runInContext(fs.readFileSync(path.join(__dirname,'../../main/resources/static/js',file),'utf8'),context);
    return {run:source=>vm.runInContext(source,context),element};
}
test('skip controls require both eligible role and independent grant; normal grant is separate',()=>{
    const {run}=browser();
    run('AuthState.user={role:"ADMIN",permissions:["SKIP_PIECE_STAGE"]}');
    assert.equal(run('canUseStageSkip()'),true);assert.equal(run('hasPermission("ADVANCE_PIECE_STAGE")'),false);
    run('AuthState.user={role:"OPERATOR",permissions:["SKIP_PIECE_STAGE"]}');assert.equal(run('canUseStageSkip()'),false);
    run('AuthState.user={role:"ADMIN",permissions:["ADVANCE_PIECE_STAGE"]}');assert.equal(run('canUseStageSkip()'),false);
    assert.equal(run('canAdvancePiece({currentStage:"NOT_STARTED",forwardMove:{requiredPermission:"ADVANCE_PIECE_STAGE"}})'),true);
});
test('skip modal requires a reason and sends the selected server target in one request',async()=>{
    const {run,element}=browser();
    run(`AuthState.user={role:"ADMIN",permissions:["SKIP_PIECE_STAGE"]};
        globalThis.openNestedModal=(title,html)=>{globalThis.modalHtml=html;};
        globalThis.closeNestedModal=()=>{};globalThis.showToast=()=>{};
        globalThis.withButtonLoader=async(button,fn)=>fn();globalThis.loadTracking=()=>{};
        globalThis.openPieceProfile=async()=>{};
        globalThis.PieceAPI={getSkipTargets:async()=>[{stage:"RADIOGRAPHY",radiographySubStage:"VT",heatTreatmentSubStage:null,roughMachiningSubStage:null}],
            skipStage:async(id,data)=>{globalThis.sent={id,data};}};`);
    await run('openStageSkipModal({id:"piece-id"})');
    element('stage-skip-target').value='0';element('stage-skip-reason').value='  ';
    await element('stage-skip-submit').listeners.click({currentTarget:{}});
    assert.equal(run('typeof sent'),'undefined');assert.equal(element('stage-skip-error').style.display,'block');
    element('stage-skip-reason').value='  دلیل مجاز  ';
    await element('stage-skip-submit').listeners.click({currentTarget:{}});
    assert.equal(run('sent.id'),'piece-id');assert.equal(run('sent.data.radiographySubStage'),'VT');assert.equal(run('sent.data.reason'),'دلیل مجاز');
});
test('skip history escapes user-provided reasons and actor names',()=>{
    const {run}=browser();run('globalThis.formatDate=value=>value');
    const html=run(`renderStageSkipHistory([{fromStage:"NOT_STARTED",toStage:"RADIOGRAPHY",toSubStage:"VT",reason:"<img src=x>",actorName:"<script>bad</script>",occurredAt:"2026-11-01"}])`);
    assert.ok(html.includes('&lt;img'));assert.ok(!html.includes('<script>'));assert.ok(html.includes('سوابق پرش'));
});
