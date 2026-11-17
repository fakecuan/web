# 3MF Editor HP — APK Android

Aplikasi Android ini membuka https://fakecuan.github.io/web/3MF/ di dalam aplikasi, jadi fiturnya
selalu versi terbaru tanpa instal ulang (setelah dibuka sekali, bisa dipakai offline dari cache).
Bagian Android menambah:

- **Simpan .3mf langsung** lewat dialog simpan Android (pilih folder, misal Download).
- **"Buka dengan" 3MF Editor HP** untuk file .3mf / .stl dari File Manager / WhatsApp.
- Tombol kembali menutup jendela di aplikasi dulu.
- Nomor versi tampil di bawah aplikasi: `versi <web> · APK 1.<build>`.

APK dibuat otomatis oleh GitHub Actions (`.github/workflows/3mf-editor-apk.yml`) setiap folder
`android-3mf/` berubah, lalu diunggah ke Releases dengan tag `3mf-editor-latest`:

https://github.com/fakecuan/web/releases/download/3mf-editor-latest/3MF-Editor-HP.apk

## Kunci tanda tangan
APK ditandatangani dengan `signing/editor3mf.jks` (kata sandi ada di workflow) supaya APK baru selalu
bisa dipasang menimpa yang lama. Kalau ingin kunci rahasia, isi secrets `EDITOR3MF_KEYSTORE_BASE64`,
`EDITOR3MF_KEYSTORE_PASSWORD`, `EDITOR3MF_KEY_ALIAS`.
