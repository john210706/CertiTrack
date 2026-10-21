const app = document.querySelector('#app');
const page = document.body.dataset.page;
const role = document.body.dataset.role;
let currentUser;
let csrfPromise;

const text = (tag, value, className) => {
  const element = document.createElement(tag);
  element.textContent = value ?? '';
  if (className) element.className = className;
  return element;
};
const badge = status => text('span', status.replaceAll('_', ' '), 'badge ' + status);
const date = value => value ? new Intl.DateTimeFormat(undefined, {dateStyle:'medium'}).format(new Date(value + 'T00:00:00')) : 'No expiry';
const home = user => '/' + user.role.toLowerCase() + '/dashboard.html';
const fields = form => Object.fromEntries(new FormData(form));
function notice(message = '', error = false, target = '#notice') {
  const node = document.querySelector(target);
  if (node) { node.textContent = message; node.className = 'notice' + (error ? ' error' : ''); }
}
async function api(path, {method = 'GET', body, raw = false} = {}) {
  const headers = {};
  if (!['GET','HEAD'].includes(method)) {
    csrfPromise ??= fetch('/api/auth/csrf', {credentials:'same-origin'}).then(async response => {
      if (!response.ok) throw new Error('Could not start a secure session. Please reload the page.');
      return response.json();
    }).catch(error => { csrfPromise = undefined; throw error; });
    headers['X-CSRF-TOKEN'] = (await csrfPromise).token;
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  let response;
  try {
    response = await fetch(path, {method, headers, credentials:'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body)});
  } catch { throw new Error('Cannot reach CertiTrack. Check your connection and try again.'); }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    if (response.status === 401 && role) { location.assign('/login.html'); }
    if (response.status === 403) csrfPromise = undefined;
    throw new Error(error.message || 'This request could not be completed. Please try again.');
  }
  if (raw) return response;
  return response.status === 204 ? null : response.json();
}
function formAction(form, handler) {
  form.addEventListener('submit', async event => {
    event.preventDefault();
    const button = form.querySelector('[type=submit]');
    button.disabled = true;
    notice();
    try { await handler(fields(form)); }
    catch (error) { notice(error.message, true); }
    finally { button.disabled = false; }
  });
}
function publicShell(content) {
  app.innerHTML = `<header class="public-header"><a class="brand" href="/"><span class="brand-mark">CT</span>CertiTrack</a>
    <nav aria-label="Main navigation"><a href="/verify.html">Verify a certificate</a><a class="button secondary" href="/login.html">Sign in ↗</a></nav></header>
    <main id="main" class="public-main">${content}<footer>CertiTrack · A clear record of every achievement.</footer></main>`;
}
function shell() {
  const nav = [['dashboard','Overview','dashboard.html'],['certificates','Certificates','certificates.html']];
  if (role !== 'student') nav.push(['issue','Issue certificate','issue-certificate.html']);
  if (role === 'admin') nav.push(['users','People & accounts','users.html'],['reports','Reports & history','reports.html']);
  app.innerHTML = `<div class="app-layout"><aside class="sidebar"><a class="brand" href="/"><span class="brand-mark">CT</span>CertiTrack</a>
    <p class="eyebrow">${role} workspace</p><nav aria-label="Workspace navigation"></nav>
    <div class="sidebar-bottom"><a href="/verify.html">Public verification ↗</a><p>One record. A lasting achievement.</p></div></aside>
    <div class="workspace"><header class="topbar"><span class="context">Certificate lifecycle management</span>
    <div class="profile"><span class="avatar"></span><span class="profile-name"></span><button class="link-button" id="logout">Sign out</button></div></header>
    <main id="main"><div id="notice" class="notice" role="status" aria-live="polite"></div><div id="content"></div></main></div></div>`;
  const menu = document.querySelector('.sidebar nav');
  for (const [key,label,path] of nav) {
    const link = text('a', label); link.href = '/' + role + '/' + path;
    if (key === page) link.setAttribute('aria-current','page');
    menu.append(link);
  }
  document.querySelector('.avatar').textContent = currentUser.fullName.slice(0,1);
  document.querySelector('.profile-name').textContent = currentUser.fullName;
  document.querySelector('#logout').onclick = async () => {
    try { await api('/api/auth/logout', {method:'POST'}); location.assign('/login.html'); }
    catch(error) { notice(error.message,true); }
  };
}
function heading(title, subtitle, action = '') {
  return `<div class="page-heading"><div><p class="eyebrow">Your workspace</p><h1>${title}</h1><p class="subtitle">${subtitle}</p></div>${action}</div>`;
}
const issueLink = () => role === 'student' ? '' : `<a class="button" href="/${role}/issue-certificate.html">+ Issue certificate</a>`;
function table(headers) {
  const wrap = document.createElement('div'); wrap.className = 'table-wrap';
  const table = document.createElement('table');
  const head = table.createTHead().insertRow();
  headers.forEach(label => { const th = text('th',label); th.scope='col'; head.append(th); });
  table.createTBody(); wrap.append(table);
  return wrap;
}
function pager(container, result, load) {
  const bar = document.createElement('div'); bar.className='pagination';
  const before = text('button','← Previous','secondary'); before.type='button'; before.disabled=result.page===0;
  const next = text('button','Next →','secondary'); next.type='button'; next.disabled=result.page+1>=result.totalPages;
  before.onclick=()=>load(result.page-1).catch(error=>notice(error.message,true));
  next.onclick=()=>load(result.page+1).catch(error=>notice(error.message,true));
  bar.append(before, text('span',result.totalElements ? 'Page '+(result.page+1)+' of '+result.totalPages+' · '+result.totalElements+' records' : 'No records'),next);
  container.append(bar);
}
async function certificateDetails(id, refresh) {
  try {
    const record = await api('/api/certificates/' + encodeURIComponent(id));
    const dialog = document.createElement('dialog');
    dialog.innerHTML=`<div class="dialog-head"><h2>Certificate details</h2><button class="secondary" aria-label="Close details">×</button></div>
      <div class="record"></div><div class="notice" id="dialog-notice" role="status"></div>`;
    dialog.querySelector('button').onclick=()=>dialog.close();
    fillRecord(dialog.querySelector('.record'),record);
    if(role!=='student' && record.status!=='REVOKED'){
      const form=document.createElement('form'); form.className='revoke-form';
      form.innerHTML=`<h3>Revoke this certificate</h3><p class="muted">Revocation is permanent. The reason is visible only in authorized certificate details.</p>
        <label for="reason">Reason for revocation</label><textarea id="reason" name="reason" required maxlength="1000"></textarea>
        <div class="actions"><button class="secondary danger" type="submit">Revoke permanently</button></div>`;
      form.onsubmit=async event=>{
        event.preventDefault();
        if(!confirm('Permanently revoke this certificate?')) return;
        const button=form.querySelector('button');button.disabled=true;
        try{await api('/api/certificates/'+encodeURIComponent(id)+'/revoke',{method:'POST',body:fields(form)});
          dialog.close();notice('Certificate revoked.');await refresh();}
        catch(error){notice(error.message,true,'#dialog-notice');button.disabled=false;}
      };
      dialog.append(form);
    }
    dialog.addEventListener('close',()=>dialog.remove());
    document.body.append(dialog);dialog.showModal();
  } catch(error){notice(error.message,true);}
}
function fillRecord(container, record) {
  container.append(badge(record.status));
  const details=document.createElement('dl');details.className='detail-grid';
  for(const [label,value] of [['Certificate ID',record.certificateId],['Certificate holder',record.holderName],
    ['Program',record.programName],['Certificate type',record.certificateType],['Organization',record.organizationName],
    ['Issued on',date(record.issueDate)],['Expires on',date(record.expiryDate)],
    ...(record.issuerName?[['Issued by',record.issuerName]]:[]),
    ...(record.revocationReason?[['Revocation reason',record.revocationReason]]:[])]) {
    const entry=document.createElement('div');entry.append(text('dt',label),text('dd',value));details.append(entry);
  }
  container.append(details);
}
function renderCertificates(container, records, refresh) {
  container.replaceChildren();
  if(!records.length){container.append(text('p','No certificates to show yet.','empty'));return;}
  const wrap=table(['Certificate / program','Holder','Issued','Status','']);const tbody=wrap.querySelector('tbody');
  for(const record of records){
    const row=tbody.insertRow();
    row.insertCell().append(text('span',record.certificateId,'mono'),text('span',record.programName,'subline'));
    row.insertCell().textContent=record.holderName;row.insertCell().textContent=date(record.issueDate);
    row.insertCell().append(badge(record.status));
    const button=text('button','View details','link-button');button.type='button';
    button.onclick=()=>certificateDetails(record.certificateId,refresh);row.insertCell().append(button);
  }
  container.append(wrap);
}
async function dashboard() {
  const content=document.querySelector('#content');
  content.innerHTML=heading('Overview','A clear view of your certificate records.',issueLink())+
    `<div class="stats" id="stats"></div><div class="panel"><div class="section-heading"><h2>Recent certificates</h2><a href="/${role}/certificates.html">View all →</a></div><div id="recent"></div></div><div id="extra" class="compact-stats"></div>`;
  async function load(){
    const [summary,records]=await Promise.all([api('/api/dashboard'),api('/api/certificates?size=5')]);
    const stats=document.querySelector('#stats');stats.replaceChildren();
    for(const [label,key] of [['Total certificates','totalCertificates'],['Valid','validCertificates'],['Expired','expiredCertificates'],['Revoked','revokedCertificates']]){
      const card=text('div','','stat');card.append(text('span',label,'stat-label'),text('strong',summary[key],'stat-value'));stats.append(card);
    }
    renderCertificates(document.querySelector('#recent'),records.content,load);
    const extra=document.querySelector('#extra');extra.replaceChildren();
    if(role==='admin') for(const [label,key] of [['Students','totalStudents'],['Issuers','totalIssuers'],['Verifications today','verificationAttemptsToday']]){
      const card=document.createElement('div');card.append(text('strong',summary[key]),text('span',label));extra.append(card);
    }
  } await load();
}
async function certificatesPage(){
  document.querySelector('#content').innerHTML=heading('Certificates',role==='student'?'Your achievements, all in one place.':'Manage and review your issued certificate records.',issueLink())+
    '<div class="panel"><div id="filters"></div><div id="cert-list"></div></div>';
  document.querySelector('#filters').innerHTML=`<form id="filter-form" class="filter-grid">
    <div class="search-field"><label for="search">Search certificates</label><input id="search" name="search" maxlength="200" placeholder="Certificate ID, student, or program"></div>
    <div><label for="status">Status</label><select id="status" name="status"><option value="">All statuses</option><option>VALID</option><option>EXPIRED</option><option>REVOKED</option></select></div>
    <div><label for="type">Certificate type</label><input id="type" name="certificateType" maxlength="100" placeholder="Any type"></div>
    <div><label for="year">Issue year</label><input id="year" name="issueYear" type="number" min="1000" max="9999" placeholder="Any year"></div>
    <button type="submit" class="primary">Apply</button></form><div class="actions"><button id="reset-filters" class="link-button" type="button">Clear filters</button></div>`;
  let currentPage=0;let sequence=0;let filters=new URLSearchParams();
  async function load(number=0){
    currentPage=number;const request=++sequence;
    const result=await api('/api/certificates?page='+number+'&'+filters);
    if(request!==sequence)return;
    const container=document.querySelector('#cert-list');
    renderCertificates(container,result.content,()=>load(currentPage));
    pager(container,result,load);
  }
  formAction(document.querySelector('#filter-form'),async data=>{
    filters=new URLSearchParams(Object.entries(data).filter(([,value])=>value!==''));await load();
  });
  document.querySelector('#reset-filters').onclick=async()=>{
    document.querySelector('#filter-form').reset();filters=new URLSearchParams();
    try{await load();}catch(error){notice(error.message,true);}
  };
  if(role==='admin'){
    const download=text('button','Export matching records ↓','link-button');download.type='button';
    download.onclick=()=>downloadCsv(filters,download);
    document.querySelector('#filters .actions').append(download);
  }
  await load();
}

async function downloadCsv(filters,button){
  button.disabled=true;
  try{
    const response=await api('/api/admin/certificates/export?'+filters,{raw:true});
    const url=URL.createObjectURL(await response.blob());
    const link=text('a','Download CSV');link.href=url;link.download='certitrack-certificates.csv';
    document.body.append(link);link.click();link.remove();
    setTimeout(()=>URL.revokeObjectURL(url),10000);notice('CSV export downloaded.');
  }catch(error){notice(error.message,true);}finally{button.disabled=false;}
}

async function reportsPage(){
  document.querySelector('#content').innerHTML=heading('Reports & history','Understand your certificate records and review public verification activity.')+
    `<div class="split-section"><section class="panel"><h2>Certificate status</h2><div id="distribution"></div></section>
    <section class="panel"><h2>Institution summary</h2><div id="report-counts" class="compact-stats"></div>
    <p class="muted">Download certificate records for offline review. Exports include the current effective status and no private account details.</p>
    <button id="export-all" class="secondary" type="button">Download all certificates ↓</button></section></div>
    <section class="panel"><div class="section-heading"><h2>Verification history</h2><span class="muted">Newest attempts first</span></div><div id="logs"></div></section>`;
  const summary=await api('/api/admin/reports/summary');
  for(const [label,key] of [['Valid','validCertificates'],['Expired','expiredCertificates'],['Revoked','revokedCertificates']]){
    const row=text('div','','report-line');const heading=text('div','','section-heading');
    heading.append(text('span',label),text('strong',summary[key]));
    const bar=document.createElement('progress');bar.max=Math.max(summary.totalCertificates,1);bar.value=summary[key];
    bar.setAttribute('aria-label',label+' certificates');row.append(heading,bar);document.querySelector('#distribution').append(row);
  }
  for(const [label,key] of [['Certificates','totalCertificates'],['Students','totalStudents'],['Issuers','totalIssuers'],['Verifications today','verificationAttemptsToday']]){
    const item=document.createElement('div');item.append(text('strong',summary[key]),text('span',label));
    document.querySelector('#report-counts').append(item);
  }
  const button=document.querySelector('#export-all');button.onclick=()=>downloadCsv(new URLSearchParams(),button);
  async function logs(number=0){
    const result=await api('/api/admin/verification-logs?page='+number);
    const container=document.querySelector('#logs');container.replaceChildren();
    if(!result.content.length){container.append(text('p','No verification attempts yet.','empty'));return;}
    const wrap=table(['Searched ID','Outcome','Verified at']);const tbody=wrap.querySelector('tbody');
    for(const entry of result.content){
      const row=tbody.insertRow();row.insertCell().textContent=entry.searchedCertificateId||'(empty ID)';
      row.insertCell().append(badge(entry.result));
      row.insertCell().textContent=entry.verifiedAt.replace('T',' ').slice(0,19);
    }
    container.append(wrap);pager(container,result,logs);
  }await logs();
}
async function issuePage(){
  document.querySelector('#content').innerHTML=heading('Issue a certificate','Recognize an achievement with a permanent, verifiable record.')+
    `<div class="two-col"><form id="issue-form" class="panel form-grid">
    <div class="field full"><label for="studentId">Student</label><select id="studentId" name="studentId" required><option value="">Choose a student</option></select><div id="student-pages"></div></div>
    <div class="field"><label for="programCode">Program code</label><input id="programCode" name="programCode" required pattern="[A-Za-z0-9]{2,12}" maxlength="12" placeholder="JAVA"><small class="help">2–12 letters or numbers</small></div>
    <div class="field"><label for="certificateType">Certificate type</label><input id="certificateType" name="certificateType" required maxlength="100" placeholder="Completion"></div>
    <div class="field full"><label for="programName">Program name</label><input id="programName" name="programName" required maxlength="200" placeholder="Advanced Java Programming"></div>
    <div class="field full"><label for="organizationName">Organization</label><input id="organizationName" name="organizationName" required maxlength="200" placeholder="Your institution"></div>
    <div class="field"><label for="issueDate">Issue date</label><input id="issueDate" name="issueDate" type="date" required min="1000-01-01" max="9999-12-31"></div>
    <div class="field"><label for="expiryDate">Expiry date <span class="muted">(optional)</span></label><input id="expiryDate" name="expiryDate" type="date" min="1000-01-01" max="9999-12-31"><small class="help">Leave empty for a certificate that does not expire.</small></div>
    <div class="full actions"><button class="primary" type="submit">Issue certificate</button><a href="/${role}/certificates.html">Cancel</a></div></form>
    <aside class="note-panel"><p class="eyebrow">Before you issue</p><h2>A record that lasts.</h2><ol><li>Confirm the student and program details.</li><li>A unique certificate ID is generated automatically.</li><li>The student can view it immediately. Anyone with its ID can verify it.</li></ol><p>Issued details cannot be edited. If a record is incorrect, revoke it and issue a replacement.</p></aside></div>`;
  const form=document.querySelector('#issue-form');
  form.issueDate.value=new Date().toLocaleDateString('en-CA');
  async function students(number=0){
    const result=await api('/api/students?page='+number);
    form.studentId.replaceChildren(new Option('Choose a student',''));
    for(const student of result.content) {
      const option=new Option(student.fullName+' · '+student.registrationNumber+(student.enabled?'':' (disabled)'),student.id);
      option.disabled=!student.enabled;form.studentId.append(option);
    }
    const pages=document.querySelector('#student-pages');pages.replaceChildren();pager(pages,result,students);
  }
  await students();
  formAction(form,async data=>{
    data.studentId=Number(data.studentId);data.expiryDate=data.expiryDate||null;
    const result=await api('/api/certificates',{method:'POST',body:data});
    form.reset();form.issueDate.value=new Date().toLocaleDateString('en-CA');
    notice('Certificate issued: '+result.certificateId);
    document.querySelector('#notice').append(document.createTextNode(' '));
    const link=text('a','View certificate');link.href='/'+role+'/certificates.html';
    document.querySelector('#notice').append(link);window.scrollTo({top:0,behavior:'auto'});
  });
}
async function usersPage(){
  document.querySelector('#content').innerHTML=heading('People & accounts','Create accounts and manage access to CertiTrack.')+
    `<div class="panel"><h2>Create an account</h2><form id="user-form" class="form-grid">
    <div class="field"><label for="fullName">Full name</label><input id="fullName" name="fullName" required maxlength="120" autocomplete="name"></div>
    <div class="field"><label for="email">Email address</label><input id="email" name="email" type="email" required maxlength="254" autocomplete="off"></div>
    <div class="field"><label for="password">Initial password</label><input id="password" name="password" type="password" required minlength="8" maxlength="72" autocomplete="new-password"><small class="help">8–72 characters, at most 72 UTF-8 bytes.</small></div>
    <div class="field"><label for="user-role">Role</label><select id="user-role" name="role"><option>STUDENT</option><option>ISSUER</option><option>ADMIN</option></select></div>
    <div class="field"><label for="registrationNumber">Registration number</label><input id="registrationNumber" name="registrationNumber" required maxlength="50"><small class="help">Required for student accounts.</small></div>
    <div class="field actions"><button type="submit" class="primary">Create account</button></div></form></div>
    <div class="panel"><div class="section-heading"><h2>All accounts</h2><span class="muted">Passwords are never displayed</span></div><div id="user-list"></div></div>`;
  const form=document.querySelector('#user-form');
  const roleField=form.elements.namedItem('role');
  roleField.onchange=()=>form.registrationNumber.required=roleField.value==='STUDENT';
  let currentPage=0;
  async function load(number=0){
    currentPage=number;
    const result=await api('/api/admin/users?page='+number);
    const container=document.querySelector('#user-list');container.replaceChildren();
    const wrap=table(['Person','Role','Registration','Access','']);const tbody=wrap.querySelector('tbody');
    for(const person of result.content){
      const row=tbody.insertRow();row.insertCell().append(text('strong',person.fullName),text('span',person.email,'subline'));
      row.insertCell().textContent=person.role;row.insertCell().textContent=person.registrationNumber||'—';
      row.insertCell().textContent=person.enabled?'Enabled':'Disabled';
      const button=text('button',person.enabled?'Disable':'Enable','link-button');button.type='button';
      button.disabled=person.id===currentUser.id;
      if(button.disabled)button.title='You cannot disable your current account here.';
      button.onclick=async()=>{
        if(!confirm((person.enabled?'Disable':'Enable')+' access for '+person.fullName+'?'))return;
        button.disabled=true;
        try{await api('/api/admin/users/'+person.id+'/enabled',{method:'PATCH',body:{enabled:!person.enabled}});await load(currentPage);}
        catch(error){notice(error.message,true);button.disabled=false;}
      };
      row.insertCell().append(button);
    }container.append(wrap);pager(container,result,load);
  }
  formAction(form,async data=>{await api('/api/admin/users',{method:'POST',body:data});form.reset();form.registrationNumber.required=true;notice('Account created.');await load();});
  await load();
}
function homePage(){
  publicShell(`<section class="hero"><div><p class="eyebrow">Achievement, on record.</p><h1>Credentials you can<br>count on.</h1><p class="muted">Issue, manage, and verify academic certificates in one place. Clear records for institutions. Lasting recognition for students.</p>
    <div class="actions"><a class="button" href="/verify.html">Verify a certificate →</a><a class="button secondary" href="/login.html">Go to your workspace</a></div><p class="tagline">No account needed for public verification.</p></div>
    <div class="hero-art" aria-label="Illustration of a certificate"><div class="paper"><div class="seal">✓</div><p class="eyebrow">A milestone, recognized</p><h2>Certificate of Achievement</h2><div class="paper-line"></div><div class="paper-line short"></div><div class="paper-line"></div><small>Issued. Recorded. Verifiable.</small></div><p class="artifact-label">Every achievement deserves a clear record.</p></div></section>
    <section class="feature-grid" aria-label="How CertiTrack works"><div><p class="feature-number">01 / ISSUE</p><h2>Recognize the achievement.</h2><p>Create certificate records with a unique, readable ID.</p></div><div><p class="feature-number">02 / MANAGE</p><h2>Keep the record current.</h2><p>Track validity, expiry, and revocation throughout its lifecycle.</p></div><div><p class="feature-number">03 / VERIFY</p><h2>Check with confidence.</h2><p>Look up an ID to see its current status and public details.</p></div></section>`);
}
function loginPage(){
  publicShell(`<div class="auth-layout"><div class="auth-aside"><p class="eyebrow">Your CertiTrack workspace</p><h1>Welcome back.</h1><p class="muted">Sign in to manage your certificate records or view your achievements.</p><p class="muted">Only checking a certificate? <a href="/verify.html">Use public verification →</a></p></div>
    <div class="panel"><h2>Sign in</h2><div id="notice" role="status" class="notice"></div><form id="login-form">
    <div class="field"><label for="email">Email address</label><input type="email" id="email" name="email" required maxlength="254" autocomplete="username" placeholder="you@institution.edu"></div>
    <div class="field"><label for="password">Password</label><input type="password" id="password" name="password" required maxlength="72" autocomplete="current-password"></div>
    <button class="primary" type="submit">Sign in to your workspace →</button></form><p class="tagline">Need an account or help signing in? Contact your institution's administrator.</p></div></div>`);
  formAction(document.querySelector('#login-form'),async data=>{
    const user=await api('/api/auth/login',{method:'POST',body:data});csrfPromise=undefined;
    document.querySelector('#login-form').reset();location.assign(home(user));
  });
}
function verifyPage(){
  publicShell(`<div class="verify-layout"><p class="eyebrow">Public verification</p><h1>Check a certificate.</h1><p class="muted">Enter the certificate ID exactly as it appears on your record. No sign-in required.</p>
    <div id="notice" role="status" class="notice"></div><div class="panel"><form id="verify-form" class="verify-form"><div class="field"><label for="certificateId">Certificate ID</label>
    <input id="certificateId" name="certificateId" required maxlength="100" placeholder="CERT-JAVA-2026-00142" spellcheck="false" autocomplete="off"></div><button type="submit" class="primary">Verify certificate →</button></form></div>
    <div id="verify-result" class="verify-result" aria-live="polite"></div><p class="tagline">Verification shows the current record held by the issuing institution. Private account details are never shared.</p></div>`);
  formAction(document.querySelector('#verify-form'),async data=>{
    const result=document.querySelector('#verify-result');result.replaceChildren();
    const record=await api('/api/public/verify/'+encodeURIComponent(data.certificateId));
    const panel=text('section','','panel');result.append(panel);
    if(['INVALID_FORMAT','NOT_FOUND'].includes(record.status)){
      panel.append(badge(record.status),text('h2',record.status==='NOT_FOUND'?'No matching certificate':'Check the certificate ID'),
        text('p',record.status==='NOT_FOUND'?'We could not find this ID. Check for typing errors or contact the issuer.':'Use the full ID, such as CERT-JAVA-2026-00142, including uppercase letters and hyphens.','muted'));
    }else fillRecord(panel,record);
  });
}
async function start(){
  if(page==='home')return homePage();
  if(page==='login')return loginPage();
  if(page==='verify')return verifyPage();
  try{
    currentUser=await api('/api/auth/me');
    if(currentUser.role.toLowerCase()!==role){location.replace(home(currentUser));return;}
    shell();
    await ({dashboard,certificates:certificatesPage,issue:issuePage,users:usersPage,reports:reportsPage}[page])();
  }catch(error){
    if(!document.querySelector('#notice')){app.replaceChildren(text('p',error.message,'notice error'));}
    else notice(error.message,true);
  }
}
start();
