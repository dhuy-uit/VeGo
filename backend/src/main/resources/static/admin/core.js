/* VéGo web admin — phần lõi: định dạng, gọi API, phiên đăng nhập, khung trang, router, thành phần dùng chung. */
'use strict';

const VG = window.VG = {};
const API_BASE = '/api/v1';
const TZ = 'Asia/Ho_Chi_Minh';

// ---------- Tiện ích ----------
const $ = (sel, el = document) => el.querySelector(sel);
const $$ = (sel, el = document) => Array.from(el.querySelectorAll(sel));
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const icon = (name, cls = '') => `<span class="ic ${cls}">${(window.ICONS || {})[name] || ''}</span>`;

const nf = new Intl.NumberFormat('vi-VN');
const fmt = {
  num: (n) => nf.format(Math.round(n ?? 0)),
  money: (n) => nf.format(Math.round(n ?? 0)) + 'đ',
  pct: (n) => (n ?? 0).toLocaleString('vi-VN', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + '%',
  millions: (n) => {
    const m = (n ?? 0) / 1e6;
    return m.toLocaleString('vi-VN', { maximumFractionDigits: m >= 100 ? 0 : 1 });
  },
  /** 'yyyy-mm-dd' -> 'dd/mm/yyyy' */
  date: (iso) => (iso ? iso.slice(8, 10) + '/' + iso.slice(5, 7) + '/' + iso.slice(0, 4) : ''),
  dm: (iso) => (iso ? iso.slice(8, 10) + '/' + iso.slice(5, 7) : ''),
  time: (instant) => new Date(instant).toLocaleTimeString('vi-VN', { timeZone: TZ, hour: '2-digit', minute: '2-digit', hour12: false }),
  dateOf: (instant) => new Intl.DateTimeFormat('en-CA', { timeZone: TZ }).format(new Date(instant)),
  dateTime: (instant) => fmt.time(instant) + ' · ' + fmt.date(fmt.dateOf(instant)),
  duration: (min) => {
    if (min == null) return '—';
    const h = Math.floor(min / 60), m = min % 60;
    return (h ? h + ' giờ' : '') + (m ? (h ? ' ' : '') + m + ' phút' : '') || '0 phút';
  },
  ago: (instant) => {
    const s = Math.max(0, (Date.now() - new Date(instant).getTime()) / 1000);
    if (s < 60) return 'vừa xong';
    if (s < 3600) return Math.floor(s / 60) + ' phút trước';
    if (s < 86400) return Math.floor(s / 3600) + ' giờ trước';
    return Math.floor(s / 86400) + ' ngày trước';
  },
  change: (cur, prev) => {
    if (!prev) return { text: 'Chưa có số liệu kỳ trước', cls: 'flat' };
    const d = ((cur - prev) / prev) * 100;
    const t = Math.abs(d).toLocaleString('vi-VN', { maximumFractionDigits: 1 }) + '% so với kỳ trước';
    if (Math.abs(d) < 0.05) return { text: 'Bằng kỳ trước', cls: 'flat' };
    return d > 0 ? { text: '↑ ' + t, cls: '' } : { text: '↓ ' + t, cls: 'down' };
  },
};
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: TZ }).format(new Date());
const addDays = (iso, n) => {
  const d = new Date(iso + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};
const BUS_TYPES = { LIMOUSINE: 'Limousine', SLEEPER: 'Giường nằm', SEATER: 'Ghế ngồi' };
const LICENSES = {
  D1: 'D1 · xe đến 16 chỗ', D2: 'D2 · xe đến 29 chỗ', D: 'D · xe trên 29 chỗ',
  D1E: 'D1E · D1 kéo rơ moóc', D2E: 'D2E · D2 kéo rơ moóc', DE: 'DE · D kéo rơ moóc',
};

// ---------- Lưu trữ trong trình duyệt (có thể bị chặn) ----------
const store = {
  get(k) { try { return JSON.parse(localStorage.getItem(k)); } catch (e) { return null; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* bỏ qua */ } },
  del(k) { try { localStorage.removeItem(k); } catch (e) { /* bỏ qua */ } },
};
const SESSION_KEY = 'vego.admin.session';
let session = store.get(SESSION_KEY);

function setSession(auth) {
  session = { accessToken: auth.accessToken, refreshToken: auth.refreshToken, user: auth.user };
  store.set(SESSION_KEY, session);
}

// ---------- Gọi API ----------
class ApiError extends Error {
  constructor(status, body) {
    super((body && body.message) || (status === 0 ? 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.' : 'Máy chủ gặp lỗi (' + status + '). Thử lại sau ít phút.'));
    this.status = status;
    this.code = body && body.code;
    this.fields = (body && body.fieldErrors) || {};
  }
}

function qs(query) {
  if (!query) return '';
  const p = new URLSearchParams();
  Object.entries(query).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') p.set(k, v); });
  const s = p.toString();
  return s ? '?' + s : '';
}

let refreshing = null;
async function refreshToken() {
  if (!session || !session.refreshToken) return false;
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const r = await fetch(API_BASE + '/auth/refresh', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: session.refreshToken }),
        });
        if (!r.ok) return false;
        setSession(await r.json());
        return true;
      } catch (e) {
        return false;
      } finally {
        setTimeout(() => { refreshing = null; }, 0);
      }
    })();
  }
  return refreshing;
}

