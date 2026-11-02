/**
 * welding-station.js
 * ایستگاه جوشکاری جدا از مراحل خط تولید است. هنگام ارسال، نوع عیب از کاربر
 * گرفته می‌شود؛ ثبت عیب «نیاز به جوش» در رادیوگرافی الزامی نیست. پس از اتمام
 * فرآیند، اگر قطعه به شوت نرسیده باشد به شوت یا مرحلهٔ قبل می‌رود؛ اگر شوت را
 * رد کرده باشد گزینهٔ بازگشت تفسیر است (شوت برای ری‌شوت هم هست).
 */

const STAGES_BEFORE_SHOT = new Set(["NOT_STARTED", "CASTING", "ROUGH_MACHINING"]);

function weldingDefectTypeLabel(type, custom) {
    if (typeof defectTypeDisplay === "function") {
        return defectTypeDisplay(type, custom);
    }
    return type || "—";
}

function canUseWeldingStation() {
    return hasPermission("MOVE_PIECE_STAGE");
}

function canSendToWelding(piece) {
    return (
        canUseWeldingStation() &&
        piece &&
        !piece.archivedAt &&
        piece.currentStage !== "WELDING" &&
        piece.currentStage !== "DELIVERED" &&
        piece.currentStage !== "SCRAPPED"
    );
}

function renderWeldingSendIcon() {
    const icon = typeof STAGE_ICON_WELDING !== "undefined"
        ? STAGE_ICON_WELDING
        : `<svg class="profile-icon-fallback" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>`;
    return `<button type="button" class="profile-action-icon profile-action-welding" id="btn-send-to-welding" title="ارسال به جوشکاری" aria-label="ارسال به جوشکاری">${icon}</button>`;
}

function renderWeldingStationSection(piece, dispatches = []) {
    if (piece.currentStage !== "WELDING") {
        return "";
    }

    const openDispatch = (dispatches || []).find((d) => d.open);

    return `
        <div class="profile-welding-station">
            ${openDispatch && hasPermission('CANCEL_WELDING_DISPATCH') ? '<button type="button" class="btn btn-danger" id="btn-cancel-welding-dispatch">لغو اعزام اشتباه</button>' : ''}
            <div class="profile-info-label">ایستگاه جوشکاری</div>
            ${
                openDispatch
                    ? `<div class="welding-station-open">
                    <div class="welding-station-open-row">
                        <span class="welding-station-k">نوع عیب</span>
                        <span class="welding-station-v">${escapeHtml((openDispatch.defectLabels && openDispatch.defectLabels.length ? openDispatch.defectLabels : [weldingDefectTypeLabel(openDispatch.defectType, openDispatch.defectTypeCustom)]).join("، "))}</span>
                    </div>
                    <div class="welding-station-open-row">
                        <span class="welding-station-k">توضیحات</span>
                        <span class="welding-station-v">${escapeHtml(openDispatch.notes || "—")}</span>
                    </div>
                    <div class="history-meta">
                        ${openDispatch.sentBy ? `<span class="history-user">${escapeHtml(openDispatch.sentBy)}</span>` : ""}
                        <span class="history-time">${formatDateTime(openDispatch.sentAt)}</span>
                    </div>
                </div>`
                    : `<p class="form-hint">این قطعه در ایستگاه جوشکاری است.</p>`
            }
            ${
                canUseWeldingStation()
                    ? `<div class="form-actions welding-station-actions">
                    ${openDispatch ? `<button type="button" class="btn btn-secondary" id="btn-edit-welding-defect">ویرایش عیب جوشکاری</button>` : ""}
                    <button type="button" class="btn btn-primary" id="btn-complete-welding">اتمام فرآیند</button>
                </div>`
                    : ""
            }
        </div>`;
}

function renderWeldingDispatchHistorySection(dispatches = []) {
    const history = (dispatches || []).filter((d) => !d.open);
    if (!history.length) return "";
    return `
        <div class="profile-history">
            ${renderWeldingDispatchHistory(history)}
        </div>`;
}

