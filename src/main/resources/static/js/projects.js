/**
 * projects.js
 * منطق مربوط به نمایش لیست پروژه‌ها، ساخت پروژه جدید،
 * و نمایش جزئیات یک پروژه (لیست PieceDefinition هایش).
 */

/* ===========================
   لیست پروژه‌ها
   =========================== */

async function loadProjects() {
    const tbody = document.getElementById("projects-table-body");
    showTableLoader(tbody, 5);
    try {
        const projects = await ProjectAPI.getAll();
        renderProjectsTable(projects);
    } catch (error) {
        showToast(error.message, "error");
    }
}

function renderProjectsTable(projects) {
    const tbody = document.getElementById("projects-table-body");

    if (projects.length === 0) {
        tbody.innerHTML = `<tr class="empty-row"><td colspan="5">هنوز هیچ پروژه‌ای ثبت نشده است</td></tr>`;
        return;
    }

    tbody.innerHTML = projects
        .map(
            (p) => `
        <tr data-project-id="${p.id}">
            <td>${escapeHtml(p.code)}</td>
            <td>${escapeHtml(p.name)}</td>
            <td>${formatDate(p.createdAt)}</td>
            <td>${p.pieceCount}</td>
            <td class="row-actions">${
                [
                    hasPermission("MANAGE_PIECES")
                        ? `<button class="btn btn-danger btn-archive-project-pieces" data-id="${p.id}">بایگانی قطعات</button>`
                        : "",
                    hasPermission("MANAGE_PROJECTS")
                        ? `<button class="btn btn-edit btn-edit-project" data-id="${p.id}" data-code="${escapeHtml(p.code)}" data-name="${escapeHtml(p.name)}">ویرایش</button>
                           <button class="btn btn-danger btn-delete-project" data-id="${p.id}">حذف</button>`
                        : "",
                ].filter(Boolean).join(" ")
            }</td>
        </tr>
    `
        )
        .join("");

    // کلیک روی ردیف -> رفتن به جزئیات پروژه
    tbody.querySelectorAll("tr[data-project-id]").forEach((row) => {
        row.addEventListener("click", (e) => {
            if (
                e.target.classList.contains("btn-delete-project") ||
                e.target.classList.contains("btn-edit-project") ||
                e.target.classList.contains("btn-archive-project-pieces")
            ) {
                return;
            }
            openProjectDetail(row.dataset.projectId);
        });
    });

    // کلیک روی دکمه ویرایش
    tbody.querySelectorAll(".btn-edit-project").forEach((btn) => {
        btn.addEventListener("click", (e) => {
            e.stopPropagation();
            openEditProjectModal(btn.dataset.id, btn.dataset.code, btn.dataset.name);
        });
    });

    // کلیک روی دکمه حذف
    tbody.querySelectorAll(".btn-delete-project").forEach((btn) => {
        btn.addEventListener("click", async (e) => {
            e.stopPropagation();
            await openCatalogDeletionPreview('projects',btn.dataset.id,loadProjects);
        });
    });

    tbody.querySelectorAll(".btn-archive-project-pieces").forEach((btn) => {
        btn.addEventListener("click", async (e) => {
            e.stopPropagation();
            await archiveProjectPieces(btn.dataset.id);
        });
    });
}

/* ===========================
   ویرایش پروژه (مودال)
   =========================== */

