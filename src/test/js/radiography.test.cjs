const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

function fixture() {
    const context = vm.createContext({
        escapeHtml: value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;'),
    });
    for (const filename of ['radiography.js', 'radiography-report.js']) {
        vm.runInContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js', filename), 'utf8'), context);
    }
    return code => vm.runInContext(code, context);
}

test('text points and ranges preserve labels and remain compatible with numeric snapshots', () => {
    const run = fixture();
    assert.equal(run(`formatPositionsText([{startCm:'L4',endCm:'L4'}, {startCm:'L5',endCm:'L7'},
        {startCm:12,endCm:20},{startCm:25,endCm:'25'},{startCm:'01',endCm:'1'}])`), '(L4)(L5-L7)(12-20)(25)(01-1)');
    assert.equal(run(`formatDefectLocation({startCm:'L4',endCm:'L4'})`), 'L4');
    assert.equal(run(`formatDefectLocation({startCm:'L4',endCm:'L7'})`), 'L4-L7');
    assert.equal(run(`formatDefectLocation({startCm:12,endCm:20})`), '12-20 cm');
    assert.equal(run(`formatStructuredDefect({defectType:'CR',startCm:'L4',endCm:'L4'})`), 'CR(L4)');
});

test('reshoot precedes welding in text and preview while other chains retain their order', () => {
    const run = fixture();
    run(`var draft = {defectType:'F.M.M',defectClass:'T1',welded:true,reshootOk:true,positions:[{startCm:'L4',endCm:'L4'}]}`);
    assert.equal(run('formatDefectChainText(draft)'), '(L4) F.M.M T1 → ریشوت → جوش → OK');
    const html = run('renderDefectChainVisual(draft)');
    assert.ok(html.indexOf('ریشوت') < html.indexOf('جوش'));
    assert.ok(html.indexOf('جوش') < html.indexOf('>OK<'));
    assert.equal(run(`formatDefectChainText({...draft,welded:false})`), '(L4) F.M.M T1 → ریشوت → OK');
    assert.equal(run(`formatDefectChainText({...draft,reshootOk:false,secondDefect:{defectType:'CR',correctiveAction:'OK'}})`),
        '(L4) F.M.M T1 → جوش → CR → OK');
});

test('text positions are escaped in report chips and the visual preview', () => {
    const run = fixture();
    run(`var defect = {id:'synthetic', reportLineId:'synthetic-line', defectType:'CR',
        positions:[{startCm:'L<test>',endCm:'L<test>'}]}`);
    for (const html of [run('renderDefectChainVisual(defect)'), run('renderReportDefectChip(defect,false)')]) {
        assert.ok(html.includes('L&lt;test&gt;'));
        assert.ok(!html.includes('L<test>'));
    }
});
