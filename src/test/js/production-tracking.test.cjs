const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { test } = require("node:test");

const staticRoot = path.resolve(__dirname, "../../main/resources/static");

function browserContext() {
    const nodes = new Map();
    const element = (id) => {
        if (!nodes.has(id)) nodes.set(id, {
            textContent: "", innerHTML: "", value: "", style: {}, dataset: {},
            classList: { toggle() {}, add() {}, remove() {} },
            listeners: {}, querySelectorAll: () => [],
            addEventListener(type, handler) { this.listeners[type] = handler; },
        });
        return nodes.get(id);
    };
    const context = vm.createContext({
        console, URLSearchParams, setTimeout, clearTimeout, hasPermission: () => false,
        document: { addEventListener() {}, getElementById: element, querySelectorAll: () => [] },
        window: { addEventListener() {} },
    });
    for (const file of ["app.js", "dashboard.js", "tracking.js", "production-flow.js"]) {
        vm.runInContext(fs.readFileSync(path.join(staticRoot, "js", file), "utf8"), context, { filename: file });
    }
    return { context, element, run: (source) => vm.runInContext(source, context) };
}

test("dashboard displays the existing scrap count and forwards its scope filters", async () => {
    const { context, element, run } = browserContext();
    let request;
    context.DashboardAPI = {
        getOverview: async (params) => {
            request = params;
            return { totalProjects: 1, totalPieces: 15, wipCount: 4, scrappedInPeriod: 7,
                scrapRatePercent: 46.7, stageCounts: {} };
        },
    };
    context.setElementLoading = () => {};
    for (const fn of ["ensureDashboardProjects", "renderBottlenecks", "renderStageDistribution",
        "renderNeedsWeldingAlerts", "renderStuckPieceAlerts", "renderSubStageOverdueAlerts",
        "renderRecentMoves", "renderPendingTensileAlerts", "syncDashboardFilterInputs",
        "renderDashboardFilterSummary", "updateDashboardLastUpdated", "startDashboardAutoRefresh"]) {
        context[fn] = () => {};
    }
    context.showToast = (message) => assert.fail(message);
    run('DashboardState.projectId = "project-a"; DashboardState.dateFrom = "2026-01-01"; DashboardState.dateTo = "2026-01-31";');
    await run("loadDashboard()");
    assert.equal(element("stat-scrapped-count").textContent, (7).toLocaleString("fa-IR"));
    assert.equal(request.projectId, "project-a");
    assert.equal(request.dateFrom, "2026-01-01");
    assert.equal(request.dateTo, "2026-01-31");
    const html = fs.readFileSync(path.join(staticRoot, "index.html"), "utf8");
    assert.match(html, /stat-card-secondary stat-card-scrapped/);
    assert.match(html, /id="stat-scrapped-count"/);
    assert.match(html, /قطعات اسقاط‌شده/);
});

test("tracking requests retain all filters and pagination alongside numeric input", () => {
    const { run } = browserContext();
    run(`Object.assign(TrackingState, {
        searchQuery: " ۹ ", pieceNameQuery: "housing", projectCodeQuery: "PR-9",
        activeFilter: "RADIOGRAPHY", radiographySubStageFilter: "VT",
        dateFrom: "2026-01-01", dateTo: "2026-01-31", sortBy: "pieceNumber", sortDir: "asc", page: 2, pageSize: 20
    });`);
    const params = JSON.parse(JSON.stringify(run("trackingListParams()")));
    assert.deepEqual(params, {
        q: " ۹ ", includeArchived: false, pieceName: "housing", projectCode: "PR-9", stage: "RADIOGRAPHY", radiographySubStage: "VT",
        dateFrom: "2026-01-01", dateTo: "2026-01-31", sortBy: "pieceNumber", sortDir: "asc", page: 2, size: 20,
    });
    const summary = JSON.parse(JSON.stringify(run("trackingSummaryParams()")));
    assert.equal(summary.q, params.q);
    assert.equal(summary.projectCode, params.projectCode);
    assert.equal(summary.pieceName, params.pieceName);
});

