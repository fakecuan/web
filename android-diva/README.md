# Diva Skate Skinsuit — APK Android

Aplikasi Android ini membuka https://fakecuan.github.io/web/sseditor/ di dalam aplikasi,
jadi isinya selalu versi terbaru tanpa instal ulang. Bagian Android hanya menambah:

- **Buka & Simpan menimpa file yang sama** (.zprj / .json) lewat penyimpanan Android.
- **"Buka dengan" Diva Skate Skinsuit** dari File Manager / WhatsApp.
- **Bagikan** video 360° langsung ke Instagram.
- Tombol kembali menutup jendela di aplikasi dulu.

APK dibuat otomatis oleh GitHub Actions (`.github/workflows/diva-skinsuit-apk.yml`) setiap folder
`android-diva/` berubah, lalu diunggah ke Releases dengan tag `diva-skinsuit-latest`:

https://github.com/fakecuan/web/releases/download/diva-skinsuit-latest/DivaSkateSkinsuit.apk

## Kunci tanda tangan
APK ditandatangani dengan `signing/diva-skinsuit.jks` (kata sandi ada di workflow) supaya APK baru
selalu bisa dipasang menimpa yang lama. Karena repo ini publik, kunci ini bisa dilihat orang lain;
pasang APK hanya dari link Releases di atas. Kalau ingin kunci rahasia, isi secrets
`DIVA_KEYSTORE_BASE64`, `DIVA_KEYSTORE_PASSWORD`, `DIVA_KEY_ALIAS` — workflow otomatis memakainya
(tetapi APK lama harus dihapus dulu sekali karena kuncinya berbeda).
