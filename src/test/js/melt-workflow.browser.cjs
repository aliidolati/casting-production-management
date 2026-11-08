/* Optional smoke against MeltPassportBrowserFixture only: synthetic in-memory H2 on loopback. */
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');

(async () => {
    const browsers = [];
    const launchOptions = process.env.MELT_CHROME
        ? { executablePath: process.env.MELT_CHROME, headless: true }
        : { channel: 'chrome', headless: true };
    const errors = [];
    const output = path.resolve(__dirname,'../../../target/melt-workflow-browser');
    fs.mkdirSync(output,{recursive:true});
    async function session(username) {
        const browser = await chromium.launch(launchOptions);
        browsers.push(browser);
        const context = await browser.newContext({viewport:{width:1440,height:1000}});
        const page = await context.newPage(); page.on('pageerror',e=>errors.push(e.message));
        await page.goto('http://127.0.0.1:18084');
        await page.locator('#input-login-username').fill(username);
        await page.locator('#input-login-password').fill('Synthetic-Melt-Only1');
        await page.locator('#form-login button[type=submit]').click();
        await page.locator('#app-shell').waitFor({state:'visible'});
        return page;
    }
    async function inbox(page,id) {
        if(await page.locator('#modal-overlay.active').count()) await page.locator('#modal-close').click();
        await page.evaluate(()=>{location.hash='#/tasks/my';});
        await page.locator(`.task-row[data-task-id="${id}"]`).waitFor();
        await page.locator(`.task-row[data-task-id="${id}"] .task-title-link`).click();
        await page.locator('#task-open-melt').waitFor();
    }
    async function current(page,id) { return page.evaluate(id=>MeltPassportAPI.get(id),id); }
    try {
        const admin=await session('melt-ui-admin');
        const assigner=await session('melt-ui-assigner');
        const operator=await session('melt-ui-operator');
        const reviewer=await session('melt-ui-reviewer');
        const pieces=await assigner.evaluate(async()=>{
            const p=await PieceAPI.getByRef('SMOKE-MELT-1-1');
            const q=await PieceAPI.getByRef('SMOKE-MELT-1-2');
            await openPieceProfile(p.id); return [p,q];
        });
        await assigner.locator('#btn-melt-passport-icon').click();
        await assigner.locator('#melt-assignee').waitFor();
        const operatorId=await operator.evaluate(()=>AuthState.user.id);
        await assigner.locator('#melt-assignee').selectOption(operatorId);
        await assigner.locator('#melt-assign').click();
        await assigner.waitForFunction(()=>document.querySelector('.melt-status-bar')?.textContent.includes('اپراتور آزمایشی'));
        const passport=await assigner.evaluate(id=>MeltPassportAPI.forPiece(id).then(rows=>rows[0]),pieces[0].id);
        assert.equal(passport.assignedOperatorId,operatorId);
        assert.equal(await assigner.locator('#melt-furnace').isDisabled(), true);
        assert.equal(await assigner.locator('#melt-assign').isEnabled(), true);
        const entry=await operator.evaluate(async id=>(await TaskAPI.list({mine:true})).items.find(t=>t.workflowId===id&&t.taskType==='MELT_PASSPORT_ENTRY'),passport.id);
        assert.ok(entry); await inbox(operator,entry.id);
        await operator.locator('#task-open-melt').click();
        await operator.locator('#melt-piece-search').fill('SMOKE-MELT-1-2');
        await operator.locator(`[data-melt-add-piece="${pieces[1].id}"]`).click();
        await operator.locator('#melt-furnace').fill('3'); await operator.locator('#melt-ingot').fill('Synthetic alloy');
        await operator.locator('#melt-primary-weight').fill('70'); await operator.locator('#melt-recycled-weight').fill('30');
        await operator.locator('#melt-operator').fill('Synthetic casting operator');
        for(const el of await operator.locator('.melt-check select').all()) await el.selectOption('ACCEPTED');
        await operator.setViewportSize({width:390,height:844});
        await operator.screenshot({path:path.join(output,'operator-mobile.png'),fullPage:true});
        await operator.locator('#melt-submit').click();
        await operator.waitForFunction(()=>document.querySelector('.melt-status-PENDING_APPROVAL'));
        let m=await current(operator,passport.id); assert.equal(m.pieces.length,2);
        const approval=await reviewer.evaluate(async id=>(await TaskAPI.list({mine:true})).items.find(t=>t.workflowId===id&&t.taskType==='MELT_PASSPORT_APPROVAL'&&t.status==='TODO'),m.id);
        assert.ok(approval);
        const adminTasks=await admin.evaluate(()=>TaskAPI.list({mine:true})); assert.ok(adminTasks.items.some(t=>t.id===approval.id));
        await inbox(reviewer,approval.id); await reviewer.locator('#task-open-melt').click();
        await reviewer.locator('#melt-return-reason').fill('Synthetic correction'); await reviewer.locator('#melt-return').click();
        await reviewer.waitForFunction(()=>document.querySelector('.melt-status-NEEDS_CORRECTION'));
        await reviewer.locator('#melt-notes').fill('Reviewer correction');
        await reviewer.locator('#melt-submit').click();
        await reviewer.waitForFunction(()=>document.querySelector('.melt-status-PENDING_APPROVAL'));
        m=await current(reviewer,m.id);
        assert.equal(m.notes,'Reviewer correction');
        assert.equal(m.assignedOperatorId,operatorId);
        const reviewAgain=await admin.evaluate(async id=>(await TaskAPI.list({mine:true})).items.find(t=>t.workflowId===id&&t.taskType==='MELT_PASSPORT_APPROVAL'&&t.status==='TODO'),m.id);
        await inbox(admin,reviewAgain.id); await admin.locator('#task-open-melt').click();
        await admin.screenshot({path:path.join(output,'admin-review-desktop.png'),fullPage:true});
        await admin.locator('#melt-approve').click(); await admin.waitForFunction(()=>document.querySelector('.melt-status-APPROVED'));
        m=await current(admin,m.id); assert.equal(m.status,'APPROVED');
        for(const p of pieces) assert.notEqual((await admin.evaluate(id=>PieceAPI.getById(id),p.id)).currentStage,'CASTING');
        assert.equal((await operator.evaluate(id=>TaskAPI.get(id),entry.id)).status,'COMPLETED');
        assert.equal((await admin.evaluate(id=>TaskAPI.get(id),reviewAgain.id)).status,'COMPLETED');
        const extra = await admin.evaluate(async () => {
            const p = await PieceAPI.getByRef('SMOKE-MELT-1-4');
            await PieceAPI.correctStage(p.id, {stage: 'CASTING'});
            await PieceAPI.updateProcessDate(p.id, '2026-11-07');
            await openPieceProfile(p.id);
            return p;
        });
        await operator.evaluate(id => openPieceProfile(id), extra.id);
        assert.equal(await operator.locator('#btn-profile-next').count(), 0);
        assert.equal(await operator.locator('#btn-admin-jump').count(), 0);
        await admin.locator('#btn-profile-next').click();
        await admin.waitForFunction(async id => (await PieceAPI.getById(id)).currentStage === 'ROUGH_MACHINING', extra.id);
        assert.deepEqual(await admin.evaluate(id => MeltPassportAPI.forPiece(id), extra.id), []);

        await reviewer.evaluate(id => openMeltPassport(id), m.id);
        await reviewer.locator('.melt-status-APPROVED').waitFor();
        assert.equal(await reviewer.locator('#melt-start-correction').count(), 0);
        await admin.evaluate(id => openMeltPassport(id), m.id);
        await admin.locator('#melt-start-correction').click();
        assert.equal(await admin.locator('[data-melt-remove-piece]').count(), 0);
        await admin.locator('#melt-piece-search').fill('SMOKE-MELT-1-4');
        await admin.locator(`[data-melt-add-piece="${extra.id}"]`).click();
        await admin.locator('#melt-added-stage').waitFor({state:'visible'});
        await admin.locator('#melt-save').click();
        await admin.locator('#toast').filter({hasText:'مرحله مقصد قطعات تازه‌اضافه‌شده را انتخاب کنید'}).waitFor();
        assert.equal((await current(admin,m.id)).pieces.length,2);
        await admin.setViewportSize({width:390,height:844});
        await admin.locator('#melt-added-stage').selectOption('RADIOGRAPHY');
        await admin.locator('#melt-save').click();
        await admin.locator('#melt-start-correction').waitFor();
        const corrected = await current(admin,m.id);
        assert.equal(corrected.approvedAt,m.approvedAt);
        assert.equal(corrected.pieces.length,3);
        assert.equal((await admin.evaluate(id => PieceAPI.getById(id), extra.id)).currentStage,'RADIOGRAPHY');
        for (const p of pieces) assert.equal((await admin.evaluate(id => PieceAPI.getById(id), p.id)).currentStage,'ROUGH_MACHINING');
        assert.deepEqual(errors,[]);
        console.log('PASS: melt workflow, manager-only manual transfer, and approved correction with a required destination on desktop/mobile.');
    } finally { await Promise.all(browsers.map(browser => browser.close())); }
})().catch(e=>{console.error(e);process.exitCode=1;});
