/**
 * api.js
 * تمام توابع مربوط به ارتباط با Backend (fetch) اینجا قرار دارند.
 * هیچ منطق نمایشی (DOM) در این فایل نباید باشد.
 */

// آدرس API نسبت به همان هاستی که صفحه از آن باز شده
// (localhost / IP سرور / production.co) تا از سیستم‌های دیگر Failed to fetch نشود
const API_BASE_URL = `${window.location.origin}/api`;

// کلید ذخیره‌ی توکن JWT در localStorage (auth.js هم از همین کلید استفاده می‌کند)
const AUTH_TOKEN_KEY = "pm_auth_token";

const OperationAPI = {
    cancelWelding: (piece,id,reason,noWorkConfirmed) => apiRequest(`/pieces/${piece}/welding-dispatches/${id}/cancel`,'POST',{reason,noWorkConfirmed}),
    batches: filters => apiRequest(`/heat-treatment-batches/archive?${trackingQueryString(filters)}`),
    batch: id => apiRequest(`/heat-treatment-batches/${id}`),
    batchHistory: (id,page=0) => apiRequest(`/heat-treatment-batches/${id}/history?page=${page}&size=10`),
};

const RecordEditAPI = {
    update: (kind, id, body) => apiRequest(`/${kind === 'materials' ? 'melt-passports/materials' : kind}/${encodeURIComponent(id)}`, 'PATCH', body),
    history: (kind, id, page = 0) => apiRequest(`/record-edits/${kind}/${encodeURIComponent(id)}?page=${page}&size=10`),
};

const MeltPassportAPI = {
    assignees: () => apiRequest('/melt-passports/assignees'),
    assign: (id, assigneeId, version) => apiRequest(`/melt-passports/${encodeURIComponent(id)}/assignment`, 'POST', { assigneeId, version }),
    assignPiece: (id, assigneeId, version = null) => apiRequest(`/melt-passports/piece/${encodeURIComponent(id)}/assignment`, 'POST', { assigneeId, version }),
    today: () => apiRequest('/melt-passports/today'),
    get: (id) => apiRequest(`/melt-passports/${encodeURIComponent(id)}`),
    forPiece: (id) => apiRequest(`/melt-passports/piece/${encodeURIComponent(id)}`),
    list: (filters) => apiRequest(`/melt-passports?${trackingQueryString(filters)}`),
    candidates: (q = '', approvedPassportId) => apiRequest(`/melt-passports/candidates?${trackingQueryString({ q, approvedPassportId })}`),
    ingotTypes: () => apiRequest('/melt-passports/ingot-types'),
    materials: () => apiRequest('/melt-passports/materials'),
    createMaterial: (body) => apiRequest('/melt-passports/materials', 'POST', body),
    setMaterialActive: (id, active) => apiRequest(`/melt-passports/materials/${encodeURIComponent(id)}/active?active=${active}`, 'PATCH'),
    create: (body) => apiRequest('/melt-passports', 'POST', body),
    update: (id, body) => apiRequest(`/melt-passports/${encodeURIComponent(id)}`, 'PUT', body),
    remove: (id, version) => apiRequest(`/melt-passports/${encodeURIComponent(id)}?version=${encodeURIComponent(version)}`, 'DELETE'),
    updateMembership: (id, body) => apiRequest(`/melt-passports/${encodeURIComponent(id)}/pieces`, 'PUT', body),
    correct: (id, data, reason = null, calculateMagnesium = false, addedPiecesStage, scrap) =>
        apiRequest(`/melt-passports/${encodeURIComponent(id)}/corrections`, 'POST', { data, reason, calculateMagnesium, addedPiecesStage, scrap }),
    action: (id, action, version, reason = null) => apiRequest(`/melt-passports/${encodeURIComponent(id)}/${action}`, 'POST', { version, reason }),
    consumption: (filters) => apiRequest(`/melt-passports/consumption?${trackingQueryString(filters)}`),
};

/**
 * تابع کمکی مرکزی برای انجام درخواست‌های HTTP
 * تمام توابع دیگر این فایل از این تابع استفاده می‌کنند.
 */
