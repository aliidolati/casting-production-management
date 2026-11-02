/** Audited cancellation and shared heat-batch controls. */
function openOperationCancellation(title,welding,onSave){
    openNestedModal(title,`<form id="operation-cancel-form"><label for="operation-cancel-reason">دلیل لغو</label><textarea id="operation-cancel-reason" required maxlength="2000"></textarea>
        ${welding?'<label><input id="operation-no-work" type="checkbox" required> تأیید می‌کنم پس از اعزام، هیچ عملیات جوشکاری انجام نشده است.</label>':'<p>ارتباط فعال قطعات آزاد و وظایف باز بسته می‌شوند؛ سوابق باقی می‌مانند.</p>'}
        <div id="operation-cancel-error" class="form-error"></div><button type="submit" class="btn btn-danger">تأیید لغو</button></form>`);
    document.getElementById('operation-cancel-form').onsubmit=async e=>{
        e.preventDefault();const reason=document.getElementById('operation-cancel-reason').value.trim();if(!reason)return;
        if(welding&&!document.getElementById('operation-no-work').checked)return;
        const button=e.submitter;if(button)button.disabled=true;
        try{await onSave(reason);closeNestedModal();showToast('عملیات لغو شد');}
        catch(error){const box=document.getElementById('operation-cancel-error');if(box){box.textContent=error.message;box.style.display='block';}}
        finally{if(button)button.disabled=false;}
    };
}
function heatMemberList(members){return `<ul>${members.map(p=>`<li>${escapeHtml(pieceRefLabel(p))} — ${escapeHtml(p.pieceName||'')}</li>`).join('')||'<li>محموله بدون قطعه</li>'}</ul>`;}
function heatCycleSummary(cycle){return `تاریخ: ${escapeHtml(formatDate(cycle.treatmentDate))} · دما: ${escapeHtml(String(cycle.temperatureC??'—'))} · مدت: ${escapeHtml(String(cycle.durationHours??'—'))} · کوره: ${escapeHtml(FURNACE_SIZE_LABELS[cycle.furnaceSize]||'—')}${cycle.notes?`<p>${escapeHtml(cycle.notes)}</p>`:''}`;}
async function openHeatBatchArchive(filters={}){
    let page=0;const current={...filters};
    openModal('آرشیو محموله‌های کوره',`<form id="batch-archive-filter" class="form-grid"><label>کد دقیق محموله<input id="batch-filter-code"></label><label>از تاریخ<input id="batch-filter-from" readonly></label><label>تا تاریخ<input id="batch-filter-to" readonly></label><label>کوره<select id="batch-filter-furnace"><option value="">همه</option>${Object.entries(FURNACE_SIZE_LABELS).map(([v,label])=>`<option value="${v}">${label}</option>`).join('')}</select></label><button class="btn btn-primary">جست‌وجو</button></form><div id="batch-archive-results"></div>`,{size:'lg'});
    document.getElementById('batch-filter-code').value=current.code||'';document.getElementById('batch-filter-furnace').value=current.furnace||'';
    for(const key of ['from','to']){const picker=attachPersianDatePicker(document.getElementById(`batch-filter-${key}`),{onSelect:({jy,jm,jd})=>{current[key]=jalaliToIsoDateString(jy,jm,jd);},onClear:()=>{delete current[key];}});if(current[key])picker.setIsoDate(current[key]);}
    async function load(){try{const result=await OperationAPI.batches({...current,page,size:25});const box=document.getElementById('batch-archive-results');if(!box)return;
        box.innerHTML=`<table class="data-table"><thead><tr><th>محموله</th><th>تاریخ</th><th>کوره</th><th>قطعات</th><th></th></tr></thead><tbody>${result.items.map(b=>`<tr><td>${escapeHtml(b.batchCode)}</td><td>${escapeHtml(formatDate(b.treatmentDate))}</td><td>${escapeHtml(b.furnaceSizeLabel||'—')}</td><td>${b.pieceCount}</td><td><button type="button" class="btn btn-secondary" data-batch-open="${b.id}">جزئیات</button></td></tr>`).join('')}</tbody></table><button class="btn btn-secondary" id="batch-page-prev" ${page===0?'disabled':''}>قبلی</button><button class="btn btn-secondary" id="batch-page-next" ${page+1>=result.totalPages?'disabled':''}>بعدی</button>`;
        box.querySelectorAll('[data-batch-open]').forEach(btn=>btn.onclick=()=>openHeatBatchDetails(btn.dataset.batchOpen,()=>openHeatBatchArchive(current)));
        box.querySelector('#batch-page-prev').onclick=()=>{page--;load();};box.querySelector('#batch-page-next').onclick=()=>{page++;load();};
    }catch(e){showToast(e.message,'error');}}
    document.getElementById('batch-archive-filter').onsubmit=e=>{e.preventDefault();current.code=document.getElementById('batch-filter-code').value.trim();current.furnace=document.getElementById('batch-filter-furnace').value;page=0;load();};await load();
}
async function openHeatBatchDetails(id,onBack=()=>openHeatBatchArchive()){
    try{const b=await OperationAPI.batch(id);openModal(`محموله ${b.batchCode}`,`<p>${heatCycleSummary(b)}</p><h3>قطعات عضو (${b.pieces.length})</h3>${heatMemberList(b.pieces)}<button class="btn btn-secondary" id="batch-details-back">بازگشت</button>${hasPermission('EDIT_HEAT_TREATMENT_BATCH')?'<button class="btn btn-primary" id="batch-details-edit">اصلاح سیکل مشترک</button><button class="btn btn-secondary" id="batch-details-history">سابقه اصلاح سیکل</button>':''}<div id="batch-history"></div>`,{size:'lg'});
        document.getElementById('batch-details-back').onclick=onBack;
        document.getElementById('batch-details-edit')?.addEventListener('click',()=>editHeatBatchCycle(b));
        document.getElementById('batch-details-history')?.addEventListener('click',()=>loadHeatBatchHistory(id,0));
    }catch(e){showToast(e.message,'error');}
}
function editHeatBatchCycle(b){
    let date=b.treatmentDate;
    openModal('اصلاح سیکل مشترک',`<form id="batch-cycle-form"><label>تاریخ<input id="batch-edit-date" required readonly></label><label>دما<input id="batch-edit-temp" type="number" step="any" value="${escapeHtml(String(b.temperatureC??''))}"></label><label>مدت<input id="batch-edit-duration" type="number" min="0" step="any" value="${escapeHtml(String(b.durationHours??''))}"></label><label>کوره<select id="batch-edit-furnace"><option value="">انتخاب کنید</option>${Object.entries(FURNACE_SIZE_LABELS).map(([v,label])=>`<option value="${v}" ${b.furnaceSize===v?'selected':''}>${label}</option>`).join('')}</select></label><label>یادداشت محموله<textarea id="batch-edit-notes">${escapeHtml(b.notes||'')}</textarea></label><button class="btn btn-primary">مرور قطعات متأثر</button></form>`);
    attachPersianDatePicker(document.getElementById('batch-edit-date'),{onSelect:({jy,jm,jd})=>{date=jalaliToIsoDateString(jy,jm,jd);},onClear:()=>{date=null;}}).setIsoDate(date);
    document.getElementById('batch-cycle-form').onsubmit=async e=>{e.preventDefault();if(!date)return;
        const numeric=id=>{const v=document.getElementById(id).value;return v===''?null:Number(v);};
        await previewHeatBatchChange(b.id,{treatmentDate:date,temperatureC:numeric('batch-edit-temp'),durationHours:numeric('batch-edit-duration'),furnaceSize:document.getElementById('batch-edit-furnace').value||null,notes:document.getElementById('batch-edit-notes').value.trim()||null},b.version);
    };
}
async function previewHeatBatchChange(id,changes,version){
    try{const b=await OperationAPI.batch(id);if(b.version!==version)throw Error('سیکل تغییر کرده است؛ محموله را دوباره باز کنید');
        openModal('تأیید اصلاح سیکل مشترک',`<h3>قبل</h3><p>${heatCycleSummary(b)}</p><h3>بعد</h3><p>${heatCycleSummary(changes)}</p><h3>تمام قطعات متأثر (${b.pieces.length})</h3>${heatMemberList(b.pieces)}<form id="batch-confirm-form"><label>دلیل اصلاح<textarea id="batch-confirm-reason" required maxlength="2000"></textarea></label><div class="form-error" id="batch-confirm-error"></div><button class="btn btn-primary">ذخیره برای تمام این قطعات</button><button type="button" class="btn btn-secondary" id="batch-confirm-back">بازگشت</button></form>`,{size:'lg'});
        document.getElementById('batch-confirm-back').onclick=()=>openHeatBatchDetails(id);
        document.getElementById('batch-confirm-form').onsubmit=async e=>{e.preventDefault();const reason=document.getElementById('batch-confirm-reason').value.trim();if(!reason)return;const button=e.submitter;if(button)button.disabled=true;
            try{await HeatTreatmentAPI.updateBatch(id,{...changes,version:b.version,confirmedPieceIds:b.pieces.map(p=>p.pieceId),reason});showToast('سیکل اصلاح شد');await openHeatBatchDetails(id);}
            catch(error){const box=document.getElementById('batch-confirm-error');if(box){box.textContent=error.message;box.style.display='block';}}finally{if(button)button.disabled=false;}
        };
    }catch(e){showToast(e.message,'error');}
}
async function loadHeatBatchHistory(id,page){
    try{const result=await OperationAPI.batchHistory(id,page);const box=document.getElementById('batch-history');if(!box)return;
        box.innerHTML=result.items.map(e=>`<section class="note-row"><p>${escapeHtml(e.actorName)} — ${formatDateTime(e.occurredAt)}</p><p>${escapeHtml(e.reason)}</p><h4>قبل</h4>${heatCycleSummary(JSON.parse(e.beforeState))}<h4>بعد</h4>${heatCycleSummary(JSON.parse(e.afterState))}<details><summary>قطعات متأثر</summary>${heatMemberList(JSON.parse(e.affectedPieces))}</details></section>`).join('')||'<p>اصلاحی ثبت نشده است.</p>';
        box.innerHTML+=`<button class="btn btn-secondary" id="batch-history-prev" ${page===0?'disabled':''}>قبلی</button><button class="btn btn-secondary" id="batch-history-next" ${page+1>=result.totalPages?'disabled':''}>بعدی</button>`;
        box.querySelector('#batch-history-prev').onclick=()=>loadHeatBatchHistory(id,page-1);box.querySelector('#batch-history-next').onclick=()=>loadHeatBatchHistory(id,page+1);
    }catch(e){showToast(e.message,'error');}
}
