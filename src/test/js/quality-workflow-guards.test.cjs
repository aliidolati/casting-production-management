const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
function browser() {
    const nodes=new Map();
    const element=id=>{
        if(!nodes.has(id))nodes.set(id,{value:'',innerHTML:'',textContent:'',style:{},listeners:{},
            querySelectorAll(){return []},addEventListener(type,fn){this.listeners[type]=fn},classList:{add(){},remove(){},toggle(){}}});
        return nodes.get(id);
    };
    const context=vm.createContext({document:{getElementById:element,addEventListener(){}},window:{addEventListener(){}},localStorage:{},console,
        escapeHtml:value=>String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;')});
    for(const name of ['auth.js','radiography.js','radiography-report.js','radiography-workflow.js','tensile.js'])
        vm.runInContext(fs.readFileSync(path.join(__dirname,'../../main/resources/static/js',name),'utf8'),context);
    return {run:source=>vm.runInContext(source,context),element};
}
test('pending report and defect locks exempt manager but not admin',()=>{
    const {run}=browser();
    run('AuthState.user={role:"ADMIN",permissions:["MANAGE_RADIOGRAPHY_DEFECTS"]};RadiographyReportState.reports=[{status:"PENDING_MANAGER_APPROVAL"}];RadiographyState.canEditReport=false;');
    assert.equal(run('canFillRadiographyReport()'),false);assert.equal(run('canEditRadiographyDefects()'),false);
    run('AuthState.user={role:"MANAGER",permissions:[]}');
    assert.equal(run('canFillRadiographyReport()'),true);assert.equal(run('canEditRadiographyDefects()'),true);
});
test('final approval UI refuses self approval when server capability is false',async()=>{
    const {run}=browser();
    await assert.rejects(run('rwManagerApprove({permissions:{canApprove:false}}, {})'),/تأیید نهایی/);
});
async function dispositionFixture(piece, testRow, tests=[testRow]) {
    const fixture=browser();const {run}=fixture;
    run(`globalThis.openModal=(title,html)=>{globalThis.modalHtml=html};globalThis.showToast=message=>{globalThis.lastToast=message};
        globalThis.attachPersianDatePicker=()=>{};globalThis.isoToJalaliString=value=>value;
        globalThis.renderTensileSamplesTable=()=>"";globalThis.formatTensileNumber=value=>value;
        globalThis.parseOptionalNumber=value=>value===""?null:Number(value);
        globalThis.requestScrapDetails=()=>{throw new Error("Historical edit must not request scrap")};
        globalThis.PieceAPI={getById:async()=>({id:"p"})};globalThis.openTensileModal=async()=>{};
        globalThis.TensileTestAPI={getByPiece:async()=>${JSON.stringify(tests)},saveDisposition:async(id,data)=>{globalThis.savedDecision={id,data}}};`);
    await run(`openTensileDispositionModal(${JSON.stringify(piece)},${JSON.stringify(testRow.id)})`);
    return fixture;
}
test('historical edit exposes record-only save and never requests a movement or scrap reason',async()=>{
    const row={id:'old',testIndex:1,disposition:{dispositionDate:'2026-11-01',nextStage:'AGING',inspectionResult:'REJECTED'}};
    const {run,element}=await dispositionFixture({id:'p',currentStage:'READY'},row);
    assert.equal(run('modalHtml.includes("ذخیره و اعمال انتقال")'),false);
    element('disposition-next-stage').value='SCRAP';element('disposition-inspection').value='REJECTED';
    await element('form-tensile-disposition').listeners.submit({preventDefault(){},submitter:{dataset:{applyMovement:'false'}}});
    assert.equal(run('savedDecision.data.applyStageMove'),false);assert.equal(run('savedDecision.data.nextStage'),'SCRAP');
    assert.ok(run('lastToast.includes("مرحله قطعه تغییر نکرد")'));
});
test('current latest decision has a separate explicit movement action',async()=>{
    const row={id:'latest',testIndex:2,disposition:{dispositionDate:'2026-11-01',nextStage:'NEXT_TENSILE',inspectionResult:'REJECTED'}};
    const {run,element}=await dispositionFixture({id:'p',currentStage:'TENSILE'},row);
    assert.equal(run('modalHtml.includes("ذخیره و اعمال انتقال")'),true);
    element('disposition-next-stage').value='APPROVED';element('disposition-inspection').value='ACCEPTED';
    await element('form-tensile-disposition').listeners.submit({preventDefault(){},submitter:{dataset:{applyMovement:'true'}}});
    assert.equal(run('savedDecision.data.applyStageMove'),true);
});
