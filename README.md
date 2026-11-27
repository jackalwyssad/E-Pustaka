# E-Pustaka

Aplikasi Perpustakaan Digital berbasis Android (Kotlin) dengan arsitektur berbasis Repository, **Firebase Authentication**, **Cloud Firestore** (real-time), pembaca PDF bawaan (`PdfRenderer`), serta unggah media lintas perangkat tanpa memerlukan paket berbayar Firebase Blaze.

---

## Fitur Utama

### 1. Autentikasi & Manajemen Akun
- **Login & Registrasi**: Pendaftaran akun member menggunakan email dan kata sandi melalui Firebase Authentication.
- **Lupa Kata Sandi**: Mengirim tautan reset kata sandi ke email pengguna (disertai validasi apakah email sudah terdaftar di sistem).
- **Mode Tamu (Guest)**: Pengguna dapat menjelajahi katalog, melihat ulasan, dan membaca cuplikan buku tanpa login.
- **Akun Admin Bawaan**: Tersedia tombol login cepat untuk Admin guna mengelola buku, kategori, dan pengguna.

### 2. Katalog, Pencarian & Favorit
- **Beranda**: Menampilkan sapaan pengguna, *reading streak* harian, filter kategori dinamis, daftar **Buku Terpopuler**, dan **Koleksi Buku**.
- **Pencarian**: Pencarian real-time berdasarkan judul atau penulis serta filter kategori.
- **Buku Favorit**: Simpan buku ke daftar favorit pribadi yang tersinkronisasi per akun.

### 3. Peminjaman Reguler, Sesi Kilat & Antrean
- **Pinjaman Reguler (7 Hari)**: Batas maksimal 3 buku aktif sekaligus per pengguna dengan pelacakan jatuh tempo dan halaman terakhir yang dibaca.
- **Sesi Baca Kilat (2 Jam)**: Akses baca langsung selama 2 jam tanpa menunggu antrean reguler, lengkap dengan *countdown timer* di pembaca PDF dan pengembalian otomatis saat waktu habis.
- **Sistem Antrean (Queue)**: Jika stok reguler habis, pengguna dapat masuk ke antrean peminjaman dan mengklaim buku saat giliran tersedia.
- **Penanganan Buku Dihapus**: Jika Admin menghapus buku yang sedang dipinjam atau sedang dibaca, pinjaman aktif dan antrean otomatis dihentikan, dan layar pembaca PDF/detail buku langsung menutup dengan pemberitahuan.

### 4. Pembaca PDF Terintegrasi (`PdfRendererHelper`)
- **Cuplikan Gratis (20%)**: Pengunjung atau pengguna yang belum meminjam hanya dapat membaca 20% awal isi buku.
- **Akses Penuh**: Peminjam aktif (7 hari maupun Sesi Kilat) dapat membaca seluruh halaman dengan slider navigasi, mode gelap (*dark reader*), dan sinkronisasi progres halaman otomatis ke Firestore.
- **Smart Disk Cache**: Berkas PDF yang telah diunduh otomatis disimpan di cache perangkat sehingga pembacaan berikutnya jauh lebih cepat.

### 5. Ulasan, Rating & Profil
- **Validasi Pemberi Ulasan**: Hanya pengguna yang pernah atau sedang meminjam buku (7 hari atau Sesi Kilat) yang dapat memberikan rating dan menulis ulasan; pengguna lain hanya dapat membaca ulasan.
- **Edit & Hapus Ulasan**: Pemilik ulasan dapat memperbarui atau menghapus ulasannya sendiri, dan rata-rata rating buku dihitung ulang otomatis.
- **Foto & Nama Profil Real-Time**: Foto profil dapat dipilih langsung dari galeri perangkat (dikompresi otomatis lalu diunggah ke Cloud). Perubahan nama maupun foto profil otomatis ikut memperbarui data pada seluruh ulasan milik pengguna tersebut.

### 6. Panel Admin
- **Dashboard Statistik**: Pantau ringkasan jumlah buku, kategori, dan pengguna terdaftar.
- **Kelola Buku (Tambah / Edit / Hapus)**:
  - Pilih sampul dan berkas PDF langsung dari penyimpanan HP (lintas versi Android dengan `OpenDocument`, penanganan rotasi EXIF, dan kompresi gambar sampul otomatis).
  - Dukungan multi-host Cloud gratis (`CloudUploader`) dengan DNS IPv4-first serta konversi otomatis tautan Google Drive / Dropbox menjadi tautan unduhan langsung.
  - Deteksi jumlah halaman PDF secara instan langsung dari berkas lokal.
- **Kelola Kategori & Pengguna**: Tambah/edit/hapus kategori buku serta pantau daftar akun pengguna.

---

## Teknologi & Dependensi

| Komponen | Keterangan |
|---|---|
| **Bahasa** | Kotlin (Java Compatibility 17) |
| **SDK Android** | `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36` |
| **UI & Layout** | Material 3 (`material`), XML Layout, ViewBinding, ConstraintLayout, RecyclerView, SwipeRefreshLayout |
| **Backend & Database** | Firebase Authentication, Cloud Firestore, Firebase Storage (opsional) |
| **Jaringan & Media** | OkHttp (via Firebase/CloudUploader), Coil (image loading & caching), Android `PdfRenderer` |
| **Asynchronous** | Kotlin Coroutines & Flow (`kotlinx-coroutines-android`, `kotlinx-coroutines-play-services`) |

