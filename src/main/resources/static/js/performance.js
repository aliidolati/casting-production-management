/** Independent profile evaluations. Every request/form captures its user, period and version. */
let PerformanceState = null;

function performanceNumber(value, digits = 2) {
    if (value === null || value === undefined) return "—";
    return new Intl.NumberFormat("fa-IR", { maximumFractionDigits: digits }).format(value);
}

function performanceCurrent(s) {
    return PerformanceState === s && s.root.isConnected && ProfileState.userId === s.userId &&
        document.getElementById("profile-section").classList.contains("active");
}

async function mountPerformance(userId) {
    const root = document.getElementById("profile-performance");
    if (!root) return;
    const s = { userId, root, page: 0, periods: null, period: null, seq: 0, historySeq: 0, historyPage: 0, showHistory: false };
    PerformanceState = s;
    await loadPerformancePeriods(s);
}

async function loadPerformancePeriods(s, selectedId = null) {
    const seq = ++s.seq;
    s.historySeq++;
    s.root.innerHTML = `<h3 class="task-card-title">ارزیابی عملکرد</h3><p>در حال دریافت دوره‌ها…</p>`;
    try {
        const result = await PerformanceAPI.list(s.userId, { page: s.page, size: 12 });
        if (!performanceCurrent(s) || seq !== s.seq) return;
        s.periods = result;
        const id = selectedId || result.items[0]?.id;
        s.period = id ? await PerformanceAPI.get(s.userId, id) : null;
        if (!performanceCurrent(s) || seq !== s.seq) return;
        s.historyPage = 0;
        renderPerformance(s);
    } catch (error) { performanceLoadError(s, seq, error); }
}

async function loadPerformancePeriod(s, id) {
    const seq = ++s.seq;
    s.historySeq++;
    s.root.setAttribute("aria-busy", "true");
    // Disable actions while the selector points at a different period than the displayed rows.
    s.root.querySelectorAll("button").forEach(button => { button.disabled = true; });
    try {
        const period = await PerformanceAPI.get(s.userId, id);
        if (!performanceCurrent(s) || seq !== s.seq) return;
        s.period = period; s.historyPage = 0;
        renderPerformance(s);
    } catch (error) { performanceLoadError(s, seq, error); }
}

function performanceLoadError(s, seq, error) {
    if (!performanceCurrent(s) || seq !== s.seq) return;
    s.root.removeAttribute("aria-busy");
    s.root.innerHTML = `<h3 class="task-card-title">ارزیابی عملکرد</h3><p role="alert">${escapeHtml(error.message)}</p><button type="button" class="btn btn-secondary" id="performance-retry">تلاش دوباره</button>`;
    s.root.querySelector("#performance-retry").onclick = () => loadPerformancePeriods(s);
}

