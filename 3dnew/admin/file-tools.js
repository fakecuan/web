// Admin Divalicious: simpan file 3MF (juga di APK Android) dan preview 3D file 3MF.
// Dipakai oleh 3dnew/admin/index.html.

/* ---------------- Simpan / unduh file ---------------- */
const blobToB64 = (blob) => new Promise((ok, no) => {
  const r = new FileReader();
  r.onload = () => ok(String(r.result).split(',')[1] || '');
  r.onerror = () => no(r.error || new Error('Gagal membaca file'));
  r.readAsDataURL(blob);
});

function browserDownload(blob, name) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}

/**
 * Di browser: unduh biasa. Di APK (Capacitor): simpan ke Documents/Divalicious lalu tawarkan bagikan/buka.
 * Mengembalikan teks status untuk ditampilkan.
 */
export async function saveFile(blob, name) {
  const cap = window.Capacitor;
  const P = cap && cap.Plugins;
  if (!(cap && cap.isNativePlatform && cap.isNativePlatform() && P && P.Filesystem)) {
    browserDownload(blob, name);
    return 'File diunduh';
  }
  const data = await blobToB64(blob);
  let uri = null, where = '';
  try {
    const r = await P.Filesystem.writeFile({ path: 'Divalicious/' + name, data, directory: 'DOCUMENTS', recursive: true });
    uri = r.uri; where = 'Documents/Divalicious/' + name;
  } catch (e) {
    // Android lama / izin ditolak: simpan sementara lalu langsung buka menu bagikan.
    const r = await P.Filesystem.writeFile({ path: name, data, directory: 'CACHE', recursive: true });
    uri = r.uri;
  }
  if (P.Share) {
    try { await P.Share.share({ title: name, dialogTitle: 'Bagikan / buka file 3MF', files: [uri] }); }
    catch (e) { /* menu bagikan ditutup tanpa memilih: abaikan */ }
  }
  return where ? 'Tersimpan di ' + where : 'File siap dibagikan';
}

/* Buka dengan aplikasi pilihan: menu berbagi perangkat (Android/iOS atau browser yang mendukung) */
export async function openWith(blob, name) {
  const cap = window.Capacitor;
  const P = cap && cap.Plugins;
  if (cap && cap.isNativePlatform && cap.isNativePlatform() && P && P.Filesystem && P.Share) {
    const data = await blobToB64(blob);
    const r = await P.Filesystem.writeFile({ path: name, data, directory: 'CACHE', recursive: true });
    try { await P.Share.share({ title: name, dialogTitle: 'Buka dengan aplikasi 3MF', files: [r.uri] }); }
    catch (e) { /* menu ditutup tanpa memilih */ }
    return 'Pilih aplikasi 3MF di menu';
  }
  const file = new File([blob], name, { type: 'model/3mf' });
  if (navigator.canShare && navigator.canShare({ files: [file] })) {
    try { await navigator.share({ files: [file], title: name }); return 'Pilih aplikasi 3MF di menu'; }
    catch (e) { if (e && e.name === 'AbortError') return ''; throw e; }
  }
  browserDownload(blob, name);
  return 'Browser ini tidak bisa membuka menu aplikasi. File diunduh, buka dari folder unduhan.';
}

/* ---------------- Baca ZIP (3MF) ---------------- */
async function inflateRaw(u8) {
  const ds = new DecompressionStream('deflate-raw');
  const out = new Response(new Blob([u8]).stream().pipeThrough(ds));
  return new Uint8Array(await out.arrayBuffer());
}

