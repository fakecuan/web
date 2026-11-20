/* The Winner — shared data bridge (Athlete + Coach)
 * Uses Supabase REST with the public anon key configured in /supabase-config.js.
 * Local demo mode is intentionally enabled when credentials are placeholders.
 */
(function () {
  const cfg = { url: window.DVA_SUPABASE_URL || "", key: window.DVA_SUPABASE_ANON_KEY || "" };
  const ready = /^https:\/\//.test(cfg.url) && cfg.key && !cfg.url.includes("ISI_SUPABASE") && !cfg.key.includes("ISI_SUPABASE");
  const table = "winner_records";
  const localKey = "winner.shared.records.v1";
  const headers = { apikey: cfg.key, Authorization: "Bearer " + cfg.key, "Content-Type": "application/json", Prefer: "return=representation" };
  const local = () => { try { return JSON.parse(localStorage.getItem(localKey) || "[]"); } catch (_) { return []; } };
  const saveLocal = rows => localStorage.setItem(localKey, JSON.stringify(rows));
  async function request(path, options = {}) {
    if (!ready) throw new Error("Supabase belum dikonfigurasi — menggunakan mode demo lokal.");
    const res = await fetch(cfg.url.replace(/\/$/, "") + "/rest/v1/" + path, { ...options, headers: { ...headers, ...(options.headers || {}) } });
    if (!res.ok) throw new Error("Supabase: " + (await res.text()).slice(0, 240));
    return res.status === 204 ? null : res.json();
  }
  const api = {
    mode: ready ? "cloud" : "local-demo",
    async list(type) {
      if (!ready) return local().filter(x => !type || x.type === type).sort((a,b) => (b.created_at || "").localeCompare(a.created_at || ""));
      const q = type ? "type=eq." + encodeURIComponent(type) + "&" : "";
      return request(table + "?" + q + "select=*&order=created_at.desc");
    },
    async add(record) {
      const row = { id: (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random()), type: record.type, athlete: record.athlete || "", title: record.title || "", detail: record.detail || "", value: record.value || "", unit: record.unit || "", session_date: record.session_date || new Date().toISOString().slice(0,10), created_at: new Date().toISOString() };
      if (!ready) { const rows = local(); rows.unshift(row); saveLocal(rows); return row; }
      const result = await request(table, { method: "POST", body: JSON.stringify(row) });
      return result && result[0] || row;
    },
    async remove(id) {
      if (!ready) { saveLocal(local().filter(x => x.id !== id)); return; }
      await request(table + "?id=eq." + encodeURIComponent(id), { method: "DELETE", headers: { Prefer: "return=minimal" } });
    },
    async ping() { if (!ready) return { ok: false, mode: "local-demo" }; await request(table + "?select=id&limit=1"); return { ok: true, mode: "cloud" }; }
  };
  window.WinnerData = api;
})();