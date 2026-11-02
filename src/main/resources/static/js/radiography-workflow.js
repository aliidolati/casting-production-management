/**
 * radiography-workflow.js
 * گردش‌کار تفسیر رادیوگرافی: تخصیص مفسر، نمودار مراحل، پنل وظیفه/قطعه، پنجره‌ی تأیید مفسر،
 * بازبینی و تأیید/رد مدیر، نمایش و چاپ گزارش مهرشده (APPROVED) و امضای پروفایل.
 *
 * همه‌ی قوانین (مجوز، وضعیت، امضا، تغییر مرحله‌ی قطعه) در سرور اعمال می‌شود؛ این فایل فقط
 * نمایش می‌دهد و درخواست می‌فرستد. هیچ شناسه‌ای از کلاینت برای تغییر مرحله ارسال نمی‌شود.
 */

const RW_STATUS_LABELS = {
    INTERPRETER_ASSIGNED: "مفسر تخصیص داده شد",
    REPORT_IN_PROGRESS: "گزارش در حال تکمیل",
    MANAGER_REVIEW: "در انتظار تأیید مدیر",
    REINTERPRETATION_REQUIRED: "نیازمند تفسیر مجدد",
    MANAGER_APPROVED: "تأیید نهایی شد",
    CANCELLED: "لغو شد",
};

const RW_VERSION_LABELS = {
    ORIGINAL: "گزارش اصلی",
    INTERPRETER_APPROVED: "تأییدشده توسط مفسر",
    MANAGER_FINAL: "نسخه‌ی نهایی (تأیید مدیر)",
};

const RW_EVENT_LABELS = {
    RADIOGRAPHY_INTERPRETER_ASSIGNED: "تخصیص مفسر",
    RADIOGRAPHY_REPORT_STARTED: "شروع تکمیل گزارش",
    RADIOGRAPHY_REPORT_ISSUED: "صدور گزارش",
    RADIOGRAPHY_INTERPRETER_APPROVED: "تأیید مفسر",
    RADIOGRAPHY_MANAGER_REVIEW_STARTED: "شروع بررسی مدیر",
    RADIOGRAPHY_MANAGER_APPROVED: "تأیید نهایی مدیر",
    RADIOGRAPHY_MANAGER_REJECTED: "رد گزارش توسط مدیر",
    RADIOGRAPHY_REINTERPRETATION_REQUESTED: "درخواست تفسیر مجدد",
    RADIOGRAPHY_INTERPRETER_REASSIGNED: "تغییر مفسر",
    RADIOGRAPHY_WORKFLOW_CANCELLED: "لغو گردش‌کار",
    PART_STAGE_CHANGED: "تغییر مرحله‌ی قطعه",
};

function rwStatusBadge(status) {
    return `<span class="rw-status rw-status-${String(status).toLowerCase()}">${escapeHtml(RW_STATUS_LABELS[status] || status)}</span>`;
}

function rwNeedsSignature() {
    return hasPermission("INTERPRET_RADIOGRAPHY") || hasPermission("REVIEW_RADIOGRAPHY_REPORT");
}

/* ===========================
   نمودار مراحل
   =========================== */

function rwStepperHtml(steps) {
    if (!steps || !steps.length) return "";
    const marks = { DONE: "✓", CURRENT: "●", PENDING: "○" };
    const stateText = { DONE: "انجام شد", CURRENT: "مرحله‌ی فعلی", PENDING: "در انتظار" };
    return `<ol class="rw-stepper" aria-label="مراحل گردش‌کار رادیوگرافی">
        ${steps
            .map(
                (s) => `<li class="rw-step is-${String(s.state).toLowerCase()}"${s.state === "CURRENT" ? ' aria-current="step"' : ""}>
                    <span class="rw-step-mark" aria-hidden="true">${marks[s.state] || "○"}</span>
                    <span class="rw-step-label">${escapeHtml(s.label)}</span>
                    <span class="rw-step-state">${stateText[s.state] || ""}</span>
                </li>`
            )
            .join("")}
    </ol>`;
}