function openEditProjectModal(id, code, name) {
    openModal(
        "ویرایش پروژه",
        `
        <form id="form-edit-project">
            <div class="form-group">
                <label for="input-edit-project-code">کد پروژه</label>
                <input type="text" id="input-edit-project-code" value="${escapeHtml(code)}" required />
            </div>
            <div class="form-group">
                <label for="input-edit-project-name">نام پروژه</label>
                <input type="text" id="input-edit-project-name" value="${escapeHtml(name)}" required />
            </div>
            <div class="form-error" id="form-error"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-cancel-edit-project">انصراف</button>
                <button type="submit" class="btn btn-primary">ذخیره</button>
            </div>
        </form>
    `
    );

    document.getElementById("btn-cancel-edit-project").addEventListener("click", closeModal);

    document.getElementById("form-edit-project").addEventListener("submit", async (e) => {
        e.preventDefault();

        const newCode = document.getElementById("input-edit-project-code").value.trim();
        const newName = document.getElementById("input-edit-project-name").value.trim();
        const errorBox = document.getElementById("form-error");

        try {
            await ProjectAPI.update(id, { code: newCode, name: newName });
            closeModal();
            showToast("پروژه با موفقیت ویرایش شد");
            loadProjects();
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
        }
    });
}

/* ===========================
   ساخت پروژه جدید (مودال)
   =========================== */

document.getElementById("btn-add-project").addEventListener("click", () => {
    openModal(
        "افزودن پروژه جدید",
        `
        <form id="form-add-project">
            <div class="form-group">
                <label for="input-project-code">کد پروژه</label>
                <input type="text" id="input-project-code" placeholder="مثلاً 502" required />
            </div>
            <div class="form-group">
                <label for="input-project-name">نام پروژه</label>
                <input type="text" id="input-project-name" placeholder="مثلاً پروژه فلان شرکت" required />
            </div>
            <div class="form-error" id="form-error"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-cancel-add-project">انصراف</button>
                <button type="submit" class="btn btn-primary">ذخیره</button>
            </div>
        </form>
    `
    );

    document.getElementById("btn-cancel-add-project").addEventListener("click", closeModal);

    document.getElementById("form-add-project").addEventListener("submit", async (e) => {
        e.preventDefault();

        const code = document.getElementById("input-project-code").value.trim();
        const name = document.getElementById("input-project-name").value.trim();
        const errorBox = document.getElementById("form-error");

        try {
            await ProjectAPI.create({ code, name });
            closeModal();
            showToast("پروژه با موفقیت ساخته شد");
            loadProjects();
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
        }
    });
});

/* ===========================
   جزئیات یک پروژه (لیست PieceDefinition ها)
   =========================== */

async function openProjectDetail(projectId) {
    AppState.currentProjectId = projectId;
    const tbody = document.getElementById("definitions-table-body");
    showTableLoader(tbody, 4);
    showSection("project-detail-section");

    try {
        const project = await ProjectAPI.getById(projectId);
        AppState.currentProjectCode = project.code || "";
        document.getElementById("project-detail-title").textContent = `پروژه ${project.code} — ${project.name}`;

        const definitions = await PieceDefinitionAPI.getByProject(projectId);
        renderDefinitionsTable(definitions);
    } catch (error) {
        showToast(error.message, "error");
    }
}

