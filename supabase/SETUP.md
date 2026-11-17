# Setup Admin Pesanan D.VA SKATE

## 1. Buat tabel database
1. Buka Supabase Dashboard untuk project yang akan dipakai.
2. Masuk ke **SQL Editor**.
3. Buka file `supabase/schema.sql` dari repository ini, salin seluruh isinya, lalu jalankan.
4. Pastikan tabel `public.orders` berhasil dibuat.

## 2. Buat akun admin
1. Buka **Authentication → Users**.
2. Pilih **Add user → Create new user**.
3. Buat email dan password admin.
4. Buka **Authentication → Settings → User Signups** lalu nonaktifkan pendaftaran publik (Allow new users to sign up).
5. Jangan bagikan akun admin.

Kebijakan database mengizinkan pelanggan anonim hanya menambahkan order baru. Membaca dan mengubah order hanya tersedia untuk pengguna yang sudah login. Dengan pendaftaran publik dimatikan, hanya akun admin yang dibuat dari Dashboard yang dapat masuk.

## 3. Isi konfigurasi Supabase
Buka `form/supabase-config.js` dan ganti:
- `https://PROJECT-REF.supabase.co` dengan **Project URL**.
- `SUPABASE_ANON_PUBLIC_KEY` dengan **anon/public key**.

Keduanya tersedia di **Project Settings → API** (atau API Keys pada tampilan Dashboard terbaru). Gunakan anon/public key, **jangan pernah** memasukkan service_role/secret key ke file website.

## 4. Buka halaman admin
Setelah konfigurasi disimpan dan hosting selesai deploy, buka:
`https://fakecuan.github.io/web/admin/`

Login menggunakan email dan password admin yang dibuat di langkah 2.

## 5. Alur pesanan
- Form pelanggan tetap memakai tampilan, input, panduan ukuran, dan pesan WhatsApp yang sama.
- Saat pelanggan menekan tombol kirim, data disimpan ke Supabase terlebih dahulu; setelah berhasil, WhatsApp dibuka dengan format pesan yang sudah ada.
- Dashboard dapat mencari order, memfilter status, mengedit data dan status, mencatat total/DP/ongkir/catatan, serta mengunduh CSV.

## Keamanan
- Jangan pernah menaruh service_role key di HTML/JavaScript publik.
- Pertahankan Row Level Security (RLS) aktif pada tabel `orders`.
- Akses baca/edit admin bergantung pada akun Supabase Auth yang dibuat secara manual.
