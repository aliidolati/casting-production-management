const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function browser() {
    const nodes = new Map();
    const element = id => {
        if (!nodes.has(id)) nodes.set(id, { value: '', innerHTML: '', textContent: '', style: {}, listeners: {},
            addEventListener(type, fn) { this.listeners[type] = fn; }, querySelectorAll() { return []; },
            classList: { add() {}, remove() {}, toggle() {} } });
        return nodes.get(id);
    };
    const context = vm.createContext({ document: { getElementById: element, addEventListener() {} },
        window: { addEventListener() {} }, localStorage: { getItem() {}, setItem() {}, removeItem() {} },
        console, escapeHtml: value => String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;') });
    for (const file of ['auth.js','users.js']) vm.runInContext(fs.readFileSync(path.join(__dirname,'../../main/resources/static/js',file),'utf8'),context);
    return source => vm.runInContext(source,context);
}
test('only manager has implicit grants; admin uses explicit grants and cannot open user administration', () => {
    const run = browser();
    run('AuthState.user = { role: "ADMIN", permissions: ["MANAGE_PROJECTS"] }');
    assert.equal(run('hasPermission("MANAGE_PROJECTS")'),true);
    assert.equal(run('hasPermission("VIEW_DASHBOARD")'),false);
    assert.equal(run('shouldShowButton({adminOnly: true})'),false);
    run('AuthState.user = { role: "MANAGER", permissions: [] }');
    assert.equal(run('hasPermission("VIEW_DASHBOARD")'),true);
    assert.equal(run('shouldShowButton({adminOnly: true})'),true);
    assert.equal(run('roleLabel("ADMIN")'),'ادمین');
    assert.equal(run('roleLabel("MANAGER")'),'مدیر');
});
test('permission chooser supports admin and operator while manager needs no checkboxes', () => {
    const run = browser();
    assert.equal(run('(() => { const el={style:{}}; togglePermissionsSection({value:"ADMIN"},el); return el.style.display; })()'),'block');
    assert.equal(run('(() => { const el={style:{}}; togglePermissionsSection({value:"OPERATOR"},el); return el.style.display; })()'),'block');
    assert.equal(run('(() => { const el={style:{}}; togglePermissionsSection({value:"MANAGER"},el); return el.style.display; })()'),'none');
});

test('account form sends name, role and explicit admin grants to the same user and escapes input', async () => {
    const run = browser();
    run(`
        AuthState.user = { id: "manager-id", role: "MANAGER", permissions: [] };
        cachedPermissionCatalog = [{code: "VIEW_DASHBOARD", label: "مشاهده داشبورد"}];
        globalThis.openModal = (title, markup) => { globalThis.modalMarkup = markup; };
        globalThis.closeModal = () => {};
        globalThis.showToast = () => {};
        globalThis.showTableLoader = () => {};
        globalThis.UserAPI = { getAll: async () => [], updateAccount: async (id, data) => {
            globalThis.savedRequest = {id, data}; return {id, ...data};
        }};
    `);
    await run(`openEditUserAccount({id:"target-id",username:"target",fullName:'<img src=x onerror=alert(1)>',role:"OPERATOR",permissions:[]})`);
    assert.equal(run('modalMarkup.includes("<img src=x")'),false);
    assert.equal(run(`modalMarkup.includes('value="ADMIN"')`),true);
    run('document.getElementById("account-full-name").value="نام اصلاح‌شده";document.getElementById("account-role").value="ADMIN";');
    await run(`document.getElementById("form-edit-account").listeners.submit({preventDefault(){}, target:{
        querySelector(){return {disabled:false}}, querySelectorAll(){return [{value:"VIEW_DASHBOARD"}]}
    }})`);
    assert.equal(run('savedRequest.id'),'target-id');
    assert.equal(run('savedRequest.data.role'),'ADMIN');
    assert.equal(run('savedRequest.data.fullName'),'نام اصلاح‌شده');
    assert.equal(run('JSON.stringify(savedRequest.data.permissions)'),'["VIEW_DASHBOARD"]');
});