function renderPerformance(s) {
    const p = s.period;
    // A concurrent rollover may move the selected historical period to another list page.
    const options = p && !s.periods.items.some(row => row.id === p.id) ? [p, ...s.periods.items] : s.periods.items;
    s.root.removeAttribute("aria-busy");
    s.root.innerHTML = `
        <div class="performance-toolbar"><h3 class="task-card-title">ارزیابی عملکرد</h3>
            ${p ? `<label class="performance-period-label">دوره <select id="performance-period">${options.map(row =>
                `<option value="${escapeHtml(row.id)}" ${row.id === p.id ? "selected" : ""}>${escapeHtml(row.title)} — ${row.status === "OPEN" ? "جاری" : "نهایی"}</option>`).join("")}</select></label>` : ""}
        </div>
        ${s.periods.totalPages > 1 ? `<div class="performance-toolbar"><span>صفحهٔ دوره‌ها: ${performanceNumber(s.page + 1)} از ${performanceNumber(s.periods.totalPages)}</span><div><button type="button" class="btn btn-secondary btn-sm" id="performance-period-prev" ${s.page === 0 ? "disabled" : ""}>جدیدتر</button> <button type="button" class="btn btn-secondary btn-sm" id="performance-period-next" ${s.page + 1 >= s.periods.totalPages ? "disabled" : ""}>قدیمی‌تر</button></div></div>` : ""}
        ${p ? `
            <div class="performance-summary">
                <div><span>راندمان ${p.status === "OPEN" ? "جاری" : "نهایی"}</span><strong id="performance-efficiency">${performanceNumber(p.efficiency, 4)}</strong><small>بین صفر و یک · نمرهٔ اولیهٔ معیارها کامل است</small></div>
                <div><span>امتیاز وزنی کسب‌شده / سقف وزنی</span><bdi>${performanceNumber(p.weightedScore, 4)} / ${performanceNumber(p.weightedMaximum, 4)}</bdi><small>مجموع (نمره × ضریب) ÷ مجموع (سقف × ضریب)</small></div>
            </div>
            <p class="task-muted">شروع: ${escapeHtml(taskFormatDateTime(p.createdAt))}${p.closedAt ? ` · پایان: ${escapeHtml(taskFormatDateTime(p.closedAt))}` : ""}</p>
            ${p.status === "CLOSED" && p.canManage ? `<p class="performance-notice">این دوره بسته شده است. اصلاح با ثبت دلیل انجام می‌شود و دورهٔ جاری را تغییر نمی‌دهد.</p>` : ""}
            ${p.criteria.length ? `<div class="table-wrap"><table class="data-table performance-table"><thead><tr><th>عنوان و توضیحات</th><th>سقف امتیاز</th><th>ضریب</th><th>نمره</th>${p.canManage ? "<th>عملیات</th>" : ""}</tr></thead><tbody>${p.criteria.map(c => `
                <tr><td><strong>${escapeHtml(c.title)}</strong>${c.description ? `<p class="performance-description">${escapeHtml(c.description)}</p>` : ""}</td><td>${performanceNumber(c.maximumScore)}</td><td>${performanceNumber(c.weight)}</td><td>${performanceNumber(c.score)}</td>${p.canManage ? `<td><div class="performance-row-actions"><button type="button" class="btn btn-secondary btn-sm" data-performance-edit="${escapeHtml(c.id)}">ویرایش</button><button type="button" class="btn btn-danger btn-sm" data-performance-delete="${escapeHtml(c.id)}">حذف</button></div></td>` : ""}</tr>`).join("")}</tbody></table></div>` : `<p class="task-muted">معیاری تعریف نشده</p>`}
            <div class="performance-toolbar performance-actions"><div>${p.canManage ? `<button type="button" class="btn btn-primary btn-sm" id="performance-add">افزودن معیار</button>` : ""} ${p.canStartNext ? `<button type="button" class="btn btn-secondary btn-sm" id="performance-next">بستن دوره و شروع دورهٔ جدید</button>` : ""}</div><button type="button" class="btn btn-secondary btn-sm" id="performance-history">${s.showHistory ? "بستن تاریخچهٔ تغییرات" : "تاریخچهٔ تغییرات"}</button></div>
            <div id="performance-history-region"></div>` : `
            <p class="task-muted">هنوز دورهٔ ارزیابی ثبت نشده است.</p>${isAdmin() ? `<button type="button" class="btn btn-primary" id="performance-create">شروع نخستین دوره</button>` : ""}`}`;
    s.root.querySelector("#performance-period")?.addEventListener("change", e => loadPerformancePeriod(s, e.target.value));
    s.root.querySelector("#performance-period-prev")?.addEventListener("click", () => { s.page--; loadPerformancePeriods(s); });
    s.root.querySelector("#performance-period-next")?.addEventListener("click", () => { s.page++; loadPerformancePeriods(s); });
    s.root.querySelector("#performance-create")?.addEventListener("click", () => performancePeriodForm(s, null));
    s.root.querySelector("#performance-next")?.addEventListener("click", () => performancePeriodForm(s, p));
    s.root.querySelector("#performance-add")?.addEventListener("click", () => performanceCriterionForm(s, p, null));
    s.root.querySelectorAll("[data-performance-edit]").forEach(button => button.onclick = () => performanceCriterionForm(s, p, p.criteria.find(c => c.id === button.dataset.performanceEdit)));
    s.root.querySelectorAll("[data-performance-delete]").forEach(button => button.onclick = () => performanceDelete(s, p, p.criteria.find(c => c.id === button.dataset.performanceDelete)));
    s.root.querySelector("#performance-history")?.addEventListener("click", () => { s.showHistory = !s.showHistory; renderPerformance(s); });
    if (s.showHistory && p) loadPerformanceHistory(s);
}