async function readZip(buf) {
  const u8 = new Uint8Array(buf), dv = new DataView(buf);
  let eocd = -1;
  for (let i = u8.length - 22; i >= Math.max(0, u8.length - 65557); i--) {
    if (dv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('Bukan file 3MF/ZIP yang valid');
  const count = dv.getUint16(eocd + 10, true);
  let p = dv.getUint32(eocd + 16, true);
  const files = {};
  const td = new TextDecoder();
  for (let n = 0; n < count; n++) {
    if (dv.getUint32(p, true) !== 0x02014b50) break;
    const method = dv.getUint16(p + 10, true);
    const csize = dv.getUint32(p + 20, true);
    const nlen = dv.getUint16(p + 28, true), xlen = dv.getUint16(p + 30, true), clen = dv.getUint16(p + 32, true);
    const local = dv.getUint32(p + 42, true);
    const name = td.decode(u8.subarray(p + 46, p + 46 + nlen));
    files[name.replace(/^\//, '')] = { method, csize, local };
    p += 46 + nlen + xlen + clen;
  }
  return {
    names: Object.keys(files),
    async text(name) {
      const f = files[name.replace(/^\//, '')];
      if (!f) return null;
      const lp = f.local;
      const start = lp + 30 + dv.getUint16(lp + 26, true) + dv.getUint16(lp + 28, true);
      const raw = u8.subarray(start, start + f.csize);
      const data = f.method === 0 ? raw : f.method === 8 ? await inflateRaw(raw) : null;
      if (!data) throw new Error('Kompresi ZIP tidak didukung (' + f.method + ')');
      return td.decode(data);
    },
  };
}

/* ---------------- Parse 3MF (format Bambu Studio / umum) ---------------- */
const FALLBACK_COLORS = ['#F6A6C8', '#F2F2EF', '#E65391', '#8ecbff', '#9be7b0', '#ffe58a', '#c9a7f5', '#ffc18a'];

// Matriks 3MF "m00 m01 m02 m10 m11 m12 m20 m21 m22 m30 m31 m32" (baris) -> array 4x4 kolom-mayor (gaya THREE).
function mat(str) {
  const v = String(str || '1 0 0 0 1 0 0 0 1 0 0 0').trim().split(/\s+/).map(Number);
  return [v[0], v[1], v[2], 0, v[3], v[4], v[5], 0, v[6], v[7], v[8], 0, v[9], v[10], v[11], 1];
}
function mul(a, b) { // a*b, kolom-mayor
  const o = new Array(16);
  for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) {
    o[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
  }
  return o;
}
const ID = mat();

export async function parse3mf(blob) {
  const zip = await readZip(await blob.arrayBuffer());
  let root = '3D/3dmodel.model';
  const rels = await zip.text('_rels/.rels');
  const m = rels && rels.match(/Target="([^"]+\.model)"/i);
  if (m) root = m[1].replace(/^\//, '');

  // Warna filamen + extruder per objek/part (Bambu Studio)
  let colors = FALLBACK_COLORS;
  try {
    const ps = JSON.parse(await zip.text('Metadata/project_settings.config') || '{}');
    if (Array.isArray(ps.filament_colour) && ps.filament_colour.length) colors = ps.filament_colour;
  } catch (e) { /* tidak ada warna: pakai cadangan */ }
  const objExt = {}, partExt = {}, names = {};
  const ms = await zip.text('Metadata/model_settings.config');
  if (ms) {
    const doc = new DOMParser().parseFromString(ms, 'application/xml');
    for (const ob of doc.querySelectorAll('config > object')) {
      const oid = ob.getAttribute('id');
      for (const md of ob.children) {
        if (md.tagName !== 'metadata') continue;
        if (md.getAttribute('key') === 'extruder') objExt[oid] = +md.getAttribute('value');
        if (md.getAttribute('key') === 'name') names[oid] = md.getAttribute('value');
      }
      for (const part of ob.querySelectorAll('part')) {
        const e = part.querySelector('metadata[key="extruder"]');
        if (e) partExt[part.getAttribute('id')] = +e.getAttribute('value');
      }
    }
  }

  const docs = {};
  const loadDoc = async (path) => {
    path = path.replace(/^\//, '');
    if (!docs[path]) {
      const t = await zip.text(path);
      if (t == null) throw new Error('Bagian model tidak ditemukan: ' + path);
      const d = new DOMParser().parseFromString(t, 'application/xml');
      const objs = {}, mats = {};
      for (const o of d.getElementsByTagName('object')) objs[o.getAttribute('id')] = o;
      // Warna 3MF standar (basematerials), dipakai file non-Bambu
      for (const bm of d.getElementsByTagName('basematerials')) {
        mats[bm.getAttribute('id')] = [...bm.getElementsByTagName('base')].map((b) => (b.getAttribute('displaycolor') || '').slice(0, 7));
      }
      docs[path] = { d, objs, mats, meshes: {} };
    }
    return docs[path];
  };
  const meshOf = (doc, id, el) => {
    if (doc.meshes[id]) return doc.meshes[id];
    const vs = el.getElementsByTagName('vertex'), ts = el.getElementsByTagName('triangle');
    const pos = new Float32Array(vs.length * 3);
    for (let i = 0; i < vs.length; i++) {
      pos[i * 3] = +vs[i].getAttribute('x'); pos[i * 3 + 1] = +vs[i].getAttribute('y'); pos[i * 3 + 2] = +vs[i].getAttribute('z');
    }
    const idx = new Uint32Array(ts.length * 3);
    for (let i = 0; i < ts.length; i++) {
      idx[i * 3] = +ts[i].getAttribute('v1'); idx[i * 3 + 1] = +ts[i].getAttribute('v2'); idx[i * 3 + 2] = +ts[i].getAttribute('v3');
    }
    return (doc.meshes[id] = { pos, idx });
  };

  const parts = []; // {mesh, matrix, ext, label}
  const walk = async (path, id, matrix, ext, label, depth) => {
    if (depth > 12) return;
    const doc = await loadDoc(path);
    const el = doc.objs[id];
    if (!el) return;
    const mesh = el.getElementsByTagName('mesh')[0];
    const myExt = partExt[id] || ext || objExt[id] || 1;
    if (mesh) {
      let hex = null;
      const t0 = mesh.getElementsByTagName('triangle')[0];
      const pid = (t0 && t0.getAttribute('pid')) || el.getAttribute('pid');
      const pi = t0 && t0.getAttribute('p1') != null ? t0.getAttribute('p1') : el.getAttribute('pindex');
      if (!ms && pid != null && doc.mats[pid]) hex = doc.mats[pid][+pi || 0] || null;
      parts.push({ mesh: meshOf(doc, path + '#' + id, mesh), matrix, ext: myExt, label, hex });
      return;
    }
    for (const c of el.getElementsByTagName('component')) {
      const cp = c.getAttribute('p:path') || c.getAttributeNS('http://schemas.microsoft.com/3dmanufacturing/production/2015/06', 'path') || path;
      const cid = c.getAttribute('objectid');
      await walk(cp, cid, mul(matrix, mat(c.getAttribute('transform'))), partExt[cid] || myExt, label, depth + 1);
    }
  };
  const rootDoc = await loadDoc(root);
  const items = rootDoc.d.getElementsByTagName('item');
  for (const it of items) {
    const oid = it.getAttribute('objectid');
    await walk(root, oid, mat(it.getAttribute('transform')), objExt[oid], names[oid] || 'Objek ' + oid, 0);
  }
  if (!parts.length) throw new Error('File 3MF tidak berisi model');
  return { parts, colors };
}

/* ---------------- Viewer 3D ---------------- */
const THREE_SRC = [
  ['https://cdn.jsdelivr.net/npm/three@0.147.0/build/three.min.js', 'https://cdn.jsdelivr.net/npm/three@0.147.0/examples/js/controls/OrbitControls.js'],
  ['https://unpkg.com/three@0.147.0/build/three.min.js', 'https://unpkg.com/three@0.147.0/examples/js/controls/OrbitControls.js'],
];
const loadScript = (src) => new Promise((ok, no) => {
  const s = document.createElement('script'); s.src = src; s.onload = ok; s.onerror = () => no(new Error('Gagal memuat ' + src));
  document.head.appendChild(s);
});
let threeP = null;
function loadThree() {
  if (window.THREE && window.THREE.OrbitControls) return Promise.resolve(window.THREE);
  return threeP || (threeP = (async () => {
    let err;
    for (const [core, orbit] of THREE_SRC) {
      try {
        if (!window.THREE) await loadScript(core);
        if (!window.THREE.OrbitControls) await loadScript(orbit);
        return window.THREE;
      } catch (e) { err = e; }
    }
    threeP = null;
    throw err || new Error('Library 3D gagal dimuat');
  })());
}

const css = `
#p3Overlay{position:fixed;inset:0;z-index:50;background:#f4f2f7;display:flex;flex-direction:column;font-family:inherit}
#p3Overlay .p3Top{display:flex;align-items:center;gap:10px;padding:10px 12px;background:#fff;border-bottom:1px solid #e6e3ec}
#p3Overlay .p3Title{flex:1;min-width:0;font-weight:800;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
#p3Overlay .p3Btn{border:0;border-radius:10px;padding:9px 12px;font-weight:800;font-size:13px;background:#f0f0f5;color:#292936}
#p3Overlay .p3Stage{position:relative;flex:1;min-height:0;touch-action:none}
#p3Overlay canvas{display:block;width:100%;height:100%}
#p3Overlay .p3Msg{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;text-align:center;padding:24px;color:#555;font-weight:700}
#p3Overlay .p3Info{padding:10px 12px;background:#fff;border-top:1px solid #e6e3ec;font-size:12px;color:#555;max-height:32vh;overflow:auto}
#p3Overlay .p3Legend{display:flex;flex-wrap:wrap;gap:6px 12px;margin-top:6px}
#p3Overlay .p3Chip{display:inline-flex;align-items:center;gap:5px}
#p3Overlay .p3Chip i{width:14px;height:14px;border-radius:4px;border:1px solid #0002;display:inline-block}
#p3Overlay .p3Hint{position:absolute;left:50%;bottom:10px;transform:translateX(-50%);background:#0008;color:#fff;border-radius:20px;padding:5px 12px;font-size:11px;pointer-events:none;white-space:nowrap}
`;

/** Buka layar penuh preview 3D. getBlob: fungsi async yang mengembalikan Blob 3MF. */
export async function openPreview(getBlob, title) {
  if (!document.getElementById('p3Css')) {
    const st = document.createElement('style'); st.id = 'p3Css'; st.textContent = css; document.head.appendChild(st);
  }
  const ov = document.createElement('div');
  ov.id = 'p3Overlay';
  ov.innerHTML = `<div class="p3Top"><div class="p3Title"></div><button class="p3Btn" data-reset>⟲ Pusatkan</button><button class="p3Btn" data-close>✕ Tutup</button></div>
    <div class="p3Stage"><div class="p3Msg">Memuat file 3MF…</div></div><div class="p3Info">Menyiapkan…</div>`;
  ov.querySelector('.p3Title').textContent = title || 'Preview 3MF';
  document.body.appendChild(ov);
  const stage = ov.querySelector('.p3Stage'), msg = ov.querySelector('.p3Msg'), info = ov.querySelector('.p3Info');
  let renderer = null, raf = 0, alive = true, ro = null;
  const close = () => {
    alive = false; cancelAnimationFrame(raf); if (ro) ro.disconnect();
    if (renderer) { renderer.dispose(); renderer.forceContextLoss && renderer.forceContextLoss(); }
    ov.remove(); window.removeEventListener('popstate', onBack);
  };
  const onBack = () => close();
  history.pushState({ p3: 1 }, ''); window.addEventListener('popstate', onBack); // tombol kembali Android menutup preview
  ov.querySelector('[data-close]').onclick = () => { if (history.state && history.state.p3) history.back(); else close(); };

  try {
    const [blob, THREE] = await Promise.all([getBlob(), loadThree()]);
    if (!alive) return;
    msg.textContent = 'Membaca model…';
    const { parts, colors } = await parse3mf(blob);
    if (!alive) return;

    renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
    renderer.setClearColor(0xf4f2f7);
    stage.appendChild(renderer.domElement);
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(40, 1, 0.1, 5000);
    scene.add(new THREE.HemisphereLight(0xffffff, 0x99909f, 0.75));
    const sun = new THREE.DirectionalLight(0xffffff, 0.75); sun.position.set(80, 160, 120); scene.add(sun);
    const fill = new THREE.DirectionalLight(0xffffff, 0.3); fill.position.set(-120, 60, -80); scene.add(fill);

    const model = new THREE.Group();
    model.rotation.x = -Math.PI / 2; // 3MF: Z ke atas
    const geoCache = new Map(), matCache = new Map(), used = new Map();
    let tris = 0;
    for (const p of parts) {
      let g = geoCache.get(p.mesh);
      if (!g) {
        const gi = new THREE.BufferGeometry();
        gi.setAttribute('position', new THREE.BufferAttribute(p.mesh.pos, 3));
        gi.setIndex(new THREE.BufferAttribute(p.mesh.idx, 1));
        g = gi.toNonIndexed(); g.computeVertexNormals(); gi.dispose();
        geoCache.set(p.mesh, g);
      }
      const hex = p.hex || colors[(p.ext - 1) % colors.length] || '#cccccc';
      const key = p.hex ? 'w' + p.hex : p.ext;
      let mtl = matCache.get(hex);
      if (!mtl) { mtl = new THREE.MeshStandardMaterial({ color: new THREE.Color(hex), roughness: 0.62, metalness: 0.02 }); matCache.set(hex, mtl); }
      const mesh = new THREE.Mesh(g, mtl);
      mesh.matrixAutoUpdate = false;
      mesh.matrix.fromArray(p.matrix);
      model.add(mesh);
      tris += p.mesh.idx.length / 3;
      used.set(key, hex);
    }
    scene.add(model);
    model.updateMatrixWorld(true);
    const box = new THREE.Box3().setFromObject(model);
    const size = box.getSize(new THREE.Vector3()), center = box.getCenter(new THREE.Vector3());
    model.position.sub(new THREE.Vector3(center.x, box.min.y, center.z));
    const grid = new THREE.GridHelper(Math.max(60, Math.ceil(Math.max(size.x, size.z) * 1.6 / 10) * 10), 12, 0xc9c3d3, 0xe2dee8);
    scene.add(grid);

    const controls = new THREE.OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true; controls.dampingFactor = 0.12;
    const fit = () => {
      const r = Math.max(1, size.length() * 0.5); // jari-jari bola pembungkus
      const vf = camera.fov * Math.PI / 180, hf = 2 * Math.atan(Math.tan(vf / 2) * camera.aspect);
      const dist = r / Math.sin(Math.min(vf, hf) / 2) * 1.05;
      controls.target.set(0, size.y / 2, 0);
      camera.position.set(dist * 0.55, size.y / 2 + dist * 0.55, dist * 0.65);
      camera.near = dist / 100; camera.far = dist * 20; camera.updateProjectionMatrix(); controls.update();
    };
    const resize = () => {
      const w = stage.clientWidth, h = stage.clientHeight;
      if (!w || !h) return;
      renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix();
    };
    ro = new ResizeObserver(resize); ro.observe(stage); resize(); fit();
    let firstFit = true; new ResizeObserver(() => { if (firstFit && stage.clientWidth) { firstFit = false; resize(); fit(); } }).observe(stage);
    ov.querySelector('[data-reset]').onclick = fit;
    msg.remove();
    const hint = document.createElement('div'); hint.className = 'p3Hint'; hint.textContent = 'Geser untuk memutar · cubit untuk zoom · dua jari untuk geser';
    stage.appendChild(hint); setTimeout(() => hint.remove(), 4000);

    const legend = [...used.entries()].sort((a, b) => String(a[0]).localeCompare(String(b[0]), undefined, { numeric: true }))
      .map(([e, hex]) => `<span class="p3Chip"><i style="background:${hex}"></i>${typeof e === 'number' ? 'Filamen ' + e + ' · ' : 'Warna '}${String(hex).toUpperCase()}</span>`).join('');
    info.innerHTML = `<b>Ukuran total:</b> ${size.x.toFixed(1)} × ${size.z.toFixed(1)} × ${size.y.toFixed(1)} mm (P × L × T) · ${parts.length} part · ${Math.round(tris).toLocaleString('id-ID')} segitiga<div class="p3Legend">${legend}</div>`;

    const loop = () => { if (!alive) return; controls.update(); renderer.render(scene, camera); raf = requestAnimationFrame(loop); };
    loop();
  } catch (e) {
    if (!alive) return;
    msg.textContent = 'Preview gagal: ' + (e && e.message || e);
    info.textContent = 'File tetap bisa diunduh lalu dibuka di Bambu Studio.';
  }
}
