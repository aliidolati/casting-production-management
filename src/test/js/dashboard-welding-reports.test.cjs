const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const staticRoot = path.resolve(__dirname, '../../main/resources/static');

function fixture() {
    const nodes = new Map();
    const node = id => {
        if (!nodes.has(id)) nodes.set(id, {
            value: '', innerHTML: '', textContent: '', style: {}, dataset: {}, listeners: {},
            classList: { toggle() {}, add() {}, remove() {} },
            querySelectorAll: () => [],
            addEventListener(type, handler) { this.listeners[type] = handler; },
        });
        return nodes.get(id);
    };
    const context = vm.createContext({
        console, URLSearchParams, setTimeout, clearTimeout,
        hasPermission: () => true,
        document: { addEventListener() {}, getElementById: node, querySelectorAll: () => [] },
        window: { addEventListener() {} },
    });
    for (const file of ['app.js', 'dashboard.js', 'radiography.js', 'heat-treatment.js', 'rough-machining.js',
        'welding-station.js', 'tensile-quality-report.js', 'tensile-trend-report.js']) {
        vm.runInContext(fs.readFileSync(path.join(staticRoot, 'js', file), 'utf8'), context, { filename: file });
    }
    const run = source => vm.runInContext(source, context);
    let exported;
    context.downloadCsv = (filename, lines) => { exported = { filename, lines }; };
    context.showToast = message => { context.lastToast = message; };
    return { context, node, run, exported: () => exported };
}

test('quality CSV matches the visible range and preserves quoted text, zero and missing values', () => {
    const view = fixture();
    view.run(`TensileQualityState.points = [
        { projectCode: 'P', pieceName: 'بدنه, "آزمایشی"', pieceNumber: 1, sampleLabel: 'A', ys: 300, el: 0, q: null, band: 'UNDEFINED' },
        { projectCode: 'P', pieceName: 'Other', pieceNumber: 2, sampleLabel: 'B', ys: 500, el: 2, q: 0.2, band: 'RED' }
    ];`);
    view.node('tensile-quality-ys-max').value = '400';
    view.run('exportTensileQualityCsv()');
    const result = view.exported();
    assert.match(result.filename, /^tensile-quality-.*\.csv$/);
    assert.equal(result.lines.length, 2);
    assert.equal(result.lines[1], 'P,"بدنه, ""آزمایشی""",1,A,300,0,,UNDEFINED');
    view.node('tensile-quality-ys-min').value = '450';
    view.run('exportTensileQualityCsv()');
    assert.match(view.node('tensile-quality-error').textContent, /حد بالای YS/);
});

test('trend CSV includes every filtered sample with all three parameters', () => {
    const view = fixture();
    view.context.formatDateTime = value => `date:${value}`;
    view.run(`TensileTrendState.report = { samples: [
        {projectCode:'P',pieceName:'قطعه',pieceNumber:7,sampleLabel:'A',testedAt:'2026-01-10T09:00:00',uts:250.25,ys:0,el:null},
        {projectCode:'P',pieceName:'قطعه',pieceNumber:7,sampleLabel:'B',testedAt:'2026-01-11T09:00:00',uts:null,ys:200,el:5.5}
    ] }; exportTensileTrendCsv();`);
    assert.equal(view.exported().lines.length, 3);
    assert.equal(view.exported().lines[1], 'P,قطعه,7,A,date:2026-01-10T09:00:00,250.25,0,');
    assert.equal(view.exported().lines[2], 'P,قطعه,7,B,date:2026-01-11T09:00:00,,200,5.5');
});

test('stage table and CSV include localized substages without claiming unknown exits are current', () => {
    const view = fixture();
    view.context.formatDate = value => value;
    view.run(`OccupancyReportState.rows = [{pieceId:'id',projectCode:'P',rowNum:1,pieceName:'قطعه',pieceNumber:7,
        stage:'RADIOGRAPHY',subStageKind:'RADIOGRAPHY',subStage:'INTERPRETATION',enteredAt:'2026-01-10',leftAt:null,stillInStage:false}];
        renderOccupancyReportTable(OccupancyReportState.rows); exportOccupancyReportCsv();`);
    assert.match(view.node('occupancy-report-table-body').innerHTML, /تفسیر/);
    assert.doesNotMatch(view.node('occupancy-report-table-body').innerHTML, /هنوز در این مرحله/);
    assert.match(view.exported().lines[0], /مرحله,زیرمرحله/);
    assert.match(view.exported().lines[1], /تفسیر,2026-01-10,,خیر$/);
});

