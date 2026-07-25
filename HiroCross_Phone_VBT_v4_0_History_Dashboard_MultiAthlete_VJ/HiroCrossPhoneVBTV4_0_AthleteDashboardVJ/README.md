# HiroCross Phone VBT v4.0

Paket pengembangan ini menambahkan empat modul utama:

## 1. Multi-atlet
- Membuat profil atlet.
- Nama, jenis kelamin, tahun lahir, cabang olahraga, dan berat badan.
- Profil dapat dipilih langsung pada halaman VBT.
- Berat badan profil otomatis dimasukkan saat atlet dipilih.

## 2. Session History
- Filter berdasarkan atlet.
- Menampilkan exercise, beban, repetisi, velocity, velocity loss, power, estimasi 1RM, dan volume.
- Export CSV.
- Perbaikan pemetaan kolom power dan estimasi 1RM pada versi sebelumnya.

## 3. Dashboard
- Filter per atlet atau semua atlet.
- Total sesi dan repetisi.
- Best mean dan peak velocity.
- Total volume.
- Rata-rata velocity loss.
- Best peak power.
- Best Vertical Jump.
- Grafik tren mean velocity, estimasi 1RM, dan velocity loss.

## 4. Vertical Jump
- Countermovement Jump.
- Squat Jump.
- Drop Jump.
- Input flight time dalam milidetik.
- Perhitungan jump height dengan flight-time method.
- Estimasi peak power bila berat badan diisi.
- Penyimpanan hasil dan history tes.

## Catatan teknis
Vertical Jump pada versi ini menggunakan input flight time. Deteksi otomatis take-off dan landing dari sensor/video perlu tahap validasi tersendiri agar hasil tidak menyesatkan.

Project belum dikompilasi di Android Studio pada lingkungan ini. Jalankan GitHub Actions atau Android Studio untuk memastikan tidak ada masalah kompatibilitas perangkat dan SDK.