function renderDefinitionsTable(definitions) {
    const tbody = document.getElementById("definitions-table-body");

    if (definitions.length === 0) {
        tbody.innerHTML = `<tr class="empty-row"><td colspan="4">هنوز هیچ تعریف قطعه‌ای برای این پروژه ثبت نشده است</td></tr>`;
        return;
    }

    // آستانه‌های کشش برای فرم ویرایش لازم‌اند ولی در جدول نمایش داده نمی‌شوند،
    // پس به‌جای شلوغ‌کردن data-attributeها اینجا نگه داشته می‌شوند
    DefinitionThresholdCache.clear();
    definitions.forEach((d) =>
        DefinitionThresholdCache.set(d.id, {
            minUts: d.minUts,
            minYs: d.minYs,
            minEl: d.minEl,
            minHb: d.minHb,
        })
    );

    tbody.innerHTML = definitions
        .map(
            (d) => `
        <tr data-definition-id="${d.id}">
            <td>${d.rowNum}</td>
            <td>${escapeHtml(d.pieceName)}</td>
            <td>${d.pieceCount}</td>
            <td class="row-actions">${
                [
                    `<button class="btn btn-secondary btn-definition-status-report" data-id="${d.id}">گزارش وضعیت</button>`,
                    hasPermission("MANAGE_PIECES")
                        ? `<button class="btn btn-danger btn-archive-definition-pieces" data-id="${d.id}">بایگانی قطعات</button>`
                        : "",
                    hasPermission("MANAGE_PIECE_DEFINITIONS")
                        ? `<button class="btn btn-edit btn-edit-definition" data-id="${d.id}" data-row-num="${d.rowNum}" data-piece-name="${escapeHtml(d.pieceName)}" data-has-drawing="${d.hasDrawing ? "1" : "0"}">ویرایش</button>
                           <button class="btn btn-danger btn-delete-definition" data-id="${d.id}">حذف</button>`
                        : "",
                ].filter(Boolean).join(" ")
            }</td>
        </tr>
    `
        )
        .join("");

    tbody.querySelectorAll("tr[data-definition-id]").forEach((row) => {
        row.addEventListener("click", (e) => {
            if (
                e.target.classList.contains("btn-delete-definition") ||
                e.target.classList.contains("btn-edit-definition") ||
                e.target.classList.contains("btn-archive-definition-pieces") ||
                e.target.classList.contains("btn-definition-status-report")
            ) {
                return;
            }
            openDefinitionDetail(row.dataset.definitionId);
        });
    });

    tbody.querySelectorAll(".btn-edit-definition").forEach((btn) => {
        btn.addEventListener("click", (e) => {
            e.stopPropagation();
            openEditDefinitionModal(
                btn.dataset.id,
                btn.dataset.rowNum,
                btn.dataset.pieceName,
                btn.dataset.hasDrawing === "1",
                DefinitionThresholdCache.get(btn.dataset.id) || {}
            );
        });
    });

    tbody.querySelectorAll('tr[data-definition-id]').forEach(row=>{
        const cell=row.lastElementChild;if(!cell)return;
        const button=document.createElement('button');button.type='button';button.className='btn btn-secondary btn-sm';button.textContent='نسخه‌های نقشه';
        button.addEventListener('click',e=>{e.stopPropagation();openDrawingRevisionHistory(row.dataset.definitionId);});cell.appendChild(button);
    });

    tbody.querySelectorAll(".btn-delete-definition").forEach((btn) => {
        btn.addEventListener("click", async (e) => {
            e.stopPropagation();
            await openCatalogDeletionPreview('definitions',btn.dataset.id,()=>openProjectDetail(AppState.currentProjectId));
        });
    });

    tbody.querySelectorAll(".btn-archive-definition-pieces").forEach((btn) => {
        btn.addEventListener("click", async (e) => {
            e.stopPropagation();
            const archived = await archiveDefinitionPieces(btn.dataset.id);
            if (archived) openProjectDetail(AppState.currentProjectId);
        });
    });

    tbody.querySelectorAll(".btn-definition-status-report").forEach((btn) => {
        btn.addEventListener("click", (e) => {
            e.stopPropagation();
            pendingOpenRowReport = true;
            openDefinitionDetail(btn.dataset.id);
        });
    });
}

/* ===========================
   حداقل پارامترهای تاییدی تست کشش
   =========================== */

const DefinitionThresholdCache = new Map();

const TENSILE_THRESHOLD_FIELDS = [
    { key: "minUts", label: "حداقل UTS" },
    { key: "minYs", label: "حداقل YS" },
    { key: "minEl", label: "حداقل EL" },
    { key: "minHb", label: "حداقل HB" },
];