async function apiRequest(endpoint, method = "GET", body = null) {
    const options = {
        method,
        headers: {
            "Content-Type": "application/json",
        },
    };

    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    if (token) {
        options.headers["Authorization"] = `Bearer ${token}`;
    }

    if (body !== null) {
        options.body = JSON.stringify(body);
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, options);

    if (response.status === 401) {
        // ورود یا تغییر رمز: پیام سرور را نشان بده، نشست را باطل نکن
        const isCredentialCheck =
            endpoint === "/auth/login" || endpoint === "/users/me/password";

        if (isCredentialCheck) {
            let message =
                endpoint === "/auth/login"
                    ? "نام کاربری یا رمز عبور اشتباه است"
                    : "رمز عبور فعلی اشتباه است";
            try {
                const data = await response.json();
                message = data.message || message;
            } catch (_) {
                /* ignore */
            }
            throw new Error(message);
        }

        window.dispatchEvent(new CustomEvent("auth:unauthorized"));
        throw new Error("نشست شما منقضی شده است؛ دوباره وارد شوید");
    }

    if (response.status === 204) {
        return null;
    }

    const data = await response.json();

    if (!response.ok) {
        const message = data.message || (data.errors && Object.values(data.errors)[0]) || "خطایی رخ داد";
        const error = new Error(message);
        error.status = response.status;
        error.errors = data.errors;
        throw error;
    }

    return data;
}

function trackingQueryString(params = {}) {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value === undefined || value === null || value === "") return;
        query.set(key, String(value));
    });
    return query.toString();
}

/** آپلود multipart بدون Content-Type دستی (boundary توسط مرورگر تنظیم می‌شود) */
async function apiUpload(endpoint, formData, method = "POST") {
    const headers = {};
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    if (token) {
        headers["Authorization"] = `Bearer ${token}`;
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        method,
        headers,
        body: formData,
    });

    if (response.status === 401) {
        window.dispatchEvent(new CustomEvent("auth:unauthorized"));
        throw new Error("نشست شما منقضی شده است؛ دوباره وارد شوید");
    }

    const data = await response.json();
    if (!response.ok) {
        const message = data.message || (data.errors && Object.values(data.errors)[0]) || "خطایی رخ داد";
        throw new Error(message);
    }
    return data;
}

/** دریافت باینری (مثلاً تصویر نقشه) به‌صورت Blob با JWT */
async function apiBlob(endpoint, cache = "default") {
    const headers = {};
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    if (token) {
        headers["Authorization"] = `Bearer ${token}`;
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, { headers, cache });

    if (response.status === 401) {
        window.dispatchEvent(new CustomEvent("auth:unauthorized"));
        throw new Error("نشست شما منقضی شده است؛ دوباره وارد شوید");
    }

    if (!response.ok) {
        let message = "خطایی رخ داد";
        try {
            const data = await response.json();
            message = data.message || message;
        } catch (_) {
            /* ignore */
        }
        throw new Error(message);
    }

    return response.blob();
}

/* ===========================
   Project API
   =========================== */

const ProjectAPI = {
    getAll: () => apiRequest("/projects"),
    getById: (id) => apiRequest(`/projects/${id}`),
    create: (data) => apiRequest("/projects", "POST", data),
    update: (id, data) => apiRequest(`/projects/${id}`, "PUT", data),
    deletionPreview: id => apiRequest(`/projects/${id}/deletion-preview`),
    delete: (id, token) => apiRequest(`/projects/${id}?confirmationToken=${encodeURIComponent(token)}`, "DELETE"),
    archivePieces: (id) => apiRequest(`/projects/${id}/archive-pieces`, "POST"),
};

/* ===========================
   PieceDefinition API
   =========================== */

