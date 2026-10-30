/**
 * notes.js
 * منطق مربوط به «یادداشت‌های قطعه»: نمایش لیست یادداشت‌های زمان‌دار داخل پروفایل قطعه
 * (جایگزین فیلد قدیمی «توضیحات»)، افزودن یادداشت جدید، و حذف یادداشت (فقط ادمین).
 */

const NotesState = {
    expanded: true,
};

function resetNotesState() {
    NotesState.expanded = true;
}

/* ===========================
   رندر بخش یادداشت‌ها داخل پروفایل قطعه
   =========================== */

function renderNotesSection(pieceId, notes) {
    const canManage = hasPermission("MANAGE_PIECE_NOTES");
    return `
        <div class="profile-notes">
            <button type="button" class="defects-header" id="notes-toggle" aria-expanded="${NotesState.expanded}" aria-controls="notes-body">
                <span class="notes-header-label"><svg class="notes-header-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 3v6h6"/><path d="M8 13h8"/><path d="M8 17h5"/></svg>یادداشت‌ها (${notes.length})</span>
                <span class="toggle-icon" id="notes-toggle-icon">${NotesState.expanded ? "▲" : "▼"}</span>
            </button>
            <div class="defects-body" id="notes-body" style="display: ${NotesState.expanded ? "block" : "none"};">
                ${
                    canManage
                        ? `<div id="add-note-form-container">
                    ${renderAddNoteForm()}
                </div>`
                        : ""
                }
                <div class="notes-list" id="notes-list">
                    ${renderNotesList(notes)}
                </div>
            </div>
        </div>
    `;
}

function renderNotesList(notes) {
    if (notes.length === 0) {
        return `<div class="history-empty">هنوز یادداشتی برای این قطعه ثبت نشده است</div>`;
    }

    return notes
        .map(
            (n) => `
        <div class="note-row">
            <div class="note-content">${escapeHtml(n.content).replaceAll("\n", "<br>")}</div>
            <div class="note-meta">
                ${n.createdBy ? `<span class="note-author">${escapeHtml(n.createdBy)}</span>` : ""}
                <span class="note-time">${formatDateTime(n.createdAt)}</span>
                ${n.canDelete ? `<button type="button" class="btn btn-danger btn-sm btn-delete-note" data-id="${n.id}">حذف</button>` : ""}
            </div>
        </div>
    `
        )
        .join("");
}

function renderAddNoteForm() {
    return `
        <form id="form-add-note" class="note-form">
            <textarea id="input-note-content" rows="2" placeholder="یادداشت جدیدی درباره این قطعه بنویسید..." required></textarea>
            <div class="form-error" id="note-form-error"></div>
            <div class="form-actions" style="margin-top: 8px;">
                <button type="submit" class="btn btn-primary btn-sm">افزودن یادداشت</button>
            </div>
        </form>
    `;
}

function attachNotesSectionEvents(pieceId) {
    const toggle = document.getElementById("notes-toggle");
    const body = document.getElementById("notes-body");
    const icon = document.getElementById("notes-toggle-icon");

    if (toggle) {
        toggle.addEventListener("click", () => {
            NotesState.expanded = !NotesState.expanded;
            body.style.display = NotesState.expanded ? "block" : "none";
            icon.textContent = NotesState.expanded ? "▲" : "▼";
            toggle.setAttribute("aria-expanded", String(NotesState.expanded));
        });
    }

    attachDeleteNoteEvents(pieceId);

    const form = document.getElementById("form-add-note");
    if (form) {
        form.addEventListener("submit", async (e) => {
            e.preventDefault();

            const content = document.getElementById("input-note-content").value.trim();
            const errorBox = document.getElementById("note-form-error");

            try {
                await PieceNoteAPI.create(pieceId, content);
                showToast("یادداشت با موفقیت ثبت شد");
                NotesState.expanded = true;
                await refreshNotesSection(pieceId);
            } catch (error) {
                errorBox.textContent = error.message;
                errorBox.style.display = "block";
            }
        });
    }
}

function attachDeleteNoteEvents(pieceId) {
    document.querySelectorAll(".btn-delete-note").forEach((btn) => {
        btn.addEventListener("click", async () => {
            if (!confirm("آیا از حذف این یادداشت مطمئن هستید؟")) return;

            try {
                await PieceNoteAPI.delete(btn.dataset.id);
                showToast("یادداشت با موفقیت حذف شد");
                await refreshNotesSection(pieceId);
            } catch (error) {
                showToast(error.message, "error");
            }
        });
    });
}

/* ===========================
   بازخوانی بخش یادداشت‌ها بعد از افزودن/حذف
   =========================== */

async function refreshNotesSection(pieceId) {
    try {
        const notes = await PieceNoteAPI.getByPiece(pieceId);
        const container = document.getElementById("profile-notes-container");
        if (!container) return;

        container.innerHTML = renderNotesSection(pieceId, notes);
        attachNotesSectionEvents(pieceId);
    } catch (error) {
        showToast(error.message, "error");
    }
}