test("entering a piece number preserves the selected production stage and column/date filters", () => {
    const { context, element, run } = browserContext();
    let calls = 0;
    context.loadTracking = options => { assert.equal(options.resetPage, true); calls++; };
    run('TrackingState.activeFilter = "RADIOGRAPHY"; TrackingState.radiographySubStageFilter = "VT"; TrackingState.projectCodeQuery = "PR"; TrackingState.dateFrom = "2026-01-01"; setupSearchListeners();');
    const input = element("tracking-filter-piece-number");
    input.value = " 9 ";
    input.listeners.keydown({ key: "Enter", target: input, preventDefault() {} });
    assert.equal(run("TrackingState.activeFilter"), "RADIOGRAPHY");
    assert.equal(run("TrackingState.radiographySubStageFilter"), "VT");
    assert.equal(run("TrackingState.projectCodeQuery"), "PR");
    assert.equal(run("TrackingState.dateFrom"), "2026-01-01");
    assert.equal(element("tracking-search-input").value, "9");
    assert.equal(calls, 1);
});

test("tensile decision and each recorded aging duration use separate tensile-colored boxes", () => {
    const { context, run } = browserContext();
    const render = (stage, info) => {
        context.fixturePiece = { currentStage: stage, tensileTrackingInfo: info };
        return run("renderTensileTrackingInfo(fixturePiece)");
    };
    const aging = render("TENSILE", { nextStage: "AGING", agingDurationMinutes: 90 });
    assert.match(aging, />پیرسازی</);
    assert.doesNotMatch(aging, /تعیین‌تکلیف/);
    assert.match(aging, /۹۰<sup>دقیقه<\/sup>/);
    assert.match(aging, /tensile-disposition-info/);
    assert.match(aging, /tensile-aging-duration/);
    assert.doesNotMatch(aging, /data-sub|data-stage|select|input/);
    for (const minutes of [null, undefined, "", "bad"]) {
        assert.doesNotMatch(render("TENSILE", { nextStage: "AGING", agingDurationMinutes: minutes }), /دقیقه/);
    }
    assert.match(render("TENSILE", { nextStage: "AGING", agingDurationMinutes: 0 }), /۰<sup>دقیقه<\/sup>/);
    assert.match(render("TENSILE", { nextStage: "AGING", agingDurationMinutes: 79.8 }), /۷۹٫۸<sup>دقیقه<\/sup>/);
    const repeat = render("TENSILE", { nextStage: "NEXT_TENSILE", agingDurationsMinutes: [30, 60] });
    assert.match(repeat, /کشش بعدی/);
    assert.match(repeat, /۳۰<sup>دقیقه<\/sup>[\s\S]*۶۰<sup>دقیقه<\/sup>/);
    assert.match(render("TENSILE", { nextStage: "RE_OPERATION", agingDurationMinutes: 120 }), /عملیات مجدد/);
    assert.doesNotMatch(render("TENSILE", { nextStage: "RE_OPERATION", agingDurationMinutes: 120 }), /دقیقه/);
    for (const stage of ["RADIOGRAPHY", "HEAT_TREATMENT", "SCRAPPED"]) {
        assert.equal(render(stage, { nextStage: "AGING", agingDurationMinutes: 90 }), "");
    }
    assert.equal(render("TENSILE", null), "");
    assert.equal(render("TENSILE", { nextStage: "APPROVED" }), "");
    assert.equal(render("TENSILE", { nextStage: '<img src=x onerror="alert(1)">' }), "");
});

test("profile timeline keeps the original production route while showing execution dates", () => {
    const { context, run } = browserContext();
    context.fixturePiece = { currentStage: "TENSILE", processDate: "2026-01-06" };
    context.fixtureHistory = [
        { id: "cast", stage: "CASTING", changedAt: "2026-02-10T10:00:00", changedBy: "casting" },
        { id: "tensile", stage: "TENSILE", changedAt: "2026-02-01T10:00:00" },
    ];
    context.fixtureSub = [
        { id: "shot", kind: "RADIOGRAPHY", subStage: "SHOT", changedAt: "2026-02-05T10:00:00" },
        { id: "vt", kind: "RADIOGRAPHY", subStage: "VT", changedAt: "2026-02-02T10:00:00" },
    ];
    context.fixtureDates = [
        { stage: "CASTING", processDate: "2026-01-04" },
        { kind: "RADIOGRAPHY", subStage: "SHOT", processDate: "2026-01-02" },
        { kind: "RADIOGRAPHY", subStage: "VT", processDate: "2026-01-02" },
    ];
    const original = JSON.stringify([context.fixtureHistory, context.fixtureSub, context.fixtureDates]);
    const steps = run("decorateProductionFlow(fixturePiece, fixtureHistory, fixtureSub, fixtureDates, []).steps");
    assert.deepEqual(Array.from(steps.filter(s => s.processDate), s => s.id), ["CASTING", "RADIO:SHOT", "RADIO:VT", "TENSILE"]);
    assert.equal(steps[0].id, "NOT_STARTED");
    assert.ok(steps.findIndex(s => s.id === "NOT_STARTED") < steps.findIndex(s => s.status === "current"));
    assert.deepEqual(Array.from(steps.filter(s => s.status === "upcoming"), s => s.id), ["SHOT_BLASTING", "READY"]);
    assert.equal(JSON.stringify([context.fixtureHistory, context.fixtureSub, context.fixtureDates]), original);
    const html = run("renderProductionFlow(fixturePiece, fixtureHistory, fixtureSub, fixtureDates, [])");
    assert.match(html, /طبق ترتیب مسیر تولید/);
    assert.match(html, /تاریخ انجام/);
});