function renderWeldingDispatchHistory(history) {
    if (!history.length) return "";
    const rows = history
        .map(
            (d) => `
        <div class="history-row">
            <span class="substage-badge">${escapeHtml((d.defectLabels && d.defectLabels.length ? d.defectLabels : [weldingDefectTypeLabel(d.defectType, d.defectTypeCustom)]).join("، "))}${d.notes ? " — " + escapeHtml(d.notes) : ""}</span>
            <span class="history-meta">
                ${d.sentBy ? `<span class="history-user">${escapeHtml(d.sentBy)}</span>` : ""}
                <span class="history-time">ارسال ${formatDateTime(d.sentAt)}</span>
                ${d.cancelledAt ? `<span class="history-time">لغو ${formatDateTime(d.cancelledAt)} — ${escapeHtml(d.cancelledBy || '')} — ${escapeHtml(d.cancellationReason || '')}</span>` : d.completedAt ? `<span class="history-time">اتمام ${formatDateTime(d.completedAt)}${d.completedBy ? " — " + escapeHtml(d.completedBy) : ""}</span>` : ""}
            </span>
        </div>`
        )
        .join("");
    return `
        <div class="profile-info-label" style="margin-bottom: 8px;">تاریخچه ایستگاه جوشکاری</div>
        <div class="history-list">${rows}</div>`;
}

function openWeldingDefects(defects) {
    return (defects || []).filter((d) => d.status === "NEEDS_WELDING" && !d.weldingConfirmed);
}

function openSendToWeldingPopup(piece, defects, editDispatch = null) {
    const openDefects = editDispatch
        ? (defects || []).filter(defect => editDispatch.defectIds?.includes(defect.id)
            || (defect.status === "NEEDS_WELDING" && !defect.weldingConfirmed))
        : openWeldingDefects(defects);
    const typeOptions =
        typeof defectTypeSelectOptionsHtml === "function"
            ? `<option value="">انتخاب کنید</option>${defectTypeSelectOptionsHtml()}`
            : `<option value="">انتخاب کنید</option>`;
    const options = openDefects
        .map((d) => {
            const typeLabel =
                typeof defectTypeDisplay === "function"
                    ? defectTypeDisplay(d.defectType, d.defectTypeCustom)
                    : d.defectType;
            const loc =
                typeof formatDefectLocation === "function"
                    ? formatDefectLocation(d)
                    : d.locationCm != null
                      ? `${d.locationCm} cm`
                      : "";
            const extra = [d.course, d.defectClass || d.typeCode, loc].filter(Boolean).join(" — ");
            return `
                <label class="permission-checkbox">
                    <input type="checkbox" class="welding-defect-checkbox" value="${d.id}" ${editDispatch?.defectIds?.includes(d.id) ? "checked" : ""}
                        data-defect-type="${escapeHtml(d.defectType || "")}"
                        data-defect-type-custom="${escapeHtml(d.defectTypeCustom || "")}" />
                    ${escapeHtml(typeLabel)}${extra ? ` <span class="form-hint">(${escapeHtml(extra)})</span>` : ""}
                </label>`;
        })
        .join("");

    const defectsBlock = openDefects.length
        ? `<div class="form-group">
                <label>عیب رادیوگرافی مرتبط (اختیاری)</label>
                <p class="form-hint">ثبت عیب «نیاز به جوش» در رادیوگرافی برای ارسال الزامی نیست.</p>
                <div class="permissions-grid">${options}</div>
            </div>`
        : "";

    openNestedModal(
        editDispatch ? "ویرایش عیب جوشکاری" : "ارسال به جوشکاری",
        `
        <form id="form-send-to-welding">
            <div class="form-group">
                <label for="welding-defect-type">نوع عیب</label>
                <select id="welding-defect-type" required>${typeOptions}</select>
            </div>
            ${typeof defectTypeCustomFieldHtml === "function" ? defectTypeCustomFieldHtml("welding-defect-type-custom") : ""}
            ${defectsBlock}
            <div class="form-group">
                <label for="welding-notes">توضیحات (اختیاری)</label>
                <textarea id="welding-notes" rows="3" placeholder="شرح کار جوشکاری">${escapeHtml(editDispatch?.notes || "")}</textarea>
            </div>
            <div class="form-error" id="welding-station-error"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-cancel-send-welding">انصراف</button>
                <button type="submit" class="btn btn-primary">${editDispatch ? "ذخیره تغییرات" : "ارسال"}</button>
            </div>
        </form>`
    );

    const typeSelect = document.getElementById("welding-defect-type");
    const customWrap = document.getElementById("welding-defect-type-custom-wrap");
    const customInput = document.getElementById("welding-defect-type-custom");
    if (editDispatch) {
        typeSelect.value = editDispatch.defectType || "";
        if (customInput) customInput.value = editDispatch.defectTypeCustom || "";
    }
    if (typeof bindDefectTypeOtherToggle === "function") {
        bindDefectTypeOtherToggle(typeSelect, customWrap);
    }

    document.querySelectorAll(".welding-defect-checkbox").forEach((box) => {
        box.addEventListener("change", () => {
            if (!box.checked || !typeSelect) return;
            const checked = [...document.querySelectorAll(".welding-defect-checkbox:checked")];
            if (checked.length !== 1) return;
            typeSelect.value = box.dataset.defectType || "";
            if (customInput) {
                customInput.value = box.dataset.defectTypeCustom || "";
            }
            typeSelect.dispatchEvent(new Event("change"));
        });
    });

    document.getElementById("btn-cancel-send-welding")?.addEventListener("click", closeNestedModal);

    document.getElementById("form-send-to-welding").addEventListener("submit", async (e) => {
        e.preventDefault();
        const defectIds = [...document.querySelectorAll(".welding-defect-checkbox:checked")].map((el) => el.value);
        const typePayload =
            typeof readDefectTypePayload === "function"
                ? readDefectTypePayload(typeSelect, customInput)
                : { defectType: typeSelect?.value };
        const notes = document.getElementById("welding-notes").value.trim();
        const errorBox = document.getElementById("welding-station-error");
        const btn = e.submitter || document.querySelector("#form-send-to-welding button[type=submit]");
        await withButtonLoader(btn, async () => {
            try {
                if (editDispatch) {
                    await PieceAPI.updateWeldingDispatch(piece.id, editDispatch.id, { defectIds, notes, ...typePayload });
                } else {
                    await PieceAPI.sendToWeldingStation(piece.id, { defectIds, notes, ...typePayload });
                }
                showToast(editDispatch ? "عیب جوشکاری ویرایش شد" : "قطعه به ایستگاه جوشکاری ارسال شد");
                closeNestedModal();
                loadTracking();
                await openPieceProfile(piece.id);
            } catch (error) {
                if (errorBox) {
                    errorBox.textContent = error.message;
                    errorBox.style.display = "block";
                } else {
                    showToast(error.message, "error");
                }
            }
        });
    });
}