async function api(path, opts = {}, retry = true) {
  const headers = { Accept: 'application/json' };
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  if (session && session.accessToken && !opts.anonymous) headers.Authorization = 'Bearer ' + session.accessToken;
  let res;
  try {
    res = await fetch(API_BASE + path + qs(opts.query), {
      method: opts.method || 'GET', headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    });
  } catch (e) {
    throw new ApiError(0, null);
  }
  if (res.status === 401 && retry && !opts.anonymous) {
    if (await refreshToken()) return api(path, opts, false);
    signOut(true);
    throw new ApiError(401, { message: 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại' });
  }
  if (res.status === 204) return null;
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (e) { data = null; }
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}

async function signOut(expired) {
  const rt = session && session.refreshToken;
  session = null;
  store.del(SESSION_KEY);
  if (rt && !expired) {
    fetch(API_BASE + '/auth/logout', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: rt }) }).catch(() => {});
  }
  shellMounted = false;
  if (expired) VG.flash = 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại';
  location.hash = '#/login';
}

// ---------- Thông báo nổi, hộp thoại ----------
function toast(message, kind = '') {
  const zone = $('#toasts');
  const el = document.createElement('div');
  el.className = 'toast ' + kind;
  el.innerHTML = icon(kind === 'error' ? 'circle-alert' : 'circle-check', 'ic-18') + '<span>' + esc(message) + '</span>';
  zone.appendChild(el);
  setTimeout(() => el.remove(), kind === 'error' ? 6000 : 3500);
}

function confirmDialog({ title, message, ok = 'Xác nhận', danger = false }) {
  return new Promise((resolve) => {
    const ov = document.createElement('div');
    ov.className = 'overlay';
    ov.innerHTML = `<div class="dialog" role="dialog" aria-modal="true" aria-labelledby="dlg-t">
      <h3 id="dlg-t">${esc(title)}</h3><p>${esc(message)}</p>
      <div class="actions"><button class="btn" data-v="0">Hủy</button>
      <button class="btn ${danger ? 'primary' : 'primary'}" data-v="1" style="${danger ? 'background:var(--danger);border-color:var(--danger)' : ''}">${esc(ok)}</button></div></div>`;
    const close = (v) => { ov.remove(); document.removeEventListener('keydown', onKey); resolve(v); };
    const onKey = (e) => { if (e.key === 'Escape') close(false); };
    ov.addEventListener('click', (e) => {
      if (e.target === ov) close(false);
      const b = e.target.closest('button[data-v]');
      if (b) close(b.dataset.v === '1');
    });
    document.addEventListener('keydown', onKey);
    document.body.appendChild(ov);
    $('button[data-v="1"]', ov).focus();
  });
}

function download(filename, rows) {
  const csv = rows.map((r) => r.map((c) => {
    const s = String(c ?? '');
    return /[",\n;]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
  }).join(',')).join('\r\n');
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 500);
}

