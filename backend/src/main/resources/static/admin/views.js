/* VéGo web admin — các màn hình (theo Figma section 08–14). */
'use strict';
(() => {
  const { $, $$, esc, icon, fmt, today, addDays, api, toast, confirmDialog, download, ui, showFieldErrors,
    formData, bindPasswordToggles, busy, setSession, setQuery, BUS_TYPES, LICENSES } = window.VG;

  const vehicleState = {
    READY: ['Sẵn sàng', 'ok'], RUNNING: ['Đang chạy', 'run'], MAINTENANCE: ['Bảo trì', 'warn'],
  };
  const driverState = { READY: ['Sẵn sàng', 'ok'], RUNNING: ['Đang chạy', 'run'], OFF: ['Tạm nghỉ', 'warn'] };
  const stateCell = (map, s) => `<span class="state ${map[s] ? map[s][1] : ''}">${esc(map[s] ? map[s][0] : s)}</span>`;
  const runLabel = (r) => r ? `${esc(r.code)} · ${fmt.time(r.departAt)} ${fmt.dm(r.date)}` : '';
  const mapOptions = (list, value, placeholder) => (placeholder ? [['', placeholder]] : []).concat(list.map((o) => [o.id, o.label]));
  let optionsCache = null;
  async function options(force) {
    if (!optionsCache || force) optionsCache = await api('/admin/options');
    return optionsCache;
  }

  // =====================================================================
  // Đăng nhập, quên mật khẩu
  // =====================================================================

  function authLayout(formHtml) {
    return `<div class="auth">
      <section class="auth-side">
        <span class="logo"><img src="logo.webp" alt="VéGo"></span>
        <div style="display:flex;flex-direction:column;gap:22px;margin-top:auto">
          ${ui.badge('VÉGO DÀNH CHO NHÀ ĐIỀU HÀNH')}
          <h1>Mỗi hành trình tốt, bắt đầu từ bạn.</h1>
          <p>Quản lý đội xe, điều phối chuyến và theo dõi doanh thu trong một không gian thống nhất.</p>
        </div>
        <div class="route">${icon('bus')}<b>TP. Hồ Chí Minh → Đà Lạt</b><span>Vận hành chủ động. Khách hàng an tâm.</span></div>
        <footer style="margin-bottom:auto">© ${new Date().getFullYear()} VéGo · Hệ thống quản trị nội bộ</footer>
      </section>
      <section class="auth-form"><div class="box">${formHtml}</div></section>
    </div>`;
  }

  VG.route('/login', async ({ root }) => {
    document.title = 'Đăng nhập · VéGo Quản trị';
    const flash = VG.flash; VG.flash = null;
    root.innerHTML = authLayout(`
      ${ui.badge('QUẢN TRỊ VIÊN')}
      <h2>Đăng nhập quản trị</h2>
      <p class="lead">Chào mừng trở lại. Truy cập không gian điều hành VéGo bằng tài khoản được cấp.</p>
      ${flash ? ui.note('Cần đăng nhập lại', esc(flash), 'warn', 'circle-alert') : ''}
      <form id="login" class="stack" style="gap:20px" novalidate>
        ${ui.field('Email quản trị', `<div class="with-icon">${ui.input('account', '', 'type="email" autocomplete="username" placeholder="admin@vego.vn" required')}<span class="trail">${icon('mail', 'ic-18')}</span></div>`, { name: 'account', for: 'f-account' })}
        ${ui.field('Mật khẩu', ui.password('password', 'autocomplete="current-password" required'), { name: 'password', for: 'f-password' })}
        <a class="text-link" href="#/forgot">Quên mật khẩu?</a>
        <button class="btn primary block" type="submit">Đăng nhập</button>
      </form>
      ${ui.note('Chỉ dành cho nhân sự được cấp quyền', 'Mọi hoạt động quản trị được ghi nhận để bảo vệ dữ liệu vận hành.')}
      <p class="foot">Cần hỗ trợ? Liên hệ quản trị hệ thống của nhà xe.</p>`);
    bindPasswordToggles(root);
    const form = $('#login');
    $('#f-account').focus();
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const d = formData(form);
      const errs = {};
      if (!d.account) errs.account = 'Vui lòng nhập email quản trị';
      if (!d.password) errs.password = 'Vui lòng nhập mật khẩu';
      if (Object.keys(errs).length) { showFieldErrors(form, { fields: errs }); return; }
      const done = busy($('button[type=submit]', form), 'Đang đăng nhập…');
      try {
        const auth = await api('/auth/login', { method: 'POST', anonymous: true, body: { account: d.account, password: d.password, device: 'Web admin' } });
        if (!auth.user || auth.user.role !== 'ADMIN') {
          fetch('/api/v1/auth/logout', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: auth.refreshToken }) }).catch(() => {});
          showFieldErrors(form, { fields: { account: 'Tài khoản này không có quyền quản trị' } });
          done();
          return;
        }
        setSession(auth);
        location.hash = '#/overview';
      } catch (err) {
        done();
        if (!showFieldErrors(form, err)) showFieldErrors(form, { fields: { password: err.message } });
      }
    });
  }, { public: true });

  VG.route('/forgot', async ({ root }) => {
    document.title = 'Lấy lại mật khẩu · VéGo Quản trị';
    root.innerHTML = authLayout(`
      ${ui.badge('QUẢN TRỊ VIÊN')}
      <h2>Lấy lại mật khẩu</h2>
      ${icon('key', 'icon-tile')}
      <p class="lead">Nhập email quản trị. VéGo gửi link đặt lại mật khẩu, có hiệu lực trong 15 phút.</p>
      <form id="forgot" class="stack" style="gap:20px" novalidate>
        ${ui.field('Email quản trị', `<div class="with-icon">${ui.input('account', '', 'type="email" autocomplete="username" placeholder="admin@vego.vn" required')}<span class="trail">${icon('mail', 'ic-18')}</span></div>`, { name: 'account', for: 'f-account' })}
        <button class="btn primary block" type="submit">Gửi link khôi phục</button>
      </form>
      <div id="forgot-msg">${ui.note('Không nhận được email?', 'Kiểm tra thư mục Spam. Nếu máy chủ chưa cấu hình gửi email, liên hệ người quản lý server để lấy link trong nhật ký hệ thống.')}</div>
      <a class="btn block" href="#/login">Quay lại đăng nhập</a>`);
    const form = $('#forgot');
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const d = formData(form);
      if (!d.account) { showFieldErrors(form, { fields: { account: 'Vui lòng nhập email quản trị' } }); return; }
      const done = busy($('button[type=submit]', form), 'Đang gửi…');
      try {
        const r = await api('/auth/forgot-password', { method: 'POST', anonymous: true, body: { account: d.account } });
        $('#forgot-msg').innerHTML = ui.note('Đã gửi yêu cầu', esc(r && r.message ? r.message : 'Nếu email đúng, link đặt lại mật khẩu sẽ được gửi trong ít phút.'), '', 'circle-check');
      } catch (err) {
        if (!showFieldErrors(form, err)) showFieldErrors(form, { fields: { account: err.message } });
      } finally { done(); }
    });
  }, { public: true });

  // =====================================================================
  // Tổng quan
  // =====================================================================

  VG.route('/overview', async ({ root, q, alive }) => {
    const mode = (q.mode || 'MONTH').toUpperCase();
    const t = today();
    const date = q.date || t;
    const d = await api('/admin/stats/overview', { query: { mode, date } });
    if (!alive()) return;
    const year = Number(date.slice(0, 4));
    const month = Number(date.slice(5, 7));
    const years = []; for (let y = Number(t.slice(0, 4)); y >= Number(t.slice(0, 4)) - 2; y--) years.push([y, 'Năm ' + y]);
    const months = []; for (let m = 1; m <= 12; m++) months.push([m, 'Tháng ' + m]);
    const unitTitle = { HOUR: 'Doanh thu theo giờ khởi hành', DAY: 'Doanh thu theo ngày', MONTH: 'Doanh thu theo tháng' }[d.seriesUnit];
    const total = d.series.reduce((s, p) => s + p.revenue, 0);
    const lastIdx = (() => { for (let i = d.series.length - 1; i >= 0; i--) if (d.series[i].revenue > 0) return i; return -1; })();
    const rev = fmt.change(d.revenue, d.revenuePrev), tic = fmt.change(d.tickets, d.ticketsPrev);
    const range = d.from === d.to ? fmt.date(d.from) : fmt.date(d.from) + ' – ' + fmt.date(d.to);
    const top = d.topRoutes.slice(0, 3);
    root.innerHTML = `
      ${ui.pageHead('Tổng quan vận hành', null, '<button class="btn primary" id="export">Xuất báo cáo</button>')}
      <div class="filters">
        <div class="segment" role="tablist" aria-label="Kiểu thống kê">
          ${[['DAY', 'Ngày'], ['MONTH', 'Tháng'], ['YEAR', 'Năm']].map(([k, l]) => `<button role="tab" aria-selected="${mode === k}" class="${mode === k ? 'on' : ''}" data-mode="${k}">${l}</button>`).join('')}
        </div>
        <div class="search" aria-label="Khoảng thời gian">${icon('search', 'ic-18')}<span>${range}</span></div>
        ${mode === 'DAY' ? `<input class="input" type="date" id="pick-date" value="${date}" max="${t}" style="width:auto">` : ''}
        ${mode === 'MONTH' ? ui.select('month', months, month, 'aria-label="Tháng"') : ''}
        ${mode !== 'DAY' ? ui.select('year', years, year, 'aria-label="Năm"') : ''}
        ${d.includesDemo ? ui.badge('SỐ LIỆU MINH HỌA', 'orange') : ''}
      </div>
      <div class="stats">
        ${ui.stat('Doanh thu vé', 'wallet', fmt.money(d.revenue), rev.text, rev.cls)}
        ${ui.stat('Vé đã bán', 'ticket', fmt.num(d.tickets), tic.text, tic.cls)}
        ${ui.stat('Tỷ lệ lấp đầy', 'armchair', fmt.pct(d.fillRate), `${fmt.num(d.tickets)} / ${fmt.num(d.seats)} ghế mở bán`)}
        ${ui.stat('Chuyến đã vận hành', 'bus', fmt.num(d.trips), `Trung bình ${d.tripsPerDay.toLocaleString('vi-VN')} chuyến mỗi ngày`)}
      </div>
      <div class="split">
        <div class="card">
          <div class="card-head"><h3>${unitTitle}</h3>${ui.badge('Triệu đồng')}</div>
          <p class="sub">${range} · Tổng ${fmt.millions(total)} triệu đồng${d.includesDemo ? ' · Có dữ liệu minh họa' : ''}</p>
          ${ui.bars(d.series.map((p) => ({ value: p.revenue, text: fmt.millions(p.revenue), label: p.label, title: `${p.label}: ${fmt.money(p.revenue)} · ${fmt.num(p.tickets)} vé` })), { highlight: lastIdx, label: unitTitle })}
        </div>
        <div class="card">
          <h3>Tỷ lệ lấp đầy ghế</h3>
          ${ui.donut(d.fillRate)}
          <div class="kv"><div><span>Đã bán</span><b class="hl">${fmt.num(d.tickets)} ghế</b></div>
          <div><span>Còn trống</span><b>${fmt.num(Math.max(0, d.seats - d.tickets))} ghế</b></div></div>
          <p class="sub">Tổng ${fmt.num(d.seats)} ghế mở bán trên các chuyến đã khởi hành trong kỳ.</p>
        </div>
      </div>
      <div class="section-head"><h2>Top tuyến trong kỳ</h2><a class="btn" href="#/top-routes?from=${d.from}&to=${d.to}">Xem bảng xếp hạng</a></div>
      ${ui.table([{ h: 'Tuyến đường' }, { h: 'Vé bán' }, { h: 'Doanh thu' }, { h: 'Lấp đầy' }],
        top.length ? top.map((r) => `<tr><td class="strong">${esc(r.name)}</td><td>${fmt.num(r.tickets)}</td><td>${fmt.money(r.revenue)}</td><td>${fmt.pct(r.fillRate)}</td></tr>`).join('')
          : `<tr><td colspan="4">${ui.empty('Chưa có vé trong kỳ này', 'Chọn khoảng thời gian khác để xem số liệu.')}</td></tr>`,
        ui.pager(`${top.length} tuyến nổi bật · Tổng ${d.routeCount} tuyến trong báo cáo`, 1, 1))}`;

    const go = (patch) => { location.hash = '#/overview?' + new URLSearchParams(Object.assign({ mode, date }, patch)).toString(); };
    $$('[data-mode]', root).forEach((b) => b.addEventListener('click', () => go({ mode: b.dataset.mode, date: b.dataset.mode === 'DAY' ? addDays(t, -1) : t })));
    const pd = $('#pick-date'); if (pd) pd.addEventListener('change', () => pd.value && go({ date: pd.value }));
    const ms = $('#f-month'), ys = $('#f-year');
    const ymChange = () => {
      const y = ys.value, m = ms ? String(ms.value).padStart(2, '0') : '01';
      let nd = `${y}-${m}-01`;
      if (nd.slice(0, 7) === t.slice(0, 7) || (mode === 'YEAR' && y === t.slice(0, 4))) nd = t;
      go({ date: nd });
    };
    if (ms) ms.addEventListener('change', ymChange);
    if (ys) ys.addEventListener('change', ymChange);
    $('#export').addEventListener('click', () => {
      download(`vego-tong-quan-${d.from}_${d.to}.csv`, [
        ['Báo cáo tổng quan VéGo', range], [],
        ['Doanh thu vé (đ)', d.revenue], ['Vé đã bán', d.tickets], ['Ghế mở bán', d.seats], ['Tỷ lệ lấp đầy (%)', d.fillRate], ['Chuyến đã vận hành', d.trips], [],
        ['Mốc', 'Doanh thu (đ)', 'Vé'], ...d.series.map((p) => [p.label, p.revenue, p.tickets]), [],
        ['Xếp hạng', 'Tuyến', 'Vé bán', 'Ghế mở bán', 'Lấp đầy (%)', 'Doanh thu (đ)'],
        ...d.topRoutes.map((r) => [r.rank, r.name, r.tickets, r.seats, r.fillRate, r.revenue]),
      ]);
      toast('Đã xuất báo cáo CSV');
    });
  }, { nav: 'overview', crumb: 'Tổng quan' });

  // =====================================================================
  // Quản lý xe
  // =====================================================================

  VG.route('/vehicles', async ({ root, q, alive }) => {
    const page = Number(q.page || 1);
    const d = await api('/admin/vehicles', { query: { q: q.q, type: q.type, state: q.state, page, size: 10 } });
    if (!alive()) return;
    const s = d.summary, p = d.page;
    const pages = Math.max(1, Math.ceil(p.total / p.size));
    root.innerHTML = `
      ${ui.pageHead('Quản lý xe', null, '<a class="btn primary" href="#/vehicles/new">+ Thêm xe mới</a>')}
      <div class="stats">
        ${ui.stat('Tổng số xe', 'bus', fmt.num(s.total), 'Toàn bộ đội xe đang quản lý')}
        ${ui.stat('Sẵn sàng vận hành', 'circle-check', fmt.num(s.ready), fmt.pct(s.total ? (100 * s.ready) / s.total : 0) + ' tổng đội xe')}
        ${ui.stat('Đang chạy', 'route', fmt.num(s.running), s.running + ' chuyến đang trên đường')}
        ${ui.stat('Đang bảo trì', 'wrench', fmt.num(s.maintenance), 'Không nhận lịch mới khi bảo trì')}
      </div>
      <form class="filters" id="filter">
        <label class="search">${icon('search', 'ic-18')}<input name="q" value="${esc(q.q || '')}" placeholder="Tìm biển số, mã xe hoặc loại xe…" aria-label="Tìm xe"></label>
        ${ui.select('type', [['', 'Tất cả loại xe'], ...Object.entries(BUS_TYPES)], q.type, 'aria-label="Loại xe"')}
        ${ui.select('state', [['', 'Tất cả trạng thái'], ['READY', 'Sẵn sàng'], ['RUNNING', 'Đang chạy'], ['MAINTENANCE', 'Bảo trì']], q.state, 'aria-label="Trạng thái"')}
      </form>
      ${ui.table([{ h: 'Mã xe / biển số' }, { h: 'Loại xe' }, { h: 'Số ghế' }, { h: 'Trạng thái' }, { h: 'Lịch tiếp theo' }, { h: 'Thao tác', w: '120px' }],
        p.items.length ? p.items.map((v) => `<tr class="clickable" data-id="${v.id}">
          <td class="strong">${esc(v.code)} · ${esc(v.plate)}</td>
          <td>${esc(v.typeLabel.replace(/ \d+ chỗ$/, ''))}<div class="muted" style="font-size:12px">${esc(v.operator)}</div></td>
          <td>${v.totalSeats}</td><td>${stateCell(vehicleState, v.state)}</td>
          <td>${v.currentRun ? 'Đang chạy ' + esc(v.currentRun.code) : v.nextRun ? fmt.dm(v.nextRun.date) + ' · ' + fmt.time(v.nextRun.departAt) : '<span class="muted">Chưa có lịch</span>'}</td>
          <td><div class="row-actions"><a class="link-btn edit" href="#/vehicles/${v.id}">Sửa</a><button class="link-btn del" data-del="${v.id}" data-code="${esc(v.code)}">Xóa</button></div></td></tr>`).join('')
          : `<tr><td colspan="6">${ui.empty('Không có xe phù hợp', 'Thử bỏ bớt bộ lọc hoặc thêm xe mới.')}</td></tr>`,
        ui.pager(`Hiển thị ${p.items.length ? (p.page - 1) * p.size + 1 : 0}–${(p.page - 1) * p.size + p.items.length} trong ${p.total} xe · ${p.size} dòng / trang`, p.page, pages))}
      ${ui.note('Quản lý vòng đời xe', 'Chọn Sửa để cập nhật cấu hình. Xe đang là xe mặc định của chuyến hoặc đã có lịch chạy thì không xóa được; dùng trạng thái Bảo trì để tạm ngừng điều phối.')}`;
    listBehaviour(root, q, '/vehicles', async (id, code) => {
      if (!await confirmDialog({ title: `Xóa xe ${code}?`, message: 'Xe sẽ bị xóa khỏi đội xe. Lịch sử chuyến đã chạy vẫn được giữ.', ok: 'Xóa xe', danger: true })) return false;
      await api('/admin/vehicles/' + id, { method: 'DELETE' });
      toast('Đã xóa xe ' + code);
      return true;
    }, (id) => { location.hash = '#/vehicles/' + id; });
  }, { nav: 'vehicles', crumb: 'Quản lý xe' });

  /** Bộ lọc tự áp dụng, phân trang, nút Xóa, bấm dòng để mở. */
  function listBehaviour(root, q, path, onDelete, onOpen) {
    const form = $('#filter', root);
    let timer;
    const apply = () => {
      const d = formData(form);
      location.hash = '#' + path + '?' + new URLSearchParams(Object.fromEntries(Object.entries(d).filter(([, v]) => v))).toString();
    };
    form.addEventListener('input', (e) => { clearTimeout(timer); timer = setTimeout(apply, e.target.tagName === 'SELECT' ? 0 : 400); });
    form.addEventListener('submit', (e) => { e.preventDefault(); apply(); });
    const searchBox = $('input[name=q]', form);
    if (q.q && searchBox) { searchBox.focus(); searchBox.setSelectionRange(searchBox.value.length, searchBox.value.length); }
    $$('[data-page]', root).forEach((b) => b.addEventListener('click', () => {
      location.hash = '#' + path + '?' + new URLSearchParams(Object.assign({}, q, { page: b.dataset.page })).toString();
    }));
    root.addEventListener('click', async (e) => {
      const del = e.target.closest('[data-del]');
      if (del) {
        e.stopPropagation();
        try { if (await onDelete(del.dataset.del, del.dataset.code)) window.dispatchEvent(new HashChangeEvent('hashchange')); }
        catch (err) { toast(err.message, 'error'); }
        return;
      }
      if (e.target.closest('a,button')) return;
      const row = e.target.closest('tr.clickable');
      if (row && onOpen) onOpen(row.dataset.id);
    });
  }

  function decksHtml(busType, seats) {
    const two = busType !== 'SEATER' && seats >= 20;
    const mk = (prefix, name, n) => `<div class="deck"><h4>${name} · ${n} ${busType === 'SEATER' ? 'ghế' : 'chỗ'}</h4><div class="seats">${Array.from({ length: n }, (_, i) => `<span>${prefix}${String(i + 1).padStart(2, '0')}</span>`).join('')}</div></div>`;
    if (!seats || seats < 1) return ui.empty('Nhập số chỗ để xem sơ đồ');
    if (!two) return mk('A', 'Khoang hành khách', seats);
    const lower = Math.ceil(seats / 2);
    return mk('A', 'Tầng dưới', lower) + mk('B', 'Tầng trên', seats - lower);
  }

  function layoutText(busType, seats) {
    const two = busType !== 'SEATER' && seats >= 20;
    const unit = busType === 'SEATER' ? 'ghế' : 'giường';
    if (!seats) return ['—', '—', '—', '—'];
    if (!two) return [`1 khoang · 3 ${unit} mỗi hàng`, 'A · Khoang hành khách', `${seats} ${unit} · A01–A${String(seats).padStart(2, '0')}`, 'Không có'];
    const lower = Math.ceil(seats / 2), upper = seats - lower;
    return [`2 tầng · 3 dãy ${unit}`, 'A · Tầng dưới / B · Tầng trên', `${lower} ${unit} · A01–A${String(lower).padStart(2, '0')}`, `${upper} ${unit} · B01–B${String(upper).padStart(2, '0')}`];
  }

  VG.route('/vehicles/(new|\\d+)', async ({ root, params, alive }) => {
    const id = params[0] !== undefined ? params[0] : location.hash.split('/')[2].split('?')[0];
    const isNew = id === 'new';
    const [opts, detail] = await Promise.all([options(), isNew ? null : api('/admin/vehicles/' + id)]);
    if (!alive()) return;
    const v = detail ? detail.vehicle : { busType: 'LIMOUSINE', totalSeats: 34, status: 'READY', operatorId: opts.operators[0] && opts.operators[0].id };
    root.innerHTML = `
      ${ui.pageHead(isNew ? 'Thêm xe mới' : 'Chỉnh sửa xe ' + v.code, null)}
      <div class="split wide-side">
        <form class="stack" id="vform" novalidate>
          <div class="card">
            <div class="chips">${ui.badge(isNew ? 'Xe mới' : 'Chế độ chỉnh sửa')}${isNew ? '' : ui.badge(v.plate, 'grey')}</div>
            <h2>Thông tin phương tiện</h2>
            <div class="form">
              ${ui.field('Biển số', ui.input('plate', v.plate || '', 'placeholder="51B-123.45" required'), { name: 'plate', for: 'f-plate' })}
              ${ui.field('Mã xe', ui.input('code', v.code || '', 'placeholder="Để trống để tự tạo (XE025…)"'), { name: 'code', for: 'f-code' })}
              ${ui.field('Loại xe', ui.select('busType', Object.entries(BUS_TYPES), v.busType), { name: 'busType', for: 'f-busType' })}
              ${ui.field('Tổng số chỗ', ui.input('totalSeats', v.totalSeats, 'type="number" min="4" max="60" required'), { name: 'totalSeats', for: 'f-totalSeats' })}
              ${ui.field('Nhà xe', ui.select('operatorId', opts.operators.map((o) => [o.id, o.name]), v.operatorId), { name: 'operatorId', for: 'f-operatorId' })}
              ${ui.field('Trạng thái', ui.select('status', [['READY', 'Sẵn sàng'], ['MAINTENANCE', 'Bảo trì']], v.status), { name: 'status', for: 'f-status' })}
              ${ui.field('Ghi chú vận hành', `<textarea class="input" name="note" id="f-note" maxlength="500" placeholder="Tiện ích, lịch bảo dưỡng…">${esc(v.note || '')}</textarea>`, { name: 'note', wide: true, for: 'f-note' })}
            </div>
          </div>
          <div class="card">
            <h2>Cấu hình sơ đồ ghế</h2>
            <div class="form" id="layout"></div>
            ${ui.note('Sơ đồ cấu hình, không phải trạng thái bán', 'Sơ đồ suy ra từ loại xe và số chỗ, giống sơ đồ khách thấy trong app. Chỉ đổi được loại xe / số chỗ khi xe chưa có lịch chạy, để không lệch ghế khách đã đặt.')}
            <div class="form-actions"><a class="btn" href="#/vehicles">Hủy</a><button class="btn primary" type="submit">${isNew ? 'Thêm xe' : 'Lưu thay đổi'}</button></div>
          </div>
        </form>
        <div class="stack">
          <div class="card"><h3 id="map-title"></h3><p class="sub">Đầu xe ↑ · A dưới / B trên</p><div id="decks" class="stack" style="gap:12px"></div>
            <div class="kv"><div><span>Tổng sức chứa</span><b class="hl" id="cap"></b></div></div></div>
          ${detail ? `<div class="card"><h3>Lịch sắp tới của ${esc(v.code)}</h3>
            ${v.defaultTrips.length ? `<p class="sub">Xe mặc định của chuyến ${v.defaultTrips.map(esc).join(', ')}</p>` : ''}
            ${detail.runs.length ? `<div class="kv">${detail.runs.map((r) => `<div><span>${esc(r.code)}</span><b>${fmt.time(r.departAt)} ${fmt.dm(r.date)} → ${esc(r.arriveTime)}</b> ${ui.runState(r)}</div>`).join('')}</div>` : '<p class="sub">Chưa có lịch trong 14 ngày tới.</p>'}
            <a class="btn small" href="#/schedule?vehicle=${v.id}">Lập lịch cho xe này</a></div>` : ''}
          ${ui.note('Thêm xe mới', 'Từ danh sách xe, chọn "+ Thêm xe mới" để mở cùng biểu mẫu ở chế độ tạo mới. Xe mới cần được gán vào chuyến ở màn Lập lịch hoặc Tuyến & chuyến.')}
        </div>
      </div>`;
    const form = $('#vform');
    const draw = () => {
      const bt = $('#f-busType').value, n = Number($('#f-totalSeats').value) || 0;
      const t = layoutText(bt, n);
      $('#layout').innerHTML = ui.field('Kiểu sơ đồ', `<input class="input" readonly value="${t[0]}">`) + ui.field('Tiền tố tầng', `<input class="input" readonly value="${t[1]}">`)
        + ui.field(bt !== 'SEATER' && n >= 20 ? 'Tầng dưới' : 'Khoang', `<input class="input" readonly value="${t[2]}">`) + ui.field('Tầng trên', `<input class="input" readonly value="${t[3]}">`);
      $('#map-title').textContent = `Sơ đồ ${n} ${bt === 'SEATER' ? 'ghế' : 'giường'}`;
      $('#decks').innerHTML = decksHtml(bt, n);
      $('#cap').textContent = n + ' chỗ';
    };
    draw();
    $('#f-busType').addEventListener('change', draw);
    $('#f-totalSeats').addEventListener('input', draw);
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const d = formData(form);
      const body = { code: d.code, plate: d.plate, busType: d.busType, totalSeats: Number(d.totalSeats), operatorId: Number(d.operatorId), status: d.status, note: d.note };
      const done = busy($('button[type=submit]', form));
      try {
        const r = await api(isNew ? '/admin/vehicles' : '/admin/vehicles/' + id, { method: isNew ? 'POST' : 'PUT', body });
        toast((isNew ? 'Đã thêm xe ' : 'Đã lưu xe ') + r.vehicle.code);
        location.hash = '#/vehicles';
      } catch (err) {
        done();
        if (!showFieldErrors(form, err)) toast(err.message, 'error');
      }
    });
  }, { nav: 'vehicles', crumb: 'Quản lý xe' });

  // =====================================================================
  // Tài xế
  // =====================================================================

  VG.route('/drivers', async ({ root, q, alive }) => {
    const page = Number(q.page || 1);
    const d = await api('/admin/drivers', { query: { q: q.q, state: q.state, licenseClass: q.licenseClass, page, size: 10 } });
    if (!alive()) return;
    const s = d.summary, p = d.page;
    const pages = Math.max(1, Math.ceil(p.total / p.size));
    const first = p.items.find((x) => x.state === 'READY') || p.items[0];
    root.innerHTML = `
      ${ui.pageHead('Quản lý tài xế', null, '<a class="btn primary" href="#/drivers/new">+ Thêm tài xế</a>')}
      <div class="stats">
        ${ui.stat('Tổng tài xế', 'users', fmt.num(s.total), 'Đội ngũ vận hành')}
        ${ui.stat('Sẵn sàng', 'circle-check', fmt.num(s.ready), 'Có thể gán chuyến mới')}
        ${ui.stat('Đang thực hiện chuyến', 'route', fmt.num(s.running), s.running + ' tài xế đang vận hành')}
        ${ui.stat('Tạm nghỉ', 'calendar-clock', fmt.num(s.off), 'Chưa nhận lịch mới')}
      </div>
      <form class="filters" id="filter">
        <label class="search">${icon('search', 'ic-18')}<input name="q" value="${esc(q.q || '')}" placeholder="Tìm họ tên, số điện thoại hoặc GPLX…" aria-label="Tìm tài xế"></label>
        ${ui.select('state', [['', 'Tất cả trạng thái'], ['READY', 'Sẵn sàng'], ['RUNNING', 'Đang chạy'], ['OFF', 'Tạm nghỉ']], q.state, 'aria-label="Trạng thái"')}
        ${ui.select('licenseClass', [['', 'Hạng bằng: Tất cả'], ...Object.keys(LICENSES).map((k) => [k, 'Hạng ' + k])], q.licenseClass, 'aria-label="Hạng GPLX"')}
      </form>
      ${ui.table([{ h: 'Tài xế' }, { h: 'Số điện thoại' }, { h: 'GPLX / hạng' }, { h: 'Trạng thái' }, { h: 'Chuyến được gán' }, { h: 'Thao tác', w: '120px' }],
        p.items.length ? p.items.map((x) => `<tr class="clickable" data-id="${x.id}">
          <td class="strong">${esc(x.code)} · ${esc(x.fullName)}</td><td>${esc(x.phone.replace(/(\d{3})(\d{3})(\d{4})/, '$1 $2 $3'))}</td>
          <td>${esc(x.licenseNo)} · ${esc(x.licenseClass)}${x.licenseValid ? '' : ' ' + ui.badge('Hết hạn', 'danger')}</td>
          <td>${stateCell(driverState, x.state)}</td>
          <td>${x.currentRun ? esc(x.currentRun.code) : x.nextRun ? esc(x.nextRun.code) : '<span class="muted">Chưa được gán</span>'}</td>
          <td><div class="row-actions"><a class="link-btn edit" href="#/drivers/${x.id}">Sửa</a><button class="link-btn del" data-del="${x.id}" data-code="${esc(x.fullName)}">Xóa</button></div></td></tr>`).join('')
          : `<tr><td colspan="6">${ui.empty('Không có tài xế phù hợp', 'Thử bỏ bớt bộ lọc hoặc thêm tài xế mới.')}</td></tr>`,
        ui.pager(`Hiển thị ${p.items.length ? (p.page - 1) * p.size + 1 : 0}–${(p.page - 1) * p.size + p.items.length} trong ${p.total} tài xế · ${p.size} dòng / trang`, p.page, pages))}
      <div class="note-row">${ui.note('Gán chuyến cho tài xế', 'Chọn một tài xế để sửa thông tin hoặc gán chuyến. Không xóa tài xế đang có lịch chạy; chuyển sang Tạm nghỉ khi cần.')}
        ${first ? `<a class="btn" href="#/drivers/${first.id}">Gán chuyến cho ${esc(first.code)}</a>` : ''}</div>`;
    listBehaviour(root, q, '/drivers', async (id, name) => {
      if (!await confirmDialog({ title: `Xóa tài xế ${name}?`, message: 'Hồ sơ sẽ bị xóa; các chuyến đã chạy vẫn được giữ trong lịch sử.', ok: 'Xóa tài xế', danger: true })) return false;
      await api('/admin/drivers/' + id, { method: 'DELETE' });
      toast('Đã xóa tài xế ' + name);
      return true;
    }, (id) => { location.hash = '#/drivers/' + id; });
  }, { nav: 'drivers', crumb: 'Tài xế' });

  VG.route('/drivers/(new|\\d+)', async ({ root, alive }) => {
    const id = location.hash.split('/')[2].split('?')[0];
    const isNew = id === 'new';
    const t = today();
    const [detail, runs] = await Promise.all([isNew ? null : api('/admin/drivers/' + id), api('/admin/schedules', { query: { from: t, days: 7 } })]);
    if (!alive()) return;
    const x = detail ? detail.driver : { status: 'READY', licenseClass: 'D', experienceYears: 0 };
    const now = Date.now();
    const assignable = runs.filter((r) => new Date(r.departAt).getTime() > now);
    const mine = detail ? detail.runs : [];
    const current = mine.find((r) => r.state !== 'DONE');
    const runOpt = (r) => [r.tripId + '|' + r.date, `${r.code} · ${r.routeName} · ${fmt.time(r.departAt)} ${fmt.dm(r.date)}${r.driver ? ' · đang gán ' + r.driver.code : ''}`];
    const seatOk = (r) => r.seatsTotal <= (x.maxSeats || 99);
    root.innerHTML = `
      ${ui.pageHead(isNew ? 'Thêm tài xế' : 'Chỉnh sửa tài xế ' + x.code, null)}
      <div class="split wide-side">
        <form class="stack" id="dform" novalidate>
          <div class="card">
            <div class="chips">${ui.badge(isNew ? 'Hồ sơ mới' : 'Chế độ chỉnh sửa')}</div>
            <h2>Hồ sơ tài xế</h2>
            <div class="form">
              ${ui.field('Họ và tên', ui.input('fullName', x.fullName || '', 'required autocomplete="off"'), { name: 'fullName', for: 'f-fullName' })}
              ${ui.field('Số điện thoại', `<div class="with-icon">${ui.input('phone', x.phone || '', 'inputmode="tel" required')}<span class="trail">${icon('phone', 'ic-18')}</span></div>`, { name: 'phone', for: 'f-phone' })}
              ${ui.field('Số giấy phép lái xe', ui.input('licenseNo', x.licenseNo || '', 'inputmode="numeric" maxlength="12" placeholder="12 chữ số" required'), { name: 'licenseNo', for: 'f-licenseNo' })}
              ${ui.field('Hạng giấy phép', ui.select('licenseClass', Object.entries(LICENSES), x.licenseClass), { name: 'licenseClass', for: 'f-licenseClass' })}
              ${ui.field('Ngày hết hạn GPLX', ui.input('licenseExpiry', x.licenseExpiry || '', 'type="date" required'), { name: 'licenseExpiry', for: 'f-licenseExpiry' })}
              ${ui.field('Trạng thái', ui.select('status', [['READY', 'Sẵn sàng'], ['OFF', 'Tạm nghỉ']], x.status), { name: 'status', for: 'f-status' })}
              ${ui.field('Kinh nghiệm (năm)', ui.input('experienceYears', x.experienceYears ?? 0, 'type="number" min="0" max="60"'), { name: 'experienceYears', for: 'f-experienceYears' })}
              ${ui.field('Mã tài xế', ui.input('code', x.code || '', 'placeholder="Để trống để tự tạo"'), { name: 'code', for: 'f-code' })}
              ${ui.field('Ghi chú', `<textarea class="input" name="note" id="f-note" maxlength="500">${esc(x.note || '')}</textarea>`, { name: 'note', wide: true, for: 'f-note' })}
            </div>
          </div>
          <div class="card">
            <h2>Gán chuyến</h2>
            <div class="form">
              ${ui.field('Chuyến được phân công', ui.select('run', [['', isNew ? 'Lưu hồ sơ trước khi gán chuyến' : 'Không gán thêm chuyến'], ...assignable.filter(seatOk).map(runOpt)], '', isNew ? 'disabled' : ''), { name: 'run', wide: true, for: 'f-run', hint: 'Chuyến trong 7 ngày tới, đúng hạng GPLX của tài xế.' })}
              ${ui.field('Khởi hành', '<input class="input" readonly id="run-dep" value="—">')}
              ${ui.field('Xe vận hành', '<input class="input" readonly id="run-veh" value="—">')}
            </div>
            <div class="chips" id="run-chk"></div>
            <div class="form-actions"><a class="btn" href="#/drivers">Hủy</a><button class="btn primary" type="submit">${isNew ? 'Thêm tài xế' : 'Lưu hồ sơ & phân công'}</button></div>
          </div>
        </form>
        <div class="stack">
          ${detail ? `<div class="card">${icon('user-check', '')}<h2>${esc(x.fullName)}</h2>
            <div class="chips">${ui.badge(driverState[x.state][0] + (x.state === 'READY' ? ' nhận chuyến' : ''), x.state === 'OFF' ? 'orange' : '')}</div>
            <div class="kv"><div><span>Mã tài xế</span><b>${esc(x.code)}</b></div><div><span>Kinh nghiệm</span><b>${x.experienceYears} năm</b></div>
            <div><span>GPLX</span><b>${esc(x.licenseClass)} · ${x.licenseValid ? 'Còn hiệu lực đến ' + fmt.date(x.licenseExpiry) : 'Hết hạn ' + fmt.date(x.licenseExpiry)}</b></div>
            <div><span>Chuyến trong tháng</span><b>${x.tripsThisMonth} chuyến</b></div></div></div>` : ''}
          ${ui.note('Thông tin giấy phép', 'Hệ thống kiểm tra hạng GPLX theo số chỗ của xe (D1 đến 16 chỗ, D2 đến 29 chỗ, D trên 29 chỗ) và hạn GPLX trước khi gán chuyến.')}
          ${detail ? `<div class="card"><h3>Chuyến sắp tới</h3>${mine.length ? `<div class="kv">${mine.map((r) => `<div><span>${esc(r.code)}</span><b>${fmt.time(r.departAt)} ${fmt.dm(r.date)} · ${esc(r.routeName)}</b></div>`).join('')}</div>` : '<p class="sub">Chưa được gán chuyến nào.</p>'}</div>` : ''}
          ${current ? `<button class="btn danger" id="unassign" data-trip="${esc(current.tripId)}" data-date="${current.date}">Hủy phân công chuyến ${esc(current.code)}</button>` : ''}
          ${ui.note('Thêm tài xế mới', 'Chọn "+ Thêm tài xế" từ danh sách để nhập hồ sơ mới trước khi điều phối.')}
        </div>
      </div>`;
    const form = $('#dform');
    const runSel = $('#f-run');
    runSel.addEventListener('change', () => {
      const r = assignable.find((z) => z.tripId + '|' + z.date === runSel.value);
      $('#run-dep').value = r ? `${fmt.time(r.departAt)} · ${fmt.date(r.date)}` : '—';
      $('#run-veh').value = r && r.vehicle ? `${r.vehicle.code} · ${r.vehicle.plate}` : '—';
      $('#run-chk').innerHTML = r ? (r.driver ? ui.badge('Sẽ thay tài xế ' + r.driver.code, 'orange') : '') + ui.badge('Kiểm tra trùng lịch khi lưu') : '';
    });
    const un = $('#unassign');
    if (un) un.addEventListener('click', async () => {
      if (!await confirmDialog({ title: 'Hủy phân công?', message: `Bỏ ${x.fullName} khỏi chuyến ${current.code}. Chuyến sẽ chưa có tài xế.`, ok: 'Hủy phân công', danger: true })) return;
      try {
        await api(`/admin/schedules/${encodeURIComponent(un.dataset.trip)}/${un.dataset.date}/driver`, { method: 'DELETE' });
        toast('Đã hủy phân công chuyến ' + current.code);
        window.dispatchEvent(new HashChangeEvent('hashchange'));
      } catch (err) { toast(err.message, 'error'); }
    });
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const d = formData(form);
      const body = { code: d.code, fullName: d.fullName, phone: d.phone, licenseNo: d.licenseNo, licenseClass: d.licenseClass, licenseExpiry: d.licenseExpiry || null, experienceYears: Number(d.experienceYears || 0), status: d.status, note: d.note };
      const done = busy($('button[type=submit]', form));
      try {
        const r = await api(isNew ? '/admin/drivers' : '/admin/drivers/' + id, { method: isNew ? 'POST' : 'PUT', body });
        if (d.run) {
          const [tripId, date] = d.run.split('|');
          try {
            const run = await api('/admin/schedules/assign', { method: 'PUT', body: { tripId, date, driverId: r.driver.id } });
            toast(`Đã lưu hồ sơ và gán chuyến ${run.code}`);
          } catch (err) {
            done();
            showFieldErrors(form, { fields: { run: err.message } });
            toast('Đã lưu hồ sơ, chưa gán được chuyến', 'error');
            return;
          }
        } else {
          toast((isNew ? 'Đã thêm tài xế ' : 'Đã lưu hồ sơ ') + r.driver.fullName);
        }
        location.hash = isNew ? '#/drivers' : '#/drivers/' + r.driver.id;
        if (!isNew) window.dispatchEvent(new HashChangeEvent('hashchange'));
      } catch (err) {
        done();
        if (!showFieldErrors(form, err)) toast(err.message, 'error');
      }
    });
  }, { nav: 'drivers', crumb: 'Tài xế' });

  // =====================================================================
  // Tuyến & chuyến
  // =====================================================================

  VG.route('/routes', async ({ root, q, alive }) => {
    const date = q.date || today();
    const page = Number(q.page || 1);
    const d = await api('/admin/routes', { query: { q: q.q, status: q.status, date, page, size: 10 } });
    if (!alive()) return;
    const p = d.page;
    const pages = Math.max(1, Math.ceil(p.total / p.size));
    const selected = p.items.find((r) => String(r.id) === String(q.route)) || p.items[0];
    root.innerHTML = `
      ${ui.pageHead('Tuyến đường & chuyến xe', null, '<a class="btn primary" href="#/routes/new">+ Thêm tuyến mới</a>')}
      <div class="section-head" style="flex-wrap:wrap">
        <div class="chips">${ui.badge('Tuyến đường · ' + d.totalRoutes)}${ui.badge('Chuyến xe ngày ' + fmt.dm(date) + ' · ' + d.runsOnDate, 'grey')}<span class="muted" style="font-size:13px">Quản lý tuyến, tìm chuyến và điều phối lịch chạy.</span></div>
        <a class="btn primary" href="#/schedule?date=${date}">+ Lập lịch chuyến</a>
      </div>
      <form class="filters" id="filter">
        <label class="search">${icon('search', 'ic-18')}<input name="q" value="${esc(q.q || '')}" placeholder="Tìm tuyến, điểm đi / đến hoặc bến xe…" aria-label="Tìm tuyến"></label>
        <input class="input" type="date" name="date" value="${date}" aria-label="Ngày" style="width:auto">
        ${ui.select('status', [['', 'Tất cả trạng thái'], ['ACTIVE', 'Đang hoạt động'], ['INACTIVE', 'Tạm ngừng']], q.status, 'aria-label="Trạng thái"')}
        <input type="hidden" name="route" value="${esc(q.route || '')}">
      </form>
      ${ui.table([{ h: 'Mã tuyến' }, { h: 'Điểm đi' }, { h: 'Điểm đến' }, { h: 'Thời lượng' }, { h: 'Chuyến trong ngày' }, { h: 'Thao tác', w: '120px' }],
        p.items.length ? p.items.map((r) => `<tr class="clickable ${selected && r.id === selected.id ? 'selected' : ''}" data-id="${r.id}">
          <td class="strong">${esc(r.code)}${r.active ? '' : ' ' + ui.badge('Tạm ngừng', 'orange')}</td><td>${esc(r.originCity)}</td><td>${esc(r.destinationCity)}</td>
          <td>${fmt.duration(r.durationMinutes)}</td><td>${r.runsOnDate} chuyến</td>
          <td><div class="row-actions"><a class="link-btn edit" href="#/routes/${r.id}">Sửa</a><button class="link-btn del" data-del="${r.id}" data-code="${esc(r.code)}">Xóa</button></div></td></tr>`).join('')
          : `<tr><td colspan="6">${ui.empty('Không có tuyến phù hợp', 'Thử bỏ bớt bộ lọc hoặc thêm tuyến mới.')}</td></tr>`,
        ui.pager(`Hiển thị ${p.total} tuyến · ${d.activeRoutes} tuyến đang hoạt động`, p.page, pages))}
      <div id="runs"></div>
      ${ui.note('Thao tác tuyến và chuyến', 'Bấm một tuyến để xem các chuyến chạy trong ngày. Không xóa tuyến còn chuyến hoặc chuyến đã có vé; hãy chuyển trạng thái Tạm ngừng để ẩn khỏi app.')}`;
    listBehaviour(root, q, '/routes', async (id, code) => {
      if (!await confirmDialog({ title: `Xóa tuyến ${code}?`, message: 'Chỉ xóa được tuyến chưa có chuyến nào.', ok: 'Xóa tuyến', danger: true })) return false;
      await api('/admin/routes/' + id, { method: 'DELETE' });
      toast('Đã xóa tuyến ' + code);
      return true;
    }, (id) => {
      setQuery(Object.assign({}, q, { route: id }));
      $$('tr.clickable', root).forEach((tr) => tr.classList.toggle('selected', tr.dataset.id === String(id)));
      showRuns(p.items.find((r) => String(r.id) === String(id)), date, q);
    });
    if (selected) showRuns(selected, date, q);
  }, { nav: 'routes', crumb: 'Tuyến & chuyến' });

  async function showRuns(route, date, q) {
    const box = $('#runs');
    if (!box || !route) return;
    box.innerHTML = '<div class="loading">Đang tải chuyến…</div>';
    let runs;
    try { runs = await api(`/admin/routes/${route.id}/runs`, { query: { date } }); } catch (err) { box.innerHTML = ui.empty('Không tải được chuyến', err.message); return; }
    box.innerHTML = `<div class="stack" style="gap:16px">
      <div class="section-head"><h2>Chuyến trên tuyến ${esc(route.code)} · ${fmt.date(date)}</h2><a class="btn" href="#/schedule?route=${route.id}&date=${date}">Lập lịch cho tuyến ${esc(route.code)}</a></div>
      ${ui.table([{ h: 'Mã chuyến' }, { h: 'Khởi hành / đến' }, { h: 'Xe / nhà xe' }, { h: 'Tài xế' }, { h: 'Giá vé' }, { h: 'Ghế đã bán' }, { h: 'Thao tác', w: '150px' }],
        runs.length ? runs.map((r) => `<tr>
          <td class="strong">${esc(r.code)}${r.oneOff ? ' ' + ui.badge('Tăng cường', 'orange') : ''}</td>
          <td>${esc(r.departTime)} / ${esc(r.arriveTime)}${r.arriveDayOffset ? ' +' + r.arriveDayOffset : ''}<div>${ui.runState(r)}</div></td>
          <td>${r.vehicle ? esc(r.vehicle.code) + ' · ' : ''}${esc(r.operator)}</td>
          <td>${r.driver ? esc(r.driver.code) + ' · ' + esc(r.driver.fullName) : '<span class="state warn">Chưa phân công</span>'}</td>
          <td>${fmt.money(r.price)}</td><td>${r.seatsSold} / ${r.seatsTotal}</td>
          <td><div class="row-actions">${r.state === 'DONE' ? '<span class="muted">—</span>' : `<a class="link-btn edit" href="#/schedule?route=${r.routeId}&date=${r.date}&time=${r.departTime}">Phân công</a>`}
            ${r.oneOff && r.state === 'SCHEDULED' ? `<button class="link-btn del" data-drop="${esc(r.tripId)}|${r.date}" data-code="${esc(r.code)}">Xóa</button>` : ''}</div></td></tr>`).join('')
          : `<tr><td colspan="7">${ui.empty('Không có chuyến chạy ngày này', 'Lập lịch chuyến tăng cường hoặc thêm chuyến cố định trong trang sửa tuyến.')}</td></tr>`,
        ui.pager(`${runs.length} chuyến trên tuyến · Ngày ${fmt.date(date)}`, 1, 1))}</div>`;
    $$('[data-drop]', box).forEach((b) => b.addEventListener('click', async () => {
      const [tripId, d] = b.dataset.drop.split('|');
      if (!await confirmDialog({ title: `Xóa chuyến ${b.dataset.code}?`, message: 'Chuyến tăng cường chưa có khách đặt sẽ bị xóa khỏi app.', ok: 'Xóa chuyến', danger: true })) return;
      try {
        await api(`/admin/schedules/${encodeURIComponent(tripId)}/${d}`, { method: 'DELETE' });
        toast('Đã xóa chuyến ' + b.dataset.code);
        showRuns(route, date, q);
      } catch (err) { toast(err.message, 'error'); }
    }));
  }

  VG.route('/routes/(new|\\d+)', async ({ root, alive }) => {
    const id = location.hash.split('/')[2].split('?')[0];
    const isNew = id === 'new';
    const [opts, r, trips, vehicles] = await Promise.all([options(true), isNew ? null : api('/admin/routes/' + id),
      isNew ? [] : api('/admin/trips', { query: { routeId: id } }), api('/admin/vehicles', { query: { size: 100 } })]);
    if (!alive()) return;
    const route = r || { active: true, durationMinutes: 420 };
    const cityList = `<datalist id="cities">${opts.cities.map((c) => `<option value="${esc(c)}">`).join('')}</datalist>`;
    root.innerHTML = `
      ${ui.pageHead(isNew ? 'Thêm tuyến mới' : 'Chỉnh sửa tuyến ' + route.code, null)}
      <div class="split">
        <form class="stack" id="rform" novalidate>${cityList}
          <div class="card">
            <div class="chips">${ui.badge(isNew ? 'Tuyến mới' : 'Chế độ chỉnh sửa · ' + (route.active ? 'Tuyến đang hoạt động' : 'Tuyến tạm ngừng'))}</div>
            <h2>Thông tin tuyến đường</h2>
            <div class="form">
              ${ui.field('Mã tuyến', ui.input('code', route.code || '', 'placeholder="Để trống để tự tạo (T006…)"'), { name: 'code', for: 'f-code' })}
              ${ui.field('Tên tuyến', `<input class="input" readonly id="r-name" value="${esc(route.name || '')}">`)}
              ${ui.field('Điểm đi', ui.input('originCity', route.originCity || '', 'list="cities" required'), { name: 'originCity', for: 'f-originCity' })}
              ${ui.field('Điểm đến', ui.input('destinationCity', route.destinationCity || '', 'list="cities" required'), { name: 'destinationCity', for: 'f-destinationCity' })}
              ${ui.field('Thời lượng dự kiến (giờ)', ui.input('hours', route.durationMinutes ? route.durationMinutes / 60 : '', 'type="number" min="0.25" step="0.25" required'), { name: 'durationMinutes', for: 'f-hours' })}
              ${ui.field('Trạng thái', ui.select('active', [['true', 'Đang hoạt động'], ['false', 'Tạm ngừng']], String(route.active)), { name: 'active', for: 'f-active', hint: 'Tạm ngừng: ẩn tuyến khỏi tìm chuyến trong app.' })}
              ${ui.field('Khoảng cách (km)', ui.input('distanceKm', route.distanceKm || '', 'type="number" min="1"'), { name: 'distanceKm', for: 'f-distanceKm' })}
            </div>
          </div>
          <div class="card">
            <h2>Bến xe & điểm đón / trả</h2>
            <div class="form">
              ${ui.field('Bến xe xuất phát / điểm đón chính', '<select class="select" name="originStationId" id="f-originStationId"></select>', { name: 'originStationId', wide: true, for: 'f-originStationId' })}
              ${ui.field('Địa chỉ điểm đón', '<input class="input" readonly id="os-addr">', { wide: true })}
              ${ui.field('Bến xe đến / điểm trả chính', '<select class="select" name="destinationStationId" id="f-destinationStationId"></select>', { name: 'destinationStationId', wide: true, for: 'f-destinationStationId' })}
              ${ui.field('Địa chỉ điểm trả', '<input class="input" readonly id="ds-addr">', { wide: true })}
              <div class="field wide"><button type="button" class="btn small" id="add-station" style="align-self:flex-start">+ Thêm bến mới</button></div>
              <div class="field wide hidden" id="station-box"><div class="form" style="background:var(--segment);padding:14px;border-radius:12px">
                ${ui.field('Thành phố', ui.input('stCity', '', 'list="cities"'), { name: 'city' })}
                ${ui.field('Tên bến', ui.input('stName', ''), { name: 'name' })}
                ${ui.field('Địa chỉ', ui.input('stAddress', ''), { name: 'address', wide: true })}
                <div class="form-actions"><button type="button" class="btn small" id="st-cancel">Hủy</button><button type="button" class="btn small primary" id="st-save">Lưu bến</button></div></div></div>
            </div>
            <div class="form-actions"><a class="btn" href="#/routes">Hủy</a><button class="btn primary" type="submit">${isNew ? 'Thêm tuyến' : 'Lưu tuyến đường'}</button></div>
          </div>
        </form>
        <div class="stack">
          <div class="card" id="preview"></div>
          ${ui.note(isNew ? 'Thêm tuyến mới' : 'Tuyến đã có vé', isNew ? 'Sau khi lưu tuyến, thêm chuyến cố định hằng ngày bên dưới hoặc lập lịch chuyến tăng cường.' : 'Không đổi được điểm đi / điểm đến khi tuyến đã bán vé; có thể sửa bến, thời lượng và trạng thái.')}
          ${ui.note('Kiểm tra điểm đón', 'Bến xuất phát phải thuộc điểm đi, bến đến phải thuộc điểm đến. Bến mới sẽ hiện trong danh sách điểm đón / trả của app.')}
        </div>
      </div>
      ${isNew ? '' : `<div class="section-head"><h2>Chuyến cố định hằng ngày</h2><button class="btn primary" id="add-trip">+ Thêm chuyến cố định</button></div>
        <div id="trip-form"></div><div id="trip-table"></div>`}`;

    const form = $('#rform');
    const oc = $('#f-originCity'), dc = $('#f-destinationCity'), os = $('#f-originStationId'), ds = $('#f-destinationStationId');
    const stationsOf = (city) => opts.stations.filter((s) => s.city.toLowerCase() === (city || '').trim().toLowerCase());
    const fill = (sel, city, value, addr) => {
      const list = stationsOf(city);
      sel.innerHTML = (list.length ? list : [{ id: '', name: city ? 'Chưa có bến ở ' + city + ' — thêm bến mới' : 'Nhập thành phố trước', address: '' }])
        .map((s) => `<option value="${s.id}" ${String(s.id) === String(value) ? 'selected' : ''}>${esc(s.name)}</option>`).join('');
      const cur = list.find((s) => String(s.id) === sel.value);
      $(addr).value = cur ? cur.address : '';
    };
    const preview = () => {
      const o = oc.value.trim(), dd = dc.value.trim();
      $('#r-name').value = o && dd ? `${o} – ${dd}` : '';
      const osS = opts.stations.find((s) => String(s.id) === os.value), dsS = opts.stations.find((s) => String(s.id) === ds.value);
      const hrs = Number($('#f-hours').value) || 0;
      $('#preview').innerHTML = `${icon('route', 'ic')}<h2>${esc(o || 'Điểm đi')} → ${esc(dd || 'Điểm đến')}</h2><p class="sub">Lịch trình hiển thị cho khách hàng</p>
        <div class="kv"><div><b>${esc(osS ? osS.name : '—')}</b></div><div><span>Điểm đón chính</span><b>${esc(o)}</b></div>
        <div>${icon('arrow-down', 'ic-16')}</div><div><b>${esc(dsS ? dsS.name : '—')}</b></div><div><span>Điểm trả chính</span><b>${esc(dd)}</b></div>
        <div><span>Thời lượng</span><b class="hl">${fmt.duration(Math.round(hrs * 60))}</b></div><div><span>Khoảng cách</span><b>${$('#f-distanceKm').value ? 'Khoảng ' + $('#f-distanceKm').value + ' km' : '—'}</b></div></div>`;
    };
    fill(os, route.originCity, route.originStation && route.originStation.id, '#os-addr');
    fill(ds, route.destinationCity, route.destinationStation && route.destinationStation.id, '#ds-addr');
    preview();
    oc.addEventListener('input', () => { fill(os, oc.value, null, '#os-addr'); preview(); });
    dc.addEventListener('input', () => { fill(ds, dc.value, null, '#ds-addr'); preview(); });
    os.addEventListener('change', () => { fill(os, oc.value, os.value, '#os-addr'); preview(); });
    ds.addEventListener('change', () => { fill(ds, dc.value, ds.value, '#ds-addr'); preview(); });
    $('#f-hours').addEventListener('input', preview);
    $('#f-distanceKm').addEventListener('input', preview);
    $('#add-station').addEventListener('click', () => { $('#station-box').classList.remove('hidden'); $('#f-stCity').value = oc.value; $('#f-stName').focus(); });
    $('#st-cancel').addEventListener('click', () => $('#station-box').classList.add('hidden'));
    $('#st-save').addEventListener('click', async () => {
      const box = $('#station-box');
      try {
        const s = await api('/admin/stations', { method: 'POST', body: { city: $('#f-stCity').value, name: $('#f-stName').value, address: $('#f-stAddress').value } });
        opts.stations.push(s);
        optionsCache = null;
        box.classList.add('hidden');
        toast('Đã thêm ' + s.name);
        fill(os, oc.value, s.city.toLowerCase() === oc.value.trim().toLowerCase() ? s.id : os.value, '#os-addr');
        fill(ds, dc.value, s.city.toLowerCase() === dc.value.trim().toLowerCase() ? s.id : ds.value, '#ds-addr');
        preview();
      } catch (err) { if (!showFieldErrors(box, err)) toast(err.message, 'error'); }
    });
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const d = formData(form);
      const body = { code: d.code, originCity: d.originCity, destinationCity: d.destinationCity, durationMinutes: d.hours ? Math.round(Number(d.hours) * 60) : null,
        distanceKm: d.distanceKm ? Number(d.distanceKm) : null, active: d.active === 'true',
        originStationId: d.originStationId ? Number(d.originStationId) : null, destinationStationId: d.destinationStationId ? Number(d.destinationStationId) : null };
      const done = busy($('button[type=submit]', form));
      try {
        const saved = await api(isNew ? '/admin/routes' : '/admin/routes/' + id, { method: isNew ? 'POST' : 'PUT', body });
        optionsCache = null;
        toast((isNew ? 'Đã thêm tuyến ' : 'Đã lưu tuyến ') + saved.code);
        location.hash = '#/routes/' + saved.id;
        if (!isNew) window.dispatchEvent(new HashChangeEvent('hashchange'));
      } catch (err) {
        done();
        if (!showFieldErrors(form, err)) toast(err.message, 'error');
      }
    });
    if (!isNew) tripSection(route, trips, vehicles.page.items);
  }, { nav: 'routes', crumb: 'Tuyến & chuyến' });

  function tripSection(route, trips, vehicles) {
    const table = () => {
      $('#trip-table').innerHTML = ui.table([{ h: 'Mã chuyến' }, { h: 'Giờ đi' }, { h: 'Thời lượng' }, { h: 'Nhà xe / xe mặc định' }, { h: 'Giá vé' }, { h: 'Trạng thái' }, { h: 'Thao tác', w: '120px' }],
        trips.length ? trips.map((t) => `<tr><td class="strong">${esc(t.id)}${t.serviceDate ? ' ' + ui.badge('Chỉ ngày ' + fmt.dm(t.serviceDate), 'orange') : ''}</td>
          <td>${esc(t.departTime)}</td><td>${fmt.duration(t.durationMinutes)}</td>
          <td>${esc(t.operator)} · ${t.vehicle ? esc(t.vehicle.code) : '<span class="state warn">Chưa có xe</span>'}<div class="muted" style="font-size:12px">${esc(t.busName)}</div></td>
          <td>${fmt.money(t.price)}</td><td>${t.active ? '<span class="state ok">Đang chạy</span>' : '<span class="state warn">Ngừng chạy</span>'}</td>
          <td><div class="row-actions">${t.serviceDate ? '<span class="muted">Sửa ở Lập lịch</span>' : `<button class="link-btn edit" data-edit="${esc(t.id)}">Sửa</button><button class="link-btn del" data-deltrip="${esc(t.id)}">Xóa</button>`}</div></td></tr>`).join('')
          : `<tr><td colspan="7">${ui.empty('Tuyến chưa có chuyến cố định', 'Thêm chuyến để khách đặt được vé hằng ngày.')}</td></tr>`,
        ui.pager(`${trips.length} chuyến · chạy mỗi ngày trừ chuyến tăng cường`, 1, 1));
      $$('[data-edit]').forEach((b) => b.addEventListener('click', () => openForm(trips.find((t) => t.id === b.dataset.edit))));
      $$('[data-deltrip]').forEach((b) => b.addEventListener('click', async () => {
        if (!await confirmDialog({ title: `Xóa chuyến ${b.dataset.deltrip}?`, message: 'Chuyến chưa bán vé sẽ bị xóa; chuyến đã có lịch sử bán vé sẽ chuyển sang ngừng chạy.', ok: 'Xóa chuyến', danger: true })) return;
        try {
          const r = await api('/admin/trips/' + encodeURIComponent(b.dataset.deltrip), { method: 'DELETE' });
          toast(r.message);
          trips = await api('/admin/trips', { query: { routeId: route.id } });
          table();
        } catch (err) { toast(err.message, 'error'); }
      }));
    };
    const openForm = (trip) => {
      const box = $('#trip-form');
      const usable = vehicles.filter((v) => v.status === 'READY' || (trip && trip.vehicle && trip.vehicle.id === v.id));
      box.innerHTML = `<form class="card" id="tform" novalidate><h3>${trip ? 'Sửa chuyến ' + esc(trip.id) : 'Thêm chuyến cố định cho tuyến ' + esc(route.code)}</h3>
        <div class="form">
          ${ui.field('Giờ khởi hành', ui.input('departTime', trip ? trip.departTime : '', 'type="time" required'), { name: 'departTime' })}
          ${ui.field('Thời lượng (giờ)', ui.input('hours', (trip ? trip.durationMinutes : route.durationMinutes || 0) / 60, 'type="number" min="0.25" step="0.25"'), { name: 'durationMinutes' })}
          ${ui.field('Xe mặc định', ui.select('vehicleId', usable.map((v) => [v.id, `${v.code} · ${v.plate} · ${v.operator} · ${v.typeLabel}`]), trip && trip.vehicle ? trip.vehicle.id : ''), { name: 'vehicleId', hint: trip ? 'Chuyến đã bán vé chỉ đổi được sang xe cùng loại và số chỗ.' : 'Nhà xe, loại xe và sơ đồ ghế lấy theo xe này.' })}
          ${ui.field('Giá vé (đ)', ui.input('price', trip ? trip.price : '', 'type="number" min="10000" step="1000" required'), { name: 'price' })}
          ${trip ? ui.field('Trạng thái', ui.select('active', [['true', 'Đang chạy'], ['false', 'Ngừng chạy']], String(trip.active)), { name: 'active' }) : ''}
          <div class="form-actions"><button type="button" class="btn" id="t-cancel">Hủy</button><button class="btn primary" type="submit">${trip ? 'Lưu chuyến' : 'Thêm chuyến'}</button></div>
        </div></form>`;
      box.scrollIntoView({ behavior: 'smooth', block: 'center' });
      const f = $('#tform');
      $('#t-cancel').addEventListener('click', () => { box.innerHTML = ''; });
      f.addEventListener('submit', async (e) => {
        e.preventDefault();
        const d = formData(f);
        const body = { routeId: route.id, departTime: d.departTime, durationMinutes: d.hours ? Math.round(Number(d.hours) * 60) : null, price: d.price ? Number(d.price) : null, vehicleId: d.vehicleId ? Number(d.vehicleId) : null, active: d.active ? d.active === 'true' : true };
        const done = busy($('button[type=submit]', f));
        try {
          const saved = await api(trip ? '/admin/trips/' + encodeURIComponent(trip.id) : '/admin/trips', { method: trip ? 'PUT' : 'POST', body });
          toast((trip ? 'Đã lưu chuyến ' : 'Đã thêm chuyến ') + saved.id);
          box.innerHTML = '';
          trips = await api('/admin/trips', { query: { routeId: route.id } });
          table();
        } catch (err) {
          done();
          if (!showFieldErrors(f, err)) toast(err.message, 'error');
        }
      });
    };
    $('#add-trip').addEventListener('click', () => openForm(null));
    table();
  }

  // =====================================================================
  // Lập lịch chuyến
  // =====================================================================

  VG.route('/schedule', async ({ root, q, alive }) => {
    const t = today();
    const date = q.date && q.date >= t ? q.date : t;
    const [opts, vehicles, drivers, dayRuns] = await Promise.all([options(), api('/admin/vehicles', { query: { size: 100 } }),
      api('/admin/drivers', { query: { size: 100 } }), api('/admin/schedules', { query: { from: date } })]);
    if (!alive()) return;
    const routeList = opts.routes;
    const vList = vehicles.page.items.filter((v) => v.status === 'READY');
    const dList = drivers.page.items.filter((x) => x.status === 'READY');
    const routeId = q.route || (routeList[0] && routeList[0].id);
    root.innerHTML = `
      ${ui.pageHead('Lập lịch chuyến xe', null)}
      <div class="split">
        <form class="stack" id="sform" novalidate>
          <div class="card">
            <div class="chips">${ui.badge('Tạo lịch mới · phân công xe và tài xế')}</div>
            <h2>Gán tuyến, xe và tài xế</h2>
            <div class="form">
              ${ui.field('Tuyến đường', ui.select('routeId', routeList.map((r) => [r.id, `${r.code} · ${r.name}`]), routeId), { name: 'routeId', wide: true, for: 'f-routeId' })}
              ${ui.field('Xe vận hành', ui.select('vehicleId', [['', 'Chọn xe'], ...vList.map((v) => [v.id, `${v.code} · ${v.plate} · ${v.typeLabel}`])], q.vehicle || ''), { name: 'vehicleId', for: 'f-vehicleId' })}
              ${ui.field('Tài xế', ui.select('driverId', [['', 'Chưa phân công'], ...dList.map((x) => [x.id, `${x.code} · ${x.fullName} · hạng ${x.licenseClass}`])], q.driver || ''), { name: 'driverId', for: 'f-driverId' })}
              ${ui.field('Ngày khởi hành', ui.input('date', date, `type="date" min="${t}" max="${addDays(t, 60)}" required`), { name: 'date', for: 'f-date' })}
              ${ui.field('Giờ khởi hành', ui.input('departTime', q.time || '22:00', 'type="time" required'), { name: 'departTime', for: 'f-departTime' })}
              ${ui.field('Ngày đến dự kiến', '<input class="input" readonly id="arr-date" value="—">')}
              ${ui.field('Giờ đến dự kiến', '<input class="input" readonly id="arr-time" value="—">')}
              ${ui.field('Giá vé / ghế (đ)', ui.input('price', '', 'type="number" min="10000" step="1000" placeholder="Bắt buộc với chuyến tăng cường"'), { name: 'price', for: 'f-price' })}
              ${ui.field('Thời lượng (giờ)', ui.input('hours', '', 'type="number" min="0.25" step="0.25" placeholder="Theo tuyến"'), { name: 'durationMinutes', for: 'f-hours' })}
              ${ui.field('Số ghế mở bán', '<input class="input" readonly id="seats" value="—">')}
              ${ui.field('Ghi chú điều phối', ui.input('note', '', 'maxlength="300" placeholder="Vd: đón khách trước giờ chạy 30 phút tại bến"'), { name: 'note', wide: true, for: 'f-note' })}
              <div class="form-actions"><a class="btn" href="#/routes">Hủy</a><button class="btn primary" type="submit">Lưu lịch chuyến</button></div>
            </div>
          </div>
          ${ui.note('Kiểm tra thời gian nghỉ', 'Xe và tài xế cần nghỉ tối thiểu 30 phút giữa hai chuyến. Hệ thống kiểm tra trùng lịch, xe bảo trì, hạng và hạn GPLX trước khi lưu.', '', 'clock')}
        </form>
        <div class="stack" id="side"></div>
      </div>
      <div class="section-head"><h2>Lịch chạy ngày ${fmt.date(date)}</h2><span class="muted" style="font-size:13px">${dayRuns.length} chuyến · ${dayRuns.filter((r) => !r.driver).length} chuyến chưa có tài xế</span></div>
      ${ui.table([{ h: 'Mã chuyến' }, { h: 'Tuyến' }, { h: 'Khởi hành / đến' }, { h: 'Xe' }, { h: 'Tài xế' }, { h: 'Ghế đã bán' }, { h: '' }],
        dayRuns.length ? dayRuns.map((r) => `<tr><td class="strong">${esc(r.code)}${r.oneOff ? ' ' + ui.badge('Tăng cường', 'orange') : ''}</td><td>${esc(r.routeName)}</td>
          <td>${esc(r.departTime)} / ${esc(r.arriveTime)}${r.arriveDayOffset ? ' +' + r.arriveDayOffset : ''}</td><td>${r.vehicle ? esc(r.vehicle.code) : '—'}</td>
          <td>${r.driver ? esc(r.driver.fullName) : '<span class="state warn">Chưa phân công</span>'}</td><td>${r.seatsSold} / ${r.seatsTotal}</td>
          <td>${r.state === 'SCHEDULED' ? `<button class="link-btn edit" data-pick="${r.routeId}|${r.departTime}">Chọn</button>` : ui.runState(r)}</td></tr>`).join('')
          : `<tr><td colspan="7">${ui.empty('Chưa có chuyến nào ngày này')}</td></tr>`, '')}`;

    const form = $('#sform');
    let timer, seq = 0, last = null;
    const body = () => {
      const d = formData(form);
      return { routeId: Number(d.routeId), vehicleId: d.vehicleId ? Number(d.vehicleId) : null, driverId: d.driverId ? Number(d.driverId) : null,
        date: d.date, departTime: d.departTime, price: d.price ? Number(d.price) : null,
        durationMinutes: d.hours ? Math.round(Number(d.hours) * 60) : null, note: d.note };
    };
    const side = $('#side');
    const renderSide = (b, p, err) => {
      const v = vList.find((x) => x.id === b.vehicleId), drv = dList.find((x) => x.id === b.driverId);
      const route = routeList.find((r) => r.id === b.routeId);
      if (!p) {
        side.innerHTML = `<div class="card"><h3>Tóm tắt lịch mới</h3><p class="sub">${err ? esc(err) : 'Chọn xe để xem giờ đến dự kiến và kiểm tra trùng lịch.'}</p></div>`;
        return;
      }
      $('#arr-date').value = fmt.date(fmt.dateOf(p.arriveAt));
      $('#arr-time').value = fmt.time(p.arriveAt);
      $('#seats').value = p.seats;
      const ok = !p.errors.length;
      side.innerHTML = `<div class="card"><h3>Tóm tắt lịch mới</h3>
        <div class="kv"><div><span>Tuyến</span><b>${esc(route ? route.name : '')}</b></div>
        <div><span>Nhà xe</span><b>${esc(p.existingTrip ? p.existingTrip.operator : v ? v.operator : '—')}</b></div>
        <div><span>Loại xe</span><b>${esc(v ? v.typeLabel : '—')}</b></div>
        <div><span>Khởi hành</span><b>${fmt.dateTime(p.departAt)}</b></div><div><span>Đến nơi</span><b>${fmt.dateTime(p.arriveAt)}</b></div>
        ${p.existingTrip ? `<div><span>Chuyến</span><b>${esc(p.existingTrip.id)} (cố định)</b></div><div><span>Điểm đón</span><b>${esc(p.existingTrip.pickupStation.name)}</b></div><div><span>Điểm trả</span><b>${esc(p.existingTrip.dropoffStation.name)}</b></div>` : '<div><span>Chuyến</span><b>Tăng cường, chỉ chạy ngày này</b></div>'}
        <div><span>Giá vé</span><b class="hl">${p.price ? fmt.money(p.price) + ' / ghế' : '—'}</b></div></div>
        <div class="chips">${ok ? ui.badge(`${v ? v.code : 'Xe'}${drv ? ' và ' + drv.code : ''} không trùng lịch`) : ui.badge('Chưa lưu được', 'danger')}${drv ? '' : ui.badge('Chưa có tài xế', 'orange')}</div></div>
        ${p.errors.length ? ui.note('Cần xử lý trước khi lưu', '<ul>' + p.errors.map((x) => '<li>' + esc(x) + '</li>').join('') + '</ul>', 'danger', 'circle-alert') : ''}
        ${p.warnings.length ? ui.note('Lưu ý', '<ul>' + p.warnings.map((x) => '<li>' + esc(x) + '</li>').join('') + '</ul>', 'warn', 'circle-alert') : ''}
        ${v ? `<div class="card"><h3>Lịch liên quan của ${esc(v.code)}</h3>${p.vehicleRuns.length ? `<div class="kv">${p.vehicleRuns.map((r) => `<div><span>${esc(r.code)}</span><b>${fmt.dm(r.date)} · ${esc(r.departTime)} → ${esc(r.arriveTime)}</b></div>`).join('')}</div>` : '<p class="sub">Không có chuyến nào trong ngày trước và sau.</p>'}</div>` : ''}`;
      $('button[type=submit]', form).disabled = !ok;
    };
    const check = () => {
      clearTimeout(timer);
      timer = setTimeout(async () => {
        const b = body();
        const my = ++seq;
        $('#seats').value = (vList.find((x) => x.id === b.vehicleId) || {}).totalSeats || '—';
        if (!b.vehicleId || !b.date || !b.departTime) { renderSide(b, null); return; }
        try {
          const p = await api('/admin/schedules/preview', { method: 'POST', body: b });
          if (my !== seq) return;
          last = p;
          renderSide(b, p);
          if (p.existingTrip && !$('#f-price').value) $('#f-price').placeholder = 'Theo chuyến ' + p.existingTrip.id + ': ' + fmt.money(p.existingTrip.price);
        } catch (err) {
          if (my === seq) renderSide(b, null, err.message);
        }
      }, 250);
    };
    form.addEventListener('input', check);
    form.addEventListener('change', check);
    check();
    $$('[data-pick]', root).forEach((b) => b.addEventListener('click', () => {
      const [rid, time] = b.dataset.pick.split('|');
      $('#f-routeId').value = rid;
      $('#f-departTime').value = time;
      check();
      form.scrollIntoView({ behavior: 'smooth' });
    }));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const b = body();
      if (!b.vehicleId) { showFieldErrors(form, { fields: { vehicleId: 'Vui lòng chọn xe' } }); return; }
      const done = busy($('button[type=submit]', form));
      try {
        const run = await api('/admin/schedules', { method: 'POST', body: b });
        toast(`Đã lưu lịch chuyến ${run.code}`);
        location.hash = `#/routes?route=${run.routeId}&date=${run.date}`;
      } catch (err) {
        done();
        if (!showFieldErrors(form, err)) toast(err.message, 'error');
      }
    });
  }, { nav: 'schedule', crumb: 'Lập lịch chuyến' });

  // =====================================================================
  // Top tuyến bán chạy
  // =====================================================================

  function rangeFilter(q, defFrom, defTo, extra) {
    return `<form class="filters" id="rf">
      <div class="search">${icon('search', 'ic-18')}<input type="date" name="from" value="${q.from || defFrom}" aria-label="Từ ngày" style="border:0;outline:0;background:none">–<input type="date" name="to" value="${q.to || defTo}" max="${today()}" aria-label="Đến ngày" style="border:0;outline:0;background:none"></div>
      ${ui.select('preset', [['', 'Chọn nhanh'], ['month', 'Tháng này'], ['last-month', 'Tháng trước'], ['30', '30 ngày qua'], ['90', '90 ngày qua'], ['year', 'Năm nay']], '', 'aria-label="Chọn nhanh khoảng thời gian"')}
      ${extra}</form>`;
  }

  function bindRange(path, q) {
    const f = $('#rf');
    const t = today();
    const go = (patch) => {
      const params = Object.assign({}, formData(f), patch);
      delete params.preset;
      delete params.model;
      location.hash = '#' + path + '?' + new URLSearchParams(params).toString();
    };
    f.addEventListener('change', (e) => {
      if (e.target.name === 'preset') {
        const v = e.target.value;
        const first = t.slice(0, 8) + '01';
        const ranges = {
          month: [first, t], 'last-month': [addDays(first, -1).slice(0, 8) + '01', addDays(first, -1)],
          30: [addDays(t, -29), t], 90: [addDays(t, -89), t], year: [t.slice(0, 5) + '01-01', t],
        };
        if (ranges[v]) go({ from: ranges[v][0], to: ranges[v][1] });
        return;
      }
      go({});
    });
  }

  VG.route('/top-routes', async ({ root, q, alive }) => {
    const t = today();
    const d = await api('/admin/stats/top-routes', { query: { from: q.from || t.slice(0, 8) + '01', to: q.to || t, sort: q.sort } });
    if (!alive()) return;
    const max = Math.max(1, ...d.items.map((r) => r.tickets));
    const h = d.highlight;
    root.innerHTML = `
      ${ui.pageHead('Top tuyến bán chạy', null, '<button class="btn primary" id="export">Xuất bảng xếp hạng</button>')}
      ${rangeFilter({ from: d.from, to: d.to }, d.from, d.to, ui.select('sort', [['TICKETS', 'Xếp theo vé bán'], ['REVENUE', 'Xếp theo doanh thu'], ['FILL', 'Xếp theo lấp đầy']], d.sort, 'aria-label="Sắp xếp"'))}
      ${d.includesDemo ? `<div>${ui.badge('SỐ LIỆU MINH HỌA · CÙNG KỲ VỚI DASHBOARD', 'orange')}</div>` : ''}
      <div class="stats three">
        ${ui.stat('Tổng vé bán · ' + d.items.length + ' tuyến', 'ticket', fmt.num(d.totalTickets), 'Tổng ' + fmt.num(d.totalSeats) + ' ghế mở bán')}
        ${ui.stat('Doanh thu trong kỳ', 'wallet', fmt.money(d.totalRevenue), 'Giá vé thực thu bình quân: ' + fmt.money(d.avgPrice))}
        ${ui.stat('Tuyến dẫn đầu', 'trophy', d.leader ? esc(d.leader.name.split(' → ')[1] || d.leader.name) : '—', d.leader ? fmt.pct(d.totalTickets ? (100 * d.leader.tickets) / d.totalTickets : 0) + ' tổng số vé bán' : 'Chưa có vé trong kỳ')}
      </div>
      ${ui.table([{ h: 'Xếp hạng / tuyến' }, { h: 'Vé bán' }, { h: 'Ghế mở bán' }, { h: 'Lấp đầy' }, { h: 'Doanh thu' }],
        d.items.length ? d.items.map((r) => `<tr><td class="strong">${String(r.rank).padStart(2, '0')} · ${esc(r.name)}</td><td>${fmt.num(r.tickets)}</td><td>${fmt.num(r.seats)}</td><td>${fmt.pct(r.fillRate)}</td><td>${fmt.money(r.revenue)}</td></tr>`).join('')
          : `<tr><td colspan="5">${ui.empty('Chưa có vé trong kỳ này', 'Chọn khoảng thời gian khác.')}</td></tr>`,
        ui.pager(`${d.items.length} tuyến · Tổng vé bán ${fmt.num(d.totalTickets)} · Tổng doanh thu ${fmt.money(d.totalRevenue)}`, 1, 1))}
      <div class="split" style="grid-template-columns:1fr 1fr">
        <div class="card"><h3>Phân bố vé theo tuyến</h3><div class="hbars">${d.items.map((r) => `<div class="hbar"><span>${esc(r.name.split(' → ')[1] || r.name)}</span><div class="track"><i style="width:${(100 * r.tickets) / max}%"></i></div><b>${fmt.num(r.tickets)}</b></div>`).join('') || '<p class="sub">Chưa có dữ liệu.</p>'}</div></div>
        <div class="card">${h ? `${ui.badge('CHUYẾN NỔI BẬT TRONG KỲ')}<h2>${esc(h.operator)} · ${esc(h.code)}</h2>
          <div class="kv"><div><b>${esc(h.routeName)}</b></div><div><span>Khởi hành</span><b>${fmt.dateTime(h.departAt)}</b></div>
          <div><span>Vé / sức chứa</span><b class="hl">${h.sold} / ${h.total} ghế</b></div><div><span>Tỷ lệ lấp đầy</span><b class="hl">${fmt.pct(h.fillRate)}</b></div></div>
          <p class="sub">Chuyến thuộc tuyến dẫn đầu, lấp đầy cao nhất trong kỳ; số vé đã nằm trong tổng ở trên.</p>` : ui.empty('Chưa có chuyến nổi bật')}</div>
      </div>`;
    bindRange('/top-routes', q);
    $('#export').addEventListener('click', () => {
      download(`vego-top-tuyen-${d.from}_${d.to}.csv`, [['Top tuyến bán chạy', fmt.date(d.from) + ' – ' + fmt.date(d.to)], [],
        ['Xếp hạng', 'Mã tuyến', 'Tuyến', 'Vé bán', 'Ghế mở bán', 'Lấp đầy (%)', 'Doanh thu (đ)'],
        ...d.items.map((r) => [r.rank, r.code, r.name, r.tickets, r.seats, r.fillRate, r.revenue])]);
      toast('Đã xuất bảng xếp hạng CSV');
    });
  }, { nav: 'top-routes', crumb: 'Top tuyến bán chạy' });

  // =====================================================================
  // Báo cáo đề xuất
  // =====================================================================

  VG.route('/suggestions', async ({ root, q, alive }) => {
    const t = today();
    const d = await api('/admin/stats/suggestions', { query: { from: q.from || t.slice(0, 8) + '01', to: q.to || t, group: q.group } });
    if (!alive()) return;
    const imp = fmt.change(d.impressions, d.impressionsPrev);
    const lastWeek = d.weekly.length - 1;
    const m = d.model;
    root.innerHTML = `
      ${ui.pageHead('Hiệu quả đề xuất chuyến', null, '<button class="btn primary" id="export">Xuất báo cáo</button>')}
      ${rangeFilter({ from: d.from, to: d.to }, d.from, d.to,
        ui.select('group', [['ALL', 'Tất cả nhóm khách'], ...d.groups.map((g) => [g.group, g.label])], d.group, 'aria-label="Nhóm khách"')
        + ui.select('model', [[m.version, 'Mô hình ' + m.version]], m.version, 'aria-label="Mô hình" disabled'))}
      <div class="chips">${ui.badge('Gợi ý "Dành riêng cho bạn" trong app')}${d.includesDemo ? ui.badge('SỐ LIỆU MINH HỌA', 'orange') : ''}</div>
      <div class="stats">
        ${ui.stat('Lượt hiển thị đề xuất', 'eye', fmt.num(d.impressions), imp.text, imp.cls)}
        ${ui.stat('Lượt nhấp', 'pointer', fmt.num(d.clicks), 'Tỷ lệ nhấp: ' + fmt.pct(d.ctr))}
        ${ui.stat('Vé từ đề xuất', 'ticket', fmt.num(d.bookings), fmt.pct(d.conversion) + ' lượt nhấp chuyển thành vé')}
        ${ui.stat('Doanh thu đóng góp', 'wallet', fmt.money(d.revenue), fmt.pct(d.revenueShare) + ' doanh thu toàn hệ thống')}
      </div>
      <div class="split">
        <div class="card">
          <div class="card-head"><h3>Xu hướng tỷ lệ nhấp đề xuất</h3>${ui.badge('Tỷ lệ nhấp (%)')}</div>
          <p class="sub">12 tuần gần nhất · Tuần cuối ${fmt.pct(d.weekly[lastWeek] ? d.weekly[lastWeek].ctr : 0)}</p>
          ${ui.bars(d.weekly.map((w, i) => ({ value: w.ctr, text: w.ctr.toLocaleString('vi-VN', { maximumFractionDigits: 1 }), label: String(i + 1).padStart(2, '0'), title: `Tuần từ ${fmt.date(w.weekStart)}: ${fmt.num(w.clicks)} nhấp / ${fmt.num(w.impressions)} hiển thị` })), { highlight: lastWeek, label: 'Tỷ lệ nhấp theo tuần' })}
        </div>
        <div class="card">${icon('sparkles')}<h3>Trạng thái mô hình</h3>
          <div class="chips">${ui.badge('Đang hoạt động · Ổn định')}</div>
          <div class="kv"><div><span>Phiên bản</span><b>${esc(m.version)}</b></div><div><span>Cập nhật gần nhất</span><b>${fmt.dateTime(m.updatedAt)}</b></div>
          <div><span>Dữ liệu lịch sử</span><b>${m.windowDays} ngày gần nhất</b></div><div><span>Lần cập nhật tới</span><b>Tính trực tiếp mỗi lần mở app</b></div></div>
          <p class="sub">${esc(m.description)}</p></div>
      </div>
      <div class="section-head"><h2>Hiệu quả theo nhóm hành khách</h2></div>
      ${ui.table([{ h: 'Nhóm hành khách' }, { h: 'Hiển thị' }, { h: 'Lượt nhấp' }, { h: 'Vé đặt' }, { h: 'Tỷ lệ nhấp' }],
        d.groups.map((g) => `<tr><td class="strong">${esc(g.label)}</td><td>${fmt.num(g.impressions)}</td><td>${fmt.num(g.clicks)}</td><td>${fmt.num(g.bookings)}</td><td>${fmt.pct(g.ctr)}</td></tr>`).join(''),
        ui.pager(`Tổng: ${fmt.num(d.groups.reduce((s, g) => s + g.impressions, 0))} hiển thị · ${fmt.num(d.groups.reduce((s, g) => s + g.clicks, 0))} lượt nhấp · ${fmt.num(d.groups.reduce((s, g) => s + g.bookings, 0))} vé đặt`, 1, 1))}
      ${ui.note('Đọc báo cáo đúng ngữ cảnh', `Vé từ đề xuất là vé khách đặt đúng chuyến trong 48 giờ sau khi bấm vào gợi ý; ${fmt.num(d.bookings)} vé và ${fmt.money(d.revenue)} là phần đóng góp nằm trong tổng doanh thu của dashboard, không cộng thêm. Khách quay lại: có chuyến đã đi trong 90 ngày; khách chưa đi gần đây: chuyến gần nhất đã hơn 90 ngày; khách mới: chưa đi chuyến nào hoặc chưa đăng nhập.`)}`;
    bindRange('/suggestions', q);
    $('#export').addEventListener('click', () => {
      download(`vego-de-xuat-${d.from}_${d.to}.csv`, [['Báo cáo đề xuất', fmt.date(d.from) + ' – ' + fmt.date(d.to)], [],
        ['Lượt hiển thị', d.impressions], ['Lượt nhấp', d.clicks], ['Tỷ lệ nhấp (%)', d.ctr], ['Vé từ đề xuất', d.bookings], ['Doanh thu đóng góp (đ)', d.revenue], [],
        ['Tuần bắt đầu', 'Hiển thị', 'Nhấp', 'Tỷ lệ nhấp (%)'], ...d.weekly.map((w) => [w.weekStart, w.impressions, w.clicks, w.ctr]), [],
        ['Nhóm khách', 'Hiển thị', 'Nhấp', 'Vé đặt', 'Tỷ lệ nhấp (%)'], ...d.groups.map((g) => [g.label, g.impressions, g.clicks, g.bookings, g.ctr])]);
      toast('Đã xuất báo cáo CSV');
    });
  }, { nav: 'suggestions', crumb: 'Báo cáo đề xuất' });

  // =====================================================================
  // Đổi mật khẩu
  // =====================================================================

  VG.route('/password', async ({ root }) => {
    const u = VG.getSession().user || {};
    root.innerHTML = `
      ${ui.pageHead('Đổi mật khẩu', null)}
      <div class="split wide-side">
        <form class="card" id="pform" novalidate>
          <h2>Bảo mật tài khoản quản trị</h2>
          <p class="sub">Sau khi đổi, các phiên đăng nhập khác của tài khoản này sẽ bị đăng xuất.</p>
          <div class="form" style="grid-template-columns:1fr">
            ${ui.field('Mật khẩu hiện tại', ui.password('currentPassword', 'autocomplete="current-password" required'), { name: 'currentPassword', for: 'f-currentPassword' })}
            ${ui.field('Mật khẩu mới', ui.password('newPassword', 'autocomplete="new-password" required'), { name: 'newPassword', for: 'f-newPassword', hint: 'Tối thiểu 8 ký tự, gồm chữ hoa, chữ thường và số.' })}
            ${ui.field('Nhập lại mật khẩu mới', ui.password('confirm', 'autocomplete="new-password" required'), { name: 'confirm', for: 'f-confirm' })}
            <div class="form-actions"><a class="btn" href="#/overview">Hủy</a><button class="btn primary" type="submit">Cập nhật mật khẩu</button></div>
          </div>
        </form>
        <div class="stack">
          <div class="card">${icon('shield-check')}<h3>Tài khoản quản trị</h3>
            <div class="kv"><div><span>Họ tên</span><b>${esc(u.fullName || '')}</b></div><div><span>Email</span><b>${esc(u.email || '')}</b></div></div>
            <div class="chips">${ui.badge('Quyền quản trị')}</div></div>
          ${ui.note('Không chia sẻ mật khẩu', 'Mỗi nhân sự dùng tài khoản riêng. Mật khẩu quản trị không được gửi qua tin nhắn hay email.')}
          ${ui.note('Quên mật khẩu hiện tại?', 'Đăng xuất và chọn "Quên mật khẩu?" ở màn đăng nhập để nhận link đặt lại.')}
        </div>
      </div>`;
    bindPasswordToggles(root);
    const form = $('#pform');
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const d = formData(form);
      const errs = {};
      if (!d.currentPassword) errs.currentPassword = 'Vui lòng nhập mật khẩu hiện tại';
      if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,72}$/.test(d.newPassword || '')) errs.newPassword = 'Mật khẩu mới cần tối thiểu 8 ký tự, gồm chữ hoa, chữ thường và số';
      if (d.newPassword !== d.confirm) errs.confirm = 'Mật khẩu nhập lại chưa khớp';
      if (Object.keys(errs).length) { showFieldErrors(form, { fields: errs }); return; }
      const done = busy($('button[type=submit]', form), 'Đang cập nhật…');
      try {
        const auth = await api('/users/me/password', { method: 'PUT', body: { currentPassword: d.currentPassword, newPassword: d.newPassword, device: 'Web admin' } });
        setSession(auth);
        form.reset();
        showFieldErrors(form, null);
        toast('Đã cập nhật mật khẩu');
      } catch (err) {
        if (!showFieldErrors(form, err)) toast(err.message, 'error');
      } finally { done(); }
    });
  }, { nav: 'password', crumb: 'Đổi mật khẩu' });
})();