test("missing process dates keep deterministic flow order and actor selection independent of API order", () => {
    const { context, run } = browserContext();
    context.fixturePiece = { currentStage: "TENSILE" };
    context.fixtureHistory = [
        { id: "a", stage: "CASTING", changedAt: "2026-02-01T10:00:00", changedBy: "old" },
        { id: "b", stage: "CASTING", changedAt: "2026-02-02T10:00:00", changedBy: "latest" },
    ];
    assert.equal(run('flowOperator({stage:"CASTING"}, fixtureHistory, [])'), "latest");
    assert.equal(run('flowOperator({stage:"CASTING"}, fixtureHistory.slice().reverse(), [])'), "latest");
    const a = run("decorateProductionFlow(fixturePiece, fixtureHistory, [], [], []).steps.map(s => s.id)");
    const b = run("buildProductionFlowSteps(fixturePiece, []).map(s => s.id)");
    assert.deepEqual(Array.from(a), Array.from(b));
});


test("archive toggle reloads list and summary while preserving search filters", async () => {
    const { context, element, run } = browserContext();
    const requests = [];
    context.PieceAPI = {
        listTracking: async params => { requests.push(['list', { ...params }]); return { items: [], totalElements: 0 }; },
        getTrackingSummary: async params => { requests.push(['summary', { ...params }]); return { totalCount: 0 }; },
    };
    context.showTableLoader = () => {};
    context.canUseBulkSelection = () => false;
    context.renderTrackingTable = () => {};
    context.renderTrackingMetrics = () => {};
    context.renderStageCards = () => {};
    context.showToast = message => assert.fail(message);
    run('TrackingState.page = 3; TrackingState.selectedPieceIds.add("old"); TrackingState.searchQuery = "9"; TrackingState.activeFilter = "SCRAPPED"; setupTrackingArchiveListener();');
    const checkbox = element('tracking-advanced-search');
    assert.equal(checkbox.checked, false);
    for (const checked of [true, false]) {
        checkbox.checked = checked;
        checkbox.listeners.change();
        await new Promise(resolve => setImmediate(resolve));
        assert.equal(run('TrackingState.page'), 0);
        assert.equal(run('TrackingState.selectedPieceIds.size'), 0);
        assert.equal(requests.at(-2)[1].includeArchived, checked);
        assert.equal(requests.at(-1)[1].includeArchived, checked);
        assert.equal(requests.at(-2)[1].q, '9');
        assert.equal(requests.at(-2)[1].stage, 'SCRAPPED');
    }
});

test("archived rows cannot join bulk selection or expose editable process dates", () => {
    const { context, element, run } = browserContext();
    context.hasPermission = () => true;
    context.fixturePieces = [{ id: 'active' }, { id: 'archived', archivedAt: '2026-01-01T00:00:00' }];
    run('TrackingState.pieces = fixturePieces; setupBulkActionsListeners();');
    context.renderTrackingTable = () => {};
    element('select-all-checkbox').listeners.change({ target: { checked: true } });
    assert.deepEqual(Array.from(run('[...TrackingState.selectedPieceIds]')), ['active']);
    run('updateSelectAllCheckboxState(fixturePieces)');
    assert.equal(element('select-all-checkbox').checked, true);
    run('updateSelectAllCheckboxState([fixturePieces[1]])');
    assert.equal(element('select-all-checkbox').disabled, true);
    assert.equal(element('select-all-checkbox').checked, false);
    assert.equal(run('renderProcessDateCell(fixturePieces[1])'), '—');
    assert.match(run('renderProcessDateCell(fixturePieces[0])'), /process-date-input/);
});
