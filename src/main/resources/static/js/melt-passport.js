/* Shared melt data; calculator inputs never read from process or ingot fields. */
const MELT_STATUS = { DRAFT: 'پیش‌نویس', PENDING_APPROVAL: 'در انتظار تأیید', NEEDS_CORRECTION: 'نیازمند اصلاح', APPROVED: 'تأییدشده', CANCELLED: 'لغوشده' };
const MELT_UNITS = { KG: 'کیلوگرم', G: 'گرم', TON: 'تن', L: 'لیتر', ML: 'میلی‌لیتر', COUNT: 'عدد' };
const MELT_CHECKS = {
    LANCE_SEAL: 'آب‌بندی اتصالات لانس', LANCE_HEAD: 'وضعیت کلگی در ظاهر و داخل مذاب',
    SMALL_SCALE: 'کالیبراسیون ترازوی کوچک', RPT_DEVICE: 'وضعیت دستگاه RPT',
    MN_TI_FLANGE: 'فلنج افزودن Mn و Ti', LAMP_CABLE: 'کابل سیم لامپ‌ها',
};
const MELT_PROCESS_GROUPS = [
    ['شرایط آماده‌سازی مذاب', [
        ['superheatTemperature', 'دمای فوق‌گداز'], ['argonInjectionDuration', 'مدت تزریق آرگون'],
        ['timeAt610Clock', 'زمان دمای ۶۱۰', 'time'], ['spiralTest', 'تست اسپیرال'], ['pouringTemperature', 'دمای بارریزی'],
        ['pressureHoldingTime', 'زمان نگهداری تحت فشار'], ['injectionStartTime', 'زمان ابتدای تزریق', 'time'],
    ]],
    ['آزمایش و وضعیت فرایند', [
        ['argonHumidityPercent', 'درصد رطوبت آرگون'], ['rptSampleStatus', 'وضعیت نمونه RPT', 'text'],
        ['runnerPipeStatus', 'وضعیت لوله راهگاه', 'text'], ['nozzleLock', 'قفل‌شدن نازل درجه به لوله راهگاه', 'lock'],
        ['nozzleFull', 'پر بودن کامل نازل درجه', 'boolean'], ['returnedMeltWeight', 'وزن مذاب برگشتی'],
    ]],
    ['اطلاعات روتاری و تزریق', [
        ['rotaryFlow', 'دبی روتاری'], ['rotaryDuration', 'مدت روتاری'], ['rotarySpeed', 'دور روتاری'],
        ['rotaryTemperature', 'دمای روتاری'], ['fluxInjectionDuration', 'مدت تزریق فلاکس'],
        ['lanceNumber', 'شماره لانس', 'text'], ['lanceOutletDiameter', 'قطر خروجی لانس'],
        ['injectedCoverallType', 'نوع کاورال تزریقی', 'text'], ['injectedCoverallWeight', 'وزن کاورال تزریقی واردشده به ذوب'],
    ]],
];
const MELT_MG_FIELDS = [['mg', 'Mg'], ['rotary', 'T rotary'], ['at610', 'T 610'], ['ar', 'T ar'], ['b', 'B'], ['w', 'w']];
const MELT_EVENT = { CANCELLED: 'لغو شناسنامه', OPERATOR_ASSIGNED: 'تخصیص اپراتور ذوب', CREATED: 'ایجاد', UPDATED: 'ذخیره تغییرات', SUBMITTED: 'ارسال برای تأیید', APPROVED: 'تأیید و انتقال قطعات', RETURNED: 'بازگشت برای اصلاح', MAGNESIUM_CALCULATED: 'محاسبه منیزیم', ADMIN_CORRECTED: 'اصلاح پس از تأیید توسط ادمین' };
const MeltViewState = { openSeq: 0, lastError: '', lastErrorAt: 0 };

async function loadMeltTaskPanel(task) {
    const region = document.getElementById('task-workflow-region');
    if (!region) return;
    try {
        const m = await MeltPassportAPI.get(task.workflowId);
        if (!region.isConnected) return;
        region.innerHTML = `<section class="rw-card"><h3>گردش‌کار شناسنامه ذوب</h3><p>${escapeHtml(MELT_STATUS[m.status])} · ${m.pieces.length} قطعه</p>
            ${m.assignedOperatorName ? `<p>اپراتور: ${escapeHtml(m.assignedOperatorName)}</p>` : ''}
            <button type="button" class="btn btn-primary" id="task-open-melt">${m.reviewable ? 'بررسی و تأیید شناسنامه ذوب' : m.editable ? 'تکمیل شناسنامه ذوب' : 'مشاهده شناسنامه ذوب'}</button></section>`;
        region.querySelector('#task-open-melt').addEventListener('click', () => openMeltPassport(m.id));
    } catch (e) { if (region.isConnected) region.textContent = e.message; }
}

function meltFeedback(message, type = 'success') {
    showToast(message, type, { position: 'top', durationMs: type === 'error' || type === 'warning' ? 6000 : 3000 });
}

function renderMeltPassportIcon(rows = [], loading = false) {
    const latest = rows[0];
    const label = `شناسنامه ذوب${latest ? ` — ${MELT_STATUS[latest.status]} — ${rows.length} شناسنامه` : ''}`;
    return `<button type="button" class="profile-melt-icon${latest?.status === 'NEEDS_CORRECTION' || latest?.status === 'PENDING_APPROVAL' ? ' has-pending' : ''}"
        id="btn-melt-passport-icon" title="${escapeHtml(label)}" aria-label="${escapeHtml(label)}" aria-busy="${loading}">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M14 2H5v20h14V7zM14 2v5h5M8 11h5M8 14h3"/><path d="M15 12c1 2-1 2.5-1 4a2 2 0 0 0 4 0c0-1-1-2-1-2.5 0 1-.5 1.5-1 1.5.5-1-.5-2-1-3z"/></svg>
        ${rows.length ? `<span class="melt-icon-badge">${rows.length}</span>` : ''}</button>`;
}