// ---------- Thành phần giao diện ----------
const ui = {
  pageHead: (title, sub, actions = '') => `<div class="page-head"><div><h1>${esc(title)}</h1><p>${esc(sub || 'VéGo · Quản lý vận hành, kết nối hành trình')}</p></div><div class="actions">${actions}</div></div>`,
  stat: (label, ic, value, note, noteCls = '') => `<div class="card stat"><div class="label"><span>${esc(label)}</span>${icon(ic)}</div>
      <div class="value">${value}</div><div class="foot ${noteCls}">${note}</div></div>`,
  note: (title, text, kind = '', ic = 'info') => `<div class="note ${kind}">${icon(ic, 'ic-18')}<div><b>${esc(title)}</b>${text}</div></div>`,
  badge: (text, kind = '') => `<span class="badge ${kind}">${esc(text)}</span>`,
  empty: (title, text = '') => `<div class="empty"><b>${esc(title)}</b>${esc(text)}</div>`,
  /** Bảng có phân trang. cols: [{h, w?}], rows: html các <tr>. */
  table: (cols, rowsHtml, footer = '') => `<div class="table-wrap"><div class="table-scroll"><table class="data">
      <thead><tr>${cols.map((c) => `<th${c.w ? ` style="width:${c.w}"` : ''}>${esc(c.h)}</th>`).join('')}</tr></thead>
      <tbody>${rowsHtml}</tbody></table></div>${footer}</div>`,
  pager: (text, page, pages) => `<div class="pager"><span>${text}</span><div class="ctl">
      <button data-page="${page - 1}" ${page <= 1 ? 'disabled' : ''} aria-label="Trang trước">${icon('chevron-left', 'ic-18')}</button>
      ${ui.badge(String(page))}
      <button data-page="${page + 1}" ${page >= pages ? 'disabled' : ''} aria-label="Trang sau">${icon('chevron-right', 'ic-18')}</button></div></div>`,
  field: (label, inner, opts = {}) => `<div class="field ${opts.wide ? 'wide' : ''}" data-field="${opts.name || ''}">
      <label${opts.for ? ` for="${opts.for}"` : ''}>${esc(label)}</label>${inner}${opts.hint ? `<span class="hint">${opts.hint}</span>` : ''}<span class="err"></span></div>`,
  input: (name, value = '', attrs = '') => `<input class="input" id="f-${name}" name="${name}" value="${esc(value)}" ${attrs}>`,
  select: (name, options, value, attrs = '') => `<select class="select" id="f-${name}" name="${name}" ${attrs}>${options.map(([v, t]) => `<option value="${esc(v)}" ${String(v) === String(value ?? '') ? 'selected' : ''}>${esc(t)}</option>`).join('')}</select>`,
  password: (name, attrs = '') => `<div class="with-icon"><input class="input" type="password" id="f-${name}" name="${name}" ${attrs}>
      <button type="button" class="trail" data-toggle-pw aria-label="Hiện mật khẩu">${icon('eye-off', 'ic-18')}</button></div>`,
  bars: (points, opts = {}) => {
    const max = Math.max(1, ...points.map((p) => p.value));
    const dense = points.length > 16;
    return `<div class="bars ${dense ? 'dense' : ''}" role="img" aria-label="${esc(opts.label || 'Biểu đồ cột')}">${points.map((p, i) => {
      const h = Math.max(1.5, (p.value / max) * 100);
      const hi = opts.highlight != null ? i === opts.highlight : false;
      return `<div class="bar ${hi ? 'hi' : ''}" title="${esc(p.title || '')}"><small class="v">${esc(p.text)}</small><i style="height:calc(${h}% - 40px)"></i><small>${esc(p.label)}</small></div>`;
    }).join('')}</div>`;
  },
  donut: (pct) => {
    const r = 70, c = 2 * Math.PI * r, v = Math.max(0, Math.min(100, pct || 0));
    return `<div class="donut"><svg viewBox="0 0 164 164" aria-hidden="true"><circle cx="82" cy="82" r="${r}" fill="none" stroke="#e4f4f1" stroke-width="20"/>
      <circle cx="82" cy="82" r="${r}" fill="none" stroke="#007f78" stroke-width="20" stroke-dasharray="${(c * v) / 100} ${c}" /></svg><b>${Math.round(v)}%</b></div>`;
  },
  runState: (run) => {
    if (run.state === 'RUNNING') return '<span class="state run">Đang chạy</span>';
    if (run.state === 'DONE') return '<span class="state muted">Đã hoàn thành</span>';
    return '<span class="state ok">Sắp chạy</span>';
  },
};

/** Gắn lỗi theo ô nhập từ ApiError.fields; trả về true nếu có lỗi theo ô. */
function showFieldErrors(form, err) {
  $$('.field', form).forEach((f) => { f.classList.remove('invalid'); $('.err', f).textContent = ''; });
  let any = false;
  Object.entries((err && err.fields) || {}).forEach(([name, msg]) => {
    const f = $(`.field[data-field="${name}"]`, form);
    if (f) { f.classList.add('invalid'); $('.err', f).textContent = msg; any = true; }
  });
  const first = $('.field.invalid input, .field.invalid select, .field.invalid textarea', form);
  if (first) first.focus();
  return any;
}

function formData(form) {
  const o = {};
  new FormData(form).forEach((v, k) => { o[k] = typeof v === 'string' ? v.trim() : v; });
  return o;
}

