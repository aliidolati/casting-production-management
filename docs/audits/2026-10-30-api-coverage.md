# پوشش مسیرهای HTTP در بررسی ۲۰۲۶-۱۰-۳۰

این جدول از کنترلرهای فعلی و درخواست‌های ثبت‌شدهٔ دور نهایی تست ساخته شده است. «درخواست شد» ممکن است شامل سناریوی منع دسترسی یا اعتبارسنجی باشد؛ «پاسخ موفق» فقط وجود یک پاسخ 2xx را نشان می‌دهد و تضمین صحت کامل آن مسیر نیست. بررسی همهٔ ترکیب‌های ورودی یا همهٔ رقابت‌های همزمان ادعا نمی‌شود.

| کنترلر | متد | مسیر | درخواست شد | پاسخ 2xx | کدهای مشاهده‌شده |
|---|---|---|---|---|---|
| AuthController | POST | `/api/auth/login` | بله | بله | 200, 401 |
| AuthController | GET | `/api/auth/me` | بله | بله | 200, 401 |
| DashboardController | GET | `/api/dashboard/stats` | بله | بله | 200, 401, 403 |
| DashboardController | GET | `/api/dashboard/alerts` | بله | بله | 200, 403 |
| DashboardController | GET | `/api/dashboard/overview` | بله | بله | 200, 403 |
| DashboardController | GET | `/api/dashboard/radiography-defect-report` | بله | بله | 200, 403 |
| DashboardController | GET | `/api/dashboard/stage-occupancy-report` | بله | بله | 200, 403, 500 |
| DashboardController | GET | `/api/dashboard/welding-station-report` | بله | بله | 200, 403 |
| DashboardController | GET | `/api/dashboard/tensile-quality-report` | بله | بله | 200, 403 |
| DashboardController | GET | `/api/dashboard/tensile-trend-report` | بله | بله | 200, 403 |
| HeatTreatmentController | GET | `/api/pieces/{pieceId}/heat-treatments` | بله | بله | 200 |
| HeatTreatmentController | POST | `/api/pieces/{pieceId}/heat-treatments` | بله | بله | 201, 400, 403 |
| HeatTreatmentController | DELETE | `/api/heat-treatments/{id}` | بله | بله | 204 |
| HeatTreatmentController | GET | `/api/heat-treatment-batches` | بله | بله | 200 |
| HeatTreatmentController | GET | `/api/heat-treatment-batches/by-code` | بله | بله | 200, 500 |
| HeatTreatmentController | GET | `/api/heat-treatment-batches/next-code` | بله | بله | 200 |
| HeatTreatmentController | PATCH | `/api/heat-treatment-batches/{id}` | بله | بله | 200 |
| MeltPassportController | POST | `/api/melt-passports/{id}/assignment` | بله | بله | 200 |
| MeltPassportController | POST | `/api/melt-passports/piece/{pieceId}/assignment` | بله | بله | 200 |
| MeltPassportController | GET | `/api/melt-passports/today` | بله | بله | 200 |
| MeltPassportController | GET | `/api/melt-passports` | بله | بله | 200, 403 |
| MeltPassportController | GET | `/api/melt-passports/consumption` | بله | بله | 200 |
| MeltPassportController | GET | `/api/melt-passports/candidates` | بله | بله | 200 |
| MeltPassportController | GET | `/api/melt-passports/ingot-types` | بله | بله | 200 |
| MeltPassportController | GET | `/api/melt-passports/materials` | بله | بله | 200, 401 |
| MeltPassportController | POST | `/api/melt-passports/materials` | بله | بله | 200 |
| MeltPassportController | PATCH | `/api/melt-passports/materials/{id}/active` | بله | بله | 200, 403 |
| MeltPassportController | GET | `/api/melt-passports/piece/{pieceId}` | بله | بله | 200 |
| MeltPassportController | GET | `/api/melt-passports/{id}` | بله | بله | 200, 401 |
| MeltPassportController | POST | `/api/melt-passports` | بله | بله | 201 |
| MeltPassportController | PUT | `/api/melt-passports/{id}` | بله | بله | 200, 400, 403, 409 |
| MeltPassportController | POST | `/api/melt-passports/{id}/calculate-magnesium` | بله | بله | 200 |
| MeltPassportController | POST | `/api/melt-passports/{id}/corrections` | بله | بله | 200 |
| MeltPassportController | POST | `/api/melt-passports/{id}/submit` | بله | بله | 200 |
| MeltPassportController | POST | `/api/melt-passports/{id}/approve` | بله | بله | 200, 400, 403 |
| MeltPassportController | POST | `/api/melt-passports/{id}/return` | بله | بله | 200 |
| NotificationController | GET | `/api/notifications` | بله | بله | 200, 401 |
| NotificationController | GET | `/api/notifications/unread-count` | بله | بله | 200 |
| NotificationController | POST | `/api/notifications/{id}/read` | بله | بله | 204, 404 |
| NotificationController | POST | `/api/notifications/read-all` | بله | بله | 204 |
| NotificationController | DELETE | `/api/notifications/{id}` | بله | بله | 204 |
| PerformanceController | GET | `/api/users/{userId}/performance/periods` | بله | بله | 200, 404 |
| PerformanceController | GET | `/api/users/{userId}/performance/periods/{periodId}` | بله | بله | 200 |
| PerformanceController | GET | `/api/users/{userId}/performance/periods/{periodId}/events` | بله | بله | 200 |
| PerformanceController | POST | `/api/users/{userId}/performance/periods` | بله | بله | 200 |
| PerformanceController | POST | `/api/users/{userId}/performance/periods/{periodId}/next` | بله | بله | 200 |
| PerformanceController | POST | `/api/users/{userId}/performance/periods/{periodId}/criteria` | بله | بله | 200, 403 |
| PerformanceController | PATCH | `/api/users/{userId}/performance/periods/{periodId}/criteria/{criterionId}` | بله | بله | 200, 400, 409 |
| PerformanceController | DELETE | `/api/users/{userId}/performance/periods/{periodId}/criteria/{criterionId}` | بله | بله | 200 |
| PermissionController | GET | `/api/permissions` | بله | بله | 200 |
| PieceController | POST | `/api/pieces` | بله | بله | 201, 409 |
| PieceController | GET | `/api/pieces` | بله | بله | 200 |
| PieceController | POST | `/api/pieces/bulk` | بله | بله | 201, 400, 409 |
| PieceController | GET | `/api/pieces/by-ref` | بله | بله | 200 |
| PieceController | GET | `/api/pieces/{id}` | بله | بله | 200, 400, 401, 404 |
| PieceController | PUT | `/api/pieces/{id}` | بله | بله | 200, 400, 403 |
| PieceController | DELETE | `/api/pieces/{id}` | بله | بله | 204, 403 |
| PieceController | POST | `/api/pieces/{id}/restore` | بله | بله | 200 |
| PieceController | POST | `/api/pieces/bulk/archive` | بله | بله | 200 |
| PieceController | POST | `/api/pieces/bulk/restore` | بله | بله | 200 |
| PieceController | PATCH | `/api/pieces/{id}/process-date` | بله | بله | 200 |
| PieceController | PATCH | `/api/pieces/{id}/stage` | بله | بله | 200, 400, 403 |
| PieceController | POST | `/api/pieces/{id}/advance` | بله | بله | 200, 400 |
| PieceController | PATCH | `/api/pieces/{id}/radiography-substage` | بله | بله | 200 |
| PieceController | PATCH | `/api/pieces/{id}/heat-treatment-substage` | بله | بله | 200 |
| PieceController | PATCH | `/api/pieces/{id}/rough-machining-substage` | بله | بله | 200 |
| PieceController | PATCH | `/api/pieces/bulk/stage` | بله | بله | 200 |
| PieceController | PATCH | `/api/pieces/bulk/radiography-substage` | بله | بله | 200 |
| PieceController | PATCH | `/api/pieces/bulk/heat-treatment-substage` | بله | بله | 200 |
| PieceController | PATCH | `/api/pieces/bulk/rough-machining-substage` | بله | بله | 200 |
| PieceController | GET | `/api/pieces/{id}/stage-process-dates` | بله | بله | 200 |
| PieceController | GET | `/api/pieces/{id}/sub-stage-process-dates` | بله | بله | 200 |
| PieceController | PATCH | `/api/pieces/{id}/sub-stage-process-dates` | بله | بله | 204 |
| PieceController | PATCH | `/api/pieces/{id}/stage-process-dates` | بله | بله | 204 |
| PieceController | GET | `/api/pieces/{id}/stage-history` | بله | بله | 200 |
| PieceController | GET | `/api/pieces/{id}/substage-history` | بله | بله | 200 |
| PieceController | GET | `/api/pieces/{id}/welding-dispatches` | بله | بله | 200 |
| PieceController | POST | `/api/pieces/{id}/welding-station` | بله | بله | 201 |
| PieceController | PATCH | `/api/pieces/{id}/welding-dispatches/{dispatchId}` | بله | بله | 200, 404 |
| PieceController | POST | `/api/pieces/{id}/welding-station/complete` | بله | بله | 200 |
| PieceController | DELETE | `/api/pieces/{id}/stage-history/{historyId}` | بله | بله | 204, 403 |
| PieceController | DELETE | `/api/pieces/{id}/substage-history/{historyId}` | بله | بله | 204 |
| PieceController | GET | `/api/pieces/all` | بله | بله | 200, 401 |
| PieceController | GET | `/api/pieces/search` | بله | بله | 200 |
| PieceController | GET | `/api/pieces/tracking` | بله | بله | 200 |
| PieceController | GET | `/api/pieces/tracking/summary` | بله | بله | 200 |
| PieceController | PATCH | `/api/pieces/{id}/description` | بله | بله | 200 |
| PieceDefinitionController | POST | `/api/piece-definitions` | بله | بله | 201, 409 |
| PieceDefinitionController | GET | `/api/piece-definitions` | بله | بله | 200 |
| PieceDefinitionController | GET | `/api/piece-definitions/{id}` | بله | بله | 200, 404 |
| PieceDefinitionController | GET | `/api/piece-definitions/{id}/status-report` | بله | بله | 200 |
| PieceDefinitionController | POST | `/api/piece-definitions/{id}/archive-pieces` | بله | بله | 200 |
| PieceDefinitionController | PUT | `/api/piece-definitions/{id}` | بله | بله | 200 |
| PieceDefinitionController | POST | `/api/piece-definitions/{id}/drawing` | بله | بله | 200, 400 |
| PieceDefinitionController | GET | `/api/piece-definitions/{id}/drawing` | بله | بله | 200, 404 |
| PieceDefinitionController | DELETE | `/api/piece-definitions/{id}/drawing` | بله | بله | 200 |
| PieceDefinitionController | DELETE | `/api/piece-definitions/{id}` | بله | بله | 204, 409 |
| PieceNoteController | POST | `/api/pieces/{pieceId}/notes` | بله | بله | 201 |
| PieceNoteController | GET | `/api/pieces/{pieceId}/notes` | بله | بله | 200 |
| PieceNoteController | DELETE | `/api/notes/{id}` | بله | بله | 204, 403 |
| ProjectController | POST | `/api/projects` | بله | بله | 201, 400, 403, 409 |
| ProjectController | GET | `/api/projects` | بله | بله | 200, 401 |
| ProjectController | GET | `/api/projects/{id}` | بله | بله | 200, 404 |
| ProjectController | PUT | `/api/projects/{id}` | بله | بله | 200 |
| ProjectController | DELETE | `/api/projects/{id}` | بله | بله | 204 |
| ProjectController | POST | `/api/projects/{id}/archive-pieces` | بله | بله | 200 |
| RadiographyDefectController | POST | `/api/pieces/{pieceId}/radiography-defects` | بله | بله | 201, 400 |
| RadiographyDefectController | GET | `/api/pieces/{pieceId}/radiography-defects` | بله | بله | 200 |
| RadiographyDefectController | PATCH | `/api/radiography-defects/{id}` | بله | بله | 200, 400 |
| RadiographyDefectController | DELETE | `/api/radiography-defects/{id}` | بله | بله | 204 |
| RadiographyDefectController | PATCH | `/api/radiography-defects/{id}/confirm-welding` | بله | بله | 200 |
| RadiographyReportController | GET | `/api/piece-definitions/{pieceDefinitionId}/radiography-templates` | بله | بله | 200 |
| RadiographyReportController | POST | `/api/piece-definitions/{pieceDefinitionId}/radiography-templates` | بله | بله | 201 |
| RadiographyReportController | PUT | `/api/radiography-templates/{templateId}` | بله | بله | 200 |
| RadiographyReportController | DELETE | `/api/radiography-templates/{templateId}` | بله | بله | 204 |
| RadiographyReportController | GET | `/api/pieces/{pieceId}/radiography-reports` | بله | بله | 200 |
| RadiographyReportController | PUT | `/api/pieces/{pieceId}/radiography-reports` | بله | بله | 200, 400 |
| RadiographyReportController | DELETE | `/api/radiography-reports/{reportId}` | بله | بله | 204 |
| RadiographyWorkflowController | GET | `/api/radiography/queue` | بله | بله | 200 |
| RadiographyWorkflowController | GET | `/api/radiography/interpreters` | بله | بله | 200 |
| RadiographyWorkflowController | GET | `/api/radiography/pieces/{pieceId}/workflow` | بله | بله | 200 |
| RadiographyWorkflowController | POST | `/api/radiography/pieces/{pieceId}/workflow/assign` | بله | بله | 201, 400 |
| RadiographyWorkflowController | GET | `/api/radiography/workflows/{workflowId}` | بله | بله | 200, 404 |
| RadiographyWorkflowController | POST | `/api/radiography/workflows/{workflowId}/reassign` | بله | بله | 200 |
| RadiographyWorkflowController | POST | `/api/radiography/workflows/{workflowId}/cancel` | بله | بله | 200 |
| RadiographyWorkflowController | POST | `/api/radiography/workflows/{workflowId}/interpreter-approve` | بله | بله | 200 |
| RadiographyWorkflowController | POST | `/api/radiography/workflows/{workflowId}/manager-approve` | بله | بله | 200, 400 |
| RadiographyWorkflowController | POST | `/api/radiography/workflows/{workflowId}/manager-reject` | بله | بله | 200 |
| RadiographyWorkflowController | GET | `/api/radiography/workflows/{workflowId}/versions/{versionId}/document` | بله | بله | 200, 404 |
| TaskCollaborationController | GET | `/api/tasks/{taskId}/comments` | بله | بله | 200, 404 |
| TaskCollaborationController | POST | `/api/tasks/{taskId}/comments` | بله | بله | 201 |
| TaskCollaborationController | PUT | `/api/tasks/{taskId}/comments/{commentId}` | بله | بله | 200, 404 |
| TaskCollaborationController | DELETE | `/api/tasks/{taskId}/comments/{commentId}` | بله | بله | 204 |
| TaskCollaborationController | GET | `/api/tasks/{taskId}/checklist` | بله | بله | 200 |
| TaskCollaborationController | POST | `/api/tasks/{taskId}/checklist` | بله | بله | 201 |
| TaskCollaborationController | PATCH | `/api/tasks/{taskId}/checklist/{itemId}` | بله | بله | 200, 404 |
| TaskCollaborationController | DELETE | `/api/tasks/{taskId}/checklist/{itemId}` | بله | بله | 200 |
| TaskCollaborationController | GET | `/api/tasks/{taskId}/activities` | بله | بله | 200 |
| TaskController | GET | `/api/tasks` | بله | بله | 200, 401 |
| TaskController | GET | `/api/tasks/{id}` | بله | بله | 200, 404 |
| TaskController | POST | `/api/tasks` | بله | بله | 201 |
| TaskController | PUT | `/api/tasks/{id}` | بله | بله | 200, 403 |
| TaskController | PATCH | `/api/tasks/{id}/status` | بله | بله | 200 |
| TaskController | PATCH | `/api/tasks/{id}/priority` | بله | بله | 200 |
| TaskController | PATCH | `/api/tasks/{id}/deadline` | بله | بله | 200 |
| TaskController | PATCH | `/api/tasks/{id}/assignee` | بله | بله | 200 |
| TaskController | DELETE | `/api/tasks/{id}` | بله | بله | 200 |
| TaskController | GET | `/api/tasks/assignees` | بله | بله | 200 |
| TaskController | GET | `/api/tasks/lookup/parts` | بله | بله | 200 |
| TensileTestController | GET | `/api/pieces/{pieceId}/tensile-tests` | بله | بله | 200 |
| TensileTestController | POST | `/api/pieces/{pieceId}/tensile-tests` | بله | بله | 201, 403 |
| TensileTestController | PUT | `/api/tensile-tests/{tensileTestId}` | بله | بله | 200, 400 |
| TensileTestController | DELETE | `/api/tensile-tests/{tensileTestId}` | بله | بله | 204 |
| TensileTestController | PUT | `/api/tensile-tests/{tensileTestId}/disposition` | بله | بله | 200 |
| TensileTestController | GET | `/api/tensile-tests/sample-labels` | بله | بله | 200 |
| TensileTestController | GET | `/api/tensile-tests/pending-review` | بله | بله | 200 |
| UserController | POST | `/api/users` | بله | بله | 201, 403 |
| UserController | GET | `/api/users` | بله | بله | 200, 401, 403 |
| UserController | DELETE | `/api/users/{id}` | بله | بله | 204, 409 |
| UserController | PATCH | `/api/users/{id}/permissions` | بله | بله | 200, 403 |
| UserController | PATCH | `/api/users/{id}/password` | بله | بله | 204, 401 |
| UserController | PATCH | `/api/users/me/password` | بله | بله | 204, 401 |
| UserProfileController | GET | `/api/users/{userId}/profile` | بله | بله | 200, 404 |
| UserProfileController | PATCH | `/api/users/{userId}/profile` | بله | بله | 200, 403 |
| UserProfileController | GET | `/api/users/{userId}/tasks` | بله | بله | 200 |
| UserSignatureController | GET | `/api/profile/signature` | بله | بله | 200 |
| UserSignatureController | PUT | `/api/profile/signature` | بله | بله | 200, 400 |
| UserSignatureController | DELETE | `/api/profile/signature` | بله | بله | 204 |
| WeldingMapMarkController | GET | `/api/pieces/{pieceId}/welding-marks` | بله | بله | 200 |
| WeldingMapMarkController | GET | `/api/piece-definitions/{definitionId}/welding-marks` | بله | بله | 200 |
| WeldingMapMarkController | POST | `/api/pieces/{pieceId}/welding-marks` | بله | بله | 201, 400 |
| WeldingMapMarkController | DELETE | `/api/welding-marks/{id}` | بله | بله | 204 |