function meltOptions(values, selected, empty = true) {
    return (empty ? '<option value="">انتخاب کنید</option>' : '') + Object.entries(values).map(([value, label]) =>
        `<option value="${escapeHtml(value)}" ${String(selected) === value ? 'selected' : ''}>${escapeHtml(label)}</option>`).join('');
}
function meltField(name, label, value, type = 'text', extra = '', labelMarkup = null, hintMarkup = '') {
    // Optional markup is supplied only by fixed calculator symbols / escaped legacy hints below.
    return `<div class="form-group"><label for="melt-${name}"${labelMarkup ? ` aria-label="${escapeHtml(label)}"` : ''}>${labelMarkup ?? escapeHtml(label)}</label><input id="melt-${name}" type="${type}"${labelMarkup ? ` aria-label="${escapeHtml(label)}"` : ''} value="${escapeHtml(String(value ?? ''))}" ${type === 'number' && !extra.includes('step=') ? 'step="any"' : ''} ${extra}>${hintMarkup}</div>`;
}
function meltMagnesiumSymbol(key) {
    switch (key) {
        case 'rotary': return '<span dir="ltr">T<sub>rotary</sub></span>';
        case 'at610': return '<span dir="ltr">T<sub>610</sub></span>';
        case 'ar': return '<span dir="ltr">T<sub>ar</sub></span>';
        default: return null;
    }
}
function meltNumber(value) { return value === '' || value === null || value === undefined ? null : Number(value); }
function meltPercentages(primary, recycled) {
    const a = Number(primary || 0), b = Number(recycled || 0), sum = a + b;
    return sum > 0 ? [a / sum * 100, b / sum * 100] : [null, null];
}

async function loadPieceMeltPassports(piece) {
    ++MeltViewState.openSeq;
    const region = document.getElementById('profile-melt-icon-container');
    if (!region) return;
    let rows = null, request = null;
    const fetchRows = () => request || (request = MeltPassportAPI.forPiece(piece.id).finally(() => { request = null; }));
    const attach = () => region.querySelector('#btn-melt-passport-icon').addEventListener('click', async () => {
        try {
            if (rows === null) rows = await fetchRows();
            if (!region.isConnected) return;
            if (rows.length) await openMeltPassport(rows[0].id, piece, rows);
            else if (!piece.archivedAt && piece.currentStage === 'CASTING') await openMeltPassport(null, piece, rows);
            else meltFeedback('شناسنامه‌ای برای این قطعه ثبت نشده است', 'warning');
        } catch (e) { if (region.isConnected) meltShowError(e); }
    });
    region.innerHTML = renderMeltPassportIcon([], true); attach();
    try {
        rows = await fetchRows();
        if (!region.isConnected) return;
        region.innerHTML = renderMeltPassportIcon(rows); attach();
    } catch (e) {
        if (!region.isConnected) return;
        region.innerHTML = renderMeltPassportIcon(); attach(); meltShowError(e);
    }
}

async function openMeltPassport(id, piece = null, history = null) {
    const seq = ++MeltViewState.openSeq;
    const origin = document.getElementById('modal-body').firstElementChild;
    const stillCurrent = () => seq === MeltViewState.openSeq && (!origin || origin.isConnected);
    try {
        const [record, catalog, ingotTypes, today, records, currentPiece, assignees] = await Promise.all([
            id ? MeltPassportAPI.get(id) : Promise.resolve(null), MeltPassportAPI.materials(), MeltPassportAPI.ingotTypes(), MeltPassportAPI.today(),
            history === null && piece ? MeltPassportAPI.forPiece(piece.id) : Promise.resolve(history || []),
            piece ? PieceAPI.getById(piece.id) : Promise.resolve(null),
            hasPermission("ASSIGN_MELT_OPERATOR") ? MeltPassportAPI.assignees() : Promise.resolve([]),
        ]);
        if (!stillCurrent()) return;
        piece = currentPiece;
        const m = record || { version: 0, status: 'DRAFT', editable: true, assignable: hasPermission("ASSIGN_MELT_OPERATOR") && !piece.archivedAt && piece.currentStage === "CASTING", meltDate: today.date,
            pieces: [{ id: piece.id, pieceName: piece.pieceName, pieceRef: pieceRefLabel(piece) }], materials: [], checks: [], magnesium: {}, process: {}, events: [] };
        renderMeltPassport({ m, catalog, ingotTypes, piece, history: records, assignees });
    } catch (e) { if (stillCurrent()) meltShowError(e); }
}

