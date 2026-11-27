package com.example.perpustakaan2.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object CloudUploader {

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private const val APP_USER_AGENT = "EPustaka/1.0 (Android)"

    /**
     * Prefer IPv4 addresses before IPv6 to avoid long connection stalls on dual-stack
     * mobile networks with broken IPv6 routing.
     */
    private val ipv4FirstDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            return addresses.sortedBy { if (it is Inet4Address) 0 else 1 }
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(ipv4FirstDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private data class UploadTarget(
        val name: String,
        val uploader: (File, String) -> Result<String>
    )

    private val uploadTargets = listOf(
        UploadTarget("Catbox", ::uploadToCatbox),
        UploadTarget("0x0.st", ::uploadTo0x0),
        UploadTarget("Pomf", ::uploadToPomf),
        UploadTarget("Qu.ax", ::uploadToQuax),
        UploadTarget("Tmpfiles", ::uploadToTmpfiles)
    )

    suspend fun uploadFile(file: File, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("Berkas lokal tidak ditemukan atau kosong (${file.name})")
            )
        }

        val errors = mutableListOf<String>()
        for (target in uploadTargets) {
            val result = target.uploader(file, mimeType)
            if (result.isSuccess) {
                return@withContext result
            }
            val reason = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName } ?: "gagal"
            errors.add("${target.name}: $reason")
        }

        Result.failure(
            Exception("Gagal mengunggah berkas ke semua server cloud (${errors.joinToString("; ")})")
        )
    }

    private fun executeUpload(
        hostLabel: String,
        request: Request,
        parseUrl: (String) -> Result<String>
    ): Result<String> {
        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()?.trim().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(Exception("HTTP ${response.code}"))
                }
                if (bodyStr.isEmpty()) {
                    return Result.failure(Exception("Respon $hostLabel kosong"))
                }
                parseUrl(bodyStr)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun validateDirectUrl(hostLabel: String, rawUrl: String): Result<String> {
        val url = rawUrl.trim()
        val isHttp = url.startsWith("https://") || url.startsWith("http://")
        val looksLikeHtml = url.contains("<") || url.contains(">") || url.any { it.isWhitespace() }
        return if (isHttp && !looksLikeHtml) {
            Result.success(url)
        } else {
            Result.failure(Exception("Respon $hostLabel tidak valid"))
        }
    }

    private fun uploadToCatbox(file: File, mimeType: String): Result<String> {
        val fileBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("fileToUpload", file.name, fileBody)
            .build()

        val request = Request.Builder()
            .url("https://catbox.moe/user/api.php")
            .header("User-Agent", BROWSER_USER_AGENT)
            .post(requestBody)
            .build()

        return executeUpload("Catbox", request) { body ->
            validateDirectUrl("Catbox", body)
        }
    }

    private fun uploadTo0x0(file: File, mimeType: String): Result<String> {
        val fileBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, fileBody)
            .build()

        val request = Request.Builder()
            .url("https://0x0.st")
            .header("User-Agent", APP_USER_AGENT)
            .post(requestBody)
            .build()

        return executeUpload("0x0.st", request) { body ->
            validateDirectUrl("0x0.st", body)
        }
    }

    private fun uploadToPomf(file: File, mimeType: String): Result<String> {
        return uploadToPomfCompatible(
            hostLabel = "Pomf",
            endpoint = "https://pomf.lain.la/upload.php",
            file = file,
            mimeType = mimeType
        )
    }

    private fun uploadToQuax(file: File, mimeType: String): Result<String> {
        return uploadToPomfCompatible(
            hostLabel = "Qu.ax",
            endpoint = "https://qu.ax/upload.php",
            file = file,
            mimeType = mimeType
        )
    }

    private fun uploadToPomfCompatible(
        hostLabel: String,
        endpoint: String,
        file: File,
        mimeType: String
    ): Result<String> {
        val fileBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("files[]", file.name, fileBody)
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", BROWSER_USER_AGENT)
            .post(requestBody)
            .build()

        return executeUpload(hostLabel, request) { body ->
            val json = JSONObject(body)
            if (json.optBoolean("success", false)) {
                val files = json.optJSONArray("files")
                val url = files?.optJSONObject(0)?.optString("url").orEmpty()
                validateDirectUrl(hostLabel, url)
            } else {
                val desc = json.optString("description", "API error")
                Result.failure(Exception("$hostLabel: $desc"))
            }
        }
    }

    private fun uploadToTmpfiles(file: File, mimeType: String): Result<String> {
        val fileBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, fileBody)
            .build()

        val request = Request.Builder()
            .url("https://tmpfiles.org/api/v1/upload")
            .header("User-Agent", BROWSER_USER_AGENT)
            .post(requestBody)
            .build()

        return executeUpload("Tmpfiles", request) { body ->
            val json = JSONObject(body)
            if (json.optString("status") == "success") {
                val rawUrl = json.getJSONObject("data").getString("url")
                // Mengubah https://tmpfiles.org/12345/file.ext menjadi https://tmpfiles.org/dl/12345/file.ext untuk unduhan langsung
                val directUrl = rawUrl.replaceFirst("tmpfiles.org/", "tmpfiles.org/dl/")
                validateDirectUrl("Tmpfiles", directUrl)
            } else {
                Result.failure(Exception("Tmpfiles API error"))
            }
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
