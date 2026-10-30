const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

(async () => {
    const browser = await chromium.launch({ channel: 'chrome', headless: true });
    const output = path.resolve(__dirname, '../../../target/performance-browser');
    fs.mkdirSync(output, { recursive: true });
    const errors = [];
    async function login(username) {
        const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
        const page = await context.newPage();
        page.on('pageerror', e => errors.push(e.message));
        await page.goto(process.env.PERFORMANCE_BASE);
        await page.locator('#input-login-username').fill(username);
        await page.locator('#input-login-password').fill(process.env.PERFORMANCE_PASSWORD);
        await page.locator('#form-login button[type=submit]').click();
        await page.locator('#app-shell').waitFor({ state: 'visible' });
        await page.evaluate(id => { location.hash = `#/profile/${id}`; }, process.env.PERFORMANCE_WORKER_ID);
        return { page, context };
    }
    async function criterion(page, title, maximum, weight) {
        await page.locator('#performance-add').click();
        await page.locator('#performance-title').fill(title);
        await page.locator('#performance-description').fill('شرح <آزمون> & "متن"');
        await page.locator('#performance-maximum').fill(maximum);
        await page.locator('#performance-weight').fill(weight);
        await page.locator('#performance-criterion-form button[type=submit]').click();
        await page.locator('#performance-criterion-form').waitFor({ state: 'detached' });
    }
    async function grade(page, score, reason) {
        await page.locator('[data-performance-edit]').first().click();
        await page.locator('#performance-score').fill(score);
        await page.locator('#performance-reason').fill(reason);
        await page.locator('#performance-criterion-form button[type=submit]').click();
        await page.locator('#performance-criterion-form').waitFor({ state: 'detached' });
    }
    const efficiency = page => page.locator('#performance-efficiency').innerText();
    try {
        const { page } = await login(process.env.PERFORMANCE_ADMIN);
        await page.locator('#performance-create').click();
        await page.locator('#performance-period-title').fill('مهر ۱۴۰۵');
        await page.locator('#performance-period-form button[type=submit]').click();
        await page.locator('#performance-add').waitFor();
        assert.match(await page.locator('#profile-performance').innerText(), /معیاری تعریف نشده/);
        await criterion(page, 'کیفیت <img src=x onerror=alert(1)>', '50', '3');
        assert.equal(await efficiency(page), '۱');
        await grade(page, '40', 'کاستی کیفیت');
        await criterion(page, 'نظم', '50', '1');
        assert.equal(await efficiency(page), '۰٫۸۵');
        assert.equal(await page.locator('#profile-performance img').count(), 0);
        const oldId = await page.locator('#performance-period').inputValue();
        await page.screenshot({ path: path.join(output, 'desktop.png'), fullPage: true });

        await page.locator('[data-performance-edit]').first().click();
        await page.locator('#performance-maximum').fill('30');
        await page.locator('#performance-criterion-form button[type=submit]').click();
        assert.match(await page.locator('#performance-form-error').innerText(), /نمره نمی‌تواند/);
        await page.locator('#performance-cancel').click();
        await page.locator('#performance-next').click();
        await page.locator('#performance-period-title').fill('آبان ۱۴۰۵');
        await page.locator('#performance-period-form button[type=submit]').click();
        await page.locator('#performance-period-form').waitFor({ state: 'detached' });
        await page.waitForFunction(() => document.querySelector('#performance-period')?.selectedOptions[0]?.textContent.includes('آبان'));
        assert.equal(await efficiency(page), '۱');
        const currentId = await page.locator('#performance-period').inputValue();
        await page.locator('#performance-period').selectOption(oldId);
        await page.locator('.performance-notice').waitFor();
        await grade(page, '45', 'اصلاح نمرهٔ دورهٔ قبل');
        assert.equal(await efficiency(page), '۰٫۹۲۵');
        await page.locator('#performance-history').click();
        await page.locator('.performance-history').waitFor();
        assert.match(await page.locator('.performance-history').innerText(), /اصلاح نمرهٔ دورهٔ قبل/);
        await page.locator('#performance-period').selectOption(currentId);
        await page.waitForFunction(() => !document.querySelector('.performance-notice') && document.querySelector('#performance-efficiency')?.textContent === '۱');
        await page.locator('[data-performance-delete]').last().click();
        await page.locator('#task-confirm-ok').click();
        await page.waitForFunction(() => document.querySelectorAll('[data-performance-delete]').length === 1);
        // Simulate another admin session saving while this view retains its old version.
        await page.evaluate(async () => {
            const s = PerformanceState, p = s.period, c = p.criteria[0];
            await PerformanceAPI.update(s.userId, p.id, c.id, { ...c, version: p.version, score: 49, reason: 'تغییر از نشست دوم' });
        });
        await page.locator('[data-performance-edit]').first().click();
        await page.locator('#performance-score').fill('48');
        await page.locator('#performance-reason').fill('درخواست با نسخهٔ قبلی');
        await page.locator('#performance-criterion-form button[type=submit]').click();
        await page.locator('#performance-form-error button').waitFor();
        await page.locator('#performance-form-error button').click();
        await page.waitForFunction(() => document.querySelector('#performance-efficiency')?.textContent === '۰٫۹۸');
        await grade(page, '50', 'بازگرداندن نمره پس از آزمون تداخل');
        await page.setViewportSize({ width: 390, height: 844 });
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
        await page.screenshot({ path: path.join(output, 'mobile.png'), fullPage: true });
        await page.locator('#performance-history').click(); // close history before the race
        // A slow response for another profile must not replace the current one.
        await page.route(`**/api/users/${process.env.PERFORMANCE_OTHER_ID}/performance/periods?*`, async route => {
            await new Promise(resolve => setTimeout(resolve, 350)); await route.continue();
        });
        await page.evaluate(async ids => {
            await openProfile(ids[0]);
            await openProfile(ids[1]);
        }, [process.env.PERFORMANCE_OTHER_ID, process.env.PERFORMANCE_WORKER_ID]);
        await page.locator('#performance-efficiency').waitFor();
        await page.waitForTimeout(500);
        assert.equal(await page.evaluate(() => PerformanceState.userId), process.env.PERFORMANCE_WORKER_ID);
        assert.equal(await efficiency(page), '۱');

        const self = await login(process.env.PERFORMANCE_WORKER);
        await self.page.locator('#performance-efficiency').waitFor();
        assert.equal(await self.page.locator('#performance-add, #performance-next, [data-performance-edit], [data-performance-delete]').count(), 0);
        await self.page.locator('#performance-history').click();
        await self.page.locator('.performance-history').waitFor();
        const manager = await login(process.env.PERFORMANCE_MANAGER);
        await manager.page.locator('.profile-name').waitFor();
        assert.equal(await manager.page.locator('#profile-performance').count(), 0);
        assert.deepEqual(errors, []);
        console.log('Performance desktop/mobile, grades, rollover, closed correction, audit, escaping, visibility and navigation smoke passed.');
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