function renderMeltPassport(state) {
    const { m, catalog, ingotTypes, piece } = state;
    const correcting = !!(state.correcting && m.correctable);
    state.correcting = correcting;
    const disabled = !(m.editable || correcting);
    const history = state.history || [];
    const newCycle = !correcting && !state.pieceMoved && piece && !piece.archivedAt && piece.currentStage === 'CASTING' && m.id && ![m, ...history].some(row => row.pieces.some(link => link.id === piece.id && link.active));
    const processField = ([key, label, type]) => {
        const value = m.process?.[key];
        if (type === 'lock' || type === 'boolean') {
            return `<div class="form-group"><label for="melt-process-${key}">${escapeHtml(label)}</label><select id="melt-process-${key}">${meltOptions(type === 'lock' ? { NO: 'خیر', LITTLE: 'کمی', MUCH: 'زیاد' } : { false: 'خیر', true: 'بله' }, value)}</select></div>`;
        }
        const legacyHint = key === 'timeAt610Clock' && m.process?.timeAt610 != null
            ? `<p class="form-hint" id="melt-610-legacy">مقدار قدیمی: <bdi>${escapeHtml(String(m.process.timeAt610))}</bdi></p>` : '';
        const extra = !type ? 'min="0"' : type === 'text' ? 'maxlength="255"' : key === 'timeAt610Clock'
            ? `step="60"${legacyHint ? ' aria-describedby="melt-610-legacy"' : ''}` : '';
        return meltField(`process-${key}`, label, value, type || 'number', extra, null, legacyHint);
    };
    openModal(m.id ? `شناسنامه ذوب · ${m.number}` : 'شناسنامه ذوب جدید', `
        <form id="melt-form" class="melt-form" novalidate>
            ${history.length > 1 || (!m.id && history.length) ? `<div class="form-group melt-history"><label for="melt-history">شناسنامه‌های این قطعه</label><select id="melt-history">${!m.id ? '<option value="">شناسنامه جدید</option>' : ''}${history.map(row => `<option value="${escapeHtml(row.id)}" ${row.id === m.id ? 'selected' : ''}>${escapeHtml(row.number)} · ${MELT_STATUS[row.status]}${row.meltDate ? ` · ${isoToJalaliString(row.meltDate)}` : ''}</option>`).join('')}</select></div>` : ''}
            ${newCycle ? '<button type="button" class="btn btn-secondary btn-sm" id="melt-new-cycle">ایجاد شناسنامه برای نوبت جدید ذوب</button>' : ''}
            <div class="melt-status-bar"><span class="melt-status melt-status-${m.status}">${MELT_STATUS[m.status]}</span>
                ${m.assignedOperatorName ? `<span>اپراتور تخصیص‌یافته: ${escapeHtml(m.assignedOperatorName)}</span>` : ''}
                ${m.createdBy ? `<span>ثبت‌کننده: ${escapeHtml(m.createdBy)}</span>` : ''}
                ${m.approvedBy ? `<span>تأییدکننده: ${escapeHtml(m.approvedBy)} · ${formatDateTime(m.approvedAt)}</span>` : ''}</div>
            ${m.correctionReason ? `<div class="melt-correction" role="status"><strong>علت بازگشت برای اصلاح</strong><p>${escapeHtml(m.correctionReason)}</p><small>${escapeHtml(m.returnedBy || '')} · ${formatDateTime(m.returnedAt)}</small></div>` : ''}
            ${correcting ? '<p class="form-hint" role="status">در حال اصلاح شناسنامه تأییدشده هستید. تغییرات با نام شما و مقادیر قبل و بعد در تاریخچه ثبت می‌شود.</p>' : ''}
            ${m.assignable ? `<section class="melt-section"><label for="melt-assignee">تخصیص اپراتور ذوب</label><select id="melt-assignee">${meltOptions(Object.fromEntries((state.assignees || []).map(u => [u.id, u.fullName || u.username])), m.assignedOperatorId)}</select><button type="button" class="btn btn-secondary" id="melt-assign">${isManager() ? "ذخیره و تخصیص اپراتور" : "تخصیص اپراتور"}</button></section>` : ''}
            <fieldset ${disabled ? 'disabled' : ''}>
            <section class="melt-section"><h3>اطلاعات ذوب و قطعات</h3>
                <div id="melt-pieces" class="melt-piece-list"></div>
                ${m.editable || correcting ? `<div class="melt-piece-search"><label for="melt-piece-search">${correcting ? 'افزودن قطعهٔ بدون شناسنامهٔ فعال' : 'افزودن قطعه از مرحله ذوب‌ریزی'}</label><input id="melt-piece-search" type="search" placeholder="نام یا شماره کامل قطعه"><div id="melt-piece-results" aria-live="polite"></div></div>` : ''}
                ${correcting ? `<div id="melt-added-destination" hidden>
                    <div class="form-group"><label for="melt-added-stage">قطعات تازه‌اضافه‌شده به کدام مرحله بروند؟</label>
                        <select id="melt-added-stage" required>${meltOptions(Object.fromEntries(TRANSFERABLE_STAGES.map(stage => [stage, stageLabel(stage)])), '')}</select>
                        <p class="form-hint">این مقصد برای همهٔ قطعات تازه‌اضافه‌شده اعمال می‌شود.</p>
                    </div>
                </div>` : ''}
                <div class="melt-grid">
                    ${meltField('date', 'تاریخ ذوب', '', 'text')}
                    ${meltField('furnace', 'شماره کوره', m.furnaceNumber, 'number', 'min="0" step="1"')}
                    ${meltField('ingot', 'نوع شمش', m.ingotType, 'text', 'list="melt-ingot-types" maxlength="160"')}
                    <datalist id="melt-ingot-types">${ingotTypes.map(t => `<option value="${escapeHtml(t)}"></option>`).join('')}</datalist>
                    ${meltField('primary-batch', 'شماره / بچ شمش اولیه', m.primaryBatch, 'text', 'maxlength="160"')}
                    ${meltField('primary-weight', 'وزن شمش اولیه (کیلوگرم)', m.primaryWeight, 'number', 'min="0"')}
                    ${meltField('primary-percent', 'درصد شمش اولیه', m.primaryPercent, 'text', 'readonly')}
                    ${meltField('recycled-batch', 'شماره / بچ شمش بازیافتی', m.recycledBatch, 'text', 'maxlength="160"')}
                    ${meltField('recycled-weight', 'وزن شمش بازیافتی (کیلوگرم)', m.recycledWeight, 'number', 'min="0"')}
                    ${meltField('recycled-percent', 'درصد شمش بازیافتی', m.recycledPercent, 'text', 'readonly')}
                </div>
            </section>
            <section class="melt-section"><h3>مواد و محاسبات</h3>
                <div class="table-wrapper"><table class="data-table melt-material-table"><thead><tr><th>ماده</th><th>دسته</th><th>مقدار مصرف</th><th>واحد</th><th>زمان افزودن</th><th>بچ / سری ساخت</th><th>توضیحات</th><th></th></tr></thead><tbody id="melt-material-rows"></tbody></table></div>
                ${!disabled ? `<div class="form-actions"><button type="button" class="btn btn-secondary btn-sm" id="melt-add-material">افزودن ردیف ماده</button><button type="button" class="btn btn-secondary btn-sm" id="melt-new-material">تعریف ماده جدید</button></div><div id="melt-new-material-form"></div>` : ''}
                <div class="melt-calculator"><h4>محاسبه مستقل منیزیم شمش بازیافتی</h4>
                    <p class="form-hint">شش ورودی را دستی وارد کنید. این بخش از وزن‌ها و شرایط فرایند مستقل است؛ محاسبه فقط با دکمه انجام می‌شود.</p>
                    <p class="melt-formula" dir="ltr" role="math" aria-label="(0.32 - Mg + (T rotary * 0.2) + ((T 610 + T ar) * B)) * w * 10 * 1.1">(0.32 - Mg + (${meltMagnesiumSymbol('rotary')} * 0.2) + ((${meltMagnesiumSymbol('at610')} + ${meltMagnesiumSymbol('ar')}) * B)) * w * 10 * 1.1</p>
                    <div class="melt-grid melt-mg-grid" dir="ltr">${MELT_MG_FIELDS.map(([key, label]) => meltField(`mg-${key}`, label, m.magnesium?.[key], 'number', '', meltMagnesiumSymbol(key))).join('')}</div>
                    ${!disabled ? `<button type="button" class="btn btn-secondary" id="melt-calculate">${correcting ? 'محاسبه و ذخیره اصلاحات' : 'محاسبه منیزیم'}</button>` : ''}
                    <div id="melt-magnesium-result" class="melt-result" aria-live="polite"></div>
                </div>
            </section>
            <section class="melt-section"><h3>شرایط فرایند</h3>
                ${MELT_PROCESS_GROUPS.map(([title, fields]) => `<h4>${title}</h4><div class="melt-grid">${fields.map(processField).join('')}</div>`).join('')}
            </section>
            <section class="melt-section"><h3>کنترل و تأیید</h3>
                <p class="form-hint">برای ارسال: تاریخ، کوره، نوع شمش، هر دو وزن با مجموع مثبت، نام اپراتور و بررسی همه موارد کنترلی لازم است. موارد ردشده توضیح می‌خواهند.</p>
                <div class="melt-checks">${Object.entries(MELT_CHECKS).map(([kind, label]) => {
                    const c = m.checks.find(x => x.kind === kind);
                    return `<div class="melt-check"><label for="melt-check-${kind}">${label}</label><select id="melt-check-${kind}">${meltOptions({ UNCHECKED: 'بررسی‌نشده', ACCEPTED: 'تأیید', REJECTED: 'رد' }, c?.status || 'UNCHECKED', false)}</select><input id="melt-check-notes-${kind}" aria-label="توضیحات ${label}" placeholder="توضیحات" maxlength="2000" value="${escapeHtml(c?.notes || '')}"></div>`;
                }).join('')}</div>
                ${meltField('operator', 'نام اپراتور تزریق‌کننده', m.injectionOperator, 'text', 'maxlength="160"')}
                <div class="melt-grid"><div class="form-group"><label for="melt-notes">توضیحات عمومی</label><textarea id="melt-notes" maxlength="4000">${escapeHtml(m.notes || '')}</textarea></div><div class="form-group"><label for="melt-additional-notes">توضیحات تکمیلی</label><textarea id="melt-additional-notes" maxlength="4000">${escapeHtml(m.additionalNotes || '')}</textarea></div></div>
            </section></fieldset>
            ${correcting ? '<div class="form-group melt-review"><label for="melt-correction-reason">دلیل اصلاح ـ اختیاری</label><textarea id="melt-correction-reason" maxlength="2000"></textarea></div>' : ''}
            <div class="form-actions melt-actions">
                <button type="button" class="btn btn-secondary" id="melt-back">${piece ? 'بازگشت به قطعه' : 'بستن'}</button>
                ${correcting ? '<button type="button" class="btn btn-secondary" id="melt-cancel-correction">انصراف</button><button type="button" class="btn btn-primary" id="melt-save">ذخیره اصلاحات</button>' : m.editable ? '<button type="button" class="btn btn-secondary" id="melt-save">ذخیره پیش‌نویس / اصلاحات</button><button type="button" class="btn btn-primary" id="melt-submit">ارسال برای تأیید</button>' : ''}
                ${m.cancellable ? '<button type="button" class="btn btn-danger" id="melt-cancel-passport">لغو شناسنامه</button>' : ''}
                ${m.correctable && !correcting ? '<button type="button" class="btn btn-primary" id="melt-start-correction">ویرایش شناسنامه تأییدشده</button>' : ''}
            </div>
            ${m.reviewable ? `<section class="melt-section melt-review"><h3>بررسی شناسنامه</h3><label for="melt-return-reason">دلیل بازگشت برای اصلاح (الزامی برای بازگشت)</label><textarea id="melt-return-reason" maxlength="2000"></textarea><div class="form-actions"><button type="button" class="btn btn-secondary" id="melt-return">بازگشت برای اصلاح</button><button type="button" class="btn btn-primary" id="melt-approve">تأیید و انتقال همه قطعات</button></div></section>` : ''}
            ${m.events.length ? `<details class="melt-section"><summary>سوابق عملیات (${m.events.length})</summary><ol class="melt-events">${m.events.map(e => `<li><strong>${MELT_EVENT[e.action] || escapeHtml(e.action)}</strong> · ${escapeHtml(e.actorName)} · ${formatDateTime(e.occurredAt)}${e.reason ? `<p>${escapeHtml(e.reason)}</p>` : ''}${renderMeltAuditChanges(e)}</li>`).join('')}</ol></details>` : ''}
        </form>`, { size: 'xl' });
    state.form = document.getElementById('melt-form');
    state.mgStale = false;
    document.getElementById('melt-history')?.addEventListener('change', e => openMeltPassport(e.target.value || null, piece, history));
    document.getElementById('melt-new-cycle')?.addEventListener('click', () => openMeltPassport(null, piece, history));
    document.getElementById('melt-cancel-passport')?.addEventListener('click',()=>openOperationCancellation('لغو شناسنامه ذوب',false,async reason=>{await OperationAPI.cancelMelt(m.id,m.version,reason);await openMeltPassport(m.id);}));
    document.getElementById('melt-start-correction')?.addEventListener('click', () => { state.correcting = true; renderMeltPassport(state); });
    document.getElementById('melt-cancel-correction')?.addEventListener('click', () => { state.correcting = false; renderMeltPassport(state); });
    state.selectedPieces = [...m.pieces];
    state.date = m.meltDate;
    const dateInput = document.getElementById('melt-date');
    if (disabled) dateInput.value = m.meltDate ? isoToJalaliString(m.meltDate) : '';
    else {
        const picker = attachPersianDatePicker(dateInput, { onSelect: d => { state.date = jalaliToIsoDateString(d.jy, d.jm, d.jd); }, onClear: () => { state.date = null; } });
        picker.setIsoDate(m.meltDate);
    }
    renderMeltPieces(state);
    m.materials.forEach(row => addMeltMaterialRow(state, row));
    renderMeltMagnesium(m);
    updateMeltPercentages();
    document.getElementById('melt-form').addEventListener('submit', e => e.preventDefault());
    document.getElementById('melt-back').addEventListener('click', () => { if (piece) openPieceProfile(piece.id); else closeModal(); });
    if (!disabled) attachMeltEditEvents(state);
    document.getElementById('melt-assign')?.addEventListener('click', () => runMeltAction(state, 'assign'));
    if (m.reviewable) {
        document.getElementById('melt-return').addEventListener('click', () => runMeltAction(state, 'return', document.getElementById('melt-return-reason').value));
        document.getElementById('melt-approve').addEventListener('click', () => runMeltAction(state, 'approve'));
    }
}