function attachWeldingStationEvents(piece, defects = [], dispatches = []) {
    document.getElementById('btn-cancel-welding-dispatch')?.addEventListener('click',()=>{
        const d=dispatches.find(x=>x.open);if(!d)return;
        openOperationCancellation('لغو اعزام جوشکاری',true,async reason=>{await OperationAPI.cancelWelding(piece.id,d.id,reason,true);await openPieceProfile(piece.id);});
    });
    const editBtn = document.getElementById("btn-edit-welding-defect");
    const editableDispatch = dispatches.find(dispatch => dispatch.open);
    if (editBtn && editableDispatch) {
        editBtn.addEventListener("click", () => openSendToWeldingPopup(piece, defects, editableDispatch));
    }
    const sendBtn = document.getElementById("btn-send-to-welding");
    if (sendBtn) {
        sendBtn.addEventListener("click", () => openSendToWeldingPopup(piece, defects));
    }

    const completeBtn = document.getElementById("btn-complete-welding");
    if (completeBtn) {
        const openDispatch = (dispatches || []).find((d) => d.open);
        completeBtn.addEventListener("click", () => openCompleteWeldingPopup(piece, openDispatch));
    }
}

function weldingOriginLabel(dispatch) {
    if (!dispatch || !dispatch.originStage) return "";
    if (dispatch.originStage === "RADIOGRAPHY" && dispatch.originRadiographySubStage) {
        return RADIOGRAPHY_SUBSTAGE_LABELS[dispatch.originRadiographySubStage] || stageLabel("RADIOGRAPHY");
    }
    if (dispatch.originStage === "HEAT_TREATMENT" && dispatch.originHeatTreatmentSubStage) {
        return HEAT_TREATMENT_SUBSTAGE_LABELS[dispatch.originHeatTreatmentSubStage] || stageLabel("HEAT_TREATMENT");
    }
    if (dispatch.originStage === "ROUGH_MACHINING" && dispatch.originRoughMachiningSubStage) {
        return ROUGH_MACHINING_SUBSTAGE_LABELS[dispatch.originRoughMachiningSubStage] || stageLabel("ROUGH_MACHINING");
    }
    return stageLabel(dispatch.originStage);
}

