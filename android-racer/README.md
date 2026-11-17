# Diva Skate — APK Android

Aplikasi Android ini membuka game https://fakecuan.github.io/web/racer/ di dalam aplikasi
(layar penuh, posisi tegak, layar tidak mati saat main). Isinya selalu versi terbaru:
setiap `racer/index.html` diperbarui, aplikasi ikut terbaru tanpa instal ulang.

APK dibuat otomatis oleh GitHub Actions (`.github/workflows/diva-skate-apk.yml`) setiap folder
`android-racer/` berubah (atau dijalankan manual dari tab Actions), lalu diunggah ke Releases:

https://github.com/fakecuan/web/releases/download/diva-skate-latest/DivaSkate.apk

Saat dibuka, aplikasi mengecek versi APK terbaru di Releases dan menawarkan UPDATE bila ada versi baru.

Tombol kembali HP: menutup pop up / kembali ke menu sebelumnya di game; di menu utama,
tekan dua kali untuk keluar.

## Kunci tanda tangan
APK ditandatangani dengan `signing/diva-skate.jks` (kata sandi ada di workflow) supaya APK baru
bisa dipasang menimpa yang lama. Repo ini publik, jadi pasang APK hanya dari link Releases di atas.
Untuk kunci rahasia, isi secrets `RACER_KEYSTORE_BASE64`, `RACER_KEYSTORE_PASSWORD`, `RACER_KEY_ALIAS`.