function bindPasswordToggles(root) {
  $$('[data-toggle-pw]', root).forEach((b) => b.addEventListener('click', () => {
    const inp = b.previousElementSibling;
    const show = inp.type === 'password';
    inp.type = show ? 'text' : 'password';
    b.innerHTML = icon(show ? 'eye' : 'eye-off', 'ic-18');
    b.setAttribute('aria-label', show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
  }));
}

/** Nút đang xử lý: khóa + đổi chữ, trả về hàm khôi phục. */
function busy(btn, text = 'Đang lưu…') {
  if (!btn) return () => {};
  const old = btn.innerHTML;
  btn.disabled = true;
  btn.textContent = text;
  return () => { btn.disabled = false; btn.innerHTML = old; };
}

// ---------- Khung trang ----------
const NAV = [
  ['overview', 'Tổng quan', 'grid'],
  ['vehicles', 'Quản lý xe', 'bus'],
  ['routes', 'Tuyến & chuyến', 'route'],
  ['schedule', 'Lập lịch chuyến', 'calendar-clock'],
  ['drivers', 'Tài xế', 'users'],
  ['top-routes', 'Top tuyến bán chạy', 'trending-up'],
  ['suggestions', 'Báo cáo đề xuất', 'sparkles'],
  ['password', 'Đổi mật khẩu', 'key'],
];
let shellMounted = false;

function mountShell() {
  const u = (session && session.user) || {};
  $('#root').innerHTML = `<div class="app" id="app">
    <aside class="sidebar" id="sidebar">
      <a class="logo" href="#/overview" aria-label="VéGo — Tổng quan"><img src="logo.webp" alt="VéGo"></a>
      ${ui.badge('KHÔNG GIAN QUẢN TRỊ')}
      <nav class="nav" aria-label="Danh mục quản trị">${NAV.map(([k, t, ic]) => `<a href="#/${k}" data-nav="${k}">${icon(ic)}${esc(t)}</a>`).join('')}</nav>
      <div class="side-card"><b>Vận hành thông suốt</b><span>Một nơi để quản lý mọi hành trình.</span></div>
      <button class="logout" id="logout">${icon('log-out')}Đăng xuất</button>
    </aside>
    <div class="workspace">
      <header class="toolbar">
        <div style="display:flex;align-items:center;gap:8px">
          <button class="icon-btn menu-btn" id="menu-btn" aria-label="Mở danh mục">${icon('menu')}</button>
          <div class="crumbs" id="crumbs">Quản trị</div>
        </div>
        <div class="account">
          <button class="icon-btn" id="bell" aria-label="Hoạt động gần đây">${icon('bell')}<span class="dot hidden" id="bell-dot"></span></button>
          <div class="who"><b>${esc(u.fullName || 'Quản trị viên')}</b><span>Quản trị viên</span></div>
          <button class="icon-btn" id="acc-btn" aria-label="Tài khoản">${icon('chevron-down', 'ic-16')}</button>
          <div class="popover hidden" id="pop"></div>
        </div>
      </header>
      <main class="content" id="main"></main>
    </div>
  </div>`;
  $('#logout').addEventListener('click', async () => {
    if (await confirmDialog({ title: 'Đăng xuất?', message: 'Bạn sẽ cần đăng nhập lại để vào không gian quản trị.', ok: 'Đăng xuất', danger: true })) signOut(false);
  });
  $('#menu-btn').addEventListener('click', () => $('#app').classList.toggle('nav-open'));
  $('#sidebar').addEventListener('click', (e) => { if (e.target.closest('a')) $('#app').classList.remove('nav-open'); });
  const pop = $('#pop');
  const closePop = () => pop.classList.add('hidden');
  document.addEventListener('click', (e) => { if (!e.target.closest('.account')) closePop(); });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closePop(); });
  $('#acc-btn').addEventListener('click', () => {
    const open = pop.classList.contains('hidden') || pop.dataset.kind !== 'acc';
    pop.dataset.kind = 'acc';
    pop.innerHTML = `<h4>${esc(u.email || '')}</h4>
      <button class="item" data-go="#/password">Đổi mật khẩu</button>
      <button class="item" data-go="logout" style="color:var(--danger)">Đăng xuất</button>`;
    pop.classList.toggle('hidden', !open);
  });
  pop.addEventListener('click', (e) => {
    const b = e.target.closest('[data-go]');
    if (!b) return;
    closePop();
    if (b.dataset.go === 'logout') $('#logout').click(); else location.hash = b.dataset.go;
  });
  $('#bell').addEventListener('click', async () => {
    const open = pop.classList.contains('hidden') || pop.dataset.kind !== 'bell';
    if (!open) { closePop(); return; }
    pop.dataset.kind = 'bell';
    pop.innerHTML = '<h4>Hoạt động gần đây</h4><div class="loading">Đang tải…</div>';
    pop.classList.remove('hidden');
    try {
      const feed = await api('/admin/activity');
      store.set('vego.admin.seenActivity', Date.now());
      $('#bell-dot').classList.add('hidden');
      pop.innerHTML = '<h4>Hoạt động gần đây</h4>' + (feed.items.length ? feed.items.map((a) => `<div class="item"><b>${esc(a.title)}</b><small>${esc(a.detail || '')}</small><small>${fmt.ago(a.at)}</small></div>`).join('')
        : ui.empty('Chưa có hoạt động mới', 'Vé mới, hủy vé, đánh giá và khách đăng ký trong 7 ngày sẽ hiện ở đây.'));
    } catch (err) {
      pop.innerHTML = '<h4>Hoạt động gần đây</h4>' + ui.empty('Không tải được', err.message);
    }
  });
  shellMounted = true;
  refreshBell();
}

