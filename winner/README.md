# The Winner — Atlet & Pelatih

- `atlet/index.html`: antarmuka atlet.
- `pelatih/index.html`: dashboard pelatih.
- `shared/data.js`: jembatan data bersama, dipakai kedua halaman.
- `schema.sql`: skema tabel Supabase untuk sinkronisasi cloud.

## Preview
Buka `/winner/atlet/` atau `/winner/pelatih/` melalui GitHub Pages.

## Sinkronisasi
Kedua halaman menggunakan `/supabase-config.js` pada root repo.
1. Isi `DVA_SUPABASE_URL` dengan Project URL Supabase.
2. Isi `DVA_SUPABASE_ANON_KEY` dengan anon/public key (jangan service_role).
3. Jalankan `schema.sql` pada SQL Editor Supabase.

Tanpa konfigurasi Supabase, kedua halaman berjalan dalam mode demo lokal pada browser yang sama; data belum tersinkron lintas perangkat. Kebijakan SQL awal bersifat terbuka untuk prototipe dan wajib diperketat dengan autentikasi/identitas tim sebelum dipakai untuk data atlet sungguhan.
