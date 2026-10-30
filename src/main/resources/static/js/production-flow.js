/**
 * production-flow.js
 * شماتیک خطی روند تولید در پروفایل قطعه.
 * ترتیب همان گراف رو به جلوی پروژه است:
 * شروع‌نشده → ذوب‌ریزی → خشن‌تراشی → رادیوگرافی → عملیات حرارتی → تست کشش → شات‌بلاست → آماده تحویل.
 * مرحله‌های سوم تا پنجم عملیات حرارتی فقط اگر قطعه واقعاً از آن‌ها گذشته باشد اضافه می‌شوند.
 */

const PRODUCTION_FLOW_STEPS = [
    { id: "NOT_STARTED", stage: "NOT_STARTED", label: "شروع نشده" },
    { id: "CASTING", stage: "CASTING", label: "ذوب‌ریزی" },
    { id: "ROUGH:DEBURRING", stage: "ROUGH_MACHINING", sub: "DEBURRING", kind: "ROUGH_MACHINING", group: "خشن‌تراشی", label: "پلیسه‌گیری" },
    { id: "ROUGH:INITIAL_CORRECTION", stage: "ROUGH_MACHINING", sub: "INITIAL_CORRECTION", kind: "ROUGH_MACHINING", group: "خشن‌تراشی", label: "اصلاح اولیه" },
    { id: "ROUGH:MACHINING", stage: "ROUGH_MACHINING", sub: "MACHINING", kind: "ROUGH_MACHINING", group: "خشن‌تراشی", label: "تراش" },
    { id: "RADIO:SHOT", stage: "RADIOGRAPHY", sub: "SHOT", kind: "RADIOGRAPHY", group: "رادیوگرافی", label: "شوت" },
    { id: "RADIO:INTERPRETATION", stage: "RADIOGRAPHY", sub: "INTERPRETATION", kind: "RADIOGRAPHY", group: "رادیوگرافی", label: "تفسیر" },
    { id: "RADIO:VT", stage: "RADIOGRAPHY", sub: "VT", kind: "RADIOGRAPHY", group: "رادیوگرافی", label: "VT" },
    { id: "HEAT:AWAITING_OPERATIONS", stage: "HEAT_TREATMENT", sub: "AWAITING_OPERATIONS", kind: "HEAT_TREATMENT", group: "عملیات حرارتی", label: "منتظر عملیات" },
    { id: "HEAT:STAGE_1", stage: "HEAT_TREATMENT", sub: "STAGE_1", kind: "HEAT_TREATMENT", group: "عملیات حرارتی", label: "مرحله اول" },
    { id: "HEAT:STAGE_2", stage: "HEAT_TREATMENT", sub: "STAGE_2", kind: "HEAT_TREATMENT", group: "عملیات حرارتی", label: "مرحله دوم" },
    { id: "HEAT:CNC_SAMPLE", stage: "HEAT_TREATMENT", sub: "CNC_SAMPLE", kind: "HEAT_TREATMENT", group: "عملیات حرارتی", label: "نمونه CNC" },
    { id: "TENSILE", stage: "TENSILE", label: "تست کشش" },
    { id: "SHOT_BLASTING", stage: "SHOT_BLASTING", label: "شات‌بلاست" },
    { id: "READY", stage: "READY", label: "آماده تحویل" },
];

const HEAT_AGING_SUBS = ["STAGE_3", "STAGE_4", "STAGE_5"];

function canonicalRadiographySub(value) {
    if (!value) return null;
    if (value === "SHOT" || value === "AWAITING_SHOT" || value === "AWAITING_RESHOOT") return "SHOT";
    if (value === "VT") return "VT";
    if (
        value === "INTERPRETATION" ||
        value === "AWAITING_INTERPRETATION" ||
        value === "INTERPRETATION_REJECTED" ||
        value === "INTERPRETATION_FINALIZED"
    ) {
        return "INTERPRETATION";
    }
    return value;
}

