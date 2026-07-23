# HiroCross Phone VBT v3.2 — Power Update

## Perbaikan yang dipertahankan
- Anti-getaran
- ROM minimum
- Peak dan mean velocity minimum
- Durasi rep minimum
- Konfirmasi gerakan berkelanjutan
- Jeda antarrepetisi
- Validasi perubahan arah dan kondisi diam

## Penambahan power
Aplikasi sekarang menampilkan:
- Mean Power per repetisi
- Peak Power per repetisi
- Riwayat power

Estimasi power dihitung selama fase konsentrik:

`Power = moving mass × (gravity + vertical acceleration) × vertical velocity`

Moving mass terdiri dari:
- Beban eksternal/barbell
- Persentase berat badan efektif yang dimasukkan pengguna

Contoh:
- Bench press: kontribusi berat badan efektif dapat diatur 0%
- Squat: persentase dapat diisi sesuai protokol penelitian yang digunakan
- Gerakan dengan HP terpasang pada tubuh: gunakan kontribusi berat badan sesuai model biomekanika yang dipilih

## Catatan ilmiah
Nilai power adalah estimasi mekanik berbasis sensor smartphone. Gunakan protokol yang konsisten dan validasi terhadap force plate, LPT, atau encoder sebelum digunakan sebagai nilai laboratorium.