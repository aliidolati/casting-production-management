const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

(async () => {
    const browser = await chromium.launch({ channel: 'chrome', headless: true });
    const output = path.resolve(__dirname, '../../../target/radiography-browser');
    fs.mkdirSync(output, { recursive: true });
    const errors = [];
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    const expected = ['F.M.M', 'F.M.L', 'F.M', 'S.P', 'S.C', 'CR', 'G.H', 'G.P', 'VT', 'C.S', 'M.R', 'ماسه ریزی', 'ابعادی', 'OTHER'];
    async function save() {
        await page.locator('#form-rt-line-defect button[type=submit]').click();
        await page.locator('#form-rt-line-defect').waitFor({ state: 'detached' });
        await page.locator('.rt-defect-chip').first().waitFor();
    }
    async function edit() {
        await page.locator('.rt-defect-chip').first().click();
        await page.locator('#form-rt-line-defect').waitFor();
    }
    async function stored() {
        return page.evaluate(async id => (await RadiographyReportAPI.getByPiece(id))[0], process.env.RADIOGRAPHY_PIECE);
    }
    try {
        await page.goto(process.env.RADIOGRAPHY_BASE);
        await page.locator('#input-login-username').fill(process.env.RADIOGRAPHY_ADMIN);
        await page.locator('#input-login-password').fill(process.env.RADIOGRAPHY_PASSWORD);
        await page.locator('#form-login button[type=submit]').click();
        await page.locator('#app-shell').waitFor({ state: 'visible' });
        await page.evaluate(async id => {
            const piece = await PieceAPI.getById(id);
            const template = await RadiographyReportAPI.createTemplate(piece.pieceDefinitionId, {
                name: 'قالب آزمایشی', lines: [{ courseCode: 'C1', imageCount: '1', th: '10' }],
            });
            await RadiographyReportAPI.save(id, { templateId: template.id, lines: [{ templateLineId: template.lines[0].id, result: 'RS' }] });
            await openRadiographyReportModal(piece);
        }, process.env.RADIOGRAPHY_PIECE);
        await page.locator('.btn-rt-add-defect').first().click();
        await page.locator('.rt-pos-start').fill('L4');
        await page.locator('#btn-rt-pos-add').click();
        await page.locator('.rt-pos-start').fill('L5');
        await page.locator('.rt-pos-end').fill('L7');
        await page.locator('#btn-rt-pos-add').click();
        await page.locator('.rt-pos-start').fill(' L4 ');
        await page.locator('#btn-rt-pos-add').click();
        assert.match(await page.locator('#rt-defect-form-error').innerText(), /قبلاً اضافه شده/);
        await page.locator('#rt-pos-list [data-act="edit"]').nth(1).click();
        await page.locator('.rt-pos-end').fill('L8');
        await page.locator('#btn-rt-pos-add').click();
        assert.match(await page.locator('#rt-pos-list').innerText(), /L5-L8/);
        assert.deepEqual(await page.locator('.rt-defect-type option').evaluateAll(nodes => nodes.map(n => n.value).filter(Boolean)), expected);
        await page.locator('.rt-defect-type').selectOption('OTHER');
        await page.locator('#rt-defect-custom').fill('عیب <آزمایشی> & "خاص"');
        await page.locator('.rt-action-1').selectOption('THICKNESS_MEASUREMENT');
        assert.match(await page.locator('.rt-action-1 option:checked').innerText(), /ضخامت مجاز/);
        await page.locator('.rt-welded').check();
        await page.locator('#btn-rt-add-second').click();
        await page.locator('#form-rt-line-defect button[type=submit]').click();
        assert.match(await page.locator('#rt-defect-form-error').innerText(), /نتیجه بررسی مجدد/);
        await page.locator('#rt-review-mode').selectOption('RESHOOT_OK');
        assert.equal(await page.locator('#rt-second-section').isVisible(), false);
        assert.match(await page.locator('#rt-chain-text').innerText(), /ریشوت → ضخامت مجاز → جوش → OK/);
        const labels = await page.locator('#rt-chain-visual').innerText();
        assert.ok(labels.indexOf('ریشوت') < labels.indexOf('جوش'));
        assert.equal(await page.locator('#rt-chain-visual آزمایشی').count(), 0);
        await page.screenshot({ path: path.join(output, 'review-desktop.png'), fullPage: true });
        await save();
        let report = await stored();
        assert.equal(report.lines[0].result, 'RS');
        assert.equal(report.lines[0].defects[0].reshootOk, true);
        assert.equal(report.lines[0].defects[0].secondDefect, null);
        assert.equal(report.lines[0].defects[0].defectTypeCustom, 'عیب <آزمایشی> & "خاص"');
        assert.deepEqual(report.lines[0].defects[0].positions, [{ startCm: 'L4', endCm: 'L4' }, { startCm: 'L5', endCm: 'L8' }]);
        await page.reload();
        await page.locator('#app-shell').waitFor({ state: 'visible' });
        await page.evaluate(async id => openRadiographyReportModal(await PieceAPI.getById(id)), process.env.RADIOGRAPHY_PIECE);
        await edit();
        assert.equal(await page.locator('#rt-review-mode').inputValue(), 'RESHOOT_OK');
        assert.equal(await page.locator('#rt-defect-custom').inputValue(), 'عیب <آزمایشی> & "خاص"');
        await page.locator('#rt-pos-list [data-act="edit"]').first().click();
        assert.equal(await page.locator('.rt-pos-start').inputValue(), 'L4');
        assert.equal(await page.locator('.rt-pos-end').inputValue(), '');
        await page.locator('#btn-rt-pos-add').click();
        await page.locator('#rt-review-mode').selectOption('SECOND_DEFECT');
        assert.deepEqual(await page.locator('.rt-defect2-type option').evaluateAll(nodes => nodes.map(n => n.value).filter(Boolean)), expected);
        await page.locator('.rt-defect2-type').selectOption('OTHER');
        await page.locator('#rt-defect2-custom').fill('عیب دوم دلخواه');
        await page.locator('.rt-action-2').selectOption('OK');
        await save();
        report = await stored();
        assert.equal(report.lines[0].defects[0].reshootOk, false);
        assert.equal(report.lines[0].defects[0].secondDefect.defectTypeCustom, 'عیب دوم دلخواه');
        await edit();
        await page.locator('#rt-review-mode').selectOption('RESHOOT_OK');
        await page.locator('.rt-action-1').selectOption('');
        await save();
        report = await stored();
        assert.equal(report.lines[0].defects[0].secondDefect, null);
        // Verify the actual print stylesheet and generated PDF with the saved reshoot chain.
        await page.evaluate(() => {
            window.print = () => {};
            printRadiographyPages(document.getElementById('rt-pages'));
        });
        await page.emulateMedia({ media: 'print' });
        await page.locator('#rt-print-root').waitFor({ state: 'visible' });
        assert.match(await page.locator('#rt-print-root').innerText(), /ریشوت → جوش → OK/);
        assert.match(await page.locator('#rt-print-root').innerText(), /\(L4\)\(L5-L8\)/);
        await page.screenshot({ path: path.join(output, 'report-print.png'), fullPage: true });
        await page.pdf({ path: path.join(output, 'report.pdf'), format: 'A4', landscape: true, printBackground: true });
        await page.evaluate(() => window.dispatchEvent(new Event('afterprint')));
        await page.emulateMedia({ media: 'screen' });
        await page.setViewportSize({ width: 390, height: 844 });
        await edit();
        assert.equal(await page.locator('#rt-review-mode').inputValue(), 'RESHOOT_OK');
        assert.match(await page.locator('#rt-pos-list').innerText(), /L4/);
        const overflow = await page.locator('#form-rt-line-defect').evaluate(el => el.scrollWidth > el.clientWidth + 2);
        assert.equal(overflow, false);
        await page.screenshot({ path: path.join(output, 'review-mobile.png'), fullPage: true });
        await page.locator('#btn-rt-remove-second').click();
        await save();
        report = await stored();
        assert.equal(report.lines[0].defects[0].reshootOk, false);
        assert.equal(report.lines[0].defects[0].secondDefect, null);
        assert.equal(report.lines[0].result, 'RS');
        assert.deepEqual(errors, []);
        console.log('Radiography browser smoke passed: text points/ranges, reshoot before welding, desktop/mobile, persistence, print.');
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
