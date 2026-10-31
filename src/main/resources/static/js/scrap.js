/** Scrap uses the same reason catalog and optional notes as welding. */
async function requestScrapDetails({ pieceId, defects, dispatch = null, count = 1 } = {}) {
    if (!defects) defects = pieceId ? await RadiographyDefectAPI.getByPiece(pieceId) : [];
    const selected = new Set(dispatch?.defectIds || []);
    return new Promise(resolve => {
        openNestedModal(dispatch ? "ویرایش علت اسقاط" : "ارسال به اسقاط", `
            <form id="form-scrap-reason">
                ${count > 1 ? `<p class="form-hint">این علت برای ${formatFaNumber(count)} قطعه ثبت می‌شود.</p>` : ""}
                <div class="form-group"><label for="scrap-type">علت اسقاط</label>
                    <select id="scrap-type" required><option value="">انتخاب کنید</option>${defectTypeSelectOptionsHtml(dispatch?.defectType)}</select></div>
                ${defectTypeCustomFieldHtml("scrap-custom")}
                <div class="form-group"><label for="scrap-notes">توضیحات (اختیاری)</label>
                    <textarea id="scrap-notes" rows="3" maxlength="10000">${escapeHtml(dispatch?.notes || "")}</textarea></div>
                ${defects.length ? `<fieldset><legend>ارتباط با عیوب ثبت‌شده (اختیاری)</legend>${defects.map(d => `
                    <label class="checkbox-label"><input type="checkbox" class="scrap-defect" value="${escapeHtml(d.id)}" ${selected.has(d.id) ? "checked" : ""}>
                    ${escapeHtml(formatStructuredDefect(d))}</label>`).join("")}</fieldset>` : ""}
                <div class="form-error" id="scrap-error" role="alert"></div>
                <div class="form-actions"><button type="button" class="btn btn-secondary" id="scrap-cancel">انصراف</button>
                    <button type="submit" class="btn btn-primary">${dispatch ? "ذخیره تغییرات" : "ثبت اسقاط"}</button></div>
            </form>`);
        const overlay = document.getElementById("nested-modal-overlay");
        let settled = false;
        const finish = value => {
            if (settled) return;
            settled = true; observer.disconnect(); resolve(value);
        };
        // Escape, backdrop and the dialog X must also release the caller's button loader.
        const observer = new MutationObserver(() => {
            if (!overlay.classList.contains("active") || !document.getElementById("form-scrap-reason")) finish(null);
        });
        observer.observe(overlay, { attributes: true, attributeFilter: ["class"], childList: true, subtree: true });
        const type = document.getElementById("scrap-type");
        const custom = document.getElementById("scrap-custom");
        custom.maxLength = 255;
        custom.value = dispatch?.defectTypeCustom || "";
        bindDefectTypeOtherToggle(type, document.getElementById("scrap-custom-wrap"));
        document.getElementById("scrap-cancel").onclick = () => closeNestedModal();
        document.getElementById("form-scrap-reason").onsubmit = event => {
            event.preventDefault();
            const data = readDefectTypePayload(type, custom);
            if (!data.defectType || (data.defectType === "OTHER" && !data.defectTypeCustom)) {
                const error = document.getElementById("scrap-error");
                error.textContent = "علت اسقاط و متن گزینهٔ سایر را وارد کنید"; error.style.display = "block"; return;
            }
            data.notes = document.getElementById("scrap-notes").value.trim();
            data.defectIds = [...document.querySelectorAll(".scrap-defect:checked")].map(el => el.value);
            data.version = dispatch?.version ?? null;
            finish(data); closeNestedModal();
        };
    });
}

function renderScrapSection(piece, dispatches = []) {
    if (piece.currentStage !== "SCRAPPED" && !dispatches.length) return "";
    const active = dispatches.find(d => d.active);
    return `<section class="profile-welding-station" id="profile-scrap-section">
        <div class="profile-info-label">اسقاط قطعه</div>
        ${piece.currentStage === "SCRAPPED" && !piece.archivedAt && hasPermission("MOVE_PIECE_STAGE")
            ? '<button type="button" class="btn btn-secondary btn-sm" id="btn-edit-scrap">ویرایش علت اسقاط</button>' : ""}
        ${piece.currentStage === "SCRAPPED" && !active ? '<p class="form-hint">علت اسقاط ثبت نشده است.</p>' : ""}
        <div class="history-list">${dispatches.map(d => `<div class="history-row">
            <span class="substage-badge">${escapeHtml(d.defectLabel || "ثبت نشده")}</span>
            <span>${escapeHtml(d.notes || "—")}</span>
            <span>${d.sentAt ? formatDateTime(d.sentAt) : "زمان ثبت نشده"} — ${escapeHtml(d.sentBy || "—")}</span>
            ${d.originStage ? `<span>از ${escapeHtml(stageLabel(d.originStage))}</span>` : ""}
            ${d.reversedAt ? `<span>بازگشت از اسقاط: ${formatDateTime(d.reversedAt)} — ${escapeHtml(d.reversedBy || "—")}</span>` : ""}
            ${d.updatedAt && d.updatedAt !== d.sentAt ? `<span>آخرین ویرایش: ${formatDateTime(d.updatedAt)} — ${escapeHtml(d.updatedBy || "—")}</span>` : ""}
        </div>`).join("")}</div></section>`;
}

function attachScrapEvents(piece, defects, dispatches) {
    document.getElementById("btn-edit-scrap")?.addEventListener("click", async () => {
        try {
            const dispatch = dispatches.find(d => d.active) || { version: null };
            const data = await requestScrapDetails({ pieceId: piece.id, defects, dispatch });
            if (!data) return;
            await PieceAPI.updateScrapDispatch(piece.id, data);
            showToast("علت اسقاط ویرایش شد");
            await openPieceProfile(piece.id);
        } catch (error) { showToast(error.message, "error"); }
    });
}
