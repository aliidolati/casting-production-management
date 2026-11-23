const assert = require('node:assert/strict');
const path = require('node:path');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../../main/resources/static');
const origin = 'http://spectrometry.test';
const projectId = '10000000-0000-4000-8000-000000000001';
const definitionId = '20000000-0000-4000-8000-000000000001';
const pieceId = '30000000-0000-4000-8000-000000000001';
const keys = ['al', 'si', 'mg', 'cu', 'fe', 'mn', 'zn', 'ti'];
const iso = day => new Date(Date.UTC(2026, 0, 1 + day)).toISOString().slice(0, 10);
const markColor = { gray: [133, 140, 153], green: [22, 163, 74], orange: [223, 133, 13], blue: [59, 130, 246] };

function records() {
    return Array.from({ length: 50 }, (_, i) => ({
        id: `test-${i}`, version: 0, pieceId, definitionId, projectId, pieceNumber: 9, projectCode: 'LAB-26',
        pieceName: 'قطعه آزمایشگاهی', testDate: iso(i > 0 ? i - 1 : 0),
        al: '92', si: '7', cu: '0', fe: '0.1234567891', mn: '0', zn: '0',
        mg: String(0.4 + Math.max(0, i - 1) * 0.002), ti: String(0.22 - Math.max(0, i - 1) * 0.002),
    }));
}

async function colorPixels(canvas, color, details = false) {
    return canvas.evaluate((node, { color, details }) => {
        let count = 0;
        let right = -1;
        let yTotal = 0;
        let yCount = 0;
        const { width, height } = node;
        const bytes = node.getContext('2d').getImageData(0, 0, width, height).data;
        for (let i = 0; i < bytes.length; i += 4) {
            if (bytes[i] !== color[0] || bytes[i + 1] !== color[1] || bytes[i + 2] !== color[2]) continue;
            count++;
            if (!details) continue;
            const x = (i / 4) % width;
            const y = Math.floor(i / 4 / width);
            if (x > right) { right = x; yTotal = 0; yCount = 0; }
            if (x === right) { yTotal += y; yCount++; }
        }
        if (!details) return count;
        return { x: (right - 2) / devicePixelRatio, y: yTotal / yCount / devicePixelRatio };
    }, { color, details });
}

async function waitForColor(page, chartIndex, color) {
    await page.waitForFunction(({ chartIndex, color }) => {
        const canvas = document.querySelectorAll('#spectrometry-charts canvas')[chartIndex];
        const data = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
        for (let i = 0; i < data.length; i += 4) {
            if (data[i] === color[0] && data[i + 1] === color[1] && data[i + 2] === color[2]) return true;
        }
        return false;
    }, { chartIndex, color });
}

