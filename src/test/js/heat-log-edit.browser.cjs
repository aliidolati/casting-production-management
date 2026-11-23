const assert = require('node:assert/strict');
const path = require('node:path');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../../main/resources/static');
const origin = 'https://heat-log.test';
const piece = {
    id: 'synthetic-piece', pieceNumber: 9, pieceName: 'قطعه آزمایشی',
    currentStage: 'HEAT_TREATMENT', heatTreatmentSubStage: 'STAGE_1',
};

(async () => {
    const browser = await chromium.launch({ executablePath: process.env.CHROME_BIN });
    try {
        const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
        const errors = [];
        const edits = [];
        let record = {
            id: 'synthetic-heat-record', batchCode: 'HT-TEST-1', treatmentDate: '2026-11-22',
            treatmentStage: 'مرحله اول', testPlateLabel: 'P1', testPlateHardnessHb: 72, canDelete: true,
        };
        page.on('pageerror', error => errors.push(error.message));
        await page.route(`${origin}/**`, async route => {
            const { pathname } = new URL(route.request().url());
            if (!pathname.startsWith('/api/')) {
                return route.fulfill({ path: path.join(root, pathname === '/' ? 'index.html' : pathname) });
            }
            if (pathname === `/api/pieces/${piece.id}`) return route.fulfill({ json: piece });
            if (pathname === `/api/pieces/${piece.id}/heat-treatments`) return route.fulfill({ json: [record] });
            if (pathname === `/api/heat-treatments/${record.id}` && route.request().method() === 'PATCH') {
                const changes = route.request().postDataJSON();
                edits.push(changes);
                record = { ...record, ...changes };
                return route.fulfill({ status: 204 });
            }
            if (pathname.endsWith('/next-code')) return route.fulfill({ json: { code: 'HT-TEST-2' } });
            if (pathname.endsWith('/by-code') || pathname.includes('/radiography-workflow') || pathname.includes('/melt-passports/piece/')) {
                return route.fulfill({ status: 204 });
            }
            return route.fulfill({ json: [] });
        });
        await page.goto(origin);
        await page.evaluate(async id => {
            AuthState.user = { username: 'synthetic-manager', role: 'MANAGER', permissions: [] };
            await openPieceProfile(id);
        }, piece.id);

        await page.locator('#btn-heat-log-icon').click();
        const edit = page.getByRole('button', { name: 'اصلاح', exact: true });
        await edit.scrollIntoViewIfNeeded();
        const bounds = await edit.boundingBox();
        // Hold the pointer near the lower edge long enough for hover and active transitions.
        await page.mouse.click(bounds.x + bounds.width / 2, bounds.y + bounds.height - 2, { delay: 300 });

        const plateLabel = page.getByLabel('شماره پلیت');
        await plateLabel.waitFor({ timeout: 4000 });
        assert.equal(await plateLabel.inputValue(), 'P1');
        await plateLabel.fill('P2');
        await page.getByRole('button', { name: 'ذخیره اصلاحات', exact: true }).click();
        await page.locator('.heat-log-plate').getByText('تست پلیت P2', { exact: false }).waitFor();
        assert.deepEqual(edits, [{
            treatmentStage: 'مرحله اول', testPlateLabel: 'P2', testPlateHardnessHb: 72,
            details: null, tensileDispositionId: null,
        }]);
        assert.deepEqual(errors, []);
        console.log('Heat treatment correction opens from the card and refreshes the edited record after saving.');
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