function renderMeltPieces(state) {
    const region = document.getElementById('melt-pieces');
    const originalIds = new Set(state.m.pieces.map(p => p.id));
    region.innerHTML = state.selectedPieces.map(p => {
        const isNew = state.correcting && !originalIds.has(p.id);
        const remove = state.m.editable || isNew
            ? `<button type="button" class="btn btn-secondary btn-sm" data-melt-remove-piece="${escapeHtml(p.id)}" aria-label="حذف ${escapeHtml(p.pieceRef)}">حذف</button>` : '';
        return `<div class="melt-piece"><strong>${escapeHtml(p.pieceName)}</strong><bdi>${escapeHtml(p.pieceRef)}</bdi>${isNew ? '<span>قطعه جدید</span>' : ''}${remove}</div>`;
    }).join('');
    region.querySelectorAll('[data-melt-remove-piece]').forEach(btn => btn.addEventListener('click', () => {
        state.selectedPieces = state.selectedPieces.filter(p => p.id !== btn.dataset.meltRemovePiece);
        renderMeltPieces(state);
    }));
    const destination = document.getElementById('melt-added-destination');
    if (destination) {
        destination.hidden = state.selectedPieces.length === originalIds.size;
        if (destination.hidden) document.getElementById('melt-added-stage').value = '';
    }
}

