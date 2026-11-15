/**
 * persian-datepicker.js
 * یک تقویم شمسیِ سبک، بدون وابستگی خارجی، برای انتخاب تاریخ در فیلترها.
 * از توابع تبدیل تعریف‌شده در jalali.js استفاده می‌کند.
 */

let closeActivePersianDatePicker;

function parseJalaliDateInput(value) {
    const normalized = value.trim()
        .replace(/[۰-۹]/g, (digit) => String(digit.charCodeAt(0) - 0x06f0))
        .replace(/[٠-٩]/g, (digit) => String(digit.charCodeAt(0) - 0x0660));
    const match = normalized.match(/^(\d{1,4})\/(\d{1,2})\/(\d{1,2})$/);
    if (!match) return null;

    const [, jy, jm, jd] = match.map(Number);
    if (jy < 1 || jy >= JALALI_BREAKS.at(-1) || jm < 1 || jm > 12 || jd < 1 || jd > jalaliMonthLength(jy, jm)) {
        return null;
    }
    return { jy, jm, jd };
}

/**
 * به یک input متنی، تقویم شمسی وصل می‌کند.
 * @param {HTMLInputElement} inputEl
 * @param {{ onSelect?: (d: {jy:number, jm:number, jd:number}) => void, onClear?: () => void, editable?: boolean, onInvalid?: () => void }} options
 * @returns {{ clear: () => void, getSelected: () => ({jy:number, jm:number, jd:number}|null) }}
 */
