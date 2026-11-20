/* Diva Skate Racer — comments on admin announcements.
 * Loaded from index.html after the core game scripts.
 * Uses the existing acct {name, token, profile} session and existing rpc() adapter.
 */
(function () {
  'use strict';
  const MAX_BODY = 500;
  const $ = (id) => document.getElementById(id);
  const esc = (value) => String(value == null ? '' : value)
    .replace(/[&<>"']/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const dateLabel = (value) => {
    try { return new Date(value).toLocaleString('id-ID', {dateStyle:'short', timeStyle:'short'}); }
    catch (_) { return ''; }
  };
  const initials = (name) => esc((String(name || '?').trim()[0] || '?').toUpperCase());
  const avatar = (src, name) => {
    const value = String(src || '');
    if (/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(value) && value.length < 20000) {
      return '<img class="nwcAvatar" src="' + value + '" alt="">';
    }
    if (/^e:.{1,3}$/.test(value)) return '<span class="nwcAvatar nwcEmoji">' + esc(value.slice(2)) + '</span>';
    return '<span class="nwcAvatar">' + initials(name) + '</span>';
  };
  const style = document.createElement('style');
  style.textContent = `
    #newsModal .nwcWrap{margin:10px 0 1px 29px;padding-top:8px;border-top:1px solid #ffffff24}
    #newsModal .nwcHeading{font:800 11px "Exo 2",system-ui;color:#dceaff;margin-bottom:6px}
    #newsModal .nwcList{display:flex;flex-direction:column;gap:7px}
    #newsModal .nwcItem{display:flex;gap:7px;align-items:flex-start;min-width:0}
    #newsModal .nwcAvatar{width:27px;height:27px;min-width:27px;border-radius:50%;display:flex;align-items:center;justify-content:center;object-fit:cover;background:#dbeafe;color:#153b83;font:800 12px system-ui}
    #newsModal .nwcEmoji{font-size:17px}
    #newsModal .nwcContent{min-width:0;flex:1;background:#ffffff12;border-radius:9px;padding:6px 8px}
    #newsModal .nwcMeta{display:flex;gap:6px;align-items:center;flex-wrap:wrap;color:#adc8ee;font:700 10px system-ui}
    #newsModal .nwcBody{white-space:pre-wrap;overflow-wrap:anywhere;color:#fff;font:500 12px/1.4 system-ui;margin-top:2px}
    #newsModal .nwcDelete{background:transparent!important;color:#ffb8ae!important;border:0!important;padding:2px 4px!important;font:700 10px system-ui!important;cursor:pointer}
    #newsModal .nwcForm{display:flex;gap:6px;margin-top:7px;align-items:flex-end}
    #newsModal .nwcForm textarea{flex:1;min-width:0;resize:vertical;min-height:38px;max-height:100px;border:1px solid #90bdf766;border-radius:9px;background:#09295f;color:#fff;padding:7px;font:12px system-ui}
    #newsModal .nwcForm textarea::placeholder{color:#b6c9e8}
    #newsModal .nwcForm button{border:0;border-radius:9px;background:#e8302a;color:white;padding:9px 11px;font:italic 900 11px "Exo 2",system-ui;white-space:nowrap}
    #newsModal .nwcHint{font:10px system-ui;color:#b8cbea;margin-top:4px}
    #newsModal .nwcLogin{font:11px system-ui;color:#ffe2a1;margin-top:7px}
    #newsModal .nwcEmpty{font:11px system-ui;color:#b8cbea}
  `;
  document.head.appendChild(style);

  async function loadComments(newsId, panel) {
    const list = panel.querySelector('.nwcList');
    if (!list) return;
    list.innerHTML = '<div class="nwcEmpty">Memuat komentar…</div>';
    const result = await rpc('skate_news_comments_list', {p_news_id: newsId});
    if (!panel.isConnected) return;
    if (!result || !result.ok || !Array.isArray(result.rows)) {
      list.innerHTML = '<div class="nwcEmpty">Komentar belum dapat dimuat.</div>';
      return;
    }
    if (!result.rows.length) list.innerHTML = '<div class="nwcEmpty">Belum ada komentar.</div>';
    else list.innerHTML = result.rows.map((item) => {
      const own = !!(acct && String(item.name || '').toLowerCase() === acct.name.toLowerCase());
      const canDelete = own || (typeof isAdminAcct === 'function' && isAdminAcct());
      return `<div class="nwcItem">
        ${avatar(item.avatar, item.name)}
        <div class="nwcContent">
          <div class="nwcMeta"><b>${esc(item.name)}</b><span>${esc(dateLabel(item.created_at))}</span>
            ${canDelete ? `<button class="nwcDelete" type="button" data-nwc-del="${Number(item.id)}">HAPUS</button>` : ''}
          </div>
          <div class="nwcBody">${esc(item.body)}</div>
        </div>
      </div>`;
    }).join('');
    list.querySelectorAll('[data-nwc-del]').forEach((button) => {
      button.addEventListener('click', async () => {
        if (!acct) return;
        button.disabled = true;
        const res = await rpc('skate_news_comment_delete', {
          p_name: acct.name, p_token: acct.token, p_id: Number(button.dataset.nwcDel)
        });
        if (res && res.ok) await loadComments(newsId, panel);
        else { button.disabled = false; alert(res && res.err === 'forbidden' ? 'Komentar ini bukan milik akunmu.' : 'Gagal menghapus komentar.'); }
      });
    });
  }

  window.DivaNewsComments = {
    attach: function (modal) {
      if (!modal) return;
      /* Jangan mengunci modal secara global: baris rekor tidak punya ID skate_news,
         sedangkan pengumuman admin menggunakan ID asli tabel skate_news. */
      modal.querySelectorAll('.it[id^="nwm_adm"]').forEach((card) => {
        if (card.dataset.commentsAttached === '1') return;
        const id = Number(card.id.slice('nwm_adm'.length));
        if (!Number.isSafeInteger(id) || id <= 0) return;
        card.dataset.commentsAttached = '1';
        const panel = document.createElement('section');
        panel.className = 'nwcWrap';
        panel.innerHTML = `<div class="nwcHeading">💬 KOMENTAR</div>
          <div class="nwcList"></div>
          ${acct ? `<form class="nwcForm"><textarea maxlength="${MAX_BODY}" required placeholder="Tulis komentar…"></textarea><button type="submit">KIRIM</button></form><div class="nwcHint">Berkomentar sebagai ${esc(acct.name)} · maksimal ${MAX_BODY} karakter</div>` : '<div class="nwcLogin">Masuk ke akun Diva Skate untuk ikut berkomentar.</div>'}`;
        card.appendChild(panel);
        loadComments(id, panel);
        const form = panel.querySelector('form');
        if (form) form.addEventListener('submit', async (event) => {
          event.preventDefault();
          if (!acct) return;
          const input = form.querySelector('textarea');
          const button = form.querySelector('button');
          const body = String(input.value || '').trim().slice(0, MAX_BODY);
          if (!body) return;
          button.disabled = true; button.textContent = '…';
          const result = await rpc('skate_news_comment_add', {
            p_name: acct.name, p_token: acct.token, p_news_id: id, p_body: body
          });
          button.disabled = false; button.textContent = 'KIRIM';
          if (result && result.ok) { input.value = ''; await loadComments(id, panel); }
          else {
            alert(result && result.err === 'session' ? 'Sesi login berakhir. Masuk kembali.' :
              result && result.err === 'rate' ? 'Terlalu banyak komentar. Coba lagi sebentar.' : 'Komentar gagal dikirim.');
          }
        });
      });
    }
  };
  /* Pemberitahuan dibuat secara dinamis setiap kali pemain membuka frame. */
  const modalObserver = new MutationObserver(() => {
    const modal = $('newsModal');
    if (modal) window.DivaNewsComments.attach(modal);
  });
  if (document.body) modalObserver.observe(document.body, { childList: true, subtree: true });
  if (document.readyState !== 'loading') {
    const modal = $('newsModal');
    if (modal) window.DivaNewsComments.attach(modal);
  }
})();