function weldingOriginBeforeShot(dispatch) {
    return Boolean(dispatch && dispatch.originStage && STAGES_BEFORE_SHOT.has(dispatch.originStage));
}

function openCompleteWeldingPopup(piece, openDispatch) {
    const originLabel = weldingOriginLabel(openDispatch);
    const beforeShot = weldingOriginBeforeShot(openDispatch);
    const shotLabel =
        typeof RADIOGRAPHY_SUBSTAGE_LABELS !== "undefined" && RADIOGRAPHY_SUBSTAGE_LABELS.SHOT
            ? RADIOGRAPHY_SUBSTAGE_LABELS.SHOT
            : "شوت";
    const interpretationLabel =
        typeof RADIOGRAPHY_SUBSTAGE_LABELS !== "undefined" && RADIOGRAPHY_SUBSTAGE_LABELS.INTERPRETATION
            ? RADIOGRAPHY_SUBSTAGE_LABELS.INTERPRETATION
            : "تفسیر";

    const radios = beforeShot
        ? `<label class="permission-checkbox">
                    <input type="radio" name="welding-return" value="SHOT" checked />
                    ${escapeHtml(shotLabel)}
                </label>
                ${
                    originLabel
                        ? `<label class="permission-checkbox">
                    <input type="radio" name="welding-return" value="PREVIOUS" />
                    ${escapeHtml(originLabel)}
                </label>`
                        : ""
                }`
        : `<label class="permission-checkbox">
                    <input type="radio" name="welding-return" value="SHOT" />
                    ${escapeHtml(shotLabel)}
                </label>
                <label class="permission-checkbox">
                    <input type="radio" name="welding-return" value="INTERPRETATION" checked />
                    ${escapeHtml(interpretationLabel)}
                </label>`;

    openNestedModal(
        "اتمام فرآیند جوشکاری",
        `
        <form id="form-complete-welding">
            <p class="form-hint">قطعه پس از اتمام جوشکاری به کجا برود؟</p>
            <div class="form-group">
                ${radios}
            </div>
            <div class="form-error" id="complete-welding-error"></div>
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" id="btn-cancel-complete-welding">انصراف</button>
                <button type="submit" class="btn btn-primary">اتمام فرآیند</button>
            </div>
        </form>
        `
    );

    document.getElementById("btn-cancel-complete-welding").addEventListener("click", () => closeNestedModal());
    document.getElementById("form-complete-welding").addEventListener("submit", async (e) => {
        e.preventDefault();
        const destination =
            document.querySelector('input[name="welding-return"]:checked')?.value ||
            (beforeShot ? "SHOT" : "INTERPRETATION");
        const errorBox = document.getElementById("complete-welding-error");
        const btn = e.submitter || document.querySelector("#form-complete-welding button[type=submit]");
        await withButtonLoader(btn, async () => {
            try {
                await PieceAPI.completeWeldingStation(piece.id, destination);
                let destLabel = interpretationLabel;
                if (destination === "PREVIOUS" && originLabel) destLabel = originLabel;
                else if (destination === "SHOT") destLabel = shotLabel;
                showToast(`فرآیند جوشکاری تمام شد؛ قطعه به «${destLabel}» منتقل شد`);
                closeNestedModal();
                loadTracking();
                await openPieceProfile(piece.id);
            } catch (error) {
                if (errorBox) {
                    errorBox.textContent = error.message;
                    errorBox.style.display = "block";
                } else {
                    showToast(error.message, "error");
                }
            }
        });
    });
}