function attachPersianDatePicker(inputEl, options = {}) {
    const { onSelect, onClear, editable = false, onInvalid } = options;

    let viewYear;
    let viewMonth;
    let selected = null;
    let popupListeners;

    inputEl.readOnly = !editable;
    inputEl.classList.add("jalali-date-input");
    if (!inputEl.placeholder) inputEl.placeholder = "انتخاب تاریخ";

    const wrapper = document.createElement("div");
    wrapper.className = "jalali-datepicker-wrapper";
    inputEl.parentNode.insertBefore(wrapper, inputEl);
    wrapper.appendChild(inputEl);

    const trigger = editable ? document.createElement("button") : inputEl;
    if (editable) {
        wrapper.classList.add("jalali-datepicker-editable");
        inputEl.autocomplete = "off";
        inputEl.placeholder = "سال/ماه/روز";
        trigger.type = "button";
        trigger.className = "jalali-datepicker-trigger";
        trigger.setAttribute("aria-label", "باز کردن تقویم");
        trigger.setAttribute("aria-expanded", "false");
        trigger.innerHTML = '<svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/></svg>';
        wrapper.appendChild(trigger);
    }

    const popup = document.createElement("div");
    popup.className = "jalali-datepicker-popup";
    wrapper.appendChild(popup);

    function todayJalaliObj() {
        const [jy, jm, jd] = todayJalali();
        return { jy, jm, jd };
    }

    function formatSelected() {
        if (!selected) return "";
        return `${selected.jy}/${String(selected.jm).padStart(2, "0")}/${String(selected.jd).padStart(2, "0")}`;
    }

    function isOpen() {
        return popup.classList.contains("open");
    }

    function positionPopup() {
        if (!wrapper.isConnected) {
            closePopup();
            return;
        }
        const rect = inputEl.getBoundingClientRect();
        const popupWidth = popup.offsetWidth || 240;
        const popupHeight = popup.offsetHeight || 280;
        const gap = 4;
        const rtl = document.documentElement.dir === "rtl";
        let top = rect.bottom + gap;
        if (top + popupHeight > window.innerHeight - 8 && rect.top - gap - popupHeight > 8) {
            top = rect.top - gap - popupHeight;
        }
        popup.style.position = "fixed";
        popup.style.top = `${Math.round(top)}px`;
        popup.style.bottom = "auto";
        popup.style.margin = "0";
        popup.style.zIndex = "400";
        popup.style.insetInlineStart = "auto";
        popup.style.insetInlineEnd = "auto";
        if (rtl) {
            popup.style.left = "auto";
            popup.style.right = `${Math.round(window.innerWidth - rect.right)}px`;
        } else {
            popup.style.right = "auto";
            popup.style.left = `${Math.round(rect.left)}px`;
        }
        const afterRect = popup.getBoundingClientRect();
        if (afterRect.left < 8) {
            popup.style.left = "8px";
            popup.style.right = "auto";
        } else if (afterRect.right > window.innerWidth - 8) {
            popup.style.left = `${Math.round(window.innerWidth - popupWidth - 8)}px`;
            popup.style.right = "auto";
        }
    }

    function openPopup() {
        closeActivePersianDatePicker?.();
        const base = (editable ? parseJalaliDateInput(inputEl.value) : selected) || todayJalaliObj();
        viewYear = base.jy;
        viewMonth = base.jm;
        if (popup.parentNode !== document.body) {
            document.body.appendChild(popup);
        }
        render();
        popup.classList.add("open");
        positionPopup();
        if (editable) trigger.setAttribute("aria-expanded", "true");

        closeActivePersianDatePicker = closePopup;
        popupListeners = new AbortController();
        const { signal } = popupListeners;
        document.addEventListener("click", (e) => {
            if (!wrapper.contains(e.target) && !popup.contains(e.target)) closePopup();
        }, { signal });
        if (editable) {
            document.addEventListener("keydown", (e) => {
                if (e.key === "Escape") {
                    e.stopPropagation();
                    closePopup();
                }
            }, { capture: true, signal });
        }
        window.addEventListener("scroll", positionPopup, { capture: true, signal });
        window.addEventListener("resize", positionPopup, { signal });
    }

    function closePopup() {
        popupListeners?.abort();
        if (closeActivePersianDatePicker === closePopup) closeActivePersianDatePicker = undefined;
        if (editable) trigger.setAttribute("aria-expanded", "false");
        popup.classList.remove("open");
        if (popup.parentNode === document.body) {
            wrapper.appendChild(popup);
        }
    }

    function changeMonth(delta) {
        viewMonth += delta;
        if (viewMonth < 1) {
            viewMonth = 12;
            viewYear -= 1;
        } else if (viewMonth > 12) {
            viewMonth = 1;
            viewYear += 1;
        }
        render();
    }

    function selectDate(jy, jm, jd) {
        selected = { jy, jm, jd };
        inputEl.value = formatSelected();
        inputEl.removeAttribute("aria-invalid");
        closePopup();
        if (onSelect) onSelect(selected);
    }

    function render() {
        const today = todayJalaliObj();
        const currentDate = editable ? parseJalaliDateInput(inputEl.value) : selected;
        const daysInMonth = jalaliMonthLength(viewYear, viewMonth);

        const [gy1, gm1, gd1] = jalaliToGregorian(viewYear, viewMonth, 1);
        const firstWeekday = new Date(gy1, gm1 - 1, gd1).getDay(); // 0=یکشنبه ... 6=شنبه (میلادی)
        const leadingEmpty = (firstWeekday + 1) % 7; // تبدیل به چیدمان هفته‌ی شمسی (شروع از شنبه)

        let daysHtml = "";
        for (let i = 0; i < leadingEmpty; i += 1) {
            daysHtml += `<span class="jdp-day jdp-day-empty"></span>`;
        }
        for (let d = 1; d <= daysInMonth; d += 1) {
            const isSelected = currentDate?.jy === viewYear && currentDate.jm === viewMonth && currentDate.jd === d;
            const isToday = today.jy === viewYear && today.jm === viewMonth && today.jd === d;
            daysHtml += `<span class="jdp-day${isSelected ? " jdp-day-selected" : ""}${isToday ? " jdp-day-today" : ""}" data-day="${d}">${d}</span>`;
        }

        popup.innerHTML = `
            <div class="jdp-header">
                <button type="button" class="jdp-nav" data-nav="prev-year" title="سال قبل">&laquo;</button>
                <button type="button" class="jdp-nav" data-nav="prev-month" title="ماه قبل">&lsaquo;</button>
                <span class="jdp-title">${JALALI_MONTH_NAMES[viewMonth - 1]} ${viewYear}</span>
                <button type="button" class="jdp-nav" data-nav="next-month" title="ماه بعد">&rsaquo;</button>
                <button type="button" class="jdp-nav" data-nav="next-year" title="سال بعد">&raquo;</button>
            </div>
            <div class="jdp-weekdays">
                <span>ش</span><span>ی</span><span>د</span><span>س</span><span>چ</span><span>پ</span><span>ج</span>
            </div>
            <div class="jdp-grid">${daysHtml}</div>
            <div class="jdp-footer">
                <button type="button" class="jdp-footer-btn" data-action="today">امروز</button>
                <button type="button" class="jdp-footer-btn" data-action="clear">پاک کردن</button>
            </div>
        `;

        popup.querySelectorAll(".jdp-nav").forEach((btn) => {
            btn.addEventListener("click", (e) => {
                e.stopPropagation();
                const nav = btn.dataset.nav;
                if (nav === "prev-month") changeMonth(-1);
                else if (nav === "next-month") changeMonth(1);
                else if (nav === "prev-year") {
                    viewYear -= 1;
                    render();
                } else if (nav === "next-year") {
                    viewYear += 1;
                    render();
                }
            });
        });

        popup.querySelectorAll(".jdp-day:not(.jdp-day-empty)").forEach((cell) => {
            cell.addEventListener("click", (e) => {
                e.stopPropagation();
                selectDate(viewYear, viewMonth, Number(cell.dataset.day));
            });
        });

        popup.querySelector('[data-action="today"]').addEventListener("click", (e) => {
            e.stopPropagation();
            const t = todayJalaliObj();
            selectDate(t.jy, t.jm, t.jd);
        });

        popup.querySelector('[data-action="clear"]').addEventListener("click", (e) => {
            e.stopPropagation();
            selected = null;
            inputEl.value = "";
            inputEl.removeAttribute("aria-invalid");
            closePopup();
            if (onClear) onClear();
        });
    }

    function commitTypedDate() {
        if (inputEl.disabled) return;
        const value = inputEl.value.trim();
        if (!value) {
            inputEl.value = "";
            if (selected) {
                selected = null;
                onClear?.();
            }
            return;
        }
        const date = parseJalaliDateInput(value);
        if (!date) {
            inputEl.setAttribute("aria-invalid", "true");
            onInvalid?.();
            return;
        }
        selectDate(date.jy, date.jm, date.jd);
    }

    trigger.addEventListener("click", (e) => {
        e.stopPropagation();
        if (inputEl.disabled) return;
        if (isOpen()) closePopup();
        else openPopup();
    });

    if (editable) {
        // Keep the text field focused while using the mouse to select a calendar date.
        for (const element of [trigger, popup]) {
            element.addEventListener("mousedown", (e) => e.preventDefault());
        }
        inputEl.addEventListener("input", () => inputEl.removeAttribute("aria-invalid"));
        if (onSelect || onClear) {
            inputEl.addEventListener("blur", commitTypedDate);
            inputEl.addEventListener("keydown", (e) => {
                if (e.key === "Enter" && !e.isComposing) {
                    e.preventDefault();
                    inputEl.blur();
                }
            });
        }
    }

    return {
        clear() {
            selected = null;
            inputEl.value = "";
        },
        getSelected() {
            if (!editable) return selected;
            const date = parseJalaliDateInput(inputEl.value);
            if (!date) inputEl.setAttribute("aria-invalid", "true");
            return date;
        },
        setIsoDate(iso) {
            inputEl.removeAttribute("aria-invalid");
            if (!iso) {
                selected = null;
                inputEl.value = "";
                return;
            }
            const m = String(iso).match(/^(\d{4})-(\d{2})-(\d{2})/);
            if (!m) return;
            const [jy, jm, jd] = gregorianToJalali(Number(m[1]), Number(m[2]), Number(m[3]));
            selected = { jy, jm, jd };
            inputEl.value = formatSelected();
        },
    };
}