function canonicalFlowSub(kind, value) {
    if (kind === "RADIOGRAPHY") return canonicalRadiographySub(value);
    return value || null;
}

function currentFlowSub(piece) {
    if (piece.currentStage === "ROUGH_MACHINING") return piece.roughMachiningSubStage || "DEBURRING";
    if (piece.currentStage === "RADIOGRAPHY") return canonicalRadiographySub(piece.radiographySubStage) || "SHOT";
    if (piece.currentStage === "HEAT_TREATMENT") return piece.heatTreatmentSubStage || "AWAITING_OPERATIONS";
    return null;
}

function buildProductionFlowSteps(piece, subStageHistory) {
    const steps = PRODUCTION_FLOW_STEPS.map((step) => ({ ...step }));
    const seenAging = new Set(
        (subStageHistory || [])
            .filter((row) => row.kind === "HEAT_TREATMENT" && HEAT_AGING_SUBS.includes(row.subStage))
            .map((row) => row.subStage)
    );
    if (piece.currentStage === "HEAT_TREATMENT" && HEAT_AGING_SUBS.includes(piece.heatTreatmentSubStage)) {
        seenAging.add(piece.heatTreatmentSubStage);
    }
    const aging = HEAT_AGING_SUBS.filter((sub) => seenAging.has(sub));
    if (aging.length) {
        const insertAt = steps.findIndex((step) => step.id === "HEAT:CNC_SAMPLE") + 1;
        const extras = aging.map((sub) => ({
            id: `HEAT:${sub}`,
            stage: "HEAT_TREATMENT",
            sub,
            kind: "HEAT_TREATMENT",
            group: "عملیات حرارتی",
            label: (typeof HEAT_TREATMENT_SUBSTAGE_LABELS !== "undefined" && HEAT_TREATMENT_SUBSTAGE_LABELS[sub]) || sub,
        }));
        steps.splice(insertAt, 0, ...extras);
    }
    return steps;
}

function flowStepIndex(steps, stage, sub) {
    if (!stage) return -1;
    if (sub) {
        const exact = steps.findIndex((step) => step.stage === stage && step.sub === sub);
        if (exact >= 0) return exact;
    }
    return steps.findIndex((step) => step.stage === stage);
}

function resolveFlowCursor(piece, weldingDispatches, steps) {
    if (piece.currentStage === "DELIVERED") {
        return { index: steps.length, note: "delivered" };
    }
    if (piece.currentStage === "SCRAPPED") {
        return { index: -1, note: "scrapped" };
    }
    if (piece.currentStage === "WELDING") {
        const open = (weldingDispatches || []).find((item) => item.open);
        const originStage = open && open.originStage && open.originStage !== "WELDING" ? open.originStage : null;
        const originSub = open
            ? open.originRoughMachiningSubStage || open.originRadiographySubStage || open.originHeatTreatmentSubStage || null
            : null;
        const index = originStage ? flowStepIndex(steps, originStage, canonicalFlowSub(originStage, originSub)) : -1;
        return { index, note: "welding" };
    }
    return { index: flowStepIndex(steps, piece.currentStage, currentFlowSub(piece)), note: null };
}

function flowStepVisited(step, history, subStageHistory) {
    if (step.sub) {
        return (subStageHistory || []).some(
            (row) => row.kind === step.kind && canonicalFlowSub(step.kind, row.subStage) === step.sub
        );
    }
    if (step.stage === "NOT_STARTED") return false;
    return (history || []).some((row) => row.stage === step.stage);
}

function flowOperator(step, history, subStageHistory) {
    const row = flowHistoryRow(step, history, subStageHistory);
    return row && row.changedBy ? row.changedBy : "";
}

function flowHistoryRow(step, history, subStageHistory) {
    const rows = step.sub
        ? (subStageHistory || []).filter(item => item.kind === step.kind && canonicalFlowSub(step.kind, item.subStage) === step.sub)
        : (history || []).filter(item => item.stage === step.stage);
    return rows.slice().sort((a, b) => String(b.changedAt || "").localeCompare(String(a.changedAt || ""))
        || String(b.id || "").localeCompare(String(a.id || "")))[0] || null;
}