const PieceDefinitionAPI = {
    getByProject: (projectId) => apiRequest(`/piece-definitions?projectId=${projectId}`),
    getById: (id) => apiRequest(`/piece-definitions/${id}`),
    create: (data) => apiRequest("/piece-definitions", "POST", data),
    update: (id, data) => apiRequest(`/piece-definitions/${id}`, "PUT", data),
    deletionPreview: id => apiRequest(`/piece-definitions/${id}/deletion-preview`),
    delete: (id, token) => apiRequest(`/piece-definitions/${id}?confirmationToken=${encodeURIComponent(token)}`, "DELETE"),
    archivePieces: (id) => apiRequest(`/piece-definitions/${id}/archive-pieces`, "POST"),
    getStatusReport: (id) => apiRequest(`/piece-definitions/${id}/status-report`),
    uploadDrawing: (id, file) => {
        const formData = new FormData();
        formData.append("file", file);
        return apiUpload(`/piece-definitions/${id}/drawing`, formData);
    },
    getDrawingBlob: (id) => apiBlob(`/piece-definitions/${id}/drawing`, "no-store"),
    drawingRevisions: (id,page=0) => apiRequest(`/piece-definitions/${id}/drawing-revisions?page=${page}&size=100`),
    currentDrawingId: id => apiRequest(`/piece-definitions/${id}/drawing-current-id`),
    revisionBlob: (id,revision) => apiBlob(`/piece-definitions/${id}/drawing-revisions/${revision}`),
    restoreDrawing: (id,revision) => apiRequest(`/piece-definitions/${id}/drawing-revisions/${revision}/restore`,'POST'),
    deleteDrawing: (id) => apiRequest(`/piece-definitions/${id}/drawing`, "DELETE"),
};

/* ===========================
   Piece API
   =========================== */