---

## Struktur Proyek

```text
app/src/main/java/com/example/perpustakaan2/
├── adapter/
│   ├── AdminBookAdapter.kt
│   ├── BookAdapter.kt
│   ├── CategoryChipAdapter.kt
│   ├── LoanAdapter.kt
│   ├── ManageCategoryAdapter.kt
│   ├── PopularBookAdapter.kt
│   ├── QueueAdapter.kt
│   ├── ReviewAdapter.kt
│   └── UserAdapter.kt
├── data/
│   ├── model/
│   │   ├── Book.kt
│   │   ├── Category.kt
│   │   ├── Loan.kt
│   │   ├── QueueEntry.kt
│   │   ├── Review.kt
│   │   └── User.kt
│   └── repository/
│       ├── AuthRepository.kt
│       ├── BookRepository.kt
│       ├── CategoryRepository.kt
│       ├── LoanRepository.kt
│       ├── QueueRepository.kt
│       └── UserRepository.kt
├── ui/
│   ├── fragment/
│   │   ├── BookshelfFragment.kt
│   │   ├── HomeFragment.kt
│   │   ├── ProfileFragment.kt
│   │   └── SearchFragment.kt
│   ├── AddEditBookActivity.kt
│   ├── AdminDashboardActivity.kt
│   ├── BookDetailActivity.kt
│   ├── FavoritesActivity.kt
│   ├── LoginActivity.kt
│   ├── MainActivity.kt
│   ├── ManageCategoriesActivity.kt
│   ├── PdfReaderActivity.kt
│   ├── ReadingHistoryActivity.kt
│   ├── RegisterActivity.kt
│   ├── SplashActivity.kt
│   └── UserListActivity.kt
└── util/
    ├── CloudUploader.kt
    ├── Constants.kt
    ├── DummyData.kt
    └── PdfRendererHelper.kt
```

---

## Struktur Koleksi Cloud Firestore

Aplikasi menggunakan 7 koleksi utama pada Cloud Firestore (didefinisikan di `Constants.kt`):
- `users` — Profil pengguna, role (`member` / `admin`), foto profil, dan *reading streak*.
- `books` — Data buku, stok reguler, stok Sesi Kilat, URL sampul, URL PDF, dan rata-rata rating.
- `categories` — Daftar kategori buku.
- `loans` — Catatan peminjaman aktif maupun riwayat (`pinjam` 7 hari / `kilat` 2 jam), progres halaman, dan status (`aktif`, `dikembalikan`, `buku_dihapus`, dll).
- `queue` — Daftar antrean peminjaman saat stok buku habis.
- `favorites` — Daftar buku favorit tiap pengguna.
- `reviews` — Ulasan dan rating buku dari peminjam.

---

## Akun Default (Master Admin)

Untuk masuk sebagai Admin tanpa mendaftar manual:
- **Email**: `admin@epustaka.com`
- **Password**: `admin123`

---

## Panduan Setup Database (Firebase)

Demi keamanan, file `google-services.json` **tidak disertakan** di repositori ini (diabaikan melalui `.gitignore`). Gunakan salah satu cara berikut untuk menjalankan project:

### Cara 1: Menggunakan Database yang Sama (Kolaborator)
1. Minta file `google-services.json` dari pemilik repositori.
2. Letakkan file tersebut di dalam folder `app/`:
   ```text
   perpustakaan2/app/google-services.json
   ```
3. Buka project di Android Studio, lakukan **Sync Project with Gradle Files**, lalu jalankan aplikasi.

### Cara 2: Membuat Project Firebase Sendiri
1. Buka [Firebase Console](https://console.firebase.google.com/) dan buat project baru.
2. Tambahkan aplikasi Android dengan **Package Name**: `com.example.perpustakaan2`.
3. Unduh `google-services.json` lalu simpan ke `app/google-services.json` (dapat mengacu pada format di `app/google-services.json.example`).
4. Aktifkan layanan berikut di Firebase Console:
   - **Authentication**: *Sign-in method* $\rightarrow$ Aktifkan provider **Email/Password**.
   - **Cloud Firestore**: Buat database, lalu pada tab **Rules** atur akses:
     ```javascript
     rules_version = '2';
     service cloud.firestore {
       match /databases/{database}/documents {
         match /{document=**} {
           allow read, write: if request.auth != null;
         }
       }
     }
     ```
5. Lakukan **Gradle Sync** di Android Studio dan jalankan aplikasi.

---

## Cara Menjalankan Aplikasi

1. Clone repositori:
   ```bash
   git clone https://github.com/jackalwyssad/E-Pustaka.git
   ```
2. Pasang file `app/google-services.json`.
3. Buka folder project di **Android Studio**.
4. Tunggu proses **Gradle Sync** selesai, lalu klik **Run** pada Emulator atau perangkat Android fisik (Android 7.0 / API 24 ke atas).

### Catatan Pengujian Durasi Pinjam (Untuk Demo / Presentasi)
Jika ingin menguji pengembalian otomatis dalam hitungan menit saat demo, Anda dapat mengubah sementara perhitungan `dueDate` pada `LoanRepository.kt`:
- Pinjaman reguler (baris 59) dan Sesi Kilat (baris 133) menggunakan satuan **milidetik**, misalnya `now + (5 * 60_000L)` untuk durasi 5 menit.
