// Laporan & analisa pesanan D.VA SKATE (dipakai oleh admin/index.html).
// Omzet hanya dihitung dari pesanan berstatus Selesai (sama dengan aturan tagihan).

const BLN = ['Jan','Feb','Mar','Apr','Mei','Jun','Jul','Agu','Sep','Okt','Nov','Des'];
const BULAN = ['Januari','Februari','Maret','April','Mei','Juni','Juli','Agustus','September','Oktober','November','Desember'];
const SIZE_ORDER = ['12S','11S','10S','9S','8S','7S','6S','5S','4S','3S','2S','XXS','XS','S','M','L','XL','2XL','XXL','3XL','Custom','Lainnya'];

let state = { year: 'all', metric: 'omzet' };

const css = `
.rp{--s1:#2a78d6;--s2:#eb6834;--s3:#1baf7a;--grid:#ececf1;--axis:#8a8a96}
.rp-filters{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:14px}
.rp-filters select{padding:10px 12px;border:1px solid #dcdce5;border-radius:10px;background:#fff;font:inherit;font-weight:700}
.rp-seg{display:inline-flex;background:#f0f0f5;border-radius:10px;padding:3px}
.rp-seg button{border:0;background:none;padding:7px 12px;border-radius:8px;font-weight:800;font-size:12px;color:#6a6a78}
.rp-seg button.on{background:#fff;color:#17171c;box-shadow:0 1px 3px #0001}
.rp-kpis{display:grid;grid-template-columns:repeat(6,1fr);gap:10px;margin-bottom:16px}
.rp-kpi{background:#fff;border:1px solid var(--line);border-radius:13px;padding:14px}
.rp-kpi span{display:block;color:var(--muted);font-size:11px;font-weight:700}
.rp-kpi strong{display:block;font-size:22px;margin-top:3px;white-space:nowrap}
.rp-kpi small{display:block;color:var(--muted);font-size:11px;margin-top:2px}
.rp-grid{display:grid;grid-template-columns:1.6fr 1fr;gap:16px}
.rp-card{background:#fff;border:1px solid var(--line);border-radius:16px;padding:18px;margin-bottom:16px;min-width:0}
.rp-card h3{margin:0 0 3px;font-size:15px}.rp-card .muted{margin-bottom:12px}
.rp-insight{list-style:none;margin:0;padding:0;display:grid;gap:9px}
.rp-insight li{display:flex;gap:10px;font-size:13px;line-height:1.45}.rp-insight li b{white-space:nowrap}
.rp-insight i{font-style:normal;flex:none;width:26px;height:26px;border-radius:8px;background:#fdeef5;display:grid;place-items:center}
.rp-svg{width:100%;height:auto;display:block;overflow:visible}
.rp-svg text{font-size:11px;fill:var(--axis);font-family:inherit}
.rp-svg .bar{transition:opacity .15s}.rp-svg .hit:hover+.bar,.rp-svg .bar:hover{opacity:.8}
.rp-tip{position:fixed;pointer-events:none;background:#17171c;color:#fff;padding:8px 10px;border-radius:9px;font-size:12px;z-index:30;box-shadow:0 6px 20px #0003;white-space:nowrap}
.rp-tip b{display:block;font-size:13px}
.rp-hbar{display:grid;grid-template-columns:72px 1fr auto;gap:10px;align-items:center;font-size:12px;margin:6px 0}
.rp-hbar .track{height:14px;background:#f3f3f7;border-radius:4px;overflow:hidden}
.rp-hbar .fill{height:100%;border-radius:0 4px 4px 0;background:var(--s1)}
.rp-hbar .lbl{font-weight:800;color:#2b2b35}.rp-hbar .val{color:#55555f;font-variant-numeric:tabular-nums;white-space:nowrap}
.rp-mix{display:flex;height:22px;border-radius:6px;overflow:hidden;gap:2px;margin:6px 0 12px}
.rp-mix div{height:100%}
.rp-legend{display:flex;flex-wrap:wrap;gap:12px;font-size:12px;color:#44444e}
.rp-legend span{display:inline-flex;align-items:center;gap:6px}.rp-legend span:before{content:'';width:10px;height:10px;border-radius:3px;background:var(--c)}
.rp-table{width:100%;border-collapse:collapse;min-width:0}
.rp-table th,.rp-table td{padding:8px 6px;font-size:12px;border-bottom:1px solid var(--line);text-align:left}
.rp-table td.n,.rp-table th.n{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap}
.rp-scroll{max-height:340px;overflow:auto}
.rp .rp-table thead{display:table-header-group}.rp .rp-table tbody tr{display:table-row;border:0;padding:0;margin:0;border-radius:0}.rp .rp-table tbody td{display:table-cell;padding:8px 6px}.rp .rp-table tbody td:before{content:none}
.rp-3{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}
@media(max-width:1000px){.rp-kpis{grid-template-columns:repeat(3,1fr)}.rp-grid,.rp-3{grid-template-columns:1fr}}
@media(max-width:480px){.rp-kpis{grid-template-columns:repeat(2,1fr)}.rp-kpi strong{font-size:18px}}
`;