/* ===========================
   پنل گردش‌کار (صفحه‌ی قطعه و جزئیات وظیفه)
   =========================== */

function rwApprovalLine(label, a) {
    if (!a) return "";
    return `<div class="rw-approval-line"><span class="rw-check" aria-hidden="true">✓</span>
        <span>${escapeHtml(label)}: <strong>${escapeHtml(a.approverName)}</strong></span>
        <span class="task-muted">${escapeHtml(taskFormatDateTime(a.approvedAt))}</span></div>`;
}

function rwPanelHtml(wf, { canAssign = false } = {}) {
    const perms = wf ? wf.permissions : null;
    const buttons = [];
    if (wf) {
        if (perms.canEditReport) buttons.push(["open-report", "باز کردن گزارش رادیوگرافی", "btn-secondary"]);
        if (perms.canIssue) buttons.push(["issue", "صدور و تأیید گزارش", "btn-primary"]);
        if (perms.canReview) buttons.push(["review", "بررسی گزارش و تصمیم‌گیری", "btn-primary"]);
        if (wf.status === "MANAGER_APPROVED") buttons.push(["final", "مشاهده‌ی گزارش نهایی", "btn-primary"]);
        else if (wf.versions && wf.versions.length > 1) buttons.push(["versions", "نسخه‌های گزارش", "btn-secondary"]);
        if (perms.canReassign) buttons.push(["reassign", "تغییر مفسر", "btn-secondary"]);
        if (perms.canCancel) buttons.push(["cancel", "لغو گردش‌کار", "btn-danger"]);
    }
    if (canAssign) buttons.push(["assign", wf ? "تخصیص مفسر جدید" : "تخصیص مفسر", "btn-primary"]);

    const rejection =
        wf && wf.status === "REINTERPRETATION_REQUIRED" && wf.rejectionReason
            ? `<div class="rw-reject-box" role="status"><strong>دلیل رد توسط مدیر${wf.rejectedBy ? ` (${escapeHtml(wf.rejectedBy)})` : ""}:</strong>
                 <p>${escapeHtml(wf.rejectionReason)}</p></div>`
            : "";
    const signatureWarn =
        wf && wf.open && (perms.canIssue || perms.canReview) && !perms.viewerHasSignature
            ? `<div class="rw-warn" role="alert">امضای شما در پروفایل ثبت نشده است؛ بدون امضا تأیید ممکن نیست.
                 <a href="#/profile/${AuthState.user.id}" data-rw-act="to-profile">ثبت امضا در پروفایل</a></div>`
            : "";

    const events =
        wf && wf.events && wf.events.length
            ? `<details class="rw-history"><summary>تاریخچه‌ی گردش‌کار (${taskNumber(wf.events.length)})</summary>
                <ul>${wf.events
                    .map(
                        (e) => `<li><strong>${escapeHtml(RW_EVENT_LABELS[e.action] || e.action)}</strong>
                            <span class="task-muted">${escapeHtml(e.actorName || "")} — ${escapeHtml(taskFormatDateTime(e.createdAt))}</span>
                            ${e.reason ? `<div class="rw-history-reason">${escapeHtml(e.reason)}</div>` : ""}</li>`
                    )
                    .join("")}</ul></details>`
            : "";

    return `
        <div class="rw-panel">
            ${
                wf
                    ? `<div class="rw-panel-head">
                         ${rwStatusBadge(wf.status)}
                         <span class="task-muted">دور ${taskNumber(wf.round)}</span>
                       </div>
                       <dl class="rw-meta">
                         <div><dt>مفسر</dt><dd>${taskPersonHtml(wf.interpreter)}</dd></div>
                         <div><dt>تخصیص‌دهنده</dt><dd>${taskPersonHtml(wf.assignedBy)}</dd></div>
                         <div><dt>زمان تخصیص</dt><dd>${escapeHtml(taskFormatDateTime(wf.assignedAt))}</dd></div>
                       </dl>
                       ${rwStepperHtml(wf.steps)}
                       ${rwApprovalLine("تأیید مفسر", wf.interpreterApproval)}
                       ${rwApprovalLine("تأیید مدیر", wf.managerApproval)}`
                    : `<p class="task-muted">این قطعه در مرحله‌ی تفسیر رادیوگرافی است و هنوز مفسری برای آن تخصیص داده نشده است.</p>`
            }
            ${rejection}
            ${signatureWarn}
            ${
                buttons.length
                    ? `<div class="rw-actions" role="group" aria-label="عملیات گردش‌کار رادیوگرافی">
                         ${buttons.map(([act, label, cls]) => `<button type="button" class="btn ${cls}" data-rw-act="${act}">${escapeHtml(label)}</button>`).join("")}
                       </div>`
                    : ""
            }
            ${events}
        </div>`;
}

