/**
 * welding-map.js
 * نقشه جوشکاری: نمایش نقشه دو‌بعدی تعریف قطعه در قاب A4 افقی،
 * و ثبت نقاط جوش (مختصات نسبی + نوع عیب + توضیح) روی هر قطعه.
 */

const WeldingMapState = {
    pieceId: null,
    definitionId: null,
    marks: [],
    imageObjectUrl: null,
    pendingPoint: null, // { xPercent, yPercent } هنگام باز بودن فرم ثبت
};

function revokeWeldingMapImage() {
    if (WeldingMapState.imageObjectUrl) {
        URL.revokeObjectURL(WeldingMapState.imageObjectUrl);
        WeldingMapState.imageObjectUrl = null;
    }
}

function resetWeldingMapState() {
    revokeWeldingMapImage();
    WeldingMapState.pieceId = null;
    WeldingMapState.definitionId = null;
    WeldingMapState.marks = [];
    WeldingMapState.pendingPoint = null;
}

/**
 * آیکن دسترسی به نقشه جوشکاری در پروفایل قطعه.
 */
function renderWeldingMapIcon(piece) {
    if (!piece.hasDrawing) {
        return `
            <button type="button"
                class="profile-welding-map-icon disabled"
                id="btn-welding-map-icon"
                title="نقشه دو‌بعدی برای این قطعه ثبت نشده"
                aria-label="نقشه دو‌بعدی برای این قطعه ثبت نشده"
                aria-disabled="true">
                <span class="welding-map-icon-symbol"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 7l6-3 6 3 6-3v13l-6 3-6-3-6 3V7z"/><path d="M9 4v13M15 7v13"/></svg></span>
            </button>
        `;
    }

    return `
        <button type="button"
            class="profile-welding-map-icon"
            id="btn-welding-map-icon"
            title="نقشه جوشکاری"
            aria-label="نقشه جوشکاری">
            <span class="welding-map-icon-symbol"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 7l6-3 6 3 6-3v13l-6 3-6-3-6 3V7z"/><path d="M9 4v13M15 7v13"/></svg></span>
        </button>
    `;
}

function attachWeldingMapIconEvents(piece) {
    const btn = document.getElementById("btn-welding-map-icon");
    if (!btn) return;

    btn.addEventListener("click", (e) => {
        e.stopPropagation();
        if (btn.getAttribute("aria-disabled") === "true") {
            e.preventDefault();
            return;
        }
        openWeldingMapViewer(piece);
    });
}

async function openWeldingMapViewer(piece) {
    try {
        const [blob, marks] = await Promise.all([
            PieceDefinitionAPI.getDrawingBlob(piece.pieceDefinitionId),
            WeldingMapMarkAPI.getByPiece(piece.id),
        ]);

        revokeWeldingMapImage();
        WeldingMapState.pieceId = piece.id;
        WeldingMapState.definitionId = piece.pieceDefinitionId;
        WeldingMapState.marks = marks;
        WeldingMapState.pendingPoint = null;
        WeldingMapState.imageObjectUrl = URL.createObjectURL(blob);

        const canManage = hasPermission("MANAGE_WELDING_MAP");

        openModal(
            `نقشه جوشکاری — قطعه ${piece.pieceNumber}`,
            `
            <div class="welding-map-viewer">
                <p class="form-hint">${
                    canManage
                        ? "روی نقشه کلیک کنید تا محل جوش ثبت شود. روی نقاط موجود کلیک کنید تا جزئیات را ببینید."
                        : "نقاط جوش ثبت‌شده روی نقشه نمایش داده می‌شوند."
                }</p>
                <div class="welding-map-a4" id="welding-map-a4">
                    <img src="${WeldingMapState.imageObjectUrl}" alt="نقشه قطعه" id="welding-map-image" draggable="false" />
                    <div class="welding-map-marks-layer" id="welding-map-marks-layer"></div>
                    <div id="welding-map-popover" class="welding-map-popover" style="display: none;"></div>
                </div>
            </div>
            `,
            { size: "xl" }
        );

        renderWeldingMapMarks();
        attachWeldingMapViewerEvents(canManage);
    } catch (error) {
        showToast(error.message, "error");
    }
}