function assignFlowProcessDates(steps, piece, processDates) {
    const byStage = {};
    const bySub = {};
    const subKey = (kind, sub) => `${kind}|${canonicalFlowSub(kind, sub)}`;
    (processDates || []).forEach((row) => {
        if (!row) return;
        if (row.kind && row.subStage) {
            bySub[subKey(row.kind, row.subStage)] = row.processDate;
        } else if (row.stage) {
            byStage[row.stage] = row.processDate;
        }
    });

    const stages = [];
    steps.forEach((step) => {
        if (!stages.includes(step.stage)) stages.push(step.stage);
    });

    stages.forEach((stage) => {
        const group = steps.filter((step) => step.stage === stage);
        const tableDate = byStage[stage] || null;
        const liveDate = piece.currentStage === stage ? piece.processDate : null;
        if (!group.some((step) => step.sub)) {
            group[0].processDate = liveDate || tableDate || null;
            return;
        }
        // تاریخ ثبت‌شده برای هر زیرمرحله، مستقل از زیرمرحله‌های دیگر
        let hasSubDates = false;
        group.forEach((step) => {
            const subDate = bySub[subKey(step.kind, step.sub)];
            if (subDate) {
                step.processDate = subDate;
                hasSubDates = true;
            }
        });
        if (liveDate) {
            const current = group.find((step) => step.status === "current");
            if (current) current.processDate = liveDate;
            return;
        }
        // سازگاری با داده‌های قدیمی که فقط تاریخ کل مرحله را دارند
        if (hasSubDates || !tableDate) return;
        const done = group.filter((step) => step.status === "done");
        if (done.length) done[done.length - 1].processDate = tableDate;
    });
}

function decorateProductionFlow(piece, history, subStageHistory, processDates, weldingDispatches) {
    const steps = buildProductionFlowSteps(piece, subStageHistory);
    const cursor = resolveFlowCursor(piece, weldingDispatches, steps);
    const movedOn = cursor.note === "delivered" || (history || []).some((row) => row.stage && row.stage !== "NOT_STARTED") || (cursor.index > 0);

    const recordedFlags = steps.map((step) => flowStepVisited(step, history, subStageHistory));

    steps.forEach((step, index) => {
        const visited =
            recordedFlags[index] ||
            (step.stage === "NOT_STARTED" && movedOn && cursor.index !== 0);
        const earlierVisited = recordedFlags.slice(0, index).some(Boolean);
        const laterVisited = recordedFlags.slice(index + 1).some(Boolean);
        if (cursor.note !== "scrapped" && cursor.note !== "delivered" && index === cursor.index) {
            step.status = "current";
        } else if (visited) {
            step.status = "done";
        } else if (!visited && earlierVisited && laterVisited) {
            step.status = "skipped";
        } else if (cursor.index > index) {
            step.status = "done";
        } else {
            step.status = "upcoming";
        }
        step.operator = flowOperator(step, history, subStageHistory);
        step.changedAt = flowHistoryRow(step, history, subStageHistory)?.changedAt || null;
        step.processDate = null;
        step.weldingPause = step.status === "current" && cursor.note === "welding";
    });

    assignFlowProcessDates(steps, piece, processDates);
    return { steps, note: cursor.note };
}