function performancePeriodForm(s, p) {
    openModal(p ? "بستن دوره و شروع دورهٔ جدید" : "شروع نخستین دوره", `
        <form id="performance-period-form"><p>${p ? `نتیجهٔ «${escapeHtml(p.title)}» حفظ و معیارهای فعال با امتیاز کامل منتقل می‌شوند.` : "معیارهای ارزیابی این فرد را پس از ساخت دوره اضافه کنید."}</p>
        <div class="form-group"><label for="performance-period-title">عنوان دورهٔ ${p ? "جدید" : "ارزیابی"}</label><input id="performance-period-title" required maxlength="160" placeholder="مثلاً آبان ۱۴۰۵"></div>
        <p class="form-error" id="performance-form-error" role="alert"></p><div class="form-actions"><button type="button" class="btn btn-secondary" id="performance-cancel">انصراف</button><button type="submit" class="btn btn-primary">${p ? "بستن و شروع دورهٔ جدید" : "ایجاد دوره"}</button></div></form>`);
    const form = document.getElementById("performance-period-form");
    document.getElementById("performance-cancel").onclick = closeModal;
    form.onsubmit = e => {
        e.preventDefault();
        const title = form.querySelector("input").value.trim();
        if (!title) return;
        performanceSubmit(s, form, () => p ? PerformanceAPI.next(s.userId, p.id, { title, version: p.version }) : PerformanceAPI.create(s.userId, { title }), true);
    };
}

function performanceCriterionForm(s, p, c) {
    const numeric = (id, label, value, min) => `<div class="form-group"><label for="${id}">${label}</label><input type="number" inputmode="decimal" id="${id}" min="${min}" max="9999999999.99" step="0.01" required value="${escapeHtml(String(value ?? ""))}"></div>`;
    openModal(c ? "ویرایش معیار و نمره" : "افزودن معیار", `<form id="performance-criterion-form">
        <div class="form-group"><label for="performance-title">عنوان معیار</label><input id="performance-title" maxlength="200" required value="${escapeHtml(c?.title || "")}"></div>
        <div class="form-group"><label for="performance-description">توضیحات معیار</label><textarea id="performance-description" maxlength="2000" rows="3">${escapeHtml(c?.description || "")}</textarea></div>
        <div class="performance-form-grid">${numeric("performance-maximum", "سقف امتیاز", c?.maximumScore, "0.01")}${numeric("performance-weight", "ضریب", c?.weight ?? 1, "0.01")}${c ? numeric("performance-score", "نمرهٔ فعلی", c.score, "0") : ""}</div>
        ${!c ? `<p class="task-muted">امتیاز اولیه برابر سقف امتیاز خواهد بود.</p>` : ""}
        <div class="form-group"><label for="performance-reason">دلیل تغییر ${p.status === "CLOSED" ? "(الزامی برای اصلاح دورهٔ بسته)" : "(هنگام تغییر نمره الزامی است)"}</label><textarea id="performance-reason" maxlength="2000" rows="2" ${p.status === "CLOSED" ? "required" : ""}></textarea></div>
        <p class="form-error" id="performance-form-error" role="alert"></p><div class="form-actions"><button type="button" class="btn btn-secondary" id="performance-cancel">انصراف</button><button type="submit" class="btn btn-primary">ذخیره</button></div></form>`);
    const form = document.getElementById("performance-criterion-form");
    document.getElementById("performance-cancel").onclick = closeModal;
    form.onsubmit = e => {
        e.preventDefault();
        const data = { version: p.version, title: form.querySelector("#performance-title").value.trim(), description: form.querySelector("#performance-description").value,
            maximumScore: form.querySelector("#performance-maximum").value, weight: form.querySelector("#performance-weight").value, reason: form.querySelector("#performance-reason").value.trim() };
        if (c) data.score = form.querySelector("#performance-score").value;
        const error = form.querySelector("#performance-form-error");
        if (c && Number(data.score) > Number(data.maximumScore)) { error.textContent = "نمره نمی‌تواند از سقف بیشتر باشد"; error.style.display = "block"; return; }
        if ((p.status === "CLOSED" || (c && Number(data.score) !== Number(c.score))) && !data.reason) { error.textContent = "دلیل تغییر را وارد کنید"; error.style.display = "block"; return; }
        performanceSubmit(s, form, () => c ? PerformanceAPI.update(s.userId, p.id, c.id, data) : PerformanceAPI.add(s.userId, p.id, data));
    };
}

async function performanceSubmit(s, form, operation, reloadPeriods = false) {
    if (!performanceCurrent(s)) { closeModal(); return; }
    const button = form.querySelector("button[type=submit]");
    if (button.disabled) return;
    const viewSeq = s.seq;
    button.disabled = true;
    try {
        const period = await operation();
        if (!performanceCurrent(s) || viewSeq !== s.seq) return;
        if (form.isConnected) closeModal();
        if (reloadPeriods) { s.page = 0; await loadPerformancePeriods(s, period.id); }
        else { s.period = period; renderPerformance(s); }
        showToast("ارزیابی ذخیره شد");
    } catch (error) {
        if (!performanceCurrent(s) || viewSeq !== s.seq || !form.isConnected) return;
        const box = form.querySelector("#performance-form-error");
        box.textContent = error.message; box.style.display = "block";
        if (error.status === 409) {
            const refresh = document.createElement("button"); refresh.type = "button"; refresh.className = "btn btn-secondary btn-sm";
            refresh.textContent = "دریافت آخرین اطلاعات";
            refresh.onclick = () => { closeModal(); loadPerformancePeriods(s, s.period?.id); };
            box.append(document.createElement("br"), refresh);
        }
    } finally { if (button.isConnected) button.disabled = false; }
}

