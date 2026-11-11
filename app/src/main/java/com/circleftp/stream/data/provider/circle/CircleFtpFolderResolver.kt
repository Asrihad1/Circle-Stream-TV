package com.circleftp.stream.data.provider.circle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Circle FTP Folder Resolver
 * Inspects Apache/h5ai directory listing pages ending with '/' and extracts
 * the primary media file (.mkv, .mp4, .ts) with in-memory caching.
 * Follows PRD Section 5.1 specification.
 */
object CircleFtpFolderResolver {

    private val cache = ConcurrentHashMap<String, String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val VIDEO_EXTENSIONS = listOf(".mkv", ".mp4", ".ts", ".m4v", ".avi", ".mov")

    private val HREF_PATTERN = Pattern.compile("href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

    fun hasVideoExtension(url: String): Boolean {
        val cleanUrl = url.substringBefore('?').lowercase()
        return VIDEO_EXTENSIONS.any { cleanUrl.endsWith(it) }
    }

    suspend fun resolve(rawUrl: String): String = withContext(Dispatchers.IO) {
        if (rawUrl.isBlank()) return@withContext rawUrl

        // 1. Fast path: If already a known media extension (.mkv, .mp4, .ts), return immediately
        if (hasVideoExtension(rawUrl)) return@withContext rawUrl

        // 2. Memory Cache: Check ConcurrentHashMap
        cache[rawUrl]?.let { return@withContext it }

        try {
            // 3. HTTP Ingestion: Fetch HTML directory index with 4-second timeout
            val request = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val html = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext rawUrl
                response.body?.string()
            } ?: return@withContext rawUrl

            // 4. Regex Analysis: Find all video links, pick the largest/best master video file
            val matcher = HREF_PATTERN.matcher(html)
            val mediaFiles = mutableListOf<String>()
            while (matcher.find()) {
                val href = matcher.group(1) ?: continue
                if (hasVideoExtension(href)) {
                    mediaFiles.add(href)
                }
            }

            if (mediaFiles.isEmpty()) {
                return@withContext rawUrl
            }

            val directUrl = selectBestMediaFile(rawUrl, mediaFiles)
            cache[rawUrl] = directUrl
            return@withContext directUrl
        } catch (_: Exception) {
            return@withContext rawUrl
        }
    }

    private fun selectBestMediaFile(baseUrl: String, files: List<String>): String {
        // Exclude sample files if full video is present
        val nonSample = files.filter { !it.contains("sample", ignoreCase = true) }
        val candidateList = if (nonSample.isNotEmpty()) nonSample else files

        val candidate = candidateList.maxByOrNull { file ->
            var score = 0
            val lower = file.lowercase()
            if (lower.endsWith(".mkv")) score += 10
            if (lower.endsWith(".mp4")) score += 8
            if (lower.contains("2160p") || lower.contains("4k")) score += 6
            if (lower.contains("1080p")) score += 5
            if (lower.contains("720p")) score += 2
            score
        } ?: candidateList.first()

        return if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            candidate
        } else {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val cleanCandidate = candidate.removePrefix("/")
            base + cleanCandidate
        }
    }
}