(async () => {
    const browser = await chromium.launch({ executablePath: process.env.CHROME_BIN, args: ['--no-sandbox'] });
    try {
        const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, acceptDownloads: true });
        const errors = [];
        const batches = [];
        const updates = [];
        const deletions = [];
        let data = records();
        page.on('pageerror', error => errors.push(error.message));
        await page.route(`${origin}/**`, async route => {
            const { pathname, searchParams } = new URL(route.request().url());
            if (!pathname.startsWith('/api/')) {
                return route.fulfill({ path: path.join(root, pathname === '/' ? 'index.html' : pathname) });
            }
            if (pathname === '/api/projects') return route.fulfill({ json: [{ id: projectId, code: 'LAB-26', name: 'پروژه آزمایشی' }] });
            if (pathname === '/api/piece-definitions') return route.fulfill({ json: [{ id: definitionId, pieceName: 'قطعه آزمایشگاهی', rowNum: 1 }] });
            if (pathname === '/api/spectrometry/pieces') return route.fulfill({ json: [{ id: pieceId, pieceNumber: 9, archivedAt: iso(60) }] });
            if (pathname === '/api/spectrometry/batch') {
                const request = route.request().postDataJSON();
                batches.push(request);
                if (batches.length === 1) {
                    data.push(...request.rows.map((row, i) => ({
                        ...data[0], ...row, id: `saved-${i}`,
                    })));
                    return route.abort();
                }
                return route.fulfill({ json: { savedCount: request.rows.length, replayed: true } });
            }
            if (pathname === '/api/spectrometry/csv') {
                assert.equal(searchParams.get('pieceNumber'), '9');
                return route.fulfill({ contentType: 'text/csv', body: '\uFEFFMg\r\n0.1234567891' });
            }
            if (pathname.startsWith('/api/spectrometry/test-') || pathname.startsWith('/api/spectrometry/saved-')) {
                const id = pathname.split('/').at(-1);
                if (route.request().method() === 'PUT') {
                    const request = route.request().postDataJSON();
                    updates.push(request);
                    data = data.map(row => row.id === id ? { ...row, ...request.result, version: row.version + 1 } : row);
                } else {
                    deletions.push(id);
                    data = data.filter(row => row.id !== id);
                }
                return route.fulfill({ status: 204 });
            }
            if (pathname === '/api/spectrometry' || pathname === '/api/spectrometry/points') {
                const values = data.filter(row =>
                    (!searchParams.get('pieceNumber') || Number(searchParams.get('pieceNumber')) === row.pieceNumber) &&
                    (!searchParams.get('projectCode') || row.projectCode.includes(searchParams.get('projectCode'))) &&
                    (!searchParams.get('pieceName') || row.pieceName.includes(searchParams.get('pieceName'))) &&
                    (!searchParams.get('dateFrom') || row.testDate >= searchParams.get('dateFrom')) &&
                    (!searchParams.get('dateTo') || row.testDate <= searchParams.get('dateTo')))
                    .sort((a, b) => a.testDate.localeCompare(b.testDate) || a.id.localeCompare(b.id));
                if (pathname.endsWith('/points')) return route.fulfill({ json: values });
                if (searchParams.get('ascending') !== 'true') values.reverse();
                const size = Number(searchParams.get('size'));
                const page = Number(searchParams.get('page'));
                return route.fulfill({ json: {
                    items: values.slice(page * size, (page + 1) * size), page, size,
                    totalElements: values.length, totalPages: Math.ceil(values.length / size),
                } });
            }
            return route.fulfill({ json: [] });
        });

        await page.goto(origin);
        await page.evaluate(() => {
            AuthState.user = { username: 'synthetic-manager', role: 'MANAGER', permissions: [] };
            showApp();
        });
        const enter = async () => {
            await page.locator('#dashboard-tab-btn-reports').click();
            await page.locator('#btn-dashboard-spectrometry').click();
        };
        await enter();
        const drafts = page.locator('#spectrometry-drafts tr');
        const table = page.locator('#spectrometry-history');
        const chart = page.locator('#spectrometry-charts .spectrometry-chart');
        const canvas = chart.locator('canvas');
        const select = table.locator('[data-select-test="test-49"]');
        await select.waitFor();
        assert.equal(await table.locator('[data-select-test]:checked').count(), 0);
        assert.equal(await canvas.count(), 3);
        await chart.nth(0).getByText('افزایشی', { exact: true }).waitFor();
        assert.match(await chart.nth(0).locator('[data-metric="trend"]').textContent(), /افزایشی/);
        assert.match(await chart.nth(1).locator('[data-metric="trend"]').textContent(), /ثابت/);
        assert.match(await chart.nth(2).locator('[data-metric="trend"]').textContent(), /کاهشی/);
        assert.equal(await chart.nth(1).locator('[data-metric="r2"]').textContent(), '—');

        // A chart click selects the last test by its actual plotted coordinates.
        await waitForColor(page, 0, markColor.gray);
        const point = await colorPixels(canvas.nth(0), markColor.gray, true);
        await canvas.nth(0).click({ position: point });
        assert.equal(await select.isChecked(), true);
        for (let i = 0; i < 3; i++) await waitForColor(page, i, markColor.green);
        assert.match(await page.locator('#spectrometry-tooltip').textContent(), /LAB-26.*قطعه آزمایشگاهی/s);
        assert.match(await page.locator('#spectrometry-tooltip').textContent(), /انتخاب‌شده برای تحلیل/);
        const greenBefore = await colorPixels(canvas.nth(0), markColor.green);
        const grayBefore = await colorPixels(canvas.nth(0), markColor.gray);

        await page.locator('#spectrometry-page-size').selectOption('25');
        await page.locator('#spectrometry-next:not([disabled])').waitFor();
        await page.locator('#spectrometry-next').click();
        await table.locator('[data-select-test="test-0"]').waitFor();
        assert.equal(await select.count(), 0);
        await page.locator('#spectrometry-prev').click();
        await select.waitFor();
        assert.equal(await select.isChecked(), true);

        const mean = chart.nth(0).locator('[data-metric="mean"]');
        assert.notEqual(await mean.textContent(), '0.496 wt%');
        await page.locator('[name="spectrometry-basis"][value="selected"]').check();
        await chart.nth(0).getByText('0.496 wt%', { exact: true }).waitFor();
        assert.equal(await colorPixels(canvas.nth(0), markColor.green), greenBefore);
        assert.equal(await colorPixels(canvas.nth(0), markColor.gray), grayBefore);
        assert.match(await chart.nth(0).locator('.spectrometry-stat-note').textContent(), /دو تاریخ/);
        await chart.nth(0).getByLabel('نمایش میانگین').check();
        await waitForColor(page, 0, markColor.orange);
        await chart.nth(0).getByLabel('نمایش رگرسیون خطی').check();
        assert.equal(await colorPixels(canvas.nth(0), markColor.blue), 0);
        await page.locator('[name="spectrometry-basis"][value="all"]').check();
        await waitForColor(page, 0, markColor.blue);
        await page.locator('#spectrometry-clear-selection').click();
        assert.equal(await select.isChecked(), false);

        // Filter down to the two coincident observations. Both remain independently selectable.
        const firstJalali = await page.evaluate(date => isoToJalaliString(date), iso(0));
        await page.locator('#spectrometry-filter-from').fill(firstJalali);
        await page.locator('#spectrometry-filter-to').fill(firstJalali);
        await page.locator('#spectrometry-filter-number').fill('۹');
        await page.locator('#spectrometry-filter-project').fill('LAB');
        await page.locator('#spectrometry-filter-name').fill('آزمایشگاهی');
        await page.locator('#spectrometry-filters button[type="submit"]').click();
        await table.locator('[data-select-test="test-0"]').waitFor();
        assert.equal(await table.locator('[data-select-test]').count(), 2);
        await waitForColor(page, 0, markColor.gray);
        await canvas.nth(0).click({ position: await colorPixels(canvas.nth(0), markColor.gray, true) });
        const overlap = page.locator('#spectrometry-tooltip [data-point-id]');
        assert.equal(await overlap.count(), 2);
        await page.locator('#spectrometry-tooltip [data-point-id="test-0"]').click();
        assert.equal(await table.locator('[data-select-test="test-0"]').isChecked(), true);
        assert.equal(await table.locator('[data-select-test="test-1"]').isChecked(), false);
        const downloaded = page.waitForEvent('download');
        await page.locator('#spectrometry-export').click();
        assert.equal((await downloaded).suggestedFilename(), 'spectrometry.csv');

        await page.locator('#spectrometry-reset-filters').click();
        await select.waitFor();
        await page.locator('#spectrometry-sort').click();
        await table.locator('[data-select-test="test-0"]').waitFor();
        assert.equal(await table.locator('[data-select-test]').first().getAttribute('data-select-test'), 'test-0');

        // Repeated row operations append in place and preserve pre-existing form data.
        await page.locator('#spectrometry-add').evaluate(button => { for (let i = 0; i < 127; i++) button.click(); });
        assert.equal(await drafts.count(), 128);
        const draftDate = await drafts.first().locator('[data-input="date"]').inputValue();
        assert.match(draftDate, /^\d{4}\/\d{2}\/\d{2}$/);
        await drafts.nth(1).locator('[data-input="mg"]').fill('۱');
        await drafts.nth(1).getByRole('button', { name: 'حذف ردیف', exact: true }).evaluate(button => button.click());
        await drafts.last().getByRole('button', { name: 'حذف ردیف', exact: true }).evaluate(button => button.click());
        assert.equal(await drafts.count(), 126);
        assert.equal(await drafts.first().locator('[data-input="date"]').inputValue(), draftDate);
        await drafts.first().locator('[data-input="mg"]').fill('۰٫۱۲۳۴۵۶۷۸۹۱');
        await page.locator('#spectrometry-save').click();
        assert.match(await page.locator('#spectrometry-entry-error').textContent(), /ردیف ۱/);
        assert.equal(batches.length, 0);

        for (let i = 0; i < 2; i++) {
            const row = drafts.nth(i);
            await row.locator('[data-input="project"]').fill('LAB-26');
            await row.locator('[data-input="definition"]').fill('قطعه آزمایشگاهی · ردیف 1');
            await row.locator('[data-input="piece"]').fill('۹');
            for (const key of keys) await row.locator(`[data-input="${key}"]`).fill(key === 'mg' ? '۰٫۱۲۳۴۵۶۷۸۹۱' : '0');
            await row.locator('[data-input="date"]').fill(firstJalali);
        }
        const calendar = drafts.first().getByRole('button', { name: 'باز کردن تقویم' });
        await calendar.click();
        const popup = page.locator('.jalali-datepicker-popup.open');
        await popup.waitFor();
        const day = Number(firstJalali.split('/').at(-1));
        await popup.locator(`[data-day="${day}"]`).click();
        assert.equal(await drafts.first().locator('[data-input="date"]').inputValue(), firstJalali);
        const save = page.locator('#spectrometry-save');
        await save.click();
        await save.getByText('تلاش دوباره برای ذخیره').waitFor();
        assert.equal(batches.length, 1);
        await save.click();
        await table.locator('[data-select-test="saved-0"]').waitFor();
        assert.equal(await drafts.count(), 1);
        assert.deepEqual(batches[0], batches[1]);
        assert.equal(batches[0].rows.length, 2);
        assert.equal(batches[0].rows[0].mg, '0.1234567891');
        assert.equal(batches[0].rows[0].pieceId, pieceId);

        await table.locator('[data-edit-test="saved-0"]').click();
        await page.locator('#spectrometry-edit [data-input="piece"]').waitFor();
        const editor = page.locator('#spectrometry-edit');
        await page.waitForFunction(() => document.querySelector('#spectrometry-edit [data-input="piece"]').value === '9');
        await editor.locator('[data-input="mg"]').fill('0.23');
        await editor.getByRole('button', { name: 'ذخیره ویرایش' }).click();
        await table.locator('tr:has([data-select-test="saved-0"])').getByText('0.23', { exact: true }).waitFor();
        assert.equal(updates[0].result.mg, '0.23');
        await table.locator('[data-delete-test="saved-0"]').click();
        await page.locator('#spectrometry-cancel-delete').click();
        assert.equal(deletions.length, 0);
        await table.locator('[data-delete-test="saved-0"]').click();
        await page.locator('#spectrometry-delete button[type="submit"]').click();
        await table.locator('[data-select-test="saved-0"]').waitFor({ state: 'detached' });
        assert.deepEqual(deletions, ['saved-0']);

        await table.locator('[data-select-test="test-0"]').check();
        await page.locator('#spectrometry-back').click();
        await enter();
        await table.locator('[data-select-test]').first().waitFor();
        assert.equal(await table.locator('[data-select-test]:checked').count(), 0);
        await page.setViewportSize({ width: 390, height: 844 });
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 390);
        assert.ok(await page.locator('.spectrometry-entry-table').evaluate(table => table.parentElement.scrollWidth > table.parentElement.clientWidth));

        await page.locator('#spectrometry-back').click();
        await page.evaluate(() => {
            AuthState.user = { username: 'synthetic-viewer', role: 'OPERATOR', permissions: ['VIEW_SPECTROMETRY'] };
            renderCurrentUser();
        });
        await page.locator('#nav-btn-spectrometry').click();
        await table.locator('[data-select-test]').first().waitFor();
        assert.equal(await page.locator('#spectrometry-entry').isVisible(), false);
        assert.equal(await table.locator('[data-edit-test], [data-delete-test]').count(), 0);
        assert.deepEqual(errors, []);
        console.log('Spectrometry registration, retry, filters, CSV, chart interactions, permissions and mobile layout passed.');
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