const PieceAPI = {
    getAll: () => apiRequest("/pieces/all"),
    search: (keyword) => apiRequest(`/pieces/search?q=${encodeURIComponent(keyword || "")}`),
    getByDefinition: (pieceDefinitionId, archived = false) =>
        apiRequest(`/pieces?pieceDefinitionId=${pieceDefinitionId}${archived ? "&archived=true" : ""}`),
    getTrackingSummary: (params = {}) =>
        apiRequest(`/pieces/tracking/summary?${trackingQueryString(params)}`),
    listTracking: (params = {}) =>
        apiRequest(`/pieces/tracking?${trackingQueryString(params)}`),
    getById: (id) => apiRequest(`/pieces/${id}`),
    getByRef: (code) => apiRequest(`/pieces/by-ref?code=${encodeURIComponent(code)}`),
    create: (data) => apiRequest("/pieces", "POST", data),
    bulkCreate: (data) => apiRequest("/pieces/bulk", "POST", data),
    update: (id, data) => apiRequest(`/pieces/${id}`, "PUT", data),
    delete: (id) => apiRequest(`/pieces/${id}`, "DELETE"),
    restore: (id) => apiRequest(`/pieces/${id}/restore`, "POST"),
    bulkArchive: (pieceIds) => apiRequest("/pieces/bulk/archive", "POST", { pieceIds }),
    bulkRestore: (pieceIds) => apiRequest("/pieces/bulk/restore", "POST", { pieceIds }),
    updateProcessDate: (id, processDate) =>
        apiRequest(`/pieces/${id}/process-date`, "PATCH", { processDate }),
    updateStageProcessDate: (id, stage, processDate) =>
        apiRequest(`/pieces/${id}/stage-process-dates`, "PATCH", { stage, processDate }),
    updateSubStageProcessDate: (id, kind, subStage, processDate) =>
        apiRequest(`/pieces/${id}/sub-stage-process-dates`, "PATCH", { kind, subStage, processDate }),
    getSubStageProcessDates: (id) => apiRequest(`/pieces/${id}/sub-stage-process-dates`),
    updateStage: (id, data, scrap, substages = {}) => apiRequest(`/pieces/${id}/stage`, "PATCH", { newStage: data, scrap, ...substages }),
    getStageCorrectionTargets: (id) => apiRequest(`/pieces/${id}/stage-correction/targets`),
    correctStage: (id, target) => apiRequest(`/pieces/${id}/stage-correction`, "PATCH", target),
    getBulkStageCorrectionTargets: (pieceIds) => apiRequest("/pieces/bulk/stage-correction/targets", "POST", { pieceIds }),
    bulkCorrectStage: (pieceIds, target) => apiRequest("/pieces/bulk/stage-correction", "PATCH", { pieceIds, target }),
    advance: (id, processDate) =>
        apiRequest(`/pieces/${id}/advance`, "POST", processDate ? { processDate } : {}),
    updateRadiographySubStage: (id, radiographySubStage) =>
        apiRequest(`/pieces/${id}/radiography-substage`, "PATCH", { radiographySubStage }),
    updateHeatTreatmentSubStage: (id, heatTreatmentSubStage) =>
        apiRequest(`/pieces/${id}/heat-treatment-substage`, "PATCH", { heatTreatmentSubStage }),
    updateRoughMachiningSubStage: (id, roughMachiningSubStage) =>
        apiRequest(`/pieces/${id}/rough-machining-substage`, "PATCH", { roughMachiningSubStage }),
    getStageProcessDates: (id) => apiRequest(`/pieces/${id}/stage-process-dates`),
    getStageHistory: (id) => apiRequest(`/pieces/${id}/stage-history`),
    getSubStageHistory: (id) => apiRequest(`/pieces/${id}/substage-history`),
    getScrapDispatches: (id) => apiRequest(`/pieces/${id}/scrap-dispatches`),
    updateScrapDispatch: (id, data) => apiRequest(`/pieces/${id}/scrap-dispatch`, "PATCH", data),
    getWeldingDispatches: (id) => apiRequest(`/pieces/${id}/welding-dispatches`),
    sendToWeldingStation: (id, data) => apiRequest(`/pieces/${id}/welding-station`, "POST", data),
    updateWeldingDispatch: (id, dispatchId, data) =>
        apiRequest(`/pieces/${id}/welding-dispatches/${dispatchId}`, "PATCH", data),
    completeWeldingStation: (id, destination) =>
        apiRequest(`/pieces/${id}/welding-station/complete`, "POST", { destination }),
    deleteStageHistory: (pieceId, historyId) =>
        apiRequest(`/pieces/${pieceId}/stage-history/${historyId}`, "DELETE"),
    deleteSubStageHistory: (pieceId, historyId) =>
        apiRequest(`/pieces/${pieceId}/substage-history/${historyId}`, "DELETE"),
    bulkUpdateStage: (pieceIds, newStage, scrap) => apiRequest("/pieces/bulk/stage", "PATCH", { pieceIds, newStage, scrap }),
    bulkUpdateRadiographySubStage: (pieceIds, radiographySubStage) =>
        apiRequest("/pieces/bulk/radiography-substage", "PATCH", { pieceIds, radiographySubStage }),
    bulkUpdateHeatTreatmentSubStage: (pieceIds, heatTreatmentSubStage) =>
        apiRequest("/pieces/bulk/heat-treatment-substage", "PATCH", { pieceIds, heatTreatmentSubStage }),
    bulkUpdateRoughMachiningSubStage: (pieceIds, roughMachiningSubStage) =>
        apiRequest("/pieces/bulk/rough-machining-substage", "PATCH", { pieceIds, roughMachiningSubStage }),
};

/* ===========================
   Tensile Test API
   =========================== */

const TensileTestAPI = {
    getSampleLabels: () => apiRequest("/tensile-tests/sample-labels"),
    getByPiece: (pieceId) => apiRequest(`/pieces/${pieceId}/tensile-tests`),
    create: (pieceId, data) => apiRequest(`/pieces/${pieceId}/tensile-tests`, "POST", data),
    update: (tensileTestId, data) => apiRequest(`/tensile-tests/${tensileTestId}`, "PUT", data),
    delete: (tensileTestId) => apiRequest(`/tensile-tests/${tensileTestId}`, "DELETE"),
    saveDisposition: (tensileTestId, data) =>
        apiRequest(`/tensile-tests/${tensileTestId}/disposition`, "PUT", data),
    getPendingReview: (projectId) =>
        apiRequest(`/tensile-tests/pending-review${projectId ? `?projectId=${projectId}` : ""}`),
};

