# D.VA Admin — APK Android

APK ini membuka https://fakecuan.github.io/web/admin/ (admin pesanan D.VA SKATE) di dalam aplikasi.
Isi aplikasi ikut ter-update setiap kali `admin/index.html` diperbarui, tanpa instal ulang APK.

- Dibuat otomatis oleh GitHub Actions (`.github/workflows/dva-admin-apk.yml`) dan diunggah ke **Releases**.
- Link unduh tetap: https://github.com/fakecuan/web/releases/download/dva-admin-apk-latest/DVA-Admin.apk
- Build ulang hanya perlu kalau folder `android-dva-admin/` berubah, atau jalankan manual dari **Actions → D.VA Admin APK → Run workflow**.
- Ditandatangani dengan `signing/dva-admin.jks`. Pakai kunci yang sama terus supaya APK baru bisa dipasang menimpa yang lama.