function ensureStyle() {
  if (document.getElementById('rp-style')) return;
  const st = document.createElement('style'); st.id = 'rp-style'; st.textContent = css; document.head.appendChild(st);
}
let tip;
function showTip(e, html) {
  if (!tip) { tip = document.createElement('div'); tip.className = 'rp-tip'; document.body.appendChild(tip); }
  tip.innerHTML = html; tip.style.display = 'block';
  const x = Math.min(e.clientX + 14, innerWidth - tip.offsetWidth - 8);
  tip.style.left = x + 'px'; tip.style.top = (e.clientY - tip.offsetHeight - 12) + 'px';
}
function hideTip() { if (tip) tip.style.display = 'none'; }

const short = n => n >= 1e9 ? (n / 1e9).toFixed(1).replace('.', ',') + ' M' : n >= 1e6 ? (n / 1e6).toFixed(n >= 1e7 ? 0 : 1).replace('.', ',') + ' jt' : n >= 1e3 ? Math.round(n / 1e3) + ' rb' : String(n);
const pct = (a, b) => b ? Math.round(a / b * 100) : 0;

function noteField(note, key) {
  const m = String(note || '').match(new RegExp(key + ':\\s*([^·]+)'));
  return m ? m[1].trim() : '';
}
function customerKey(o) {
  const src = String(o.customer_phone || '') + ' ' + String(o.shipping_address || '');
  const m = src.replace(/[\s.\-()]/g, '').match(/(?:\+?62|0)8\d{7,12}/);
  if (m) return 'p' + m[0].replace(/^\+?62/, '0');
  const n = String(o.customer_name || '').trim().toLowerCase();
  return n && n !== 'tanpa nama' ? 'n' + n : null;
}

// Grafik batang vertikal (satu seri) dengan tooltip.
function barChart(points, fmt, color, width) {
  const W = Math.max(300, Math.min(900, width || 720)), H = 240, L = 46, R = 8, T = 12, B = 30;
  const max = Math.max(1, ...points.map(p => p.v));
  const step = niceStep(max);
  const top = Math.ceil(max / step) * step;
  const iw = W - L - R, ih = H - T - B;
  const bw = iw / Math.max(points.length, 1);
  const barW = Math.max(4, Math.min(38, bw - 2));
  let g = '';
  for (let v = 0; v <= top + 1e-9; v += step) {
    const y = T + ih - v / top * ih;
    g += `<line x1="${L}" x2="${W - R}" y1="${y}" y2="${y}" stroke="var(--grid)" stroke-width="1"/>`;
    g += `<text x="${L - 8}" y="${y + 4}" text-anchor="end">${fmt.axis(v)}</text>`;
  }
  const every = Math.ceil(points.length / Math.max(4, Math.floor(W / 52)));
  points.forEach((p, i) => {
    const h = p.v / top * ih, x = L + i * bw + (bw - barW) / 2, y = T + ih - h;
    const r = Math.min(4, barW / 2, h);
    const path = h > 0 ? `M${x},${T + ih}V${y + r}Q${x},${y} ${x + r},${y}H${x + barW - r}Q${x + barW},${y} ${x + barW},${y + r}V${T + ih}Z` : '';
    g += `<rect class="hit" x="${L + i * bw}" y="${T}" width="${bw}" height="${ih}" fill="transparent" data-tip="${encodeURIComponent(`<b>${p.label}</b>${fmt.tip(p)}`)}"/>`;
    if (path) g += `<path class="bar" d="${path}" fill="${color}" pointer-events="none"/>`;
    if (i % every === 0) g += `<text x="${L + i * bw + bw / 2}" y="${H - 10}" text-anchor="middle">${p.tick}</text>`;
  });
  g += `<line x1="${L}" x2="${W - R}" y1="${T + ih}" y2="${T + ih}" stroke="#cfcfd8"/>`;
  return `<svg class="rp-svg" viewBox="0 0 ${W} ${H}" role="img">${g}</svg>`;
}
function niceStep(max) {
  const raw = max / 4, p = Math.pow(10, Math.floor(Math.log10(raw))), n = raw / p;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * p;
}
function hbars(rows, fmt, color) {
  const max = Math.max(1, ...rows.map(r => r.v));
  return rows.map(r => `<div class="rp-hbar" data-tip="${encodeURIComponent(`<b>${r.label}</b>${fmt(r)}`)}"><span class="lbl">${r.label}</span><div class="track"><div class="fill" style="width:${Math.max(1, r.v / max * 100)}%;background:${color}"></div></div><span class="val">${r.text}</span></div>`).join('');
}