/**
 * ctx: { pieceId, onChange: async () => void }  — onChange بعد از هر تغییر موفق برای بازآفرینی صفحه
 */
function rwBindPanel(root, wf, ctx) {
    root.querySelectorAll("[data-rw-act]").forEach((el) => {
        if (el.dataset.rwAct === "to-profile") {
            el.addEventListener("click", () => closeModal());
            return;
        }
        el.addEventListener("click", () =>
            withButtonLoader(el, async () => {
                try {
                    await rwHandleAction(el.dataset.rwAct, wf, ctx);
                } catch (error) {
                    showToast(error.message, "error");
                }
            })
        );
    });
}

async function rwHandleAction(act, wf, ctx) {
    switch (act) {
        case "assign":
            await openAssignInterpreterModal({ pieceId: ctx.pieceId, workflow: null }, ctx.onChange);
            break;
        case "reassign":
            await openAssignInterpreterModal({ pieceId: wf.pieceId, workflow: wf }, ctx.onChange);
            break;
        case "cancel":
            await rwCancelWorkflow(wf, ctx);
            break;
        case "open-report": {
            const piece = await PieceAPI.getById(wf.pieceId);
            await openRadiographyReportModal(piece);
            break;
        }
        case "issue":
            await rwIssueAndApprove(wf, ctx);
            break;
        case "review":
            await openWorkflowReview(wf.id, ctx);
            break;
        case "final": {
            const finalVersion = (wf.versions || []).find((v) => v.kind === "MANAGER_FINAL");
            await openWorkflowDocument(wf.id, finalVersion ? finalVersion.id : null, ctx);
            break;
        }
        case "versions":
            await openWorkflowDocument(wf.id, null, ctx);
            break;
        default:
            break;
    }
}

async function loadPieceRadiographyWorkflow(piece) {
    const region = document.getElementById("profile-radiography-workflow");
    if (!region || !piece || piece.archivedAt) return;
    let state;
    try {
        state = await RadiographyWorkflowAPI.pieceState(piece.id);
    } catch (error) {
        return; // نمایش این بخش اختیاری است؛ خطا نباید پروفایل قطعه را مختل کند
    }
    if (typeof syncRadiographyEditControls === "function") {
        syncRadiographyEditControls(!state.workflow || !state.workflow.open || state.workflow.permissions.canEditReport);
    }
    const live = document.getElementById("profile-radiography-workflow");
    if (!live || (!state.workflow && !state.canAssign)) {
        if (live) live.innerHTML = "";
        return;
    }
    live.innerHTML = `<section class="rw-card" aria-labelledby="rw-title">
        <h3 class="rw-title" id="rw-title">گردش‌کار تفسیر رادیوگرافی</h3>
        ${rwPanelHtml(state.workflow, { canAssign: state.canAssign })}
    </section>`;
    rwBindPanel(live, state.workflow, {
        pieceId: piece.id,
        onChange: async () => {
            const fresh = await PieceAPI.getById(piece.id).catch(() => piece);
            piece.currentStage = fresh.currentStage;
            piece.radiographySubStage = fresh.radiographySubStage;
            if (typeof loadTracking === "function") loadTracking();
            await openPieceProfile(piece.id);
        },
    });
}

