const assert = require('node:assert/strict');
const path = require('node:path');
const { chromium } = require('playwright');

// Run directly with Node; no server, datasource or runtime credentials are used.
(async () => {
    const browser = await chromium.launch({
        executablePath: process.env.CHROME_BIN || '/usr/bin/chromium', headless: true,
    });
    try {
        const page = await browser.newPage();
        const staticRoot = path.resolve(__dirname, '../../main/resources/static');
        await page.setContent('<html dir="rtl"><head></head><body><div id="fixture"></div></body></html>');
        await page.addStyleTag({ path: path.join(staticRoot, 'css/style.css') });
        await page.evaluate(() => {
            window.escapeHtml = value => String(value).replaceAll('&', '&amp;')
                .replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;')
                .replaceAll("'", '&#39;');
        });
        for (const script of ['radiography.js', 'radiography-report.js']) {
            await page.addScriptTag({ path: path.join(staticRoot, 'js', script) });
        }
        for (const width of [1440, 390]) {
            await page.setViewportSize({ width, height: 900 });
            for (const media of ['screen', 'print']) {
                await page.emulateMedia({ media });
                for (const first of ['ابعادی', 'ماسه ریزی', 'CR']) {
                    for (const second of ['F.M.M', 'ابعادی']) {
                      for (const located of [false, true]) {
                        const result = await page.evaluate(({ first, second, located }) => {
                            const defect = {
                                id: 'synthetic', reportLineId: 'synthetic-line', defectType: first,
                                welded: true, positions: located ? [{ startCm: 'L4', endCm: 'L4' }] : [],
                                secondDefect: { defectType: second, correctiveAction: 'OK' },
                            };
                            document.getElementById('fixture').innerHTML =
                                '<div class="rt-chain-text">' + formatDefectChainHtml(defect) + '</div>' +
                                '<table class="rt-sheet-table"><tr><td class="rt-defect-cell"><div class="rt-defect-list">' +
                                renderReportDefectChip(defect, true) + '</div></td></tr></table>' +
                                renderDefectsList([defect], false);
                            return [...document.querySelectorAll('.rt-chain-inline')].map(chain => {
                                const steps = [...chain.querySelectorAll('bdi')];
                                return {
                                    text: chain.textContent,
                                    steps: steps.map(el => el.textContent),
                                    lefts: steps.map(el => el.getBoundingClientRect().left),
                                };
                            });
                        }, { first, second, located });
                        assert.equal(result.length, 3);
                        for (const chain of result) {
                            const head = (located ? '(L4) ' : '') + first;
                            assert.deepEqual(chain.steps, [head, 'جوش', second, 'OK']);
                            assert.equal(chain.text, head + ' → جوش → ' + second + ' → OK');
                            assert.ok(chain.lefts.every((left, i) => i === 0 || left > chain.lefts[i - 1]),
                                JSON.stringify({ width, media, first, second, chain }));
                        }
                      }
                    }
                }
            }
        }
        // Escaping still applies to custom defect names and positions in both consumers.
        assert.equal(await page.evaluate(() => {
            const defect = { id: 'test', reportLineId: 'line', defectType: 'OTHER',
                defectTypeCustom: '<img src=x onerror=alert(1)>', welded: true,
                positions: [{ startCm: '<script>bad</script>' }] };
            document.getElementById('fixture').innerHTML = formatDefectChainHtml(defect) +
                renderReportDefectChip(defect, false);
            return document.querySelectorAll('#fixture img, #fixture script').length;
        }), 0);
        console.log('Radiography order passed: Persian/Latin defects, preview/report, desktop/mobile, screen/print, escaping.');
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
