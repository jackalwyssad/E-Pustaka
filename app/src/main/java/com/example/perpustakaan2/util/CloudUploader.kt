package com.example.perpustakaan2.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object CloudUploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun uploadFile(file: File, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("fileToUpload", file.name, fileBody)
                .build()

            val request = Request.Builder()
                .url("https://catbox.moe/user/api.php")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val url = response.body?.string()?.trim() ?: ""
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    Result.success(url)
                } else {
                    Result.failure(Exception("Respon unggahan tidak valid: $url"))
                }
            } else {
                Result.failure(Exception("Gagal mengunggah (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Otomatis mengonversi URL Google Drive yang dibagikan menjadi link unduhan langsung
     */
    fun convertToDirectDownloadUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
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
        return trimmed
    }
}
