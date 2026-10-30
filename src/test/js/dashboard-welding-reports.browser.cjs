const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

(async () => {
    const browser = await chromium.launch({ channel: 'chrome', headless: true });
    const output = path.resolve(__dirname, '../../../target/dashboard-reports-browser');
    fs.mkdirSync(output, { recursive: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    async function openReport(tab) {
        await page.locator('#nav-btn-dashboard').click();
        await page.locator('#dashboard-tab-btn-reports').click();
        await page.locator(`#dashboard-reports-menu [data-tab="${tab}"]`).click();
    }
    async function csv(button, filename) {
        const downloadEvent = page.waitForEvent('download');
        await page.locator(button).click();
        const download = await downloadEvent;
        const destination = path.join(output, filename);
        await download.saveAs(destination);
        const bytes = fs.readFileSync(destination);
        assert.equal(bytes.subarray(0, 3).toString('hex'), 'efbbbf');
        return bytes.toString('utf8');
    }
    try {
        await page.goto('http://127.0.0.1:18085');
        await page.locator('#input-login-username').fill('reports-ui-admin');
        await page.locator('#input-login-password').fill('Synthetic-Reports-Only1');
        await page.locator('#form-login button[type=submit]').click();
        await page.locator('#app-shell').waitFor({ state: 'visible' });
        await openReport('tensile-quality-report');
        await page.locator('#btn-tensile-quality-export:enabled').waitFor();
        const quality = await csv('#btn-tensile-quality-export', 'quality.csv');
        assert.match(quality, /SMOKE-REPORTS/);
        assert.equal(quality.trim().split('\r\n').length, 3);
        await page.locator('#tensile-quality-ys-max').fill('250');
        const filtered = await csv('#btn-tensile-quality-export', 'quality-filtered.csv');
        assert.equal(filtered.trim().split('\r\n').length, 2);
        await page.screenshot({ path: path.join(output, 'quality-desktop.png') });
        await openReport('tensile-trend-report');
        await page.locator('#btn-tensile-trend-export:enabled').waitFor();
        const trend = await csv('#btn-tensile-trend-export', 'trend.csv');
        assert.equal(trend.trim().split('\r\n').length, 3);
        assert.match(trend, /250.25/);
        await openReport('occupancy-report');
        await page.evaluate(async () => {
            const today = new Date().toISOString().slice(0, 10);
            OccupancyReportState.dateFrom = '2026-01-01';
            OccupancyReportState.dateTo = today;
            await runOccupancyReport();
        });
        await page.locator('#occupancy-report-table-body').getByText('تفسیر', { exact: true }).waitFor();
        const occupancy = await csv('#btn-occupancy-report-export', 'occupancy.csv');
        assert.match(occupancy, /زیرمرحله/);
        assert.match(occupancy, /شوت/);
        assert.match(occupancy, /تفسیر/);
        await page.screenshot({ path: path.join(output, 'occupancy-desktop.png') });
        await page.evaluate(async () => {
            const piece = await PieceAPI.getByRef('SMOKE-REPORTS-1-1');
            await openPieceProfile(piece.id);
        });
        await page.locator('#btn-edit-welding-defect').click();
        assert.equal(await page.locator('#welding-defect-type').inputValue(), 'OTHER');
        assert.equal(await page.locator('#welding-defect-type-custom').inputValue(), 'عیب آزمایشی');
        assert.equal(await page.locator('#welding-notes').inputValue(), 'توضیح اولیه');
        await page.locator('#welding-defect-type-custom').fill('عیب ویرایش‌شده');
        await page.locator('#welding-notes').fill('شرح جدید <آزمون>');
        await page.locator('#form-send-to-welding button[type=submit]').click();
        await page.locator('.welding-station-v').getByText('عیب ویرایش‌شده', { exact: true }).waitFor();
        assert.match(await page.locator('.profile-welding-station').innerText(), /شرح جدید <آزمون>/);
        await page.locator('#btn-edit-welding-defect').click();
        await page.setViewportSize({ width: 390, height: 844 });
        await page.screenshot({ path: path.join(output, 'welding-edit-mobile.png') });
        await page.locator('#btn-cancel-send-welding').click();
        await page.locator('#modal-close').click();
        await openReport('tensile-quality-report');
        assert.equal(await page.locator('#btn-tensile-quality-export').isVisible(), true);
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
        await page.screenshot({ path: path.join(output, 'quality-mobile.png') });
        assert.deepEqual(errors, []);
        console.log('Dashboard CSV, substage report, welding edit and mobile browser smoke passed.');
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