async function refreshBell() {
  try {
    const feed = await api('/admin/activity');
    const seen = store.get('vego.admin.seenActivity') || 0;
    const fresh = feed.items.filter((a) => new Date(a.at).getTime() > seen).length;
    const dot = $('#bell-dot');
    if (dot) { dot.textContent = fresh > 9 ? '9+' : String(fresh); dot.classList.toggle('hidden', fresh === 0); }
  } catch (e) { /* chuông chỉ là phụ */ }
}

// ---------- Router ----------
const ROUTES = [];
VG.route = (pattern, view, meta = {}) => {
  const keys = [];
  const re = new RegExp('^' + pattern.replace(/:(\w+)/g, (_, k) => { keys.push(k); return '([^/]+)'; }) + '$');
  ROUTES.push({ re, keys, view, meta });
};

let renderSeq = 0;
async function render() {
  const raw = location.hash.replace(/^#/, '') || '/overview';
  const [path, query] = raw.split('?');
  const q = Object.fromEntries(new URLSearchParams(query || ''));
  let match = null;
  for (const r of ROUTES) {
    const m = path.match(r.re);
    if (m) { match = { r, params: Object.fromEntries(r.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])])) }; break; }
  }
  if (!match) { location.replace('#/overview'); return; }
  const { r, params } = match;
  const seq = ++renderSeq;

  if (r.meta.public) {
    if (session && session.user && session.user.role === 'ADMIN' && path === '/login') { location.replace('#/overview'); return; }
    shellMounted = false;
    await r.view({ root: $('#root'), params, q, seq, alive: () => seq === renderSeq });
    return;
  }
  if (!session || !session.accessToken) { location.replace('#/login'); return; }
  if (!shellMounted) mountShell();
  $$('[data-nav]').forEach((a) => a.classList.toggle('active', a.dataset.nav === r.meta.nav));
  $('#crumbs').innerHTML = 'Quản trị / ' + esc(r.meta.crumb || '');
  document.title = (r.meta.crumb ? r.meta.crumb + ' · ' : '') + 'VéGo Quản trị';
  const main = $('#main');
  main.innerHTML = '<div class="loading">Đang tải…</div>';
  window.scrollTo(0, 0);
  try {
    await r.view({ root: main, params, q, seq, alive: () => seq === renderSeq });
  } catch (err) {
    if (seq !== renderSeq) return;
    if (err.status === 403) {
      main.innerHTML = ui.empty('Tài khoản không có quyền quản trị', 'Đăng nhập bằng tài khoản quản trị viên để tiếp tục.');
    } else if (err.status !== 401) {
      main.innerHTML = `<div class="card">${ui.empty('Không tải được dữ liệu', err.message)}<div style="text-align:center"><button class="btn" id="retry">Thử lại</button></div></div>`;
      $('#retry').addEventListener('click', render);
    }
  }
}

/** Cập nhật query trên URL mà không tải lại màn hình. */
function setQuery(q) {
  const [path] = (location.hash.replace(/^#/, '') || '/overview').split('?');
  history.replaceState(null, '', '#' + path + qs(q));
}

window.addEventListener('hashchange', render);
window.addEventListener('DOMContentLoaded', () => setTimeout(render, 0));

Object.assign(VG, { $, $$, esc, icon, fmt, today, addDays, api, ApiError, toast, confirmDialog, download, ui, showFieldErrors, formData, bindPasswordToggles, busy, setSession, signOut, setQuery, BUS_TYPES, LICENSES, getSession: () => session, refreshBell });