function renderProductionFlow(piece, history, subStageHistory, processDates, weldingDispatches) {
    const flow = decorateProductionFlow(piece, history, subStageHistory, processDates, weldingDispatches);
    const note =
        flow.note === "scrapped"
            ? `<p class="flow-banner flow-banner-scrap">این قطعه اسقاط شده است. موقعیت‌هایی که رد شده‌اند روی خط مانده‌اند.</p>`
            : flow.note === "delivered"
              ? `<p class="flow-banner flow-banner-done">این قطعه تحویل شده است.</p>`
              : flow.note === "welding"
                ? `<p class="flow-banner flow-banner-weld">قطعه اکنون در ایستگاه جوشکاری است. جایگاهش روی خط، همان مرحلهٔ قبل از ارسال است.</p>`
                : "";

    const items = flow.steps
        .map((step, index) => {
            const stageDate = flow.steps.find((item) => item.stage === step.stage && item.processDate)?.processDate || "";
            const dateText = step.processDate && typeof isoToJalaliString === "function"
                ? isoToJalaliString(step.processDate)
                : "—";
            const operatorText = step.operator ? escapeHtml(step.operator) : "—";
            const prevGroup = index > 0 ? flow.steps[index - 1].group || "" : "";
            const nextGroup = index < flow.steps.length - 1 ? flow.steps[index + 1].group || "" : "";
            const groupName = step.group || "";
            const groupStart = Boolean(groupName) && groupName !== prevGroup;
            const groupEnd = Boolean(groupName) && groupName !== nextGroup;
            const groupClass = groupName
                ? ` is-grouped${groupStart ? " is-group-start" : ""}${groupEnd ? " is-group-end" : ""}`
                : "";
            const group = groupStart
                ? `<div class="flow-group"><span class="flow-group-name">${escapeHtml(step.group)}</span></div>`
                : "";
            const weld = step.weldingPause ? `<span class="flow-weld-tag">ایستگاه جوشکاری</span>` : "";
            const statusLabel = {
                done: "انجام شده",
                current: "موقعیت فعلی",
                upcoming: "مانده",
                skipped: "عبور نشده",
            }[step.status];
            const currentAttr = step.status === "current" ? ` aria-current="step"` : "";
            const editable = typeof canEditProcessDate === "function"
                && canEditProcessDate()
                && step.status !== "upcoming";
            const editTitle = editable ? ` title="برای ثبت یا اصلاح تاریخ انجام کلیک کنید"` : "";
            return `
            <li class="flow-step is-${step.status}${groupClass}${editable ? " is-editable" : ""}"${currentAttr}${editTitle}
                data-stage="${escapeHtml(step.stage)}"
                data-label="${escapeHtml(step.label)}"
                data-group="${escapeHtml(step.group || "")}"
                data-kind="${escapeHtml(step.kind || "")}"
                data-sub="${escapeHtml(step.sub || "")}"
                data-process-date="${escapeHtml(step.sub ? (step.processDate || "") : stageDate)}"
                ${editable ? `tabindex="0" role="button"` : ""}>
                <div class="flow-track" aria-hidden="true">
                    <span class="flow-connector flow-connector-before"></span>
                    <span class="flow-dot"></span>
                    <span class="flow-connector flow-connector-after"></span>
                </div>
                <div class="flow-node">
                    ${group}
                    <div class="flow-row">
                        <div class="flow-label-row">
                            <span class="flow-label">${escapeHtml(step.label)}</span>
                            ${weld}
                        </div>
                        <span class="flow-status">${statusLabel}</span>
                        <span class="flow-date"><span class="flow-meta-key">تاریخ انجام</span><span class="flow-meta-val">${escapeHtml(dateText)}</span></span>
                        <span class="flow-operator"><span class="flow-meta-key">ثبت‌کننده</span><span class="flow-meta-val">${operatorText}</span></span>
                    </div>
                </div>
            </li>`;
        })
        .join("");

    return `
        <section class="production-flow" aria-label="روند تولید">
            <div class="profile-info-label">روند تولید</div>
            <p class="flow-hint">مراحل طبق ترتیب مسیر تولید نمایش داده می‌شوند؛ تاریخ انجام ثبت‌شده کنار هر مرحله آمده است. برای ثبت یا اصلاح تاریخ روی مرحله کلیک کنید.</p>
            ${note}
            <ol class="flow-rail">${items}</ol>
        </section>`;
}