function addMeltMaterialRow(state, value = {}) {
    const tr = document.createElement('tr');
    const available = state.catalog.filter(c => c.active || c.id === value.materialId);
    tr.innerHTML = `<td><select data-material="materialId" aria-label="نام ماده"><option value="">انتخاب ماده</option>${available.map(c => `<option value="${escapeHtml(c.id)}" ${c.id === value.materialId ? 'selected' : ''}>${escapeHtml(c.id === value.materialId ? value.materialName || c.name : c.name)}${c.active ? '' : ' (غیرفعال)'}</option>`).join('')}</select></td>
        <td data-material-category>${escapeHtml(value.category || '')}</td>
        <td><input data-material="quantity" aria-label="مقدار مصرف" type="number" min="0" step="any" value="${escapeHtml(String(value.quantity ?? ''))}"></td>
        <td><select data-material="unit" aria-label="واحد">${meltOptions(MELT_UNITS, value.unit || 'KG', false)}</select></td>
        <td><input data-material="addedAt" aria-label="زمان افزودن" type="time" value="${escapeHtml(value.addedAt || '')}"></td>
        <td><input data-material="batchNumber" aria-label="شماره بچ" maxlength="160" value="${escapeHtml(value.batchNumber || '')}"></td>
        <td><input data-material="notes" aria-label="توضیحات ماده" maxlength="2000" value="${escapeHtml(value.notes || '')}"></td>
        <td>${state.m.editable || state.correcting ? '<button type="button" class="btn btn-secondary btn-sm" data-material-remove aria-label="حذف ردیف ماده">حذف</button>' : ''}</td>`;
    tr.querySelector('[data-material="materialId"]').addEventListener('change', e => {
        tr.querySelector('[data-material-category]').textContent = state.catalog.find(c => c.id === e.target.value)?.category || '';
    });
    tr.querySelector('[data-material-remove]')?.addEventListener('click', () => tr.remove());
    document.getElementById('melt-material-rows').appendChild(tr);
}

function updateMeltPercentages() {
    const [a, b] = meltPercentages(document.getElementById('melt-primary-weight').value, document.getElementById('melt-recycled-weight').value);
    document.getElementById('melt-primary-percent').value = a === null ? '' : a.toFixed(4);
    document.getElementById('melt-recycled-percent').value = b === null ? '' : b.toFixed(4);
}
function renderMeltMagnesium(m, stale = false) {
    const box = document.getElementById('melt-magnesium-result');
    if (stale) { box.textContent = 'ورودی تغییر کرده است؛ محاسبه مجدد لازم است.'; return; }
    box.innerHTML = m.magnesiumResult === null || m.magnesiumResult === undefined ? 'مقدار منیزیم مواد اولیه: محاسبه نشده' :
        `<strong>مقدار منیزیم مواد اولیه: <bdi>${escapeHtml(String(m.magnesiumResult))}</bdi></strong><small>زمان محاسبه: ${formatDateTime(m.magnesiumCalculatedAt)}</small>${Number(m.magnesiumResult) < 0 ? '<p class="melt-negative">نتیجه منفی است و نیاز به بررسی دارد.</p>' : ''}`;
}
function collectMeltForm(state) {
    const val = id => document.getElementById(`melt-${id}`).value;
    const process = {};
    MELT_PROCESS_GROUPS.forEach(([, fields]) => fields.forEach(([key, , type]) => {
        const v = val(`process-${key}`);
        process[key] = type === 'boolean' ? (v === '' ? null : v === 'true') : !type ? meltNumber(v) : v || null;
    }));
    const magnesium = Object.fromEntries(MELT_MG_FIELDS.map(([key]) => [key, meltNumber(val(`mg-${key}`))]));
    const materials = [...document.querySelectorAll('#melt-material-rows tr')].map(tr => Object.fromEntries(
        [...tr.querySelectorAll('[data-material]')].map(el => [el.dataset.material, el.dataset.material === 'quantity' ? meltNumber(el.value) : el.value || null])))
        .filter(row => row.materialId || row.quantity !== null || row.addedAt || row.batchNumber || row.notes);
    return { version: state.m.version, pieceIds: state.selectedPieces.map(p => p.id), meltDate: state.date,
        furnaceNumber: meltNumber(val('furnace')), ingotType: val('ingot'), primaryBatch: val('primary-batch'), recycledBatch: val('recycled-batch'),
        primaryWeight: meltNumber(val('primary-weight')), recycledWeight: meltNumber(val('recycled-weight')), injectionOperator: val('operator'),
        notes: val('notes'), additionalNotes: val('additional-notes'), process, magnesium, materials,
        checks: Object.keys(MELT_CHECKS).map(kind => ({ kind, status: val(`check-${kind}`), notes: val(`check-notes-${kind}`) })) };
}
function attachMeltEditEvents(state) {
    const form = state.form;
    ['primary', 'recycled'].forEach(k => document.getElementById(`melt-${k}-weight`).addEventListener('input', updateMeltPercentages));
    MELT_MG_FIELDS.forEach(([k]) => document.getElementById(`melt-mg-${k}`).addEventListener('input', () => {
        if (state.m.magnesiumResult !== null && state.m.magnesiumResult !== undefined) {
            if (!state.mgStale) meltFeedback('ورودی منیزیم تغییر کرده است؛ محاسبه مجدد لازم است', 'warning');
            state.mgStale = true;
        }
        renderMeltMagnesium(state.m, state.mgStale);
    }));
    document.getElementById('melt-save').addEventListener('click', () => runMeltAction(state, 'save'));
    document.getElementById('melt-submit')?.addEventListener('click', () => runMeltAction(state, 'submit'));
    document.getElementById('melt-calculate').addEventListener('click', () => runMeltAction(state, 'calculate-magnesium'));
    document.getElementById('melt-add-material').addEventListener('click', () => addMeltMaterialRow(state));
    document.getElementById('melt-new-material').hidden = !hasPermission('MANAGE_MELT_MATERIALS');
    document.getElementById('melt-new-material').addEventListener('click', () => {
        if(!hasPermission('MANAGE_MELT_MATERIALS')) return;
        document.getElementById('melt-new-material-form').innerHTML = `<div class="melt-grid">${meltField('new-material-name', 'نام ماده جدید', '', 'text', 'maxlength="160"')}${meltField('new-material-category', 'دسته‌بندی', '', 'text', 'list="melt-categories" maxlength="100"')}<datalist id="melt-categories"><option value="جوانه‌زا"></option><option value="کاورال"></option><option value="سایر افزودنی‌ها"></option></datalist></div><button type="button" class="btn btn-secondary btn-sm" id="melt-save-material">ثبت ماده و افزودن ردیف</button>`;
        document.getElementById('melt-save-material').addEventListener('click', async e => {
            await withButtonLoader(e.currentTarget, async () => {
                try {
                    const name = document.getElementById('melt-new-material-name').value.trim();
                    const category = document.getElementById('melt-new-material-category').value.trim();
                    if (!name || !category) throw new Error('نام و دسته ماده را وارد کنید');
                    const c = await MeltPassportAPI.createMaterial({ name, category });
                    if (!form.isConnected) return;
                    if (!c.active) throw new Error('این ماده قبلاً ثبت و غیرفعال شده است؛ از ادمین بخواهید آن را فعال کند');
                    if (!state.catalog.some(x => x.id === c.id)) state.catalog.push(c);
                    addMeltMaterialRow(state, { materialId: c.id, materialName: c.name, category: c.category });
                    document.getElementById('melt-new-material-form').innerHTML = '';
                } catch (error) { if (form.isConnected) meltShowError(error); }
            });
        });
    });
    let timer, seq = 0;
    document.getElementById('melt-piece-search')?.addEventListener('input', e => {
        clearTimeout(timer); const q = e.target.value; const current = ++seq;
        timer = setTimeout(async () => {
            if (!form.isConnected || state.busy) return;
            try {
                const results = await MeltPassportAPI.candidates(q, state.correcting ? state.m.id : undefined);
                const box = document.getElementById('melt-piece-results');
                if (!form.isConnected || state.busy || !box || seq !== current) return;
                const eligible = results.filter(p => !state.selectedPieces.some(x => x.id === p.id));
                box.innerHTML = eligible.map(p => `<button type="button" class="btn btn-secondary btn-sm" data-melt-add-piece="${escapeHtml(p.id)}">${escapeHtml(p.pieceName)} · ${escapeHtml(p.pieceRef)}</button>`).join('') || '<p class="form-hint">قطعه مجاز دیگری پیدا نشد</p>';
                box.querySelectorAll('[data-melt-add-piece]').forEach(btn => btn.addEventListener('click', () => {
                    const p = eligible.find(x => x.id === btn.dataset.meltAddPiece);
                    if (!state.selectedPieces.some(x => x.id === p.id)) state.selectedPieces.push(p);
                    renderMeltPieces(state); btn.remove();
                }));
            } catch (error) { if (form.isConnected && seq === current) meltShowError(error); }
        }, 250);
    });
}
function meltShowError(error) {
    const details = error.errors ? [...new Set(Object.values(error.errors))].join('\n') : '';
    const message = details || error.message || 'خطایی رخ داد؛ دوباره تلاش کنید';
    if (message === MeltViewState.lastError && Date.now() - MeltViewState.lastErrorAt < 2500
        && document.getElementById('toast')?.textContent === message) return;
    MeltViewState.lastError = message; MeltViewState.lastErrorAt = Date.now();
    meltFeedback(message, 'error');
}