async function loadTaskWorkflowPanel(task) {
    const region = document.getElementById("task-workflow-region");
    if (!region || !task.workflowId) return;
    let wf;
    try {
        wf = await RadiographyWorkflowAPI.get(task.workflowId);
    } catch (error) {
        region.innerHTML = "";
        return;
    }
    const live = document.getElementById("task-workflow-region");
    if (!live) return;
    live.innerHTML = `<section class="task-card rw-card" aria-labelledby="rw-title">
        <h3 class="task-card-title" id="rw-title">گردش‌کار رادیوگرافی</h3>
        ${rwPanelHtml(wf)}
    </section>`;
    rwBindPanel(live, wf, {
        pieceId: wf.pieceId,
        onChange: async () => {
            await refreshTaskDetail();
        },
    });
}

/* ===========================
   تخصیص / تغییر مفسر / لغو
   =========================== */

async function openAssignInterpreterModal({ pieceId, workflow }, onDone) {
    const interpreters = await RadiographyWorkflowAPI.interpreters();
    const title = workflow ? "تغییر مفسر" : "تخصیص مفسر رادیوگرافی";
    if (!interpreters.length) {
        openNestedModal(
            title,
            `<p class="task-muted">هیچ کاربر فعالی با مجوز «تفسیر رادیوگرافی» وجود ندارد. ابتدا از بخش مدیریت کاربران این مجوز را به مفسر بدهید.</p>
             <div class="form-actions"><button type="button" class="btn btn-secondary" id="rw-assign-close">بستن</button></div>`
        );
        document.getElementById("rw-assign-close")?.addEventListener("click", closeNestedModal);
        return;
    }
    openNestedModal(
        title,
        `<form id="rw-assign-form" class="rw-assign-form">
            <p class="task-muted">وظیفه‌ی «تفسیر رادیوگرافی» با اولویت زیاد برای مفسر انتخابی ساخته می‌شود.</p>
            <div class="form-group">
                <label for="rw-interpreter-select">مفسر</label>
                <select id="rw-interpreter-select" required>
                    <option value="">— انتخاب کنید —</option>
                    ${interpreters
                        .filter((u) => !workflow || !workflow.interpreter || u.id !== workflow.interpreter.id)
                        .map(
                            (u) =>
                                `<option value="${u.id}">${escapeHtml(u.fullName || u.username)}${u.department ? ` — ${escapeHtml(u.department)}` : ""}</option>`
                        )
                        .join("")}
                </select>
            </div>
            <div class="form-error" id="rw-assign-error" role="alert"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="rw-assign-cancel">انصراف</button>
                <button type="submit" class="btn btn-primary" id="rw-assign-submit">${workflow ? "تغییر مفسر" : "تخصیص و ساخت وظیفه"}</button>
            </div>
        </form>`
    );
    document.getElementById("rw-assign-cancel").addEventListener("click", closeNestedModal);
    document.getElementById("rw-assign-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const errorBox = document.getElementById("rw-assign-error");
        const interpreterId = document.getElementById("rw-interpreter-select").value;
        errorBox.textContent = "";
        if (!interpreterId) {
            errorBox.textContent = "مفسر را انتخاب کنید";
            return;
        }
        const submit = document.getElementById("rw-assign-submit");
        await withButtonLoader(submit, async () => {
            try {
                if (workflow) await RadiographyWorkflowAPI.reassign(workflow.id, interpreterId);
                else await RadiographyWorkflowAPI.assign(pieceId, interpreterId);
                closeNestedModal();
                showToast(workflow ? "مفسر تغییر کرد" : "مفسر تخصیص داده شد و وظیفه ساخته شد");
                if (onDone) await onDone();
            } catch (error) {
                errorBox.textContent = error.message;
            }
        });
    });
}