function closeWeldingMapPopover() {
    const popover = document.getElementById("welding-map-popover");
    if (!popover) return;
    popover.style.display = "none";
    popover.innerHTML = "";
    popover.classList.remove("flip-x", "flip-y");
    WeldingMapState.pendingPoint = null;
}

/**
 * پاپ‌آپ را کنار نقطه کلیک قرار می‌دهد؛ اگر نزدیک لبه باشد جهت را عوض می‌کند تا از قاب بیرون نزند.
 */
function positionWeldingMapPopover(xPercent, yPercent) {
    const popover = document.getElementById("welding-map-popover");
    const frame = document.getElementById("welding-map-a4");
    if (!popover || !frame) return;

    popover.classList.remove("flip-x", "flip-y");
    popover.style.left = `${(xPercent * 100).toFixed(3)}%`;
    popover.style.top = `${(yPercent * 100).toFixed(3)}%`;
    popover.style.display = "block";

    // بعد از رندر، اگر از لبه بیرون زد، جهت باز شدن را برعکس کن
    requestAnimationFrame(() => {
        const frameRect = frame.getBoundingClientRect();
        const popRect = popover.getBoundingClientRect();

        if (popRect.right > frameRect.right - 4) {
            popover.classList.add("flip-x");
        }
        if (popRect.bottom > frameRect.bottom - 4) {
            popover.classList.add("flip-y");
        }
    });
}

function renderWeldingMapMarks() {
    const layer = document.getElementById("welding-map-marks-layer");
    if (!layer) return;

    layer.innerHTML = WeldingMapState.marks
        .map(
            (m, index) => `
        <button type="button"
            class="welding-map-mark"
            data-mark-id="${m.id}"
            style="left: ${(m.xPercent * 100).toFixed(3)}%; top: ${(m.yPercent * 100).toFixed(3)}%;"
            title="${escapeHtml(m.defectType)}">
            ${index + 1}
        </button>
    `
        )
        .join("");

    layer.querySelectorAll(".welding-map-mark").forEach((btn) => {
        btn.addEventListener("click", (e) => {
            e.stopPropagation();
            const mark = WeldingMapState.marks.find((m) => m.id === btn.dataset.markId);
            if (mark) showWeldingMarkDetails(mark);
        });
    });
}

function attachWeldingMapViewerEvents(canManage) {
    const frame = document.getElementById("welding-map-a4");
    if (!frame || !canManage) return;

    frame.addEventListener("click", (e) => {
        if (e.target.closest(".welding-map-mark")) return;
        if (e.target.closest(".welding-map-popover")) return;

        const rect = frame.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0) return;

        const xPercent = (e.clientX - rect.left) / rect.width;
        const yPercent = (e.clientY - rect.top) / rect.height;

        if (xPercent < 0 || xPercent > 1 || yPercent < 0 || yPercent > 1) return;

        WeldingMapState.pendingPoint = { xPercent, yPercent };
        showAddWeldingMarkForm(xPercent, yPercent);
    });
}

