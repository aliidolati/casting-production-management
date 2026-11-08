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
test('status correction is available only to a manager or a granted admin', () => {
    const {run} = browser();
    const profile = 'renderProfileStageActions({currentStage:"READY"})';
    run('AuthState.user={role:"ADMIN",permissions:["MOVE_PIECE_STAGE"]}');
    assert.doesNotMatch(run(profile), /btn-admin-jump/);
    run('AuthState.user.permissions=["CORRECT_PIECE_STAGE"]');
    assert.match(run(profile), /btn-admin-jump/);
    assert.equal(run('canUseBulkSelection()'), true);
    assert.equal(run('hasPermission("ADVANCE_PIECE_STAGE")'), false);
    assert.doesNotMatch(run(profile), /btn-profile-skip|btn-profile-scrap/);
    assert.doesNotMatch(run('renderProfileStageActions({currentStage:"CASTING"})'), /btn-admin-jump/);
    run('AuthState.user.role="OPERATOR"');
    assert.doesNotMatch(run(profile), /btn-admin-jump/);
    assert.equal(run('canUseBulkSelection()'), false);
    run('AuthState.user={role:"MANAGER",permissions:[]}');
    assert.match(run(profile), /btn-admin-jump/);
});

test('delegated correction asks for a server-provided location before saving', async () => {
    const {run,element} = browser();
    run(`AuthState.user={role:"ADMIN",permissions:["CORRECT_PIECE_STAGE"]};
        globalThis.openNestedModal=(title,html)=>{globalThis.modalHtml=html;};
        globalThis.closeNestedModal=()=>{};globalThis.showToast=()=>{};
        globalThis.withButtonLoader=async(button,fn)=>fn();globalThis.loadTracking=()=>{};
        globalThis.openPieceProfile=async()=>{};
        globalThis.PieceAPI={
            getStageCorrectionTargets:async()=>[{stage:"RADIOGRAPHY",radiographySubStage:"SHOT"}],
            correctStage:async(id,target)=>{globalThis.sent={id,target};}
        };`);
    await run('openStageCorrectionModal([{id:"piece-id",currentStage:"READY"}],{isConnected:true})');
    assert.match(run('modalHtml'), /شوت/);
    await element('stage-correction-submit').listeners.click({currentTarget:{}});
    assert.equal(run('typeof sent'), 'undefined');
    element('stage-correction-target').value = '0';
    await element('stage-correction-submit').listeners.click({currentTarget:{}});
    assert.equal(run('sent.id'), 'piece-id');
    assert.equal(run('sent.target.radiographySubStage'), 'SHOT');
});

test('bulk correction submits one request for all selected pieces with no reason field', async () => {
    const {run, element} = browser();
    run(`AuthState.user={role:"ADMIN",permissions:["CORRECT_PIECE_STAGE"]};
        globalThis.openNestedModal=(title,html)=>{globalThis.modalHtml=html;};
        globalThis.closeNestedModal=()=>{};globalThis.showToast=()=>{};
        globalThis.withButtonLoader=async(button,fn)=>fn();globalThis.loadTracking=()=>{};
        globalThis.formatFaNumber=value=>value;
        globalThis.openPieceProfile=()=>{throw new Error("Bulk correction should stay in tracking");};
        globalThis.PieceAPI={
            getBulkStageCorrectionTargets:async(ids)=>{
                globalThis.preview=ids;
                return [{stage:"RADIOGRAPHY",radiographySubStage:"VT"}];
            },
            bulkCorrectStage:async(ids,target)=>{globalThis.sent={ids,target};}
        };`);
    await run('openStageCorrectionModal([{id:"first"},{id:"second"}],{isConnected:true},{bulk:true})');
    assert.equal(run('preview.join(",")'), 'first,second');
    assert.match(run('modalHtml'), /بازرسی/);
    assert.doesNotMatch(run('modalHtml'), /reason|دلیل/);
    element('stage-correction-target').value = '0';
    await element('stage-correction-submit').listeners.click({currentTarget:{}});
    assert.equal(run('sent.ids.join(",")'), 'first,second');
    assert.equal(run('sent.target.radiographySubStage'), 'VT');
});