async function rwCancelWorkflow(wf, ctx) {
    const result = await taskConfirm({
        title: "لغو گردش‌کار رادیوگرافی",
        message: "با لغو گردش‌کار، وظیفه‌های باز آن بسته می‌شوند و قطعه در مرحله‌ی تفسیر می‌ماند. ادامه می‌دهید؟",
        confirmText: "لغو گردش‌کار",
        danger: true,
        reasonLabel: "دلیل لغو (اختیاری)",
    });
    if (!result.confirmed) return;
    await RadiographyWorkflowAPI.cancel(wf.id, result.reason);
    showToast("گردش‌کار لغو شد");
    if (ctx.onChange) await ctx.onChange();
}

/* ===========================
   صدور و تأیید مفسر
   =========================== */

async function rwIssueAndApprove(wf, ctx) {
    if (!wf.permissions.viewerHasSignature) {
        const go = await taskConfirm({
            title: "امضا ثبت نشده است",
            message: "برای صدور و تأیید گزارش باید امضای شما در پروفایل ثبت شده باشد.",
            confirmText: "رفتن به پروفایل",
        });
        if (go.confirmed) {
            closeModal();
            taskNavigate(`#/profile/${AuthState.user.id}`);
        }
        return;
    }
    const result = await taskConfirm({
        title: "صدور و تأیید گزارش رادیوگرافی",
        message:
            "آیا از صحت اطلاعات گزارش رادیوگرافی اطمینان دارید؟\n" +
            "پس از تأیید، گزارش با نام و امضای شما برای تأیید مدیر ارسال می‌شود و دیگر قابل ویرایش نخواهد بود.",
        confirmText: "تأیید و صدور",
    });
    if (!result.confirmed) return;
    await RadiographyWorkflowAPI.interpreterApprove(wf.id);
    showToast("گزارش صادر شد و برای تأیید مدیر ارسال گردید");
    if (ctx.onChange) await ctx.onChange();
}

/* ===========================
   بازبینی مدیر
   =========================== */

async function openWorkflowReview(workflowId, ctx) {
    const wf = await RadiographyWorkflowAPI.get(workflowId);
    const candidates = (wf.versions || []).filter((v) => v.kind === "INTERPRETER_APPROVED");
    const version = candidates.length
        ? candidates.reduce((a, b) => (b.versionNo > a.versionNo ? b : a))
        : (wf.versions || [])[wf.versions.length - 1];
    if (!version) {
        showToast("نسخه‌ای از گزارش برای بررسی وجود ندارد", "error");
        return;
    }
    await openWorkflowDocument(workflowId, version.id, { ...ctx, review: true }, wf);
}

async function rwManagerApprove(wf, ctx) {
    if (!wf.permissions.canApprove) throw new Error("تأیید نهایی گزارش خودتان مجاز نیست؛ مدیر مستثناست");
    if (!wf.permissions.viewerHasSignature) {
        const go = await taskConfirm({
            title: "امضا ثبت نشده است",
            message: "برای تأیید نهایی باید امضای شما در پروفایل ثبت شده باشد.",
            confirmText: "رفتن به پروفایل",
        });
        if (go.confirmed) {
            closeModal();
            taskNavigate(`#/profile/${AuthState.user.id}`);
        }
        return;
    }
    const result = await taskConfirm({
        title: "تأیید نهایی گزارش رادیوگرافی",
        message:
            "آیا گزارش رادیوگرافی را تأیید می‌کنید؟\n" +
            "با تأیید نهایی، گزارش با نام و امضای شما نهایی می‌شود و قطعه به مرحله‌ی بعد (VT) منتقل خواهد شد.",
        confirmText: "تأیید و نهایی‌سازی",
    });
    if (!result.confirmed) return;
    await RadiographyWorkflowAPI.managerApprove(wf.id);
    closeModal();
    showToast("گزارش تأیید نهایی شد و قطعه به مرحله‌ی بعد منتقل شد");
    if (ctx && ctx.onChange) await ctx.onChange();
}

