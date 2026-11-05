/** Immutable drawings, version-bound marks and catalog deletion preview. */
function marksForDrawingRevision(marks,id){return marks.filter(m=>(m.drawingRevisionId||null)===(id||null));}
function drawingRevisionSelector(result,selected,id){
    const options=result.items.map(v=>`<option value="${v.id}" ${v.id===selected?'selected':''}>نسخه ${v.revisionNumber}${v.current?' — جاری':''}</option>`).join('');
    return `<label for="${id}">نسخه نقشه</label><select id="${id}">${!selected?'<option value="" selected>نقشه جاری پیشین</option>':''}${selected&&!result.items.some(v=>v.id===selected)?`<option value="${selected}" selected>نسخه انتخاب‌شده</option>`:''}${options}</select><p class="form-hint">فقط نشان‌های مربوط به همین نسخه نمایش داده می‌شوند.</p>`;
}
async function openDrawingRevisionHistory(id,page=0){
    try{const result=await PieceDefinitionAPI.drawingRevisions(id,page);openModal('نسخه‌های نقشه قطعه',`<p>نسخه‌های قبلی نگهداری می‌شوند. تاریخ یا ثبت‌کننده نامشخص برای نقشه‌های قدیمی حدس زده نمی‌شود.</p><button type="button" class="btn btn-secondary" id="drawing-view-current">مشاهده نقشه جاری</button><table class="data-table"><thead><tr><th>نسخه</th><th>فایل</th><th>ثبت‌کننده</th><th>تاریخ</th><th></th></tr></thead><tbody>${result.items.map(v=>`<tr><td>${v.revisionNumber}${v.current?' — جاری':''}</td><td>${escapeHtml(v.originalName||'—')}</td><td>${escapeHtml(v.uploadedBy||'نامشخص')}</td><td>${v.uploadedAt?formatDateTime(v.uploadedAt):'نامشخص (نقشه پیشین)'}</td><td><button type="button" class="btn btn-secondary" data-drawing-view="${v.id}">مشاهده / دریافت</button><button type="button" class="btn btn-secondary" data-drawing-marks="${v.id}">نشان‌های جوش این نسخه</button>${hasPermission('MANAGE_PIECE_DEFINITIONS')?`<button type="button" class="btn btn-primary" data-drawing-restore="${v.id}">بازیابی به‌عنوان نسخه جدید</button>`:''}</td></tr>`).join('')}</tbody></table>${result.items.length?'':'<p>هنوز نسخه ثبت‌شده‌ای وجود ندارد؛ نقشه پیشین هنگام اولین تغییر به سابقه منتقل می‌شود.</p>'}<button type="button" class="btn btn-secondary" id="drawing-prev" ${page===0?'disabled':''}>قبلی</button><button type="button" class="btn btn-secondary" id="drawing-next" ${page+1>=result.totalPages?'disabled':''}>بعدی</button>`,{size:'lg'});
        document.getElementById('drawing-view-current').onclick=()=>showDrawingRevision(id,null);
        document.getElementById('drawing-prev').onclick=()=>openDrawingRevisionHistory(id,page-1);document.getElementById('drawing-next').onclick=()=>openDrawingRevisionHistory(id,page+1);
        document.querySelectorAll('[data-drawing-view]').forEach(b=>b.onclick=()=>showDrawingRevision(id,b.dataset.drawingView));
        document.querySelectorAll('[data-drawing-marks]').forEach(b=>b.onclick=()=>openAggregateWeldingMapViewer(id,'نسخه پیشین','',b.dataset.drawingMarks));
        document.querySelectorAll('[data-drawing-restore]').forEach(b=>b.onclick=async()=>{if(!confirm('این نقشه به‌عنوان نسخه جدید جاری شود؟'))return;try{await PieceDefinitionAPI.restoreDrawing(id,b.dataset.drawingRestore);await openDrawingRevisionHistory(id);}catch(e){showToast(e.message,'error');}});
    }catch(e){showToast(e.message,'error');}
}
let drawingRevisionPreviewUrl=null;
function revokeDrawingRevisionPreview(){if(drawingRevisionPreviewUrl)URL.revokeObjectURL(drawingRevisionPreviewUrl);drawingRevisionPreviewUrl=null;}
async function showDrawingRevision(id,revision){
    try{const blob=revision?await PieceDefinitionAPI.revisionBlob(id,revision):await PieceDefinitionAPI.getDrawingBlob(id);if(drawingRevisionPreviewUrl)URL.revokeObjectURL(drawingRevisionPreviewUrl);drawingRevisionPreviewUrl=URL.createObjectURL(blob);
        openModal('نمایش نقشه',`<img style="max-width:100%" src="${drawingRevisionPreviewUrl}" alt="نقشه قطعه"><a class="btn btn-secondary" href="${drawingRevisionPreviewUrl}" download="drawing">دریافت فایل</a><button type="button" class="btn btn-secondary" id="drawing-preview-back">بازگشت به نسخه‌ها</button>`,{size:'xl'});document.getElementById('drawing-preview-back').onclick=()=>{URL.revokeObjectURL(drawingRevisionPreviewUrl);drawingRevisionPreviewUrl=null;openDrawingRevisionHistory(id);};
    }catch(e){showToast(e.message,'error');}
}
async function openCatalogDeletionPreview(kind,id,onDeleted){
    const api=kind==='projects'?ProjectAPI:PieceDefinitionAPI;
    try{const impact=await api.deletionPreview(id);openModal('مرور اثر حذف',`<h3>${escapeHtml(impact.label)}</h3><p>حذف این ساختار و وابستگی‌های قابل حذف آن دائمی است. موارد مشخص‌شده مانع حذف هستند.</p><table class="data-table"><thead><tr><th>نوع وابستگی</th><th>تعداد</th><th>وضعیت</th></tr></thead><tbody>${impact.impacts.map(x=>`<tr><td>${escapeHtml(x.label)}</td><td>${x.count}</td><td>${x.blocksDeletion&&x.count>0?'مانع حذف':'—'}</td></tr>`).join('')}</tbody></table><form id="catalog-delete-confirm"><label><input type="checkbox" required> اثر حذف را بررسی کردم و تأیید می‌کنم.</label><div id="catalog-delete-error" class="form-error"></div><button class="btn btn-danger" ${impact.canDelete?'':'disabled'}>حذف نهایی</button><button type="button" class="btn btn-secondary" id="catalog-delete-cancel">انصراف</button></form>`,{size:'lg'});
        document.getElementById('catalog-delete-cancel').onclick=closeModal;
        document.getElementById('catalog-delete-confirm').onsubmit=async e=>{e.preventDefault();if(!impact.canDelete)return;const button=e.submitter;if(button)button.disabled=true;
            try{await api.delete(id,impact.confirmationToken);closeModal();showToast('حذف انجام شد');await onDeleted();}catch(error){const box=document.getElementById('catalog-delete-error');if(box){box.textContent=error.message;box.style.display='block';}}finally{if(button)button.disabled=false;}
        };
    }catch(e){showToast(e.message,'error');}
}