function renderMeltAuditChanges(event) {
    if (!event.oldValues || !event.newValues) return '';
    const flatten = (value, prefix = '', result = {}) => {
        if (value !== null && typeof value === 'object') {
            Object.keys(value).forEach(key => flatten(value[key], prefix ? `${prefix}.${key}` : key, result));
        } else result[prefix] = value;
        return result;
    };
    const before = flatten(event.oldValues), after = flatten(event.newValues);
    const changes = [...new Set([...Object.keys(before), ...Object.keys(after)])].filter(key => before[key] !== after[key]);
    const labels = {
        meltDate: 'تاریخ ذوب', furnaceNumber: 'شماره کوره', ingotType: 'نوع شمش', primaryBatch: 'بچ شمش اولیه', recycledBatch: 'بچ شمش بازیافتی',
        primaryWeight: 'وزن شمش اولیه', recycledWeight: 'وزن شمش بازیافتی', injectionOperator: 'اپراتور تزریق‌کننده',
        notes: 'توضیحات عمومی', additionalNotes: 'توضیحات تکمیلی', magnesiumResult: 'نتیجه منیزیم', magnesiumCalculatedAt: 'زمان محاسبه منیزیم',
        ...Object.fromEntries(MELT_PROCESS_GROUPS.flatMap(([, fields]) => fields.map(([key, label]) => [`process.${key}`, label]))),
        ...Object.fromEntries(MELT_MG_FIELDS.map(([key, label]) => [`magnesium.${key}`, `ورودی ${label}`])),
        'process.timeAt610': 'مقدار قدیمی دمای ۶۱۰',
    };
    const materialLabels = { materialId: 'شناسه ماده', materialName: 'نام ماده', category: 'دسته‌بندی', quantity: 'مقدار', unit: 'واحد', baseQuantity: 'مقدار پایه', baseUnit: 'واحد پایه', addedAt: 'زمان افزودن', batchNumber: 'شماره بچ', notes: 'توضیحات' };
    const label = key => {
        const [group, index, field] = key.split('.');
        if (group === 'pieceRefs') return 'شماره قطعه';
        if (group === 'materials') return `ماده ${Number(index) + 1} ـ ${materialLabels[field] || field}`;
        if (group === 'checks') {
            const kind = event.newValues.checks?.[index]?.kind || event.oldValues.checks?.[index]?.kind;
            return `${MELT_CHECKS[kind] || 'کنترل'} ـ ${({ kind: 'نوع', status: 'وضعیت', notes: 'توضیحات' })[field] || field}`;
        }
        return labels[key] || key;
    };
    const display = (key, value) => {
        if (value === null || value === undefined || value === '') return '—';
        if (typeof value === 'boolean') return value ? 'بله' : 'خیر';
        if (key === 'meltDate') return isoToJalaliString(value);
        if (key === 'magnesiumCalculatedAt') return formatDateTime(value);
        if (/^checks\.\d+\.status$/.test(key)) return ({ UNCHECKED: 'بررسی‌نشده', ACCEPTED: 'تأیید', REJECTED: 'رد' })[value] || String(value);
        if (/^materials\.\d+\.(unit|baseUnit)$/.test(key)) return MELT_UNITS[value] || String(value);
        if (key === 'process.nozzleLock') return ({ NO: 'خیر', LITTLE: 'کمی', MUCH: 'زیاد' })[value] || String(value);
        return String(value);
    };
    return changes.length ? `<details class="melt-audit"><summary>مشاهده تغییرات (${changes.length})</summary><div class="table-wrapper"><table class="melt-audit-table"><thead><tr><th>فیلد</th><th>مقدار قبلی</th><th>مقدار جدید</th></tr></thead><tbody>${changes.map(key => `<tr><td>${escapeHtml(label(key))}</td><td>${escapeHtml(display(key, before[key]))}</td><td>${escapeHtml(display(key, after[key]))}</td></tr>`).join('')}</tbody></table></div></details>` : '';
}