/* ===========================
   Notification API (تایید خودکار کشش)
   =========================== */

const NotificationAPI = {
    list: (limit) => apiRequest(`/notifications${limit ? `?limit=${limit}` : ""}`),
    unreadCount: () => apiRequest("/notifications/unread-count"),
    markRead: (id) => apiRequest(`/notifications/${id}/read`, "POST"),
    markAllRead: () => apiRequest("/notifications/read-all", "POST"),
    dismiss: (id) => apiRequest(`/notifications/${id}`, "DELETE"),
};

/* ===========================
   Heat Treatment API
   =========================== */

const HeatTreatmentAPI = {
    getByPiece: (pieceId) => apiRequest(`/pieces/${pieceId}/heat-treatments`),
    create: (pieceId, data) => apiRequest(`/pieces/${pieceId}/heat-treatments`, "POST", data),
    delete: (recordId) => apiRequest(`/heat-treatments/${recordId}`, "DELETE"),
    getTodayBatches: () => apiRequest("/heat-treatment-batches"),
    findBatchByCode: (code) =>
        apiRequest(`/heat-treatment-batches/by-code?code=${encodeURIComponent(code)}`),
    getNextBatchCode: () => apiRequest("/heat-treatment-batches/next-code"),
    updateBatch: (batchId, data) =>
        apiRequest(`/heat-treatment-batches/${batchId}`, "PATCH", data),
};

/* ===========================
   Radiography Report API
   =========================== */

const RadiographyReportAPI = {
    getTemplates: (pieceDefinitionId) =>
        apiRequest(`/piece-definitions/${pieceDefinitionId}/radiography-templates`),
    createTemplate: (pieceDefinitionId, data) =>
        apiRequest(`/piece-definitions/${pieceDefinitionId}/radiography-templates`, "POST", data),
    updateTemplate: (templateId, data) => apiRequest(`/radiography-templates/${templateId}`, "PUT", data),
    deleteTemplate: (templateId) => apiRequest(`/radiography-templates/${templateId}`, "DELETE"),
    getByPiece: (pieceId) => apiRequest(`/pieces/${pieceId}/radiography-reports`),
    save: (pieceId, data) => apiRequest(`/pieces/${pieceId}/radiography-reports`, "PUT", data),
    delete: (reportId) => apiRequest(`/radiography-reports/${reportId}`, "DELETE"),
};

/* ===========================
   Dashboard API
   =========================== */

