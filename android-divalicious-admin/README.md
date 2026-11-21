# Divalicious Admin — APK Android

APK ini membuka https://fakecuan.github.io/web/3dnew/admin/ (admin pesanan Divalicious Studio) di dalam aplikasi,
dan membunyikan notifikasi setiap ada order baru masuk, walau aplikasi sedang ditutup.

- Dibuat otomatis oleh GitHub Actions (`.github/workflows/divalicious-admin-apk.yml`) dan diunggah ke **Releases**.
- Link unduh tetap: https://github.com/fakecuan/web/releases/download/divalicious-admin-apk-latest/Divalicious-Admin.apk
- Isi admin ikut ter-update setiap kali `3dnew/admin/index.html` diperbarui, tanpa instal ulang APK.
- Notifikasi: `form/orders-db.js` mengirim pesan singkat ke topik ntfy `divalicious-order-…`;
  layanan di `android-order-notify/` mendengarkan topik itu dan membunyikan suara `order_chime.wav`.
- Ditandatangani dengan `signing/divalicious-admin.jks`. Pakai kunci yang sama terus supaya APK baru bisa dipasang menimpa yang lama.