let last = null, rt, lastW = 0;
addEventListener('resize', () => { clearTimeout(rt); rt = setTimeout(() => { if (last && last[0].offsetParent && Math.abs(last[0].clientWidth - lastW) > 40) renderReport(...last); }, 200); });
export function renderReport(el, allOrders, h) {
  ensureStyle(); last = [el, allOrders, h]; lastW = el.clientWidth;
  const { calcOrder, totalOf, money, esc, billed, MODEL_LABEL } = h;
  const years = [...new Set(allOrders.map(o => new Date(o.created_at).getFullYear()))].sort();
  if (state.year !== 'all' && !years.includes(+state.year)) state.year = 'all';
  const orders = allOrders.filter(o => state.year === 'all' || new Date(o.created_at).getFullYear() === +state.year);

  // Hitung semua angka sekali.
  const rows = orders.map(o => {
    const c = calcOrder(o.size_details);
    const d = new Date(o.created_at);
    return { o, c, d, pcs: c.pcs, omzet: billed(o) ? totalOf(o) : 0, ym: d.getFullYear() * 100 + d.getMonth() };
  });
  const totalPcs = rows.reduce((a, r) => a + r.pcs, 0);
  const omzet = rows.reduce((a, r) => a + r.omzet, 0);
  const selesai = rows.filter(r => billed(r.o));
  const avg = selesai.length ? omzet / selesai.length : 0;
  const pending = rows.filter(r => r.o.status !== 'Selesai');
  const pendingPcs = pending.reduce((a, r) => a + r.pcs, 0);

  // Pelanggan
  const cust = new Map();
  rows.forEach(r => {
    const k = customerKey(r.o); if (!k) return;
    const c = cust.get(k) || { name: r.o.customer_name, phone: '', orders: 0, pcs: 0, omzet: 0, last: r.d };
    c.orders++; c.pcs += r.pcs; c.omzet += r.omzet; if (r.d > c.last) c.last = r.d;
    if (k[0] === 'p') c.phone = k.slice(1);
    cust.set(k, c);
  });
  const custs = [...cust.values()];
  const repeat = custs.filter(c => c.orders > 1);

  // Per bulan (isi bulan kosong)
  const byMonth = new Map();
  rows.forEach(r => { const m = byMonth.get(r.ym) || { pcs: 0, omzet: 0, orders: 0 }; m.pcs += r.pcs; m.omzet += r.omzet; m.orders++; byMonth.set(r.ym, m); });
  const months = [];
  if (byMonth.size) {
    const keys = [...byMonth.keys()].sort((a, b) => a - b);
    let y = Math.floor(keys[0] / 100), m = keys[0] % 100;
    const endY = Math.floor(keys.at(-1) / 100), endM = keys.at(-1) % 100;
    while (y < endY || (y === endY && m <= endM)) {
      const k = y * 100 + m, v = byMonth.get(k) || { pcs: 0, omzet: 0, orders: 0 };
      months.push({ k, y, m, ...v, label: BULAN[m] + ' ' + y, tick: BLN[m] + (m === 0 || months.length === 0 ? ' ' + String(y).slice(2) : '') });
      if (++m > 11) { m = 0; y++; }
    }
  }
  const bestMonth = months.reduce((b, m) => (!b || m[state.metric === 'omzet' ? 'omzet' : 'pcs'] > b[state.metric === 'omzet' ? 'omzet' : 'pcs'] ? m : b), null);

  // Model & ukuran
  const model = { pendek: 0, panjang: 0, rok: 0 };
  const sizes = new Map();
  rows.forEach(r => r.c.items.forEach(it => { model[it.model] += it.qty; sizes.set(it.size, (sizes.get(it.size) || 0) + it.qty); }));
  const sizeRows = [...sizes.entries()].sort((a, b) => b[1] - a[1]);
  const topSizes = sizeRows.filter(s => s[0] !== 'Lainnya').slice(0, 10);
  const unknownPcs = sizes.get('Lainnya') || 0;

  // Klub & desain (dari catatan admin)
  const tally = key => {
    const t = new Map();
    rows.forEach(r => { const v = noteField(r.o.admin_note, key); if (!v) return; const k = v.toLowerCase().replace(/\s+/g, ' '); const e = t.get(k) || { label: v, pcs: 0, orders: 0 }; e.pcs += r.pcs; e.orders++; t.set(k, e); });
    return [...t.values()].sort((a, b) => b.pcs - a.pcs);
  };
  const clubs = tally('Klub'), designs = tally('Desain');

  // Per PO
  const byPO = new Map();
  rows.forEach(r => { const k = r.o.po_number || '(tanpa PO)'; const e = byPO.get(k) || { po: k, pcs: 0, omzet: 0, orders: 0, d: r.d }; e.pcs += r.pcs; e.omzet += r.omzet; e.orders++; if (r.d < e.d) e.d = r.d; byPO.set(k, e); });
  const pos = [...byPO.values()].sort((a, b) => b.d - a.d);

  // Insight otomatis
  const ins = [];
  if (bestMonth) ins.push(['🏆', `Bulan terbaik: <b>${bestMonth.label}</b> dengan ${bestMonth.pcs} pcs${bestMonth.omzet ? ' dan omzet ' + money(bestMonth.omzet) : ''}.`]);
  const full = months.filter(m => m.orders);
  if (full.length >= 2) {
    const a = full.at(-1), b = full.at(-2);
    const ch = b.pcs ? Math.round((a.pcs - b.pcs) / b.pcs * 100) : 0;
    ins.push([ch >= 0 ? '📈' : '📉', `Bulan terakhir (${a.label}) ${a.pcs} pcs, ${ch >= 0 ? 'naik' : 'turun'} <b>${Math.abs(ch)}%</b> dibanding ${b.label} (${b.pcs} pcs).`]);
    const avgPcs = Math.round(full.reduce((s, m) => s + m.pcs, 0) / full.length);
    ins.push(['📦', `Rata-rata <b>${avgPcs} pcs per bulan</b> dari ${full.length} bulan aktif.`]);
  }
  if (topSizes.length) {
    const top3 = topSizes.filter(s => s[0] !== 'Lainnya' && s[0] !== 'Custom').slice(0, 3);
    if (top3.length) ins.push(['📏', `Ukuran paling laris: <b>${top3.map(s => s[0]).join(', ')}</b> (${pct(top3.reduce((a, s) => a + s[1], 0), totalPcs)}% dari semua pcs). Cocok untuk stok kain/pola.`]);
  }
  if (totalPcs) ins.push(['👕', `Komposisi model: <b>${pct(model.pendek, totalPcs)}% lengan pendek</b>, ${pct(model.panjang, totalPcs)}% lengan panjang, ${pct(model.rok, totalPcs)}% rok.`]);
  if (custs.length) ins.push(['🔁', `<b>${repeat.length} dari ${custs.length} pelanggan</b> (${pct(repeat.length, custs.length)}%) pesan lebih dari sekali.`]);
  if (clubs[0]) ins.push(['🛼', `Klub dengan pesanan terbanyak: <b>${esc(clubs[0].label)}</b> (${clubs[0].pcs} pcs).`]);
  if (pending.length) ins.push(['⏳', `<b>${pending.length} pesanan</b> (${pendingPcs} pcs) masih Pending/Produksi dan belum ditagih.`]);

  const metricPts = months.map(m => ({ label: m.label, tick: m.tick, v: state.metric === 'omzet' ? m.omzet : state.metric === 'pcs' ? m.pcs : m.orders, m }));
  const fmt = state.metric === 'omzet'
    ? { axis: v => v ? short(v) : '0', tip: p => `Omzet ${money(p.m.omzet)}<br>${p.m.pcs} pcs · ${p.m.orders} pesanan` }
    : state.metric === 'pcs'
      ? { axis: v => String(Math.round(v)), tip: p => `${p.m.pcs} pcs<br>${p.m.orders} pesanan${p.m.omzet ? ' · ' + money(p.m.omzet) : ''}` }
      : { axis: v => String(Math.round(v)), tip: p => `${p.m.orders} pesanan<br>${p.m.pcs} pcs` };
  const metricTitle = { omzet: 'Omzet per bulan', pcs: 'Jumlah pcs per bulan', orders: 'Jumlah pesanan per bulan' }[state.metric];

  const modelColors = { pendek: 'var(--s1)', panjang: 'var(--s2)', rok: 'var(--s3)' };
  const mixBar = totalPcs ? Object.keys(model).filter(k => model[k]).map(k => `<div style="width:${model[k] / totalPcs * 100}%;background:${modelColors[k]}" data-tip="${encodeURIComponent(`<b>${MODEL_LABEL[k]}</b>${model[k]} pcs · ${pct(model[k], totalPcs)}%`)}"></div>`).join('') : '';

  const yearOpts = ['<option value="all">Semua tahun</option>', ...years.map(y => `<option value="${y}"${String(y) === String(state.year) ? ' selected' : ''}>${y}</option>`)].join('');

  el.innerHTML = `<div class="rp">
  <div class="rp-filters">
    <select id="rpYear">${yearOpts}</select>
    <span class="muted" style="margin:0">Omzet dihitung dari pesanan berstatus <b>Selesai</b>.</span>
  </div>
  <div class="rp-kpis">
    <div class="rp-kpi"><span>Total pesanan</span><strong>${orders.length.toLocaleString('id-ID')}</strong><small>${selesai.length.toLocaleString('id-ID')} selesai</small></div>
    <div class="rp-kpi"><span>Total pcs</span><strong>${totalPcs.toLocaleString('id-ID')}</strong><small>skinsuit</small></div>
    <div class="rp-kpi"><span>Omzet</span><strong>${short(omzet).replace(' ', '&nbsp;')}</strong><small>${money(omzet)}</small></div>
    <div class="rp-kpi"><span>Rata-rata / pesanan</span><strong>${short(Math.round(avg))}</strong><small>${money(Math.round(avg))}</small></div>
    <div class="rp-kpi"><span>Pelanggan</span><strong>${custs.length.toLocaleString('id-ID')}</strong><small>${repeat.length} pesan ulang</small></div>
    <div class="rp-kpi"><span>Belum ditagih</span><strong>${pending.length}</strong><small>${pendingPcs} pcs pending/produksi</small></div>
  </div>
  <div class="rp-grid">
    <div class="rp-card">
      <div style="display:flex;justify-content:space-between;gap:10px;flex-wrap:wrap;align-items:flex-start">
        <div><h3>${metricTitle}</h3><div class="muted">Arahkan kursor / sentuh batang untuk detail.</div></div>
        <div class="rp-seg" id="rpMetric"><button data-m="omzet" class="${state.metric === 'omzet' ? 'on' : ''}">Omzet</button><button data-m="pcs" class="${state.metric === 'pcs' ? 'on' : ''}">Pcs</button><button data-m="orders" class="${state.metric === 'orders' ? 'on' : ''}">Pesanan</button></div>
      </div>
      ${months.length ? barChart(metricPts, fmt, 'var(--s1)', el.clientWidth - (innerWidth <= 480 ? 28 : 40) - (innerWidth > 1000 ? el.clientWidth * 0.4 : 0)) : '<div class="muted">Belum ada data.</div>'}
    </div>
    <div class="rp-card"><h3>Ringkasan & insight</h3><div class="muted">Dihitung otomatis dari data pesanan.</div>
      <ul class="rp-insight">${ins.map(([i, t]) => `<li><i>${i}</i><span>${t}</span></li>`).join('') || '<li>Belum ada data.</li>'}</ul></div>
  </div>
  <div class="rp-3">
    <div class="rp-card"><h3>Model</h3><div class="muted">${totalPcs.toLocaleString('id-ID')} pcs</div>
      <div class="rp-mix">${mixBar}</div>
      <div class="rp-legend">${Object.keys(model).map(k => `<span style="--c:${modelColors[k]}">${MODEL_LABEL[k]} · <b>${model[k]}</b> (${pct(model[k], totalPcs)}%)</span>`).join('')}</div>
    </div>
    <div class="rp-card"><h3>Ukuran terlaris</h3><div class="muted">10 teratas, dalam pcs${unknownPcs ? ` · ${unknownPcs} pcs ukurannya tidak tertulis jelas` : ''}</div>
      ${hbars(topSizes.map(([s, v]) => ({ label: s, v, text: v + ' pcs', }), ), r => `${r.v} pcs · ${pct(r.v, totalPcs)}%`, 'var(--s1)')}
    </div>
    <div class="rp-card"><h3>Klub teratas</h3><div class="muted">dari catatan "Klub"</div>
      ${clubs.length ? hbars(clubs.slice(0, 10).map(c => ({ label: esc(c.label).slice(0, 12), v: c.pcs, text: c.pcs + ' pcs' })), r => `${r.v} pcs`, 'var(--s1)') : '<div class="muted">Belum ada data klub.</div>'}
    </div>
  </div>
  <div class="rp-grid">
    <div class="rp-card"><h3>Rekap per PO</h3><div class="muted">${pos.length} PO · terbaru di atas</div>
      <div class="rp-scroll"><table class="rp-table"><thead><tr><th>No. PO</th><th class="n">Pesanan</th><th class="n">Pcs</th><th class="n">Omzet</th></tr></thead><tbody>
      ${pos.map(p => `<tr><td>${esc(p.po)}</td><td class="n">${p.orders}</td><td class="n">${p.pcs}</td><td class="n">${p.omzet ? money(p.omzet) : '—'}</td></tr>`).join('')}
      </tbody></table></div></div>
    <div class="rp-card"><h3>Pelanggan teratas</h3><div class="muted">berdasarkan jumlah pcs</div>
      <div class="rp-scroll"><table class="rp-table"><thead><tr><th>Pelanggan</th><th class="n">Order</th><th class="n">Pcs</th><th class="n">Omzet</th></tr></thead><tbody>
      ${custs.sort((a, b) => b.pcs - a.pcs || b.omzet - a.omzet).slice(0, 30).map(c => `<tr><td><b>${esc(c.name)}</b>${c.phone ? `<div><a class="phone" style="margin-top:3px;padding:3px 8px;font-size:11px" href="https://wa.me/62${c.phone.slice(1)}" target="_blank" rel="noopener">${c.phone}</a></div>` : ''}</td><td class="n">${c.orders}</td><td class="n">${c.pcs}</td><td class="n">${c.omzet ? money(c.omzet) : '—'}</td></tr>`).join('')}
      </tbody></table></div></div>
  </div>
  ${designs.length ? `<div class="rp-card"><h3>Desain terpopuler</h3><div class="muted">dari catatan "Desain", 10 teratas</div>${hbars(designs.slice(0, 10).map(d => ({ label: esc(d.label).slice(0, 12), v: d.pcs, text: d.pcs + ' pcs' })), r => `${r.v} pcs`, 'var(--s1)')}</div>` : ''}
  </div>`;

  el.querySelector('#rpYear').onchange = e => { state.year = e.target.value; renderReport(el, allOrders, h); };
  el.querySelectorAll('#rpMetric button').forEach(b => b.onclick = () => { state.metric = b.dataset.m; renderReport(el, allOrders, h); });
  el.querySelectorAll('[data-tip]').forEach(n => {
    n.addEventListener('mousemove', e => showTip(e, decodeURIComponent(n.dataset.tip)));
    n.addEventListener('mouseleave', hideTip);
    n.addEventListener('click', e => showTip(e, decodeURIComponent(n.dataset.tip)));
  });
}