async function rwManagerReject(wf, ctx) {
    let reason = "";
    // دلیل رد اجباری است؛ تا زمانی که خالی باشد پنجره دوباره باز می‌شود (یا کاربر انصراف دهد)
    for (;;) {
        const result = await taskConfirm({
            title: "رد گزارش رادیوگرافی",
            message: "گزارش برای تفسیر مجدد به مفسر بازگردانده می‌شود و قطعه در مرحله‌ی تفسیر می‌ماند.",
            confirmText: "رد گزارش",
            danger: true,
            reasonLabel: "دلیل رد (الزامی)",
        });
        if (!result.confirmed) return;
        if (result.reason) {
            reason = result.reason;
            break;
        }
        showToast("نوشتن دلیل رد الزامی است", "error");
    }
    await RadiographyWorkflowAPI.managerReject(wf.id, reason);
    closeModal();
    showToast("گزارش رد شد و برای تفسیر مجدد به مفسر بازگشت");
    if (ctx && ctx.onChange) await ctx.onChange();
}

/* ===========================
   سند مهرشده (نمایش و چاپ)
   =========================== */

function rwSafeImageSrc(dataUrl) {
    return typeof dataUrl === "string" && /^data:image\/(png|jpeg);base64,[A-Za-z0-9+/=]+$/.test(dataUrl) ? dataUrl : "";
}

function rwTimeText(iso) {
    const date = taskParseDate(iso);
    return date ? date.toLocaleTimeString("fa-IR", { hour: "2-digit", minute: "2-digit" }) : "";
}

/** مهر داخل کادر امضا؛ همه‌ی لایه‌ها شفافاند تا خطوط و عنوان کادر دیده شوند */
function rwStampHtml(a) {
    const manager = a.type === "MANAGER";
    const src = rwSafeImageSrc(a.signatureDataUrl);
    return `<div class="rw-sign-stamp ${manager ? "rw-stamp-manager" : "rw-stamp-interpreter"}">
        <div class="rw-stamp-seal"><span class="rw-stamp-word">APPROVED</span><span class="rw-stamp-fa">تأیید شد</span></div>
        <div class="rw-stamp-info">
            <div class="rw-stamp-name">${escapeHtml(a.approverName || "")}</div>
            <div class="rw-stamp-when">${escapeHtml(taskFormatDate(a.approvedAt))}</div>
            <div class="rw-stamp-when">${escapeHtml(rwTimeText(a.approvedAt))}</div>
        </div>
        <div class="rw-stamp-sign">${src ? `<img src="${src}" alt="امضای ${escapeHtml(a.approverName || "")}" />` : ""}</div>
    </div>`;
}

/** مهر مفسر → کادر «مفسر»؛ مهر مدیر → کادر «کنترل کیفیت» */
function rwStampsMap(approvals) {
    const approved = (approvals || []).filter((a) => a.decision === "APPROVED" || !a.decision);
    const last = (type) => approved.filter((a) => a.type === type).pop();
    const interpreter = last("INTERPRETER");
    const manager = last("MANAGER");
    return {
        interpreter: interpreter ? rwStampHtml(interpreter) : "",
        manager: manager ? rwStampHtml(manager) : "",
    };
}