function renderTensileThresholdFields(prefix, values = {}) {
    const inputs = TENSILE_THRESHOLD_FIELDS.map(({ key, label }) => {
        const value = values[key] != null ? values[key] : "";
        return `
            <div class="form-group tensile-threshold-field">
                <label for="input-${prefix}-${key}">${label}</label>
                <input type="number" step="any" min="0" id="input-${prefix}-${key}" value="${value}" />
            </div>`;
    }).join("");

    return `
        <fieldset class="tensile-threshold-fieldset">
            <legend>حداقل پارامترهای تاییدی تست کشش</legend>
            <p class="form-hint">اختیاری — هر فیلدی که خالی بماند برای آن پارامتر کنترل خودکار انجام نمی‌شود.</p>
            <div class="tensile-threshold-grid">${inputs}</div>
        </fieldset>
    `;
}

/** در صورت وجود مقدار نامعتبر، به‌جای شیء مقادیر، null برمی‌گرداند. */
function readTensileThresholdFields(prefix) {
    const result = {};
    for (const { key } of TENSILE_THRESHOLD_FIELDS) {
        const raw = document.getElementById(`input-${prefix}-${key}`).value.trim();
        if (raw === "") {
            result[key] = null;
            continue;
        }
        const num = Number(raw);
        if (Number.isNaN(num) || num < 0) return null;
        result[key] = num;
    }
    return result;
}

/* ===========================
   ویرایش تعریف قطعه (مودال)
   =========================== */

async function openEditDefinitionModal(id, rowNum, pieceName, hasDrawing, thresholds = {}) {
    let templates = [];
    try {
        templates = await RadiographyReportAPI.getTemplates(id);
    } catch (error) {
        showToast(error.message, "error");
        return;
    }

    openModal(
        "ویرایش تعریف قطعه",
        `
        <form id="form-edit-definition">
            <div class="form-group">
                <label for="input-edit-row-num">شماره ردیف</label>
                <input type="number" id="input-edit-row-num" value="${rowNum}" required />
            </div>
            <div class="form-group">
                <label for="input-edit-piece-name">نام قطعه</label>
                <input type="text" id="input-edit-piece-name" value="${escapeHtml(pieceName)}" required />
            </div>
            ${renderTensileThresholdFields("edit", thresholds)}
            ${renderRadiographyTemplateBuilder({ existingTemplates: templates, allowAdd: true })}
            <div class="form-group">
                <label for="input-edit-drawing">نقشه دو‌بعدی قطعه ${hasDrawing ? "(اختیاری — برای جایگزینی)" : "(اختیاری)"}</label>
                <input type="file" id="input-edit-drawing" accept="image/jpeg,image/png,image/webp" />
                <p class="form-hint">${hasDrawing ? "نقشه فعلی ثبت شده است. انتخاب فایل جدید آن را جایگزین می‌کند." : "هنوز نقشه‌ای ثبت نشده است."}</p>
                <div id="edit-drawing-preview" class="drawing-preview"></div>
            </div>
            <div class="form-error" id="form-error"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-cancel-edit-definition">انصراف</button>
                <button type="submit" class="btn btn-primary">ذخیره</button>
            </div>
        </form>
    `
    );

    const fileInput = document.getElementById("input-edit-drawing");
    fileInput.addEventListener("change", () => previewDrawingFile(fileInput, "edit-drawing-preview"));

    document.getElementById("btn-cancel-edit-definition").addEventListener("click", closeModal);
    attachRadiographyTemplateBuilderEvents({ definitionId: id });

    document.getElementById("form-edit-definition").addEventListener("submit", async (e) => {
        e.preventDefault();

        const newRowNum = parseInt(document.getElementById("input-edit-row-num").value, 10);
        const newPieceName = document.getElementById("input-edit-piece-name").value.trim();
        const drawingFile = fileInput.files[0];
        const errorBox = document.getElementById("form-error");

        const editThresholds = readTensileThresholdFields("edit");
        if (!editThresholds) {
            errorBox.textContent = "حداقل پارامترهای تاییدی باید عددی و نامنفی باشند";
            errorBox.style.display = "block";
            return;
        }

        try {
            await PieceDefinitionAPI.update(id, { rowNum: newRowNum, pieceName: newPieceName, ...editThresholds });
            if (drawingFile) {
                await PieceDefinitionAPI.uploadDrawing(id, drawingFile);
            }
            await saveNewTemplateIfFilled(id);
            closeModal();
            showToast("تعریف قطعه با موفقیت ویرایش شد");
            openProjectDetail(AppState.currentProjectId);
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
        }
    });
}