const DashboardAPI = {
    getStats: () => apiRequest("/dashboard/stats"),
    getAlerts: (stuckThresholdDays) => apiRequest(`/dashboard/alerts?stuckThresholdDays=${stuckThresholdDays}`),
    getOverview: ({ projectId, dateFrom, dateTo, stuckThresholdDays, subStageThresholdDays } = {}) => {
        const params = new URLSearchParams();
        if (projectId) params.set("projectId", projectId);
        if (dateFrom) params.set("dateFrom", dateFrom);
        if (dateTo) params.set("dateTo", dateTo);
        if (stuckThresholdDays != null) params.set("stuckThresholdDays", stuckThresholdDays);
        if (subStageThresholdDays != null) params.set("subStageThresholdDays", subStageThresholdDays);
        const qs = params.toString();
        return apiRequest(`/dashboard/overview${qs ? `?${qs}` : ""}`);
    },
    getRadiographyDefectReport: ({
        projectId,
        pieceDefinitionIds,
        defectTypes,
        statuses,
        weldingConfirmed,
        dateFrom,
        dateTo,
    } = {}) => {
        const params = new URLSearchParams();
        params.set("projectId", projectId);
        (pieceDefinitionIds || []).forEach((id) => params.append("pieceDefinitionIds", id));
        (defectTypes || []).forEach((t) => params.append("defectTypes", t));
        (statuses || []).forEach((s) => params.append("statuses", s));
        (weldingConfirmed || []).forEach((v) => params.append("weldingConfirmed", String(v)));
        if (dateFrom) params.set("dateFrom", dateFrom);
        if (dateTo) params.set("dateTo", dateTo);
        return apiRequest(`/dashboard/radiography-defect-report?${params.toString()}`);
    },
    getStageOccupancyReport: ({ projectId, dateFrom, dateTo } = {}) => {
        const params = new URLSearchParams();
        if (projectId) params.set("projectId", projectId);
        if (dateFrom) params.set("dateFrom", dateFrom);
        if (dateTo) params.set("dateTo", dateTo);
        return apiRequest(`/dashboard/stage-occupancy-report?${params.toString()}`);
    },
    getScrapReport: ({ projectId } = {}) => {
        const params = new URLSearchParams();
        if (projectId) params.set("projectId", projectId);
        return apiRequest(`/dashboard/scrap-report?${params}`);
    },
    getWeldingStationReport: ({ projectId } = {}) => {
        const params = new URLSearchParams();
        if (projectId) params.set("projectId", projectId);
        const qs = params.toString();
        return apiRequest(`/dashboard/welding-station-report${qs ? `?${qs}` : ""}`);
    },
    getTensileQualityReport: ({ projectId, pieceDefinitionId, sampleLabels, dateFrom, dateTo } = {}) => {
        const params = new URLSearchParams();
        if (projectId) params.set("projectId", projectId);
        if (pieceDefinitionId) params.set("pieceDefinitionId", pieceDefinitionId);
        (sampleLabels || []).forEach((label) => {
            if (label) params.append("sampleLabels", label);
        });
        if (dateFrom) params.set("dateFrom", dateFrom);
        if (dateTo) params.set("dateTo", dateTo);
        const qs = params.toString();
        return apiRequest(`/dashboard/tensile-quality-report${qs ? `?${qs}` : ""}`);
    },
    getTensileTrendReport: ({
        projectId,
        pieceDefinitionId,
        sampleLabels,
        dateFrom,
        dateTo,
        movingAverageWindow,
        stableThresholdPercent,
    } = {}) => {
        const params = new URLSearchParams();
        if (projectId) params.set("projectId", projectId);
        if (pieceDefinitionId) params.set("pieceDefinitionId", pieceDefinitionId);
        (sampleLabels || []).forEach((label) => {
            if (label) params.append("sampleLabels", label);
        });
        if (dateFrom) params.set("dateFrom", dateFrom);
        if (dateTo) params.set("dateTo", dateTo);
        if (movingAverageWindow) params.set("movingAverageWindow", movingAverageWindow);
        if (stableThresholdPercent != null && stableThresholdPercent !== "") {
            params.set("stableThresholdPercent", stableThresholdPercent);
        }
        const qs = params.toString();
        return apiRequest(`/dashboard/tensile-trend-report${qs ? `?${qs}` : ""}`);
    },
};

/* ===========================
   Auth API
   =========================== */

const AuthAPI = {
    login: (data) => apiRequest("/auth/login", "POST", data),
    me: () => apiRequest("/auth/me"),
};

/* ===========================
   User API (مدیریت کاربران - فقط ادمین، به‌جز تغییر رمز خود کاربر)
   =========================== */

const UserAPI = {
    getAll: () => apiRequest("/users"),
    directory: () => apiRequest("/users/directory"),
    create: (data) => apiRequest("/users", "POST", data),
    delete: (id) => apiRequest(`/users/${id}`, "DELETE"),
    changeMyPassword: (data) => apiRequest("/users/me/password", "PATCH", data),
    resetPassword: (id, newPassword) => apiRequest(`/users/${id}/password`, "PATCH", { newPassword }),
    updateAccount: (id, data) => apiRequest(`/users/${id}`, "PATCH", data),
    accessHistory: (id, page = 0) => apiRequest(`/users/${id}/access-history?page=${page}&size=25`),
    updatePermissions: (id, permissions) => apiRequest(`/users/${id}/permissions`, "PATCH", { permissions }),
    getProfile: (id) => apiRequest(`/users/${id}/profile`),
    updateProfile: (id, data) => apiRequest(`/users/${id}/profile`, "PATCH", data),
    getTasks: (id, params = {}) => apiRequest(`/users/${id}/tasks${taskQueryString(params)}`),
};