function rwBuildDocumentPages(container, doc) {
    container.innerHTML = "";
    const footerStamps = rwStampsMap(doc.approvals);
    const reports = Array.isArray(doc.reports) ? doc.reports : [];
    if (!reports.length) {
        container.innerHTML = `<p class="task-muted">این نسخه گزارشی ندارد.</p>`;
        return;
    }
    reports.forEach((report) => {
        const block = document.createElement("div");
        block.className = "rw-doc-report";
        block.innerHTML = `<h4 class="rw-doc-report-title no-print">${escapeHtml(report.templateName || "گزارش رادیوگرافی")}</h4>
            <div class="rt-pages"></div>`;
        container.appendChild(block);
        const meta = {
            editable: false,
            partName: report.partName || doc.partLabel || "",
            projectCode: report.projectCode || doc.projectCode || "",
            pieceNumber: String(doc.pieceNumber ?? ""),
            date: report.reportDate ? isoToJalaliString(report.reportDate) : "",
            reportNumber: report.reportNumber || "",
            requestNumber: report.requestNumber || "",
        };
        buildSheetPages(block.querySelector(".rt-pages"), {
            lines: report.lines || [],
            renderHeader: (pageIndex) => renderSheetHeader(meta, pageIndex),
            renderRow: (line, index) => renderRadiographyReportLineRow(line, index, false),
            footerStamps,
        });
    });
}

/**
 * ctx: { review?: boolean, onChange?: fn }. اگر wf داده نشود دوباره از سرور گرفته می‌شود.
 */
