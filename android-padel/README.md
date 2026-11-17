# The Winner Padel — APK Android

APK ini hanya membuka https://fakecuan.github.io/web/padel/ di dalam aplikasi.
Isi aplikasi ikut ter-update setiap kali `padel/index.html` diperbarui, tanpa instal ulang APK.

- APK dibuat otomatis oleh GitHub Actions (`.github/workflows/padel-apk.yml`) dan diunggah ke halaman **Releases**.
- Build ulang APK hanya perlu kalau folder `android-padel/` berubah (ikon, nama, alamat situs), atau jalankan manual dari tab **Actions → Padel APK → Run workflow**.
- APK ditandatangani dengan `signing/the-winner-padel.jks` (atau secrets `PADEL_KEYSTORE_*` bila diisi). Pakai kunci yang sama terus supaya APK baru bisa dipasang menimpa yang lama.