test('welding edit form restores values, sends the nested PATCH and refreshes the piece', async () => {
    const view = fixture();
    let modal;
    let payload;
    let refreshed;
    view.context.openNestedModal = (title, html) => { modal = { title, html }; };
    view.context.closeNestedModal = () => {};
    view.context.loadTracking = () => {};
    view.context.openPieceProfile = async id => { refreshed = id; };
    view.context.withButtonLoader = async (button, action) => action();
    view.context.PieceAPI = { updateWeldingDispatch: async (...args) => { payload = args; } };
    view.run(`openSendToWeldingPopup({id:'piece'}, [], {id:'dispatch',defectType:'OTHER',defectTypeCustom:'عیب سفارشی',notes:'<شرح>',defectIds:[]})`);
    assert.equal(modal.title, 'ویرایش عیب جوشکاری');
    assert.match(modal.html, /&lt;شرح&gt;/);
    assert.equal(view.node('welding-defect-type').value, 'OTHER');
    assert.equal(view.node('welding-defect-type-custom').value, 'عیب سفارشی');
    assert.equal(view.node('welding-defect-type-custom-wrap').style.display, '');
    view.node('welding-notes').value = '  اصلاح  ';
    await view.node('form-send-to-welding').listeners.submit({ preventDefault() {}, submitter: {} });
    assert.equal(payload[0], 'piece');
    assert.equal(payload[1], 'dispatch');
    assert.equal(payload[2].notes, 'اصلاح');
    assert.equal(payload[2].defectTypeCustom, 'عیب سفارشی');
    assert.equal(refreshed, 'piece');
    assert.match(view.run(`renderWeldingStationSection({currentStage:'WELDING'}, [{open:true,defectType:'VT'}])`), /btn-edit-welding-defect/);
    assert.doesNotMatch(view.run(`renderWeldingStationSection({currentStage:'READY'}, [])`), /btn-edit-welding-defect/);
});


test('scrap report preserves archived/legacy rows, escapes reasons and exports origin substage', () => {
    const view = fixture();
    view.context.formatDateTime = value => value;
    view.run(`ScrapReportState.rows = [
        {pieceId:'id',projectCode:'P',pieceName:'قطعه',pieceNumber:7,defectLabel:'عیب <ساختگی>',notes:'شرح, "خاص"',
            sentAt:'2026-10-31',originStage:'RADIOGRAPHY',originSubStage:'INTERPRETATION',archived:true},
        {pieceId:'old',projectCode:'P',pieceName:'قدیمی',pieceNumber:8,defectLabel:'ثبت نشده',archived:false}
    ]; renderScrapReportTable(ScrapReportState.rows); exportScrapReportCsv();`);
    const html = view.node('scrap-report-table-body').innerHTML;
    assert.match(html, /عیب &lt;ساختگی&gt;/);
    assert.doesNotMatch(html, /عیب <ساختگی>/);
    assert.match(html, /بایگانی‌شده/);
    assert.match(html, /بایگانی‌نشده/);
    assert.match(html, /تفسیر/);
    assert.equal(view.exported().lines.length, 3);
    assert.match(view.exported().lines[1], /"شرح, ""خاص"""/);
    assert.match(view.exported().lines[1], /تفسیر/);
    assert.match(view.exported().lines[2], /ثبت نشده/);
});

test('stage filter is passed to the report and invalidates a previous export', async () => {
    const view = fixture();
    let request;
    view.context.showTableLoader = () => {};
    view.context.withButtonLoader = async (button, action) => action();
    view.context.formatDate = value => value;
    view.context.DashboardAPI = {
        getStageOccupancyReport: async filters => {
            request = filters;
            return { projectCode: 'P', rows: [{ stage: filters.stage, enteredAt: '2026-01-10' }] };
        }
    };
    view.run('initOccupancyReportUi()');
    const stage = view.node('occupancy-report-stage');
    assert.match(stage.innerHTML, /value="RADIOGRAPHY"/);
    stage.value = 'RADIOGRAPHY';
    view.run("OccupancyReportState.dateFrom = '2026-01-01'; OccupancyReportState.dateTo = '2026-01-31';");
    await view.run('runOccupancyReport()');
    assert.equal(request.stage, 'RADIOGRAPHY');
    view.run('exportOccupancyReportCsv()');
    assert.match(view.exported().filename, /-radiography-/);
    stage.value = 'HEAT_TREATMENT';
    stage.listeners.change();
    assert.equal(view.node('btn-occupancy-report-export').disabled, true);
    view.run('clearOccupancyReportFilters()');
    assert.equal(stage.value, '');
});