function showAddWeldingMarkForm(xPercent, yPercent) {
    const popover = document.getElementById("welding-map-popover");
    if (!popover) return;

    const defectOptions = defectTypeSelectOptionsHtml();

    popover.innerHTML = `
        <div class="welding-map-popover-pin"></div>
        <div class="welding-map-popover-card">
            <h4 class="welding-map-panel-title">ثبت نقطه جوش</h4>
            <form id="form-add-welding-mark">
                <div class="form-group">
                    <label for="input-weld-defect-type">نوع عیب</label>
                    <select id="input-weld-defect-type" required>${defectOptions}</select>
                </div>
                ${defectTypeCustomFieldHtml("input-weld-defect-type-custom")}
                <div class="form-group">
                    <label for="input-weld-note">توضیح (اختیاری)</label>
                    <textarea id="input-weld-note" rows="2" placeholder="توضیح محل جوش..."></textarea>
                </div>
                <div class="form-error" id="weld-mark-form-error"></div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary btn-sm" id="btn-cancel-weld-mark">انصراف</button>
                    <button type="submit" class="btn btn-primary btn-sm">ذخیره</button>
                </div>
            </form>
        </div>
    `;

    positionWeldingMapPopover(xPercent, yPercent);

    bindDefectTypeOtherToggle(
        document.getElementById("input-weld-defect-type"),
        document.getElementById("input-weld-defect-type-custom-wrap")
    );

    document.getElementById("btn-cancel-weld-mark").addEventListener("click", (e) => {
        e.stopPropagation();
        closeWeldingMapPopover();
    });

    document.getElementById("form-add-welding-mark").addEventListener("click", (e) => e.stopPropagation());
    document.getElementById("form-add-welding-mark").addEventListener("submit", async (e) => {
        e.preventDefault();
        e.stopPropagation();
        const errorBox = document.getElementById("weld-mark-form-error");
        const typePayload = readDefectTypePayload(
            document.getElementById("input-weld-defect-type"),
            document.getElementById("input-weld-defect-type-custom")
        );
        const note = document.getElementById("input-weld-note").value.trim();

        try {
            const created = await WeldingMapMarkAPI.create(WeldingMapState.pieceId, {
                xPercent,
                yPercent,
                ...typePayload,
                note: note || null,
            });
            WeldingMapState.marks.push(created);
            closeWeldingMapPopover();
            renderWeldingMapMarks();
            showToast("نقطه جوش ثبت شد");
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
        }
    });

    document.getElementById("input-weld-defect-type").focus();
}

