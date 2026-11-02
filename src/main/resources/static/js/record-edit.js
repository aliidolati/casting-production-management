/** Shared correction form; fields are explicitly selected by each caller. */
function openRecordEdit(kind, record, fields, onDone) {
    openModal('اصلاح اطلاعات', `<form id="record-edit-form">
        ${fields.map(f => `<div class="form-group"><label for="edit-${f.key}">${escapeHtml(f.label)}</label>${f.options
            ? `<select id="edit-${f.key}" data-edit-key="${f.key}">${f.options.map(o=>`<option value="${escapeHtml(o.value)}" ${String(record[f.key] ?? '') === String(o.value) ? 'selected' : ''}>${escapeHtml(o.label)}</option>`).join('')}</select>`
            : `<textarea id="edit-${f.key}" data-edit-key="${f.key}" rows="2" ${f.required ? 'required' : ''} ${f.max ? `maxlength="${f.max}"` : ''}>${escapeHtml(String(record[f.key] ?? ''))}</textarea>`}</div>`).join('')}
        <p class="form-hint">مقدار قبلی و مشخصات اصلاح در سابقه نگهداری می‌شود.</p>
        <div id="record-edit-error" class="form-error"></div>
        <div class="form-actions"><button class="btn btn-primary" type="submit">ذخیره اصلاحات</button><button class="btn btn-secondary" id="record-edit-back" type="button">بازگشت</button><button class="btn btn-secondary" id="record-edit-history" type="button">سابقه اصلاحات</button></div>
    </form><div id="record-edit-history-list"></div>`);
    document.getElementById('record-edit-back').addEventListener('click',onDone);
    document.getElementById('record-edit-history').addEventListener('click',()=>loadRecordEditHistory(kind,record.id,0));
    document.getElementById('record-edit-form').addEventListener('submit', async e => {
        e.preventDefault();const button=e.submitter; if(button)button.disabled=true;
        try {
            const body={};
            for(const f of fields){const raw=document.getElementById(`edit-${f.key}`).value.trim();
                if(f.number && raw !== '' && !Number.isFinite(Number(raw))) throw Error('مقدار عددی معتبر وارد کنید');
                body[f.key]=raw === '' ? null : f.number ? Number(raw) : raw;
            }
            await RecordEditAPI.update(kind,record.id,body);showToast('اصلاحات ذخیره شد');await onDone();
        } catch(error){const box=document.getElementById('record-edit-error');if(box){box.textContent=error.message;box.style.display='block';}}
        finally{if(button)button.disabled=false;}
    });
}
function formatRecordEditSnapshot(raw){
    const labels={content:'متن یادداشت',xPercent:'مختصات افقی',yPercent:'مختصات عمودی',defectType:'نوع عیب',note:'توضیح',treatmentStage:'مرحله عملیات',testPlateLabel:'شماره پلیت',testPlateHardnessHb:'سختی HB',details:'توضیحات',tensileDispositionId:'تعیین‌تکلیف مرتبط',name:'نام ماده',category:'دسته',active:'فعال'};
    try{return Object.entries(JSON.parse(raw)).map(([key,value])=>`<div><strong>${escapeHtml(labels[key]||key)}:</strong> ${escapeHtml(value == null ? '—' : typeof value === 'boolean' ? (value?'بله':'خیر') : String(value))}</div>`).join('');}catch{return escapeHtml(raw);}
}
async function loadRecordEditHistory(kind,id,page){
    try{const result=await RecordEditAPI.history(kind,id,page);const box=document.getElementById('record-edit-history-list');if(!box)return;
        box.innerHTML=result.items.map(e=>`<div class="note-row"><p>${escapeHtml(e.actorUsername)} — ${formatDateTime(e.occurredAt)}</p><div>قبل: <pre dir="auto">${formatRecordEditSnapshot(e.beforeState)}</pre></div><div>بعد: <pre dir="auto">${formatRecordEditSnapshot(e.afterState)}</pre></div></div>`).join('')||'<p>اصلاحی ثبت نشده است.</p>';
        box.innerHTML+=`<button type="button" class="btn btn-secondary" id="edit-history-prev" ${page===0?'disabled':''}>قبلی</button><button type="button" class="btn btn-secondary" id="edit-history-next" ${page+1>=result.totalPages?'disabled':''}>بعدی</button>`;
        box.querySelector('#edit-history-prev').onclick=()=>loadRecordEditHistory(kind,id,page-1);box.querySelector('#edit-history-next').onclick=()=>loadRecordEditHistory(kind,id,page+1);
    }catch(e){showToast(e.message,'error');}
}
