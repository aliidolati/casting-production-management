const SpectrometryPage = (() => {
    const elements = ["al", "si", "mg", "cu", "fe", "mn", "zn", "ti"];
    const byId = (id) => document.getElementById(`spectrometry-${id}`);
    const trash = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M4 7h16m-10-3h4l1 3m3 0-1 13H7L6 7m4 3v6m4-6v6"/></svg>';
    let page = null;
    let nextRowId = 0;
    let fromPicker;
    let toPicker;

    function init() {
        fromPicker = attachPersianDatePicker(byId("filter-from"), { editable: true });
        toPicker = attachPersianDatePicker(byId("filter-to"), { editable: true });
        SpectrometryCharts.init(toggleSelection);
        byId("back").addEventListener("click", () => document.getElementById("nav-btn-dashboard").click());
        byId("add").addEventListener("click", () => addRow(page, true));
        byId("entry").addEventListener("submit", saveBatch);
        byId("filters").addEventListener("submit", (event) => {
            event.preventDefault();
            applyFilters();
        });
        byId("reset-filters").addEventListener("click", () => {
            resetFilters();
            applyFilters();
        });
        byId("sort").addEventListener("click", () => {
            page.ascending = !page.ascending;
            syncSort();
            loadHistory(page, 0);
        });
        byId("prev").addEventListener("click", () => loadHistory(page, page.history.page - 1));
        byId("next").addEventListener("click", () => loadHistory(page, page.history.page + 1));
        byId("page-size").addEventListener("change", () => loadHistory(page, 0));
        byId("export").addEventListener("click", downloadCsvReport);
        byId("history").addEventListener("change", (event) => {
            if (event.target.matches("[data-select-test]")) toggleSelection(event.target.dataset.selectTest);
        });
        byId("history").addEventListener("click", (event) => {
            const button = event.target.closest("[data-edit-test], [data-delete-test]");
            if (!button) return;
            const row = page.history.items.find(item => item.id === (button.dataset.editTest || button.dataset.deleteTest));
            if (button.dataset.editTest) openEdit(row);
            else openDelete(row);
        });
        document.querySelectorAll('[name="spectrometry-basis"]').forEach(input => input.addEventListener("change", syncSelection));
        byId("clear-selection").addEventListener("click", () => {
            page.selected.clear();
            SpectrometryCharts.hideTooltip();
            syncSelection();
        });
    }

    function open() {
        if (!hasPermission("VIEW_SPECTROMETRY")) return;
        if (page) return;
        page = {
            rows: new Map(), selected: new Set(), matching: new Set(), filters: {},
            projects: null, definitions: new Map(), pieceOptions: new Map(),
            history: null, historyRequest: 0, pointsRequest: 0, pending: null, saving: false,
            ascending: false, exporting: false,
        };
        byId("back").hidden = !hasPermission("VIEW_DASHBOARD");
        byId("entry").hidden = !hasPermission("CREATE_SPECTROMETRY_TEST");
        byId("drafts").replaceChildren();
        byId("project-options").replaceChildren();
        byId("actions-header").hidden = !(canEdit() || canDelete());
        byId("page-size").value = "50";
        byId("export").disabled = true;
        setMessage("entry-error");
        resetFilters();
        document.querySelector('[name="spectrometry-basis"][value="all"]').checked = true;
        SpectrometryCharts.reset();
        syncSelection();
        syncSort();
        syncEntryState(page);
        setActiveNavButton(hasPermission("VIEW_DASHBOARD") ? "dashboard" : "spectrometry");
        showSection("spectrometry-section");
        byId("title").focus();
        if (!byId("entry").hidden) addRow(page);
        refresh(page);
    }

    function close() {
        page = null;
        SpectrometryCharts.reset();
        byId("history").replaceChildren();
        byId("drafts").replaceChildren();
        byId("project-options").replaceChildren();
    }

    function setMessage(id, message = "") {
        const node = byId(id);
        node.textContent = message;
        node.hidden = !message;
    }

    function canEdit() { return hasPermission("EDIT_SPECTROMETRY_TEST"); }
    function canDelete() { return hasPermission("DELETE_SPECTROMETRY_TEST"); }

    function today() {
        const [y, m, d] = todayJalali();
        return `${y}/${String(m).padStart(2, "0")}/${String(d).padStart(2, "0")}`;
    }

    function digits(value) {
        return value.replace(/[۰-۹٠-٩]/g, digit => String(digit.charCodeAt(0) & 15));
    }

    function parseDate(input) {
        const date = parseJalaliDateInput(input.value);
        if (!date) return null;
        const iso = jalaliToIsoDateString(date.jy, date.jm, date.jd);
        return iso.length === 10 && iso >= "1000-01-01" && iso <= "3798-12-31" ? iso : null;
    }

    function percent(text) {
        const value = digits(text).trim().replace("٫", ".");
        if (!/^(?:\d+(?:\.\d*)?|\.\d+)$/.test(value)) return null;
        const [whole, fraction = ""] = value.split(".");
        const integer = whole.replace(/^0+/, "") || "0";
        const decimals = fraction.replace(/0+$/, "");
        if (decimals.length > 10 || Number(integer) > 100 || (integer === "100" && decimals)) return null;
        return decimals ? `${integer}.${decimals}` : integer;
    }

    function pieceField(label, key, listId) {
        return `<input type="text" aria-label="${label}" data-input="${key}" list="${listId}" autocomplete="off"
                    ${key === "piece" ? 'inputmode="numeric"' : ""}><datalist id="${listId}"></datalist>`;
    }

    function inputFields(prefix) {
        return {
            project: '<input type="text" aria-label="کد پروژه" data-input="project" list="spectrometry-project-options" autocomplete="off">',
            definition: pieceField("نام قطعه", "definition", `${prefix}-definitions`),
            piece: pieceField("شماره قطعه", "piece", `${prefix}-numbers`),
            date: '<input type="text" aria-label="تاریخ آزمون" data-input="date">',
            ...Object.fromEntries(elements.map(key => [key, `<input type="text" dir="ltr" inputmode="decimal"
                aria-label="${key[0].toUpperCase() + key.slice(1)}" data-input="${key}" autocomplete="off">`])),
        };
    }

    function addRow(state, focus = false) {
        const id = ++nextRowId;
        const row = document.createElement("tr");
        const inputs = inputFields(`spectrometry-draft-${id}`);
        row.dataset.draftId = id;
        row.innerHTML = `<th scope="row" class="spectrometry-row-number"></th>
            ${Object.values(inputs).map(html => `<td>${html}</td>`).join("")}
            <td><button type="button" class="btn btn-secondary btn-sm spectrometry-trash"
                aria-label="حذف ردیف" title="حذف ردیف">${trash}</button></td>`;
        byId("drafts").append(row);
        const editor = bindEditor(state, row);
        state.rows.set(id, editor);
        row.querySelector(".spectrometry-row-number").textContent = formatFaNumber(state.rows.size);
        row.querySelector("button.spectrometry-trash").addEventListener("click", () => {
            row.remove();
            state.rows.delete(id);
            let number = 0;
            for (const { root } of state.rows.values()) {
                root.querySelector(".spectrometry-row-number").textContent = formatFaNumber(++number);
            }
            syncEntryState(state);
        });
        if (focus) editor.fields.project.focus();
        syncEntryState(state);
    }

    function cached(map, id, fetcher) {
        if (!map.has(id)) {
            map.set(id, fetcher(id).catch(error => {
                map.delete(id);
                throw error;
            }));
        }
        return map.get(id);
    }

    async function projects(state) {
        if (!state.projects) {
            state.projects = ProjectAPI.getAll().then(rows => {
                if (page === state) fillOptions(byId("project-options"), rows.map(row => [row.code, row.name]));
                return rows;
            }).catch(error => {
                state.projects = null;
                throw error;
            });
        }
        return state.projects;
    }

    function fillOptions(list, options) {
        list.innerHTML = options.map(([value, label]) =>
            `<option value="${escapeHtml(value)}">${escapeHtml(label)}</option>`).join("");
    }

    function bindEditor(state, root, initial) {
        const fields = Object.fromEntries([...root.querySelectorAll("[data-input]")]
            .map(element => [element.dataset.input, element]));
        const initialDate = today();
        const datePicker = attachPersianDatePicker(fields.date, { editable: true });
        fields.date.value = initialDate;
        fields.definition.disabled = true;
        fields.piece.disabled = true;
        let project = null;
        let definition = null;
        let projectRequest = 0;
        let definitionRequest = 0;
        let optionsByName = new Map();
        let numbers = new Map();

        function resetPieces() {
            definitionRequest++;
            definition = null;
            numbers = new Map();
            fields.piece.value = "";
            fields.piece.disabled = true;
            fields.piece.list.replaceChildren();
        }

        async function resolveProject(restore = null) {
            const request = ++projectRequest;
            try {
                const option = (await projects(state)).find(row => row.code.toLowerCase() === fields.project.value.trim().toLowerCase());
                if (request !== projectRequest || !root.isConnected || page !== state) return;
                if (option?.id === project?.id && !restore && (!option || !fields.definition.disabled)) return;
                project = option || null;
                definitionRequest++;
                optionsByName = new Map();
                fields.definition.value = "";
                fields.definition.disabled = true;
                fields.definition.list.replaceChildren();
                resetPieces();
                if (!project) return;
                fields.project.value = project.code;
                const definitions = await cached(state.definitions, project.id, PieceDefinitionAPI.getByProject);
                if (request !== projectRequest || !root.isConnected || page !== state) return;
                optionsByName = new Map(definitions.map(row => [`${row.pieceName} · ردیف ${row.rowNum}`, row]));
                fillOptions(fields.definition.list, [...optionsByName.keys()].map(name => [name, ""]));
                fields.definition.disabled = false;
                if (restore) {
                    fields.definition.value = [...optionsByName].find(([, row]) => row.id === restore.definitionId)?.[0] ?? "";
                    await resolveDefinition(restore);
                }
            } catch (error) {
                if (request === projectRequest && root.isConnected && page === state) {
                    project = null;
                    showToast(error.message, "error");
                }
            }
        }

        async function resolveDefinition(restore = null) {
            const option = optionsByName.get(fields.definition.value);
            if (option?.id === definition?.id && !restore) return;
            resetPieces();
            if (!option) return;
            definition = option;
            const request = definitionRequest;
            try {
                const pieces = await cached(state.pieceOptions, option.id, SpectrometryAPI.pieceOptions);
                if (request !== definitionRequest || !root.isConnected || page !== state) return;
                numbers = new Map(pieces.map(row => [String(row.pieceNumber), row]));
                fillOptions(fields.piece.list, [...numbers].map(([number, row]) => [number, row.archivedAt ? "بایگانی‌شده" : ""]));
                fields.piece.disabled = false;
                if (restore) fields.piece.value = [...numbers].find(([, row]) => row.id === restore.pieceId)?.[0] ?? "";
            } catch (error) {
                if (request === definitionRequest && root.isConnected && page === state) {
                    definition = null;
                    showToast(error.message, "error");
                }
            }
        }

        const warmProjects = () => projects(state).catch(error => {
            if (root.isConnected && page === state) showToast(error.message, "error");
        });
        fields.project.addEventListener("focus", warmProjects);
        fields.project.addEventListener("input", () => resolveProject());
        fields.definition.addEventListener("input", () => resolveDefinition());
        root.addEventListener("input", event => {
            event.target.removeAttribute("aria-invalid");
            root.classList.remove("spectrometry-invalid");
        });
        if (initial) {
            fields.project.value = initial.projectCode;
            datePicker.setIsoDate(initial.testDate);
            for (const key of elements) fields[key].value = initial[key];
            resolveProject(initial);
        } else if (!state.projects) {
            warmProjects();
        }

        function read(ignoreEmpty) {
            if (ignoreEmpty && fields.date.value === initialDate &&
                    ["project", "definition", "piece", ...elements].every(key => !fields[key].value.trim())) return null;
            const pieceNumber = digits(fields.piece.value.trim());
            const projectMatches = project && project.code === fields.project.value.trim();
            const definitionMatches = definition && optionsByName.get(fields.definition.value)?.id === definition.id;
            const piece = projectMatches && definitionMatches ? numbers.get(pieceNumber) : null;
            const date = parseDate(fields.date);
            if (!projectMatches) return invalid("project", "کد پروژه را از فهرست انتخاب کنید");
            if (!definitionMatches) return invalid("definition", "نام قطعه را از فهرست انتخاب کنید");
            if (!piece) return invalid("piece", "شماره قطعه را از فهرست انتخاب کنید");
            if (!date) return invalid("date", "تاریخ آزمون را بررسی کنید");
            const result = { pieceId: piece.id, testDate: date };
            for (const key of elements) {
                const value = percent(fields[key].value);
                if (value == null) {
                    const symbol = key[0].toUpperCase() + key.slice(1);
                    return invalid(key, `${symbol} باید عددی بین ۰ و ۱۰۰ با حداکثر ۱۰ رقم اعشار باشد`);
                }
                result[key] = value;
            }
            return { result };
        }

        function invalid(key, message) {
            fields[key].setAttribute("aria-invalid", "true");
            root.classList.add("spectrometry-invalid");
            return { message, input: fields[key] };
        }

        return { root, fields, read };
    }

    function syncEntryState(state) {
        const locked = state.saving || !!state.pending;
        byId("entry-fields").disabled = locked;
        byId("add").disabled = locked;
        byId("save").disabled = state.saving || !state.rows.size;
        byId("save").textContent = state.saving ? "در حال ذخیره…" : state.pending ? "تلاش دوباره برای ذخیره" : "ذخیره آزمون‌ها";
    }

    function requestId() {
        // getRandomValues is also available when the application is used over HTTP on the LAN.
        const bytes = crypto.getRandomValues(new Uint8Array(16));
        bytes[6] = (bytes[6] & 15) | 64;
        bytes[8] = (bytes[8] & 63) | 128;
        const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
        return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join("-");
    }

    function collectDrafts(state) {
        const rows = [];
        const rowNumbers = [];
        const errors = [];
        let number = 0;
        for (const editor of state.rows.values()) {
            number++;
            editor.root.classList.remove("spectrometry-invalid");
            Object.values(editor.fields).forEach(input => input.removeAttribute("aria-invalid"));
            const value = editor.read(true);
            if (!value) continue;
            if (value.message) {
                errors.push({ ...value, number });
            } else {
                rows.push(value.result);
                rowNumbers.push(number);
            }
        }
        if (errors.length) {
            errors[0].input.focus();
            setMessage("entry-error", errors.map(error => `ردیف ${formatFaNumber(error.number)} · ${error.message}`).join("\n"));
            return null;
        }
        if (!rows.length) {
            setMessage("entry-error", "حداقل یک ردیف را تکمیل کنید.");
            return null;
        }
        return { request: { requestId: requestId(), rows }, rowNumbers };
    }

    async function saveBatch(event) {
        event.preventDefault();
        const state = page;
        if (!state || state.saving || !hasPermission("CREATE_SPECTROMETRY_TEST")) return;
        state.pending ||= collectDrafts(state);
        if (!state.pending) return;
        state.saving = true;
        setMessage("entry-error");
        syncEntryState(state);
        const { request, rowNumbers } = state.pending;
        try {
            const saved = await SpectrometryAPI.saveBatch(request);
            if (page !== state) return;
            state.pending = null;
            state.rows.clear();
            byId("drafts").replaceChildren();
            addRow(state);
            showToast(`${formatFaNumber(saved.savedCount)} آزمون ذخیره شد`);
            refresh(state);
        } catch (error) {
            if (page !== state) return;
            if (error.status >= 400 && error.status < 500) {
                state.pending = null;
                const match = error.message.match(/^ردیف (\d+) · /);
                const row = match ? rowNumbers[Number(match[1]) - 1] : null;
                if (row) [...state.rows.values()][row - 1].root.classList.add("spectrometry-invalid");
                setMessage("entry-error", row ? error.message.replace(match[1], formatFaNumber(row)) : error.message);
            } else {
                setMessage("entry-error", "پاسخ ثبت دریافت نشد. برای بررسی یا ثبت دوباره همین درخواست، «تلاش دوباره برای ذخیره» را بزنید.");
            }
        } finally {
            if (page === state) {
                state.saving = false;
                syncEntryState(state);
            }
        }
    }

    function resetFilters() {
        byId("filters").reset();
        fromPicker.clear();
        toPicker.clear();
        for (const name of ["from", "to"]) byId(`filter-${name}`).removeAttribute("aria-invalid");
    }

    function applyFilters() {
        const first = byId("filter-from");
        const last = byId("filter-to");
        const dateFrom = parseDate(first);
        const dateTo = parseDate(last);
        for (const [input, date] of [[first, dateFrom], [last, dateTo]]) {
            if (input.value.trim() && !date) {
                input.setAttribute("aria-invalid", "true");
                input.focus();
                return setMessage("history-error", "تاریخ فیلتر را با قالب سال/ماه/روز وارد کنید.");
            }
            input.removeAttribute("aria-invalid");
        }
        if (dateFrom && dateTo && dateFrom > dateTo) {
            return setMessage("history-error", "ابتدای بازهٔ تاریخ باید قبل از پایان بازه یا برابر با آن باشد.");
        }
        const pieceNumber = digits(byId("filter-number").value.trim());
        if (pieceNumber && (!/^\d+$/.test(pieceNumber) || Number(pieceNumber) > 2147483647)) {
            return setMessage("history-error", "شماره قطعه باید عدد صحیح باشد.");
        }
        page.filters = {
            projectCode: byId("filter-project").value.trim(), pieceName: byId("filter-name").value.trim(),
            pieceNumber, dateFrom, dateTo,
        };
        refresh(page);
    }

    function syncSort() {
        byId("sort-header").setAttribute("aria-sort", page.ascending ? "ascending" : "descending");
        byId("sort").textContent = `تاریخ آزمون ${page.ascending ? "↑" : "↓"}`;
    }

    function refresh(state) {
        loadHistory(state, 0);
        loadPoints(state);
    }

    function historyMessage(text) {
        const columns = canEdit() || canDelete() ? 14 : 13;
        byId("history").innerHTML = `<tr><td class="spectrometry-empty" colspan="${columns}">${escapeHtml(text)}</td></tr>`;
    }

    async function loadHistory(state, index) {
        const request = ++state.historyRequest;
        const current = () => page === state && request === state.historyRequest;
        const filters = { ...state.filters, page: index, size: byId("page-size").value, ascending: state.ascending };
        setMessage("history-error");
        byId("history").setAttribute("aria-busy", "true");
        byId("pagination").hidden = true;
        byId("export").disabled = true;
        historyMessage("در حال دریافت سوابق…");
        try {
            const history = await SpectrometryAPI.history(filters);
            if (!current()) return;
            state.history = history;
            renderHistory(history);
        } catch (error) {
            if (!current()) return;
            state.history = null;
            historyMessage("دریافت سوابق انجام نشد.");
            setMessage("history-error", error.message);
        } finally {
            if (current()) byId("history").removeAttribute("aria-busy");
        }
    }

    function renderHistory(history) {
        const edit = canEdit();
        const remove = canDelete();
        byId("export").disabled = !history.totalElements || page.exporting;
        byId("pagination").hidden = !history.totalElements;
        byId("prev").disabled = history.page === 0;
        byId("next").disabled = history.page + 1 >= history.totalPages;
        byId("page-info").textContent = `صفحه ${formatFaNumber(history.page + 1)} از ${formatFaNumber(history.totalPages)} · ${formatFaNumber(history.totalElements)} آزمون`;
        if (!history.items.length) return historyMessage("آزمونی در این بازه یا با این فیلترها پیدا نشد.");
        const html = history.items.map(row => {
            const id = escapeHtml(row.id);
            const checked = page.selected.has(row.id) ? "checked" : "";
            const date = isoToJalaliString(row.testDate);
            const label = escapeHtml(`انتخاب آزمون ${row.projectCode}، ${row.pieceName}، قطعه ${row.pieceNumber}، ${date}`);
            return `<tr>
                <td><input type="checkbox" data-select-test="${id}" aria-label="${label}" ${checked}></td>
                <td><bdi>${escapeHtml(row.projectCode)}</bdi></td><td>${escapeHtml(row.pieceName)}</td>
                <td>${formatFaNumber(row.pieceNumber)}</td><td dir="ltr">${date}</td>
                ${elements.map(key => `<td dir="ltr">${escapeHtml(row[key])}</td>`).join("")}
                ${edit || remove ? `<td>
                    ${edit ? `<button type="button" class="btn btn-secondary btn-sm" data-edit-test="${id}">ویرایش</button>` : ""}
                    ${remove ? `<button type="button" class="btn btn-secondary btn-sm" data-delete-test="${id}">حذف</button>` : ""}
                </td>` : ""}</tr>`;
        });
        byId("history").innerHTML = html.join("");
    }

    async function loadPoints(state) {
        const request = ++state.pointsRequest;
        const current = () => page === state && request === state.pointsRequest;
        setMessage("chart-error");
        setMessage("chart-status", "در حال دریافت داده‌های نمودار…");
        byId("charts").hidden = true;
        state.matching.clear();
        SpectrometryCharts.setPoints([]);
        syncSelection();
        try {
            const rows = await SpectrometryAPI.points(state.filters);
            if (!current()) return;
            state.matching = new Set(rows.map(row => row.id));
            SpectrometryCharts.setPoints(rows);
            syncSelection();
            byId("charts").hidden = false;
            setMessage("chart-status", rows.length ? "" : "آزمونی مطابق فیلترها برای رسم نمودار وجود ندارد.");
        } catch (error) {
            if (!current()) return;
            setMessage("chart-status");
            setMessage("chart-error", error.message);
        }
    }

    function syncSelection() {
        if (!page) return;
        let inRange = 0;
        for (const id of page.selected) if (page.matching.has(id)) inRange++;
        byId("selection-count").textContent =
            `${formatFaNumber(inRange)} آزمون انتخاب‌شده در محدوده فیلتر از ${formatFaNumber(page.matching.size)} آزمون`;
        byId("clear-selection").disabled = !page.selected.size;
        byId("history").querySelectorAll("[data-select-test]").forEach(input => {
            input.checked = page.selected.has(input.dataset.selectTest);
        });
        const selectedOnly = document.querySelector('[name="spectrometry-basis"][value="selected"]').checked;
        SpectrometryCharts.setSelection(page.selected, selectedOnly);
    }

    function toggleSelection(id) {
        if (!page.selected.delete(id)) page.selected.add(id);
        syncSelection();
    }

    async function downloadCsvReport() {
        const state = page;
        state.exporting = true;
        byId("export").disabled = true;
        try {
            const blob = await SpectrometryAPI.csv({ ...state.filters, ascending: state.ascending });
            if (page !== state) return;
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = url;
            link.download = "spectrometry.csv";
            document.body.append(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
        } catch (error) {
            if (page === state) showToast(error.message, "error");
        } finally {
            state.exporting = false;
            if (page === state) byId("export").disabled = !state.history?.totalElements || byId("history").hasAttribute("aria-busy");
        }
    }

    function openEdit(row) {
        if (!row || !canEdit()) return;
        const state = page;
        const labels = ["کد پروژه", "نام قطعه", "شماره قطعه", "تاریخ آزمون",
            ...elements.map(key => key[0].toUpperCase() + key.slice(1))];
        openModal("ویرایش آزمون کوانتومتری", `
            <form id="spectrometry-edit" novalidate>
                <fieldset class="spectrometry-fields spectrometry-edit-fields">
                    ${Object.values(inputFields("spectrometry-edit")).map((html, i) =>
                        `<label><span>${labels[i]}</span>${html}</label>`).join("")}
                </fieldset>
                <p class="form-error" role="alert" hidden></p>
                <button type="submit" class="btn btn-primary">ذخیره ویرایش</button>
            </form>`, { size: "lg" });
        const form = byId("edit");
        const editor = bindEditor(state, form, row);
        bindMutation(state, form, async () => {
            const value = editor.read(false);
            if (value.message) {
                value.input.focus();
                throw new Error(value.message);
            }
            await SpectrometryAPI.update(row.id, { version: row.version, result: value.result });
        }, "آزمون ویرایش شد");
    }

    function openDelete(row) {
        if (!row || !canDelete()) return;
        const state = page;
        openModal("حذف آزمون کوانتومتری", `
            <form id="spectrometry-delete">
                <p>حذف آزمون قطعهٔ ${formatFaNumber(row.pieceNumber)} در تاریخ <bdi>${isoToJalaliString(row.testDate)}</bdi> را تأیید می‌کنید؟</p>
                <p class="form-error" role="alert" hidden></p>
                <button type="submit" class="btn btn-danger">حذف آزمون</button>
                <button type="button" class="btn btn-secondary" id="spectrometry-cancel-delete">انصراف</button>
            </form>`);
        byId("cancel-delete").addEventListener("click", closeModal);
        bindMutation(state, byId("delete"), async () => {
            await SpectrometryAPI.remove(row.id, row.version);
            state.selected.delete(row.id);
        }, "آزمون حذف شد");
    }

    function bindMutation(state, form, action, success) {
        const button = form.querySelector('[type="submit"]');
        const errorBox = form.querySelector(".form-error");
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            if (button.disabled || page !== state) return;
            errorBox.hidden = true;
            button.disabled = true;
            try {
                await action();
                if (page !== state) return;
                if (form.isConnected) closeModal();
                showToast(success);
                refresh(state);
            } catch (error) {
                errorBox.textContent = error.message;
                errorBox.hidden = false;
            } finally {
                button.disabled = false;
            }
        });
    }

    document.addEventListener("DOMContentLoaded", init);
    return { open, close };
})();

function openSpectrometryPage() { SpectrometryPage.open(); }
function closeSpectrometryPage() { SpectrometryPage.close(); }
