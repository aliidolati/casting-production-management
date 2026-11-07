const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

// Real DOM/styles and API wrappers, synthetic read-only HTTP responses; no application/database startup.
(async () => {
    const root = path.resolve(__dirname, '../../main/resources/static');
    const output = path.resolve(__dirname, '../../../target/tracking-archive-browser');
    fs.mkdirSync(output, { recursive: true });
    const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8')
        .replace(/<script\b[^>]*>[\s\S]*?<\/script>/g, '')
        .replace(/<link\b[^>]*>/g, '');
    const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_BIN });
    const requests = [];
    const errors = [];
    try {
        const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
        page.on('pageerror', error => errors.push(error.message));
        await page.route('http://tracking.test/**', async route => {
            const url = new URL(route.request().url());
            if (!url.pathname.startsWith('/api/')) return route.fulfill({ contentType: 'text/html', body: html });
            requests.push(url);
            const archived = url.searchParams.get('includeArchived') === 'true';
            const items = [{ id: 'active', pieceNumber: 9, pieceName: 'قطعه آزمایشی', projectCode: 'SYNTHETIC', currentStage: 'CASTING' }];
            if (archived) items.push({ id: 'archived', pieceNumber: 19, pieceName: 'قطعه بایگانی', projectCode: 'SYNTHETIC', currentStage: 'SCRAPPED', archivedAt: '2026-01-01T00:00:00' });
            const subStage = url.searchParams.get('radiographySubStage');
            if (url.searchParams.get('stage') === 'RADIOGRAPHY') {
                items.length = 0;
                items.push({
                    id: 'radiography', pieceNumber: 20, pieceName: 'قطعه آزمایشی', projectCode: 'SYNTHETIC',
                    currentStage: 'RADIOGRAPHY', radiographySubStage: subStage || 'SHOT',
                });
            }
            const hours = { SHOT: 2.5, INTERPRETATION: 0 }[subStage];
            const body = url.pathname.endsWith('/summary')
                ? { totalCount: items.length, inProgressCount: 1, stageCounts: { CASTING: 1, SCRAPPED: archived ? 1 : 0 } }
                : { items, totalElements: items.length, page: 0, size: 50, totalPages: 1, radiographyBacklogHours: hours };
            await route.fulfill({ json: body });
        });
        await page.goto('http://tracking.test/');
        for (const css of ['tokens.css', 'style.css']) await page.addStyleTag({ path: path.join(root, 'css', css) });
        for (const js of ['jalali.js', 'persian-datepicker.js', 'api.js', 'app.js', 'auth.js', 'radiography.js', 'heat-treatment.js', 'rough-machining.js', 'tracking.js']) {
            await page.addScriptTag({ path: path.join(root, 'js', js) });
        }
        await page.evaluate(async () => {
            AuthState.user = { role: 'MANAGER', permissions: [] };
            document.getElementById('login-screen').style.display = 'none';
            document.getElementById('app-shell').classList.remove('hidden');
            document.getElementById('app-shell').style.display = 'block';
            document.querySelectorAll('.content-section').forEach(el => el.classList.remove('active'));
            document.getElementById('tracking-section').classList.add('active');
            setupTrackingArchiveListener(); setupBulkActionsListeners(); setupSearchListeners();
            setupAdvancedFilterListeners();
            await loadTracking();
        });
        const checkbox = page.getByRole('checkbox', { name: 'جست و جوی پیشرفته', exact: true });
        await checkbox.waitFor({ state: 'visible' });
        assert.equal(await checkbox.isChecked(), false);
        assert.equal(await page.locator('#tracking-table-body tr[data-piece-id]').count(), 1);
        await page.locator('#select-all-checkbox').check();
        await checkbox.focus();
        await page.keyboard.press('Space');
        await page.locator('tr.archived-piece-row').waitFor();
        assert.equal(await page.locator('#tracking-table-body tr[data-piece-id]').count(), 2);
        assert.equal(await page.locator('.archived-piece-row input[type=checkbox]').isDisabled(), true);
        assert.equal(await page.locator('.archived-piece-row .process-date-input').count(), 0);
        assert.equal(await page.evaluate(() => TrackingState.selectedPieceIds.size), 0);
        assert.match(await page.locator('#tracking-metrics').innerText(), /با احتساب بایگانی/);
        await page.locator('#select-all-checkbox').check();
        assert.deepEqual(await page.evaluate(() => [...TrackingState.selectedPieceIds]), ['active']);
        for (const width of [1440, 390]) {
            await page.setViewportSize({ width, height: 1000 });
            await checkbox.scrollIntoViewIfNeeded();
            const bounds = await checkbox.boundingBox();
            assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= width);
            assert.equal(await page.locator('.tracking-list-header').evaluate(el => el.scrollWidth <= el.clientWidth), true);
            await page.screenshot({ path: path.join(output, `tracking-${width}.png`), fullPage: true });
        }
        await checkbox.uncheck();
        await page.waitForFunction(() => document.querySelector('#tracking-table-body tr[data-piece-id=active]')
            && !document.querySelector('.archived-piece-row'));
        assert.equal(await page.locator('#tracking-table-body tr[data-piece-id]').count(), 1);
        assert.match(await page.locator('#tracking-metrics').innerText(), /بدون بایگانی/);
        for (const suffix of ['/tracking', '/tracking/summary']) {
            const matching = requests.filter(url => url.pathname.endsWith(suffix));
            assert.deepEqual(matching.map(url => url.searchParams.get('includeArchived')), ['false', 'true', 'false']);
        }
        const filters = page.locator('#tracking-section .advanced-filters');
        const backlog = filters.locator('#filter-radiography-substage + #tracking-radiography-backlog');
        const subStage = page.locator('#filter-radiography-substage');
        await page.locator('.stage-card[data-filter=RADIOGRAPHY]').click();
        assert.equal(await backlog.isVisible(), false);
        await subStage.selectOption('SHOT');
        await backlog.waitFor();
        assert.equal(await backlog.innerText(), 'انباشتگی شوت · ۲٫۵ ساعت');
        for (const width of [1440, 390]) {
            await page.setViewportSize({ width, height: 1000 });
            const bounds = await backlog.boundingBox();
            assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= width);
            assert.equal(await filters.evaluate(el => el.scrollWidth <= el.clientWidth), true);
            await filters.screenshot({ path: path.join(output, `tracking-rt-filters-${width}.png`) });
        }
        await subStage.selectOption('INTERPRETATION');
        await page.getByText('انباشتگی تفسیر · ۰ ساعت', { exact: true }).waitFor();
        await subStage.selectOption('VT');
        await page.locator('tr[data-piece-id=radiography]').waitFor();
        assert.equal(await backlog.isVisible(), false);
        assert.deepEqual(errors, []);
        console.log('Tracking archive browser smoke passed: keyboard toggle, list/summary HTTP params, bulk exclusion, read-only dates, desktop/mobile header and selected radiography backlog.');
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