const PerformanceAPI = {
    employee: (userId) => apiRequest(`/users/${encodeURIComponent(userId)}/performance`),
    list: (userId, params = {}) => apiRequest(`/users/${userId}/performance/periods${taskQueryString(params)}`),
    get: (userId, periodId) => apiRequest(`/users/${userId}/performance/periods/${periodId}`),
    events: (userId, periodId, params = {}) => apiRequest(`/users/${userId}/performance/periods/${periodId}/events${taskQueryString(params)}`),
    create: (userId, data) => apiRequest(`/users/${userId}/performance/periods`, "POST", data),
    next: (userId, periodId, data) => apiRequest(`/users/${userId}/performance/periods/${periodId}/next`, "POST", data),
    add: (userId, periodId, data) => apiRequest(`/users/${userId}/performance/periods/${periodId}/criteria`, "POST", data),
    update: (userId, periodId, criterionId, data) => apiRequest(`/users/${userId}/performance/periods/${periodId}/criteria/${criterionId}`, "PATCH", data),
    updateScore: (userId, periodId, criterionId, data) => apiRequest(`/users/${userId}/performance/periods/${periodId}/criteria/${criterionId}/score`, "PATCH", data),
    delete: (userId, periodId, criterionId, data) => apiRequest(`/users/${userId}/performance/periods/${periodId}/criteria/${criterionId}`, "DELETE", data),
};

/* ===========================
   Task API (وظایف)
   =========================== */

/** پارامترهای تکراری (status=A&status=B) و حذف مقدارهای خالی */
function taskQueryString(params = {}) {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value === undefined || value === null || value === "") return;
        if (Array.isArray(value)) {
            value.forEach((v) => query.append(key, String(v)));
        } else {
            query.set(key, String(value));
        }
    });
    const qs = query.toString();
    return qs ? `?${qs}` : "";
}

const TaskAPI = {
    list: (params) => apiRequest(`/tasks${taskQueryString(params)}`),
    get: (id) => apiRequest(`/tasks/${id}`),
    create: (data) => apiRequest("/tasks", "POST", data),
    update: (id, data) => apiRequest(`/tasks/${id}`, "PUT", data),
    changeStatus: (id, status, reason) => apiRequest(`/tasks/${id}/status`, "PATCH", { status, reason: reason || null }),
    changePriority: (id, priority) => apiRequest(`/tasks/${id}/priority`, "PATCH", { priority }),
    changeDeadline: (id, dueDate) => apiRequest(`/tasks/${id}/deadline`, "PATCH", { dueDate }),
    changeAssignee: (id, assigneeId) => apiRequest(`/tasks/${id}/assignee`, "PATCH", { assigneeId }),
    cancel: (id, reason) => apiRequest(`/tasks/${id}${taskQueryString({ reason })}`, "DELETE"),
    assignees: () => apiRequest("/tasks/assignees"),
    lookupParts: (q, projectId) => apiRequest(`/tasks/lookup/parts${taskQueryString({ q, projectId })}`),

    comments: (id) => apiRequest(`/tasks/${id}/comments`),
    addComment: (id, content) => apiRequest(`/tasks/${id}/comments`, "POST", { content }),
    editComment: (id, commentId, content) => apiRequest(`/tasks/${id}/comments/${commentId}`, "PUT", { content }),
    deleteComment: (id, commentId) => apiRequest(`/tasks/${id}/comments/${commentId}`, "DELETE"),

    checklist: (id) => apiRequest(`/tasks/${id}/checklist`),
    addChecklistItem: (id, title) => apiRequest(`/tasks/${id}/checklist`, "POST", { title }),
    updateChecklistItem: (id, itemId, data) => apiRequest(`/tasks/${id}/checklist/${itemId}`, "PATCH", data),
    deleteChecklistItem: (id, itemId) => apiRequest(`/tasks/${id}/checklist/${itemId}`, "DELETE"),

    activities: (id, page = 0, size = 50) => apiRequest(`/tasks/${id}/activities${taskQueryString({ page, size })}`),
};