function previewDrawingFile(fileInput, previewId) {
    const preview = document.getElementById(previewId);
    if (!preview) return;
    preview.innerHTML = "";
    const file = fileInput.files[0];
    if (!file) return;
    const url = URL.createObjectURL(file);
    preview.innerHTML = `<img src="${url}" alt="پیش‌نمایش نقشه" />`;
}

/* ===========================
   ساخت تعریف قطعه جدید (مودال)
   =========================== */

document.getElementById("btn-add-definition").addEventListener("click", () => {
    openModal(
        "افزودن تعریف قطعه جدید",
        `
        <form id="form-add-definition">
            <div class="form-group">
                <label for="input-row-num">شماره ردیف</label>
                <input type="number" id="input-row-num" placeholder="مثلاً 1" required />
            </div>
            <div class="form-group">
                <label for="input-piece-name">نام قطعه</label>
                <input type="text" id="input-piece-name" placeholder="مثلاً AA" required />
            </div>
            ${renderTensileThresholdFields("add")}
            ${renderRadiographyTemplateBuilder({ allowAdd: true })}
            <div class="form-group">
                <label for="input-drawing">نقشه دو‌بعدی قطعه</label>
                <input type="file" id="input-drawing" accept="image/jpeg,image/png,image/webp" required />
                <p class="form-hint">JPG، PNG یا WEBP — حداکثر ۱۰ مگابایت</p>
                <div id="add-drawing-preview" class="drawing-preview"></div>
            </div>
            <div class="form-error" id="form-error"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-cancel-add-definition">انصراف</button>
                <button type="submit" class="btn btn-primary">ذخیره</button>
            </div>
        </form>
    `
    );

    const fileInput = document.getElementById("input-drawing");
    fileInput.addEventListener("change", () => previewDrawingFile(fileInput, "add-drawing-preview"));

    document.getElementById("btn-cancel-add-definition").addEventListener("click", closeModal);
    attachRadiographyTemplateBuilderEvents();

    document.getElementById("form-add-definition").addEventListener("submit", async (e) => {
        e.preventDefault();

        const rowNum = parseInt(document.getElementById("input-row-num").value, 10);
        const pieceName = document.getElementById("input-piece-name").value.trim();
        const drawingFile = fileInput.files[0];
        const errorBox = document.getElementById("form-error");

        if (!drawingFile) {
            errorBox.textContent = "آپلود نقشه دو‌بعدی الزامی است";
            errorBox.style.display = "block";
            return;
        }

        const addThresholds = readTensileThresholdFields("add");
        if (!addThresholds) {
            errorBox.textContent = "حداقل پارامترهای تاییدی باید عددی و نامنفی باشند";
            errorBox.style.display = "block";
            return;
        }

        try {
            const created = await PieceDefinitionAPI.create({
                projectId: AppState.currentProjectId,
                rowNum,
                pieceName,
                ...addThresholds,
            });
            await PieceDefinitionAPI.uploadDrawing(created.id, drawingFile);
            await saveNewTemplateIfFilled(created.id);
            closeModal();
            showToast("تعریف قطعه با موفقیت ساخته شد");
            openProjectDetail(AppState.currentProjectId);
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.style.display = "block";
        }
    });
});

document.getElementById("btn-archive-all-project-pieces")?.addEventListener("click", async (e) => {
    e.stopPropagation();
    if (!AppState.currentProjectId) return;
    const archived = await archiveProjectPieces(AppState.currentProjectId);
    if (archived) openProjectDetail(AppState.currentProjectId);
});