async function runMeltAction(state, action, reason) {
    if (state.busy) return;
    state.busy = true;
    const form = document.getElementById('melt-form');
    const controls = [...form.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled)')];
    controls.forEach(control => { control.disabled = true; });
    try {
        let m = state.m;
        const assigneeId = document.getElementById('melt-assignee')?.value;
        let addedPiecesCount = 0;
        if (action === 'assign' && !assigneeId) throw new Error('اپراتور ذوب را انتخاب کنید');
        if (state.correcting) {
            const body = collectMeltForm(state);
            addedPiecesCount = body.pieceIds.length - m.pieces.length;
            const stageSelect = document.getElementById('melt-added-stage');
            const addedPiecesStage = addedPiecesCount ? stageSelect.value : undefined;
            if (addedPiecesCount && !addedPiecesStage) {
                stageSelect.scrollIntoView({ block: 'nearest' });
                throw new Error('مرحله مقصد قطعات تازه‌اضافه‌شده را انتخاب کنید');
            }
            const scrap = addedPiecesStage === 'SCRAPPED' ? await requestScrapDetails({ count: addedPiecesCount }) : undefined;
            if (scrap === null || !form.isConnected) return;
            m = await MeltPassportAPI.correct(m.id, body, document.getElementById('melt-correction-reason').value || null,
                action === 'calculate-magnesium', addedPiecesStage, scrap);
            state.correcting = false;
        } else if (action === 'assign' && !isManager()) {
            m = m.id
                ? await MeltPassportAPI.assign(m.id, assigneeId, m.version)
                : await MeltPassportAPI.assignPiece(state.piece.id, assigneeId);
        } else if (['save', 'submit', 'calculate-magnesium', 'assign'].includes(action)) {
            const body = collectMeltForm(state);
            if (!body.pieceIds.length) throw new Error('حداقل یک قطعه انتخاب کنید');
            if (body.furnaceNumber !== null && !Number.isInteger(body.furnaceNumber)) throw new Error('شماره کوره باید عدد صحیح باشد');
            if (action === 'calculate-magnesium' && Object.values(body.magnesium).some(v => v === null)) throw new Error('هر شش ورودی محاسبه منیزیم را کامل کنید؛ فیلد خالی صفر نیست');
            m = m.id ? await MeltPassportAPI.update(m.id, body) : await MeltPassportAPI.create(body);
            // Retain the saved version even if submission/calculation subsequently fails.
            state.m = m;
            if (action === 'assign') m = await MeltPassportAPI.assign(m.id, assigneeId, m.version);
            else if (action !== 'save') m = await MeltPassportAPI.action(m.id, action, m.version, reason);
        } else {
            m = await MeltPassportAPI.action(m.id, action, m.version, reason);
        }
        if (action === 'approve') state.pieceMoved = true;
        state.m = m;
        if (!form.isConnected) return;
        if (state.piece) state.history = [m, ...(state.history || []).filter(row => row.id !== m.id)].sort((a,b) => (b.createdAt || '').localeCompare(a.createdAt || ''));
        renderMeltPassport(state);
        if (action === 'calculate-magnesium' && Number(m.magnesiumResult) < 0) meltFeedback('نتیجه منیزیم منفی است و نیاز به بررسی دارد', 'warning');
        else if (addedPiecesCount) meltFeedback('قطعات جدید به شناسنامه اضافه شدند و مرحلهٔ انتخاب‌شده اعمال شد');
        else meltFeedback(action === 'approve' ? 'شناسنامه تأیید و تمام قطعات منتقل شدند' : action === 'submit' ? 'شناسنامه برای تأیید ارسال شد' : 'اطلاعات ثبت شد');
        if (typeof TaskDetailState !== 'undefined' && TaskDetailState.task?.workflowId === m.id) {
            TaskAPI.get(TaskDetailState.task.id).then(refreshTaskDetail).catch(() => {});
        }
        if (action === 'approve' || addedPiecesCount) {
            if (typeof loadTracking === 'function') loadTracking();
        }
    } catch (error) { if (form.isConnected) meltShowError(error); }
    finally { state.busy = false; controls.forEach(control => { if (control.isConnected) control.disabled = false; }); }
}