function showWeldingMarkDetails(mark) {
    const popover = document.getElementById("welding-map-popover");
    if (!popover) return;

    const canManage = hasPermission("MANAGE_WELDING_MAP");

    popover.innerHTML = `
        <div class="welding-map-popover-pin"></div>
        <div class="welding-map-popover-card">
            <h4 class="welding-map-panel-title">جزئیات نقطه جوش</h4>
            <div class="welding-mark-detail">
                <div><span class="profile-info-label">نوع عیب</span> ${escapeHtml(mark.defectType)}</div>
                <div><span class="profile-info-label">توضیح</span> ${mark.note ? escapeHtml(mark.note) : "—"}</div>
                <div><span class="profile-info-label">ثبت‌کننده</span> ${mark.createdBy ? escapeHtml(mark.createdBy) : "—"}</div>
                <div><span class="profile-info-label">زمان</span> ${formatDateTime(mark.createdAt)}</div>
            </div>
            <div class="form-actions" style="margin-top: 10px;">
                <button type="button" class="btn btn-secondary btn-sm" id="btn-close-weld-detail">بستن</button>
                ${
                    canManage
                        ? `<button type="button" class="btn btn-secondary btn-sm" id="btn-edit-weld-mark">اصلاح</button><button type="button" class="btn btn-danger btn-sm" id="btn-delete-weld-mark">حذف</button>`
                        : ""
                }
            </div>
        </div>
    `;

    positionWeldingMapPopover(mark.xPercent, mark.yPercent);

    popover.addEventListener("click", (e) => e.stopPropagation());

    document.getElementById("btn-close-weld-detail").addEventListener("click", (e) => {
        e.stopPropagation();
        closeWeldingMapPopover();
    });

    document.getElementById('btn-edit-weld-mark')?.addEventListener('click',()=>{
        const type=DEFECT_TYPE_OPTIONS.includes(mark.defectType)?mark.defectType:'OTHER';
        openRecordEdit('welding-marks',{...mark,defectType:type,defectTypeCustom:type==='OTHER'?mark.defectType:''},[
            {key:'xPercent',label:'مختصات افقی (۰ تا ۱)',number:true,required:true},
            {key:'yPercent',label:'مختصات عمودی (۰ تا ۱)',number:true,required:true},
            {key:'defectType',label:'نوع عیب',options:DEFECT_TYPE_OPTIONS.map(value=>({value,label:defectTypeOptionLabel(value)}))},
            {key:'defectTypeCustom',label:'نوع عیب دیگر'}, {key:'note',label:'توضیح'}
        ],()=>openPieceProfile(WeldingMapState.pieceId));
    });
    const deleteBtn = document.getElementById("btn-delete-weld-mark");
    if (deleteBtn) {
        deleteBtn.addEventListener("click", async (e) => {
            e.stopPropagation();
            if (!confirm("آیا از حذف این نقطه جوش مطمئن هستید؟")) return;
            try {
                await WeldingMapMarkAPI.delete(mark.id);
                WeldingMapState.marks = WeldingMapState.marks.filter((m) => m.id !== mark.id);
                closeWeldingMapPopover();
                renderWeldingMapMarks();
                showToast("نقطه جوش حذف شد");
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    }
}

/* ===========================
   نقشه جوش تجمیعی (داشبورد) — همه شماره‌های یک ردیف روی یک نقشه
   =========================== */

const AggregateWeldingMapState = {
    definitionId: null,
    definitionLabel: "",
    defectTypeFilter: "",
    allMarks: [],
    marks: [],
    imageObjectUrl: null,
};

function resetAggregateWeldingMapState() {
    if (AggregateWeldingMapState.imageObjectUrl) {
        URL.revokeObjectURL(AggregateWeldingMapState.imageObjectUrl);
        AggregateWeldingMapState.imageObjectUrl = null;
    }
    AggregateWeldingMapState.definitionId = null;
    AggregateWeldingMapState.definitionLabel = "";
    AggregateWeldingMapState.defectTypeFilter = "";
    AggregateWeldingMapState.allMarks = [];
    AggregateWeldingMapState.marks = [];
}

async function openAggregateWeldingMapPicker() {
    try {
        const projects = await ProjectAPI.getAll();
        if (projects.length === 0) {
            showToast("هنوز پروژه‌ای ثبت نشده است", "error");
            return;
        }

        const projectOptions = projects
            .map((p) => `<option value="${p.id}">${escapeHtml(p.code)} — ${escapeHtml(p.name)}</option>`)
            .join("");

        const defectOptions = DEFECT_TYPE_OPTIONS.map(
            (t) => `<option value="${t}">${defectTypeOptionLabel(t)}</option>`
        ).join("");

        openModal(
            "نقشه جوش تجمیعی",
            `
            <form id="form-aggregate-welding-map">
                <p class="form-hint">همه جوش‌های ثبت‌شده برای شماره‌های یک نوع قطعه (مثلاً AR) روی یک نقشه نمایش داده می‌شود.</p>
                <div class="form-group">
                    <label for="agg-project-select">پروژه</label>
                    <select id="agg-project-select" required>
                        <option value="">انتخاب پروژه...</option>
                        ${projectOptions}
                    </select>
                </div>
                <div class="form-group">
                    <label for="agg-definition-select">تعریف قطعه (ردیف)</label>
                    <select id="agg-definition-select" required disabled>
                        <option value="">ابتدا پروژه را انتخاب کنید</option>
                    </select>
                </div>
                <div class="form-group">
                    <label for="agg-defect-filter">فیلتر نوع عیب</label>
                    <select id="agg-defect-filter">
                        <option value="">همه عیوب</option>
                        ${defectOptions}
                    </select>
                </div>
                <div class="form-error" id="agg-form-error"></div>
                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" id="btn-cancel-agg-welding">انصراف</button>
                    <button type="submit" class="btn btn-primary">نمایش نقشه</button>
                </div>
            </form>
            `
        );

        const projectSelect = document.getElementById("agg-project-select");
        const definitionSelect = document.getElementById("agg-definition-select");

        projectSelect.addEventListener("change", async () => {
            definitionSelect.innerHTML = `<option value="">در حال بارگذاری...</option>`;
            definitionSelect.disabled = true;
            const projectId = projectSelect.value;
            if (!projectId) {
                definitionSelect.innerHTML = `<option value="">ابتدا پروژه را انتخاب کنید</option>`;
                return;
            }
            try {
                const definitions = await PieceDefinitionAPI.getByProject(projectId);
                const withDrawing = definitions.filter((d) => d.hasDrawing);
                if (withDrawing.length === 0) {
                    definitionSelect.innerHTML = `<option value="">تعریفی با نقشه ثبت‌شده وجود ندارد</option>`;
                    return;
                }
                definitionSelect.innerHTML =
                    `<option value="">انتخاب تعریف قطعه...</option>` +
                    withDrawing
                        .map(
                            (d) =>
                                `<option value="${d.id}" data-label="${escapeHtml(d.pieceName)}">ردیف ${d.rowNum} — ${escapeHtml(d.pieceName)}</option>`
                        )
                        .join("");
                definitionSelect.disabled = false;
            } catch (error) {
                definitionSelect.innerHTML = `<option value="">خطا در بارگذاری</option>`;
                showToast(error.message, "error");
            }
        });

        document.getElementById("btn-cancel-agg-welding").addEventListener("click", closeModal);

        document.getElementById("form-aggregate-welding-map").addEventListener("submit", async (e) => {
            e.preventDefault();
            const errorBox = document.getElementById("agg-form-error");
            const definitionId = definitionSelect.value;
            const defectType = document.getElementById("agg-defect-filter").value;
            const selectedOption = definitionSelect.options[definitionSelect.selectedIndex];
            const label = selectedOption ? selectedOption.textContent : "";

            if (!definitionId) {
                errorBox.textContent = "تعریف قطعه را انتخاب کنید";
                errorBox.style.display = "block";
                return;
            }

            try {
                await openAggregateWeldingMapViewer(definitionId, label, defectType);
            } catch (error) {
                errorBox.textContent = error.message;
                errorBox.style.display = "block";
            }
        });
    } catch (error) {
        showToast(error.message, "error");
    }
}

async function openAggregateWeldingMapViewer(definitionId, definitionLabel, defectType) {
    const [blob, marks] = await Promise.all([
        PieceDefinitionAPI.getDrawingBlob(definitionId),
        WeldingMapMarkAPI.getByDefinition(definitionId, defectType || null),
    ]);

    resetAggregateWeldingMapState();
    AggregateWeldingMapState.definitionId = definitionId;
    AggregateWeldingMapState.definitionLabel = definitionLabel;
    AggregateWeldingMapState.defectTypeFilter = defectType || "";
    AggregateWeldingMapState.allMarks = marks;
    AggregateWeldingMapState.marks = marks;
    AggregateWeldingMapState.imageObjectUrl = URL.createObjectURL(blob);

    const filterLabel = defectType ? ` — فقط ${defectType}` : "";

    openModal(
        `نقشه جوش تجمیعی — ${definitionLabel}${filterLabel}`,
        `
        <div class="welding-map-viewer">
            <div class="aggregate-welding-toolbar">
                <span class="form-hint" id="agg-marks-count">${marks.length} نقطه جوش</span>
                <div class="form-group" style="margin: 0; min-width: 160px;">
                    <label for="agg-live-defect-filter" style="margin-bottom: 4px;">فیلتر نوع عیب</label>
                    <select id="agg-live-defect-filter">
                        <option value="">همه عیوب</option>
                        ${DEFECT_TYPE_OPTIONS.map(
                            (t) =>
                                `<option value="${t}" ${t === defectType ? "selected" : ""}>${defectTypeOptionLabel(t)}</option>`
                        ).join("")}
                    </select>
                </div>
            </div>
            <div class="welding-map-a4" id="aggregate-welding-map-a4">
                <img src="${AggregateWeldingMapState.imageObjectUrl}" alt="نقشه قطعه" draggable="false" />
                <div class="welding-map-marks-layer" id="aggregate-welding-map-marks-layer"></div>
                <div id="aggregate-welding-map-popover" class="welding-map-popover" style="display: none;"></div>
            </div>
        </div>
        `,
        { size: "xl" }
    );

    renderAggregateWeldingMapMarks();

    document.getElementById("agg-live-defect-filter").addEventListener("change", async (e) => {
        const nextFilter = e.target.value;
        try {
            const nextMarks = await WeldingMapMarkAPI.getByDefinition(definitionId, nextFilter || null);
            AggregateWeldingMapState.defectTypeFilter = nextFilter;
            AggregateWeldingMapState.allMarks = nextMarks;
            AggregateWeldingMapState.marks = nextMarks;
            document.getElementById("agg-marks-count").textContent = `${nextMarks.length} نقطه جوش`;
            closeAggregateWeldingMapPopover();
            renderAggregateWeldingMapMarks();
        } catch (error) {
            showToast(error.message, "error");
        }
    });
}

function closeAggregateWeldingMapPopover() {
    const popover = document.getElementById("aggregate-welding-map-popover");
    if (!popover) return;
    popover.style.display = "none";
    popover.innerHTML = "";
    popover.classList.remove("flip-x", "flip-y");
}

function positionAggregateWeldingMapPopover(xPercent, yPercent) {
    const popover = document.getElementById("aggregate-welding-map-popover");
    const frame = document.getElementById("aggregate-welding-map-a4");
    if (!popover || !frame) return;

    popover.classList.remove("flip-x", "flip-y");
    popover.style.left = `${(xPercent * 100).toFixed(3)}%`;
    popover.style.top = `${(yPercent * 100).toFixed(3)}%`;
    popover.style.display = "block";

    requestAnimationFrame(() => {
        const frameRect = frame.getBoundingClientRect();
        const popRect = popover.getBoundingClientRect();
        if (popRect.right > frameRect.right - 4) popover.classList.add("flip-x");
        if (popRect.bottom > frameRect.bottom - 4) popover.classList.add("flip-y");
    });
}

function renderAggregateWeldingMapMarks() {
    const layer = document.getElementById("aggregate-welding-map-marks-layer");
    if (!layer) return;

    layer.innerHTML = AggregateWeldingMapState.marks
        .map(
            (m, index) => `
        <button type="button"
            class="welding-map-mark"
            data-mark-id="${m.id}"
            style="left: ${(m.xPercent * 100).toFixed(3)}%; top: ${(m.yPercent * 100).toFixed(3)}%;"
            title="قطعه ${m.pieceNumber} — ${escapeHtml(m.defectType)}">
            ${index + 1}
        </button>
    `
        )
        .join("");

    layer.querySelectorAll(".welding-map-mark").forEach((btn) => {
        btn.addEventListener("click", (e) => {
            e.stopPropagation();
            const mark = AggregateWeldingMapState.marks.find((m) => m.id === btn.dataset.markId);
            if (mark) showAggregateWeldingMarkDetails(mark);
        });
    });
}

function showAggregateWeldingMarkDetails(mark) {
    const popover = document.getElementById("aggregate-welding-map-popover");
    if (!popover) return;

    popover.innerHTML = `
        <div class="welding-map-popover-pin"></div>
        <div class="welding-map-popover-card">
            <h4 class="welding-map-panel-title">جزئیات نقطه جوش</h4>
            <div class="welding-mark-detail">
                <div><span class="profile-info-label">شماره قطعه</span> ${mark.pieceNumber}</div>
                <div><span class="profile-info-label">نوع عیب</span> ${escapeHtml(mark.defectType)}</div>
                <div><span class="profile-info-label">توضیح</span> ${mark.note ? escapeHtml(mark.note) : "—"}</div>
                <div><span class="profile-info-label">ثبت‌کننده</span> ${mark.createdBy ? escapeHtml(mark.createdBy) : "—"}</div>
                <div><span class="profile-info-label">زمان</span> ${formatDateTime(mark.createdAt)}</div>
            </div>
            <div class="form-actions" style="margin-top: 10px;">
                <button type="button" class="btn btn-secondary btn-sm" id="btn-close-agg-weld-detail">بستن</button>
                <button type="button" class="btn btn-primary btn-sm" id="btn-open-agg-piece-profile">پروفایل قطعه</button>
            </div>
        </div>
    `;

    positionAggregateWeldingMapPopover(mark.xPercent, mark.yPercent);
    popover.addEventListener("click", (e) => e.stopPropagation());

    document.getElementById("btn-close-agg-weld-detail").addEventListener("click", (e) => {
        e.stopPropagation();
        closeAggregateWeldingMapPopover();
    });

    document.getElementById("btn-open-agg-piece-profile").addEventListener("click", (e) => {
        e.stopPropagation();
        closeModal();
        openPieceProfile(mark.pieceId);
    });
}