test('welding history exports the displayed completions and ignores stale live responses', async () => {
    const view = fixture();
    const requests = [];
    let finishLive;
    view.context.showTableLoader = () => {};
    view.context.withButtonLoader = async (button, action) => action();
    view.context.formatDateTime = value => 'date:' + value;
    view.context.DashboardAPI = {
        getWeldingStationReport: filters => {
            requests.push(filters);
            return filters.history
                ? Promise.resolve([{ pieceId: 'piece', projectCode: 'P', pieceName: 'کابین', pieceNumber: 7,
                    sentAt: 'sent', completedBy: 'نام, کاربر', completedAt: 'done' }])
                : new Promise(resolve => { finishLive = resolve; });
        }
    };
    view.run('initWeldingReportUi()');
    const live = view.run('runWeldingReport()');
    const toggle = view.node('welding-report-history');
    toggle.checked = true;
    toggle.listeners.change();
    assert.equal(view.node('btn-welding-report-export').disabled, true);
    assert.equal(view.node('welding-report-completed-at').hidden, false);
    await view.run('runWeldingReport()');
    finishLive([{ pieceName: 'قدیمی' }]);
    await live;
    assert.deepEqual(requests.map(request => request.history), [false, true]);
    assert.match(view.node('welding-report-table-body').innerHTML, /date:done/);
    assert.doesNotMatch(view.node('welding-report-table-body').innerHTML, /قدیمی/);
    view.run('exportWeldingReportCsv()');
    assert.match(view.exported().filename, /^welding-history-/);
    assert.match(view.exported().lines[0], /ثبت‌کننده اتمام,تاریخ اتمام$/);
    assert.match(view.exported().lines[1], /"نام, کاربر",date:done$/);
});

test('quality hover shows the hovered test metadata and fits the tooltip inside the chart', () => {
    const view = fixture();
    view.context.formatDateTime = value => value;
    const tooltip = view.node('tensile-quality-tooltip');
    Object.assign(tooltip, { offsetWidth: 200, offsetHeight: 170 });
    view.run(`TensileQualityState.hits = [
        { x: 30, y: 30, point: { pieceName: 'کابین دیگر', testIndex: 1, uts: 200 } },
        { x: 270, y: 330, point: { projectCode: 'P-123', pieceName: '<کابین>', pieceNumber: 7,
            testIndex: 2, testedAt: '2026-01-10', testStatus: 'APPROVED', sampleLabel: 'R1',
            uts: 260, ys: 200, el: 0, hb: null, q: null, sampleDescription: '<توضیح>' } }
    ];
    const chartRect = { left: 0, top: 0, width: 280, height: 350 };
    onTensileQualityHover({ clientX: 270, clientY: 330, currentTarget: {
        getBoundingClientRect: () => chartRect, parentElement: { getBoundingClientRect: () => chartRect }
    } });`);
    assert.equal(tooltip.hidden, false);
    assert.match(tooltip.innerHTML, /کد پروژه.*P-123/);
    assert.match(tooltip.innerHTML, /نام کابین.*&lt;کابین&gt;/);
    assert.match(tooltip.innerHTML, /شماره کابین.*۷/);
    assert.match(tooltip.innerHTML, /شماره آزمون.*۲/);
    assert.match(tooltip.innerHTML, /UTS: ۲۶۰/);
    assert.match(tooltip.innerHTML, /EL: ۰/);
    assert.match(tooltip.innerHTML, /HB: —/);
    assert.match(tooltip.innerHTML, /&lt;توضیح&gt;/);
    assert.doesNotMatch(tooltip.innerHTML, /کابین دیگر/);
    assert.equal(tooltip.style.left, '58px');
    assert.equal(tooltip.style.top, '148px');
});

test('text defect details escape markup and export one CSV row per course entry', () => {
    const { run, exported } = fixture();
    const defects = [
        { course: 'C1', text: 'L4, "بررسی"\n<نمونه>' },
        { course: 'C2', text: '=example' },
    ];
    run(`var textDefects = ${JSON.stringify(defects)};`);
    const html = run('renderFreeTextDefectDetails(textDefects)');
    assert.ok(html.includes('L4, &quot;بررسی&quot;\n&lt;نمونه&gt;'));
    assert.ok(html.includes('C2'));
    run(`DefectReportState.rows = [{ projectCode: 'P1', rowNum: 1, pieceName: 'قطعه', defectType: 'F.M.L',
        pieces: [{ pieceNumber: 9, freeTextDefects: textDefects }] }];
        exportDefectReportTextCsv();`);
    assert.equal(exported().lines[1], 'P1,1,قطعه,F.M.L,9,C1,"L4, ""بررسی""\n<نمونه>"');
    assert.equal(exported().lines[2], "P1,1,قطعه,F.M.L,9,C2,'=example");
});
