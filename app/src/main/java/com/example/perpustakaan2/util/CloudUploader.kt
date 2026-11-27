package com.example.perpustakaan2.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object CloudUploader {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun uploadFile(file: File, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        // 1. Coba Catbox.moe terlebih dahulu
        val catboxRes = uploadToCatbox(file, mimeType)
        if (catboxRes.isSuccess) {
            return@withContext catboxRes
        }

        // 2. Jika Catbox gagal, coba tmpfiles.org sebagai cadangan
        val tmpfilesRes = uploadToTmpfiles(file, mimeType)
        if (tmpfilesRes.isSuccess) {
            return@withContext tmpfilesRes
        }

        // Jika semua gagal, kembalikan rincian error
        val errCatbox = catboxRes.exceptionOrNull()?.message ?: "Gagal Catbox"
        val errTmp = tmpfilesRes.exceptionOrNull()?.message ?: "Gagal Tmpfiles"
        Result.failure(Exception("Gagal mengunggah berkas ke Cloud. Catbox: $errCatbox | Tmpfiles: $errTmp"))
    }

    private fun uploadToCatbox(file: File, mimeType: String): Result<String> {
        return try {
            val fileBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("fileToUpload", file.name, fileBody)
                .build()

            val request = Request.Builder()
                .url("https://catbox.moe/user/api.php")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val url = response.body?.string()?.trim() ?: ""
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        Result.success(url)
                    } else {
                        Result.failure(Exception("Respon Catbox tidak valid: $url"))
                    }
                } else {
                    Result.failure(Exception("Catbox HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun uploadToTmpfiles(file: File, mimeType: String): Result<String> {
        return try {
            val fileBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, fileBody)
                .build()

            val request = Request.Builder()
                .url("https://tmpfiles.org/api/v1/upload")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string()?.trim() ?: ""
                    val json = JSONObject(responseStr)
                    if (json.optString("status") == "success") {
                        val rawUrl = json.getJSONObject("data").getString("url")
                        // Mengubah https://tmpfiles.org/12345/file.ext menjadi https://tmpfiles.org/dl/12345/file.ext untuk unduhan langsung
                        val directUrl = rawUrl.replace("tmpfiles.org/", "tmpfiles.org/dl/")
                        Result.success(directUrl)
                    } else {
                        Result.failure(Exception("Tmpfiles API error: $responseStr"))
                    }
                } else {
                    Result.failure(Exception("Tmpfiles HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Otomatis mengonversi URL Google Drive & Dropbox yang dibagikan menjadi link unduhan berkas langsung
     */
    fun convertToDirectDownloadUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()

        // Google Drive conversion
        if (trimmed.contains("drive.google.com")) {
            val fileIdRegex = Regex("/d/([a-zA-Z0-9_-]+)")
            val match = fileIdRegex.find(trimmed)
            if (match != null) {
                val fileId = match.groupValues[1]
                return "https://drive.google.com/uc?export=download&id=$fileId"
            }
            val idParamRegex = Regex("[?&]id=([a-zA-Z0-9_-]+)")
            val idMatch = idParamRegex.find(trimmed)
            if (idMatch != null) {
                val fileId = idMatch.groupValues[1]
                return "https://drive.google.com/uc?export=download&id=$fileId"
            }
        }

        // Dropbox conversion: dl=0 -> dl=1
        if (trimmed.contains("dropbox.com") && trimmed.contains("dl=0")) {
            return trimmed.replace("dl=0", "dl=1")
        }

        return trimmed
    }
}
