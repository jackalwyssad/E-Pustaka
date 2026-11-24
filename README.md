# E-Pustaka 📚

Aplikasi Perpustakaan Digital berbasis Android (Kotlin) menggunakan **Firebase Authentication** dan **Cloud Firestore**.

---

## 🚀 Fitur Utama
- **Autentikasi Pengguna**: Registrasi akun member, verifikasi email, login, dan reset password.
- **Akun Master Admin**: Akun bawaan admin untuk kelola koleksi dan pengguna.
- **Katalog Buku**: Cari, filter kategori, detail informasi buku, dan stok real-time.
- **Peminjaman & Pengembalian**: Sistem peminjaman buku dengan batas waktu dan denda/status jatuh tempo.
- **Sistem Antrean (Queue)**: Mengantre peminjaman saat buku sedang habis/dipinjam orang lain.
- **Ulasan & Rating**: Memberikan ulasan dan rating pada buku yang telah dibaca.
- **Panel Admin**: Tambah/edit/hapus buku, kelola kategori, pantau daftar peminjam dan pengguna.

---

## 🔑 Akun Default (Master Admin)
Untuk pengujian atau akses fitur admin tanpa mendaftar manual:
- **Email**: `admin@epustaka.com`
- **Password**: `admin123`

---

## ⚙️ Panduan Setup Database (Firebase) untuk Teman / Kolaborator

Demi alasan keamanan, file `google-services.json` **tidak disertakan** di repositori publik ini.

Pilih salah satu dari 2 cara di bawah agar aplikasi dapat terhubung ke database:

### 🌟 Cara 1: Menggunakan Database yang Sama (Paling Praktis)
Jika Anda ingin teman Anda langsung terhubung ke database project ini:
1. Minta file `google-services.json` langsung kepada pemilik repositori (misal via WhatsApp atau Google Drive).
2. Salin/paste file `google-services.json` ke dalam folder:
   ```text
   E-Pustaka/app/google-services.json
   ```
3. Buka Android Studio, tunggu Gradle Sync selesai, dan aplikasi langsung siap di-run!

---

### 🛠️ Cara 2: Membuat Project Firebase Sendiri (Mandiri)
Jika ingin menggunakan database Firebase pribadi yang baru:
1. Buka [Firebase Console](https://console.firebase.google.com/) dan buat project baru.
2. Tambahkan aplikasi Android:
   - **Android package name**: `com.example.perpustakaan2` *(Wajib sama persis)*
3. Download file **`google-services.json`** yang diberikan oleh Firebase.
4. Letakkan file tersebut di folder `app/` project:
   ```text
   E-Pustaka/app/google-services.json
   ```
5. **Aktifkan Fitur di Firebase Console:**
   - **Authentication**: Buka menu *Authentication* -> *Sign-in method* -> Aktifkan provider **Email/Password**.
   - **Cloud Firestore**: Buka menu *Firestore Database* -> Klik **Create database**.
     - Di tab **Rules**, pastikan aturan mengizinkan user terautentikasi:
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
6. Buka project di Android Studio, lakukan **Sync Project with Gradle Files**, lalu Run aplikasi.

---

## 💻 Langkah Menjalankan Aplikasi

1. **Clone Repository:**
   ```bash
   git clone https://github.com/jackalwyssad/E-Pustaka.git
   ```
2. **Siapkan `google-services.json`** di folder `app/` (ikuti panduan di atas).
3. **Buka di Android Studio** -> Tunggu Gradle Sync selesai.
4. **Jalankan Aplikasi** di Emulator atau HP fisik Android.