async function openWorkflowDocument(workflowId, versionId, ctx = {}, wfPreloaded = null) {
    const wf = wfPreloaded || (await RadiographyWorkflowAPI.get(workflowId));
    const versions = wf.versions || [];
    const targetId = versionId || (versions.length ? versions[versions.length - 1].id : null);
    if (!targetId) {
        showToast("نسخه‌ای از گزارش وجود ندارد", "error");
        return;
    }
    const doc = await RadiographyWorkflowAPI.document(workflowId, targetId);
    const reviewing = Boolean(ctx.review) && wf.permissions.canReview;

    const versionOptions = versions
        .map(
            (v) =>
                `<option value="${v.id}" ${v.id === targetId ? "selected" : ""}>نسخه ${taskNumber(v.versionNo)} — ${escapeHtml(RW_VERSION_LABELS[v.kind] || v.kind)}</option>`
        )
        .join("");

    openModal(
        `گزارش رادیوگرافی — ${doc.partLabel || ""}`,
        `<div class="rw-doc">
            <div class="rw-doc-bar">
                ${rwStatusBadge(wf.status)}
                ${
                    versions.length > 1
                        ? `<label class="rw-doc-version">نسخه
                             <select id="rw-doc-version">${versionOptions}</select></label>`
                        : ""
                }
                <span class="task-muted rw-doc-hash" title="اثر انگشت محتوای این نسخه (SHA-256)">
                    <bdi dir="ltr">${escapeHtml((doc.contentHash || "").slice(0, 16))}</bdi>
                </span>
            </div>
            ${
                reviewing
                    ? `<div class="rw-review-note">${rwApprovalLine("تأیید مفسر", wf.interpreterApproval) || "<span>تأیید مفسر ثبت نشده است.</span>"}</div>`
                    : ""
            }
            <div id="rw-doc-pages" class="rw-doc-pages"></div>
            <div class="form-actions rw-doc-actions">
                <button type="button" class="btn btn-secondary" id="rw-doc-close">بستن</button>
                <button type="button" class="btn btn-secondary" id="rw-doc-print">پرینت</button>
                ${
                    reviewing
                        ? `<button type="button" class="btn btn-danger" id="rw-doc-reject">رد گزارش</button>
                           ${wf.permissions.canApprove ? '<button type="button" class="btn btn-primary" id="rw-doc-approve">تأیید نهایی</button>' : '<p class="form-hint">تأیید نهایی گزارش خودتان باید توسط شخص دیگری انجام شود؛ مدیر مستثناست.</p>'}`
                        : ""
                }
            </div>
        </div>`,
        { size: "sheet" }
    );

    rwBuildDocumentPages(document.getElementById("rw-doc-pages"), doc);

    document.getElementById("rw-doc-close").addEventListener("click", closeModal);
    document.getElementById("rw-doc-print").addEventListener("click", () => printRadiographyPages(document.getElementById("rw-doc-pages")));
    document.getElementById("rw-doc-version")?.addEventListener("change", (event) => {
        openWorkflowDocument(workflowId, event.target.value, ctx, wf).catch((e) => showToast(e.message, "error"));
    });
    const guard = (fn) => (event) =>
        withButtonLoader(event.currentTarget, async () => {
            try {
                await fn();
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    document.getElementById("rw-doc-approve")?.addEventListener("click", guard(() => rwManagerApprove(wf, ctx)));
    document.getElementById("rw-doc-reject")?.addEventListener("click", guard(() => rwManagerReject(wf, ctx)));
}

/* ===========================
   امضای پروفایل
   =========================== */

async function renderProfileSignatureCard() {
    const region = document.getElementById("profile-signature-region");
    if (!region || !rwNeedsSignature()) return;
    let status;
    try {
        status = await SignatureAPI.get();
    } catch (error) {
        region.innerHTML = "";
        return;
    }
    const src = status.present ? rwSafeImageSrc(status.dataUrl) : "";
    region.innerHTML = `<section class="task-card rw-signature-card" aria-labelledby="profile-signature-title">
        <h3 class="task-card-title" id="profile-signature-title">امضای من</h3>
        <p class="task-muted">این امضا هنگام تأیید گزارش‌های رادیوگرافی به‌صورت خودکار کنار مهر «تأیید شد» درج می‌شود. تصویر PNG یا JPEG با حجم حداکثر ۵۱۲ کیلوبایت.</p>
        <div class="rw-signature-body">
            <div class="rw-signature-preview">${
                src ? `<img src="${src}" alt="امضای ثبت‌شده" />` : `<span class="task-muted">امضایی ثبت نشده است</span>`
            }</div>
            <div class="rw-signature-actions">
                <input type="file" id="rw-signature-file" accept="image/png,image/jpeg" hidden />
                <button type="button" class="btn btn-secondary" id="rw-signature-pick">${status.present ? "جایگزینی امضا" : "بارگذاری امضا"}</button>
                ${status.present ? `<button type="button" class="btn btn-danger" id="rw-signature-remove">حذف امضا</button>` : ""}
            </div>
        </div>
    </section>`;

    const input = document.getElementById("rw-signature-file");
    document.getElementById("rw-signature-pick").addEventListener("click", () => input.click());
    input.addEventListener("change", async () => {
        const file = input.files && input.files[0];
        if (!file) return;
        if (!["image/png", "image/jpeg"].includes(file.type)) {
            showToast("فقط فایل PNG یا JPEG مجاز است", "error");
            input.value = "";
            return;
        }
        if (file.size > 512 * 1024) {
            showToast("حجم امضا نباید بیشتر از ۵۱۲ کیلوبایت باشد", "error");
            input.value = "";
            return;
        }
        try {
            await SignatureAPI.upload(file);
            showToast("امضا ذخیره شد");
            await renderProfileSignatureCard();
        } catch (error) {
            showToast(error.message, "error");
            input.value = "";
        }
    });
    document.getElementById("rw-signature-remove")?.addEventListener("click", async () => {
        const result = await taskConfirm({
            title: "حذف امضا",
            message: "امضای ثبت‌شده حذف شود؟ تا ثبت امضای جدید نمی‌توانید گزارش را تأیید کنید. مهرهای قبلی تغییری نمی‌کنند.",
            confirmText: "حذف امضا",
            danger: true,
        });
        if (!result.confirmed) return;
        try {
            await SignatureAPI.remove();
            showToast("امضا حذف شد");
            await renderProfileSignatureCard();
        } catch (error) {
            showToast(error.message, "error");
        }
    });
}