async function performanceDelete(s, p, c) {
    const answer = await taskConfirm({ title: "حذف معیار", message: `«${c.title}» از محاسبه و انتقال دورهٔ بعد حذف می‌شود؛ تاریخچه باقی می‌ماند.`, confirmText: "حذف معیار", danger: true, reasonLabel: p.status === "CLOSED" ? "دلیل اصلاح دورهٔ بسته (الزامی)" : null });
    if (!answer.confirmed || !performanceCurrent(s) || s.period !== p) return;
    if (p.status === "CLOSED" && !answer.reason.trim()) { showToast("دلیل اصلاح دورهٔ بسته را وارد کنید", "error"); return; }
    const seq = s.seq;
    try {
        const result = await PerformanceAPI.delete(s.userId, p.id, c.id, { version: p.version, reason: answer.reason });
        if (!performanceCurrent(s) || seq !== s.seq) return;
        s.period = result; renderPerformance(s);
    } catch (error) {
        if (!performanceCurrent(s) || seq !== s.seq) return;
        showToast(error.message, "error");
        if (error.status === 409) loadPerformancePeriod(s, p.id);
    }
}

function performanceSnapshot(raw) {
    if (!raw) return "—";
    try {
        const value = JSON.parse(raw);
        if (value.maximumScore !== undefined) return `${value.title} · سقف ${performanceNumber(value.maximumScore)} · ضریب ${performanceNumber(value.weight)} · نمره ${performanceNumber(value.score)}${value.description ? ` · ${value.description}` : ""}`;
        return value.title || (value.status === "CLOSED" ? "نهایی" : "جاری");
    } catch { return raw; }
}

async function loadPerformanceHistory(s) {
    const region = s.root.querySelector("#performance-history-region");
    if (!region) return;
    const seq = ++s.historySeq, periodId = s.period.id;
    region.innerHTML = "<p>در حال دریافت تاریخچه…</p>";
    const actions = { PERIOD_CREATED: "شروع دوره", PERIOD_CLOSED: "بستن دوره", CRITERION_COPIED: "انتقال معیار با نمرهٔ کامل", CRITERION_ADDED: "افزودن معیار", CRITERION_UPDATED: "ویرایش معیار / نمره", CRITERION_DELETED: "حذف معیار" };
    try {
        const result = await PerformanceAPI.events(s.userId, periodId, { page: s.historyPage, size: 10 });
        if (!performanceCurrent(s) || seq !== s.historySeq || s.period.id !== periodId || !region.isConnected || !s.showHistory) return;
        region.innerHTML = `<ol class="performance-history">${result.items.map(e => `<li><div><strong>${escapeHtml(actions[e.action] || e.action)}</strong> · ${escapeHtml(e.actorName || e.actorUsername)} · ${escapeHtml(taskFormatDateTime(e.occurredAt))}</div><p>قبل: ${escapeHtml(performanceSnapshot(e.oldValues))}</p><p>بعد: ${escapeHtml(performanceSnapshot(e.newValues))}</p><p>راندمان: ${performanceNumber(e.oldEfficiency, 4)} ← ${performanceNumber(e.newEfficiency, 4)}</p>${e.reason ? `<p>دلیل: ${escapeHtml(e.reason)}</p>` : ""}</li>`).join("")}</ol>
        <div class="performance-toolbar"><span>${performanceNumber(result.totalElements)} تغییر · صفحهٔ ${performanceNumber(result.page + 1)}</span><div><button type="button" class="btn btn-secondary btn-sm" id="performance-event-prev" ${s.historyPage === 0 ? "disabled" : ""}>قبلی</button> <button type="button" class="btn btn-secondary btn-sm" id="performance-event-next" ${s.historyPage + 1 >= result.totalPages ? "disabled" : ""}>بعدی</button></div></div>`;
        region.querySelector("#performance-event-prev").onclick = () => { s.historyPage--; loadPerformanceHistory(s); };
        region.querySelector("#performance-event-next").onclick = () => { s.historyPage++; loadPerformanceHistory(s); };
    } catch (error) {
        if (performanceCurrent(s) && seq === s.historySeq && region.isConnected) region.innerHTML = `<p role="alert">${escapeHtml(error.message)}</p>`;
    }
}