async function openMeltPassportRegister() {
    if (!hasPermission('VIEW_DASHBOARD')) { meltShowError(new Error('مجوز مشاهده داشبورد برای گزارش شناسنامه‌های ذوب لازم است')); return; }
    const review = hasPermission('APPROVE_MELT_PASSPORT');
    let page = 0;
    let filters = { status: review ? 'PENDING_APPROVAL' : '' };
    openModal('شناسنامه‌های ذوب و گزارش مصرف مواد', `
        <div class="melt-register"><div class="melt-grid">
            <div class="form-group"><label for="melt-filter-status">وضعیت</label><select id="melt-filter-status">${meltOptions(Object.fromEntries(Object.entries(MELT_STATUS).filter(([k]) => review || k !== 'PENDING_APPROVAL')), filters.status)}</select></div>
            ${meltField('filter-from', 'از تاریخ', '')}${meltField('filter-to', 'تا تاریخ', '')}
            ${meltField('filter-furnace', 'کوره', '', 'number', 'min="0" step="1"')}
            ${meltField('filter-ingot', 'نوع شمش (نام کامل)', '')}${meltField('filter-operator', 'اپراتور (نام کامل)', '')}
            ${meltField('filter-piece', 'شماره کامل قطعه', '', 'text', 'placeholder="498-2-54"')}
            <div class="form-group"><label for="melt-filter-material">ماده مصرفی</label><select id="melt-filter-material"><option value="">همه مواد</option></select></div>
        </div><div class="form-actions"><button type="button" class="btn btn-primary" id="melt-filter-apply">اعمال فیلتر</button><button type="button" class="btn btn-secondary" id="melt-consumption">گزارش مصرف مواد</button>${hasPermission('MANAGE_MELT_MATERIALS') ? '<button type="button" class="btn btn-secondary" id="melt-catalog-manage">مدیریت فهرست مواد</button>' : ''}</div>
        <div id="melt-register-results" aria-live="polite"></div><div class="form-actions"><button type="button" class="btn btn-secondary" id="melt-page-prev">قبلی</button><span id="melt-page-label"></span><button type="button" class="btn btn-secondary" id="melt-page-next">بعدی</button></div><div id="melt-consumption-results"></div></div>`, { size: 'xl' });
    const root = document.querySelector('.melt-register');
    const error = e => { if (root.isConnected) meltShowError(e); };
    let dates = {};
    ['from', 'to'].forEach(key => attachPersianDatePicker(root.querySelector(`#melt-filter-${key}`), {
        onSelect: d => { dates[key] = jalaliToIsoDateString(d.jy, d.jm, d.jd); }, onClear: () => { dates[key] = null; },
    }));
    const load = async () => {
        try {
            const result = await MeltPassportAPI.list({ ...filters, page, size: 20 });
            if (!root.isConnected) return;
            root.querySelector('#melt-register-results').innerHTML = `<div class="table-wrapper"><table class="data-table"><thead><tr><th>شماره</th><th>تاریخ</th><th>کوره</th><th>قطعات</th><th>اپراتور</th><th>وضعیت</th><th></th></tr></thead><tbody>${result.items.map(m => `<tr><td><bdi>${escapeHtml(m.number)}</bdi></td><td>${m.meltDate ? isoToJalaliString(m.meltDate) : '—'}</td><td>${m.furnaceNumber ?? '—'}</td><td>${m.pieces.map(p => `${escapeHtml(p.pieceName)} (${escapeHtml(p.pieceRef)})`).join('، ')}</td><td>${escapeHtml(m.injectionOperator || '')}</td><td>${MELT_STATUS[m.status]}</td><td><button type="button" class="btn btn-secondary btn-sm" data-melt-register-open="${m.id}">مشاهده / بررسی</button></td></tr>`).join('') || '<tr><td colspan="7">شناسنامه‌ای پیدا نشد</td></tr>'}</tbody></table></div>`;
            root.querySelectorAll('[data-melt-register-open]').forEach(btn => btn.addEventListener('click', () => openMeltPassport(btn.dataset.meltRegisterOpen)));
            root.querySelector('#melt-page-prev').disabled = page === 0;
            root.querySelector('#melt-page-next').disabled = page + 1 >= result.totalPages;
            root.querySelector('#melt-page-label').textContent = `${page + 1} / ${Math.max(1, result.totalPages)}`;
        } catch (e) { error(e); }
    };
    const collect = async () => {
        const ref = root.querySelector('#melt-filter-piece').value.trim();
        const pieceId = ref ? (await PieceAPI.getByRef(ref)).id : null;
        filters = { status: root.querySelector('#melt-filter-status').value, dateFrom: dates.from, dateTo: dates.to,
            furnaceNumber: root.querySelector('#melt-filter-furnace').value, ingotType: root.querySelector('#melt-filter-ingot').value,
            operator: root.querySelector('#melt-filter-operator').value, pieceId, materialId: root.querySelector('#melt-filter-material').value };
    };
    root.querySelector('#melt-filter-apply').addEventListener('click', async () => { try { await collect(); page = 0; await load(); } catch (e) { error(e); } });
    root.querySelector('#melt-page-prev').addEventListener('click', () => { page--; load(); });
    root.querySelector('#melt-page-next').addEventListener('click', () => { page++; load(); });
    root.querySelector('#melt-consumption').addEventListener('click', async () => {
        try {
            await collect();
            const rows = await MeltPassportAPI.consumption(filters);
            if (!root.isConnected) return;
            root.querySelector('#melt-consumption-results').innerHTML = `<section class="melt-section"><h3>مصرف مواد · ${filters.status ? MELT_STATUS[filters.status] : 'تأییدشده'}</h3><p class="form-hint">مصرف هر ذوب یک‌بار شمرده می‌شود. جرم به کیلوگرم، حجم به لیتر و تعداد جدا گزارش می‌شود. وضعیت خالی یعنی فقط تأییدشده‌ها.</p><table class="data-table"><thead><tr><th>ماده</th><th>مقدار</th><th>واحد</th><th>تعداد ذوب</th></tr></thead><tbody>${rows.map(r => `<tr><td>${escapeHtml(r.materialName)}</td><td>${escapeHtml(String(r.quantity))}</td><td>${MELT_UNITS[r.baseUnit]}</td><td>${r.meltCount}</td></tr>`).join('') || '<tr><td colspan="4">مصرفی ثبت نشده است</td></tr>'}</tbody></table></section>`;
        } catch (e) { error(e); }
    });
    root.querySelector('#melt-catalog-manage')?.addEventListener('click', openMeltMaterialCatalog);
    try {
        const catalog = await MeltPassportAPI.materials();
        if (root.isConnected) root.querySelector('#melt-filter-material').innerHTML = '<option value="">همه مواد</option>' + catalog.map(c => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join('');
    } catch (e) { error(e); }
    await load();
}

async function openMeltMaterialCatalog() {
    try {
        if(!hasPermission('MANAGE_MELT_MATERIALS')) throw Error('مجوز مدیریت کاتالوگ مواد لازم است');
        const rows = await MeltPassportAPI.materials();
        openModal('فهرست مواد ذوب', `<p class="form-hint">غیرفعال‌کردن ماده، سوابق مصرف آن را تغییر نمی‌دهد.</p><table class="data-table"><thead><tr><th>ماده</th><th>دسته</th><th>وضعیت</th><th></th></tr></thead><tbody>${rows.map(c => `<tr><td>${escapeHtml(c.name)}</td><td>${escapeHtml(c.category)}</td><td>${c.active ? 'فعال' : 'غیرفعال'}</td><td><button type="button" class="btn btn-secondary btn-sm" data-melt-edit="${c.id}">اصلاح</button><button type="button" class="btn btn-secondary btn-sm" data-melt-toggle="${c.id}">${c.active ? 'غیرفعال کردن' : 'فعال کردن'}</button></td></tr>`).join('')}</tbody></table>`, { size: 'lg' });
        document.querySelectorAll('[data-melt-edit]').forEach(btn=>btn.addEventListener('click',()=>openRecordEdit('materials',rows.find(c=>c.id===btn.dataset.meltEdit),[{key:'name',label:'نام ماده',required:true,max:160},{key:'category',label:'دسته',required:true,max:100}],openMeltMaterialCatalog)));
        document.querySelectorAll('[data-melt-toggle]').forEach(btn => btn.addEventListener('click', async () => {
            try { const c = rows.find(x => x.id === btn.dataset.meltToggle); await MeltPassportAPI.setMaterialActive(c.id, !c.active); await openMeltMaterialCatalog(); }
            catch (e) { meltShowError(e); }
        }));
    } catch (e) { meltShowError(e); }
}

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('btn-melt-register')?.addEventListener('click', () => {
        setReportsMenuOpen(false);
        openMeltPassportRegister();
    });
});
