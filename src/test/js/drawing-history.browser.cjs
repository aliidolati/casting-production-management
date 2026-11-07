const assert = require('node:assert/strict');
const { chromium } = require('playwright');

(async () => {
    const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_BIN });
    try {
        const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        page.on('dialog', dialog => dialog.accept());
        const { DRAW_BASE, DRAW_USER, DRAW_PASSWORD, DRAW_PIECE, DRAW_DEFINITION, DRAW_PROJECT } = process.env;
        await page.goto(DRAW_BASE);
        await page.locator('#input-login-username').fill(DRAW_USER);
        await page.locator('#input-login-password').fill(DRAW_PASSWORD);
        await page.locator('#form-login button[type=submit]').click();
        await page.locator('#app-shell').waitFor({ state: 'visible' });
        const first = await page.evaluate(async ({ definition, piece }) => {
            const canvas = document.createElement('canvas');
            canvas.width = canvas.height = 32;
            const upload = async (color, name) => {
                const ctx = canvas.getContext('2d');
                ctx.fillStyle = color;
                ctx.fillRect(0, 0, 32, 32);
                const blob = await new Promise(resolve => canvas.toBlob(resolve));
                await PieceDefinitionAPI.uploadDrawing(definition, new File([blob], name, { type: 'image/png' }));
            };
            await upload('red', 'first.png');
            const { id } = await PieceDefinitionAPI.currentDrawingId(definition);
            await WeldingMapMarkAPI.create(piece, { xPercent: 0.3, yPercent: 0.4, defectType: 'CR', drawingRevisionId: id });
            await upload('blue', 'second.png');
            return id;
        }, { definition: DRAW_DEFINITION, piece: DRAW_PIECE });

        const openProject = () => page.evaluate(id => openProjectDetail(id), DRAW_PROJECT);
        const row = page.locator('tr[data-definition-id="' + DRAW_DEFINITION + '"]');
        const history = () => row.getByRole('button', { name: 'نسخه‌های نقشه' }).click();
        await openProject();
        await page.locator('#btn-add-definition').click();
        const create = page.locator('#form-add-definition');
        await create.locator('#input-row-num').fill('2');
        await create.locator('#input-piece-name').fill('Synthetic RT duration');
        await create.getByLabel('زمان شوت (ساعت)', { exact: true }).fill('1.25');
        await create.getByLabel('زمان تفسیر (ساعت)', { exact: true }).fill('0.75');
        await create.locator('#input-drawing').setInputFiles({
            name: 'synthetic.png',
            mimeType: 'image/png',
            buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+X2L8AAAAASUVORK5CYII=', 'base64'),
        });
        await create.getByRole('button', { name: 'ذخیره', exact: true }).click();
        const durationRow = page.getByRole('row').filter({ hasText: 'Synthetic RT duration' });
        const openDurationEdit = () => durationRow.locator('.btn-edit-definition').click();
        await openDurationEdit();
        const edit = page.locator('#form-edit-definition');
        const shotHours = edit.getByLabel('زمان شوت (ساعت)', { exact: true });
        const interpretationHours = edit.getByLabel('زمان تفسیر (ساعت)', { exact: true });
        await shotHours.waitFor();
        assert.equal(Number(await shotHours.inputValue()), 1.25);
        assert.equal(Number(await interpretationHours.inputValue()), 0.75);
        await shotHours.fill('');
        await interpretationHours.fill('1.5');
        await edit.getByRole('button', { name: 'ذخیره', exact: true }).click();
        await edit.waitFor({ state: 'detached' });
        await openProject();
        await openDurationEdit();
        await interpretationHours.waitFor();
        assert.equal(await shotHours.inputValue(), '');
        assert.equal(Number(await interpretationHours.inputValue()), 1.5);
        await page.locator('#modal-close').click();

        await history();
        await page.locator('[data-drawing-view="' + first + '"]').click();
        const image = page.locator('#modal-body img[alt="نقشه قطعه"]');
        await image.waitFor();
        await page.waitForFunction(() => document.querySelector('#modal-body img')?.naturalWidth === 32);
        const pixel = await image.evaluate(img => {
            const ctx = document.createElement('canvas').getContext('2d');
            ctx.drawImage(img, 0, 0);
            return Array.from(ctx.getImageData(0, 0, 1, 1).data);
        });
        assert.deepEqual(pixel, [255, 0, 0, 255]);
        const download = page.waitForEvent('download');
        await page.getByRole('link', { name: 'دریافت فایل' }).click();
        assert.equal(await (await download).failure(), null);
        await page.locator('#drawing-preview-back').click();
        await page.locator('[data-drawing-marks="' + first + '"]').click();
        await page.locator('#aggregate-welding-map-marks-layer .welding-map-mark').waitFor();
        const newer = await page.locator('#aggregate-drawing-revision option').first().getAttribute('value');
        await page.locator('#aggregate-drawing-revision').selectOption(newer);
        await page.waitForFunction(() => document.querySelector('#aggregate-welding-map-marks-layer')?.childElementCount === 0);
        await page.locator('#modal-close').click();

        await history();
        await page.locator('[data-drawing-restore="' + first + '"]').click();
        await page.locator('#modal-body tbody tr').first().getByText(/۳|3.*جاری/).waitFor();
        await page.locator('#modal-close').click();

        await page.setViewportSize({ width: 390, height: 844 });
        await row.locator('.btn-delete-definition').click();
        const form = page.locator('#catalog-delete-confirm');
        await form.waitFor();
        await page.locator('#modal-body').getByText('قطعات (فعال و بایگانی)', { exact: true }).waitFor();
        await page.locator('#catalog-delete-cancel').click();
        await row.locator('.btn-delete-definition').click();
        await form.waitFor();
        await page.evaluate(id => PieceNoteAPI.create(id, 'Synthetic dependency added after preview'), DRAW_PIECE);
        await form.getByRole('checkbox').check();
        await form.getByRole('button', { name: 'حذف نهایی' }).click();
        await page.locator('#catalog-delete-error').getByText(/وابستگی‌ها تغییر کرده‌اند/).waitFor();
        await page.locator('#catalog-delete-cancel').click();
        await row.locator('.btn-delete-definition').click();
        await form.getByRole('checkbox').check();
        await form.getByRole('button', { name: 'حذف نهایی' }).click();
        await row.waitFor({ state: 'detached' });
        assert.deepEqual(errors, []);
        console.log('Chromium drawing history, download, revision-specific marks, restore and stale deletion preview passed');
    } finally {
        await browser.close();
    }
})().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
});