/* ===========================
   Radiography Workflow API (تفسیر و تأیید گزارش رادیوگرافی) + امضای پروفایل
   =========================== */

const RadiographyWorkflowAPI = {
    queue: (params) => apiRequest(`/radiography/queue${taskQueryString(params)}`),
    interpreters: () => apiRequest("/radiography/interpreters"),
    pieceState: (pieceId) => apiRequest(`/radiography/pieces/${pieceId}/workflow`),
    assign: (pieceId, interpreterId) =>
        apiRequest(`/radiography/pieces/${pieceId}/workflow/assign`, "POST", { interpreterId }),
    get: (id) => apiRequest(`/radiography/workflows/${id}`),
    reassign: (id, interpreterId) =>
        apiRequest(`/radiography/workflows/${id}/reassign`, "POST", { interpreterId }),
    cancel: (id, reason) => apiRequest(`/radiography/workflows/${id}/cancel`, "POST", { reason }),
    interpreterApprove: (id) =>
        apiRequest(`/radiography/workflows/${id}/interpreter-approve`, "POST", { confirmed: true }),
    managerApprove: (id) =>
        apiRequest(`/radiography/workflows/${id}/manager-approve`, "POST", { confirmed: true }),
    managerReject: (id, reason) =>
        apiRequest(`/radiography/workflows/${id}/manager-reject`, "POST", { reason }),
    document: (id, versionId) =>
        apiRequest(`/radiography/workflows/${id}/versions/${versionId}/document`),
};

const SignatureAPI = {
    get: () => apiRequest("/profile/signature"),
    upload: (file) => {
        const formData = new FormData();
        formData.append("file", file);
        return apiUpload("/profile/signature", formData, "PUT");
    },
    remove: () => apiRequest("/profile/signature", "DELETE"),
};

/* ===========================
   Permission API (فقط ادمین)
   =========================== */

const PermissionAPI = {
    getAll: () => apiRequest("/permissions"),
};

/* ===========================
   Radiography Defect API (عیوب رادیوگرافی)
   =========================== */

const RadiographyDefectAPI = {
    getByPiece: (pieceId) => apiRequest(`/pieces/${pieceId}/radiography-defects`),
    create: (pieceId, data) => apiRequest(`/pieces/${pieceId}/radiography-defects`, "POST", data),
    update: (id, data) => apiRequest(`/radiography-defects/${id}`, "PATCH", data),
    delete: (id) => apiRequest(`/radiography-defects/${id}`, "DELETE"),
    confirmWelding: (id) => apiRequest(`/radiography-defects/${id}/confirm-welding`, "PATCH"),
};

/* ===========================
   Piece Note API (یادداشت‌های قطعه)
   =========================== */

const PieceNoteAPI = {
    getByPiece: (pieceId) => apiRequest(`/pieces/${pieceId}/notes`),
    create: (pieceId, content) => apiRequest(`/pieces/${pieceId}/notes`, "POST", { content }),
    delete: (id) => apiRequest(`/notes/${id}`, "DELETE"),
};

/* ===========================
   Welding Map Mark API (نقاط جوش روی نقشه)
   =========================== */

const WeldingMapMarkAPI = {
    getByPiece: (pieceId) => apiRequest(`/pieces/${pieceId}/welding-marks`),
    getByDefinition: (definitionId, defectType) => {
        const q = defectType ? `?defectType=${encodeURIComponent(defectType)}` : "";
        return apiRequest(`/piece-definitions/${definitionId}/welding-marks${q}`);
    },
    create: (pieceId, data) => apiRequest(`/pieces/${pieceId}/welding-marks`, "POST", data),
    delete: (id) => apiRequest(`/welding-marks/${id}`, "DELETE"),
};
