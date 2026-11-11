package com.circleftp.stream.player

import android.net.Uri

object UrlNormalizer {

    /**
     * Safely encodes the FTP/HTTP media URL path and parameters.
     * Rebuilds the URI so that path segments containing spaces (e.g. "Jailer 2")
     * or special characters are safely encoded to %20 without double-encoding existing percent escapes,
     * while leaving the base domain, port, and query parameters intact.
     * Prevents HTTP 404 errors on Circle FTP media servers.
     */
    fun getSafeEncodedUrl(originalUrl: String?): String {
        if (originalUrl.isNullOrBlank()) return ""
        val trimmed = stripFileTrailingSlash(originalUrl.trim())

        return try {
            val uri = Uri.parse(trimmed)
            if (!uri.isHierarchical || uri.scheme.isNullOrBlank() || uri.authority.isNullOrBlank()) {
                return sanitizeFallback(trimmed)
            }

            val builder = Uri.Builder()
                .scheme(uri.scheme)
                .encodedAuthority(uri.encodedAuthority ?: uri.authority)

            uri.pathSegments.forEach { segment ->
                builder.appendPath(segment) // Safely encodes spaces to %20 and special characters
            }

            // Preserve trailing slash if original URL was a directory
            if (trimmed.endsWith("/") && !builder.build().toString().endsWith("/")) {
                builder.appendPath("")
            }

            // Append any query parameters if they exist
            try {
                uri.queryParameterNames.forEach { key ->
                    builder.appendQueryParameter(key, uri.getQueryParameter(key))
                }
            } catch (_: Exception) {}

            builder.build().toString()
        } catch (e: Exception) {
            sanitizeFallback(trimmed)
        }
    }

    /**
     * Normalizes BDIX HTTP media URLs.
     * Uses [getSafeEncodedUrl] to ensure proper Uri path encoding.
     */
    fun normalize(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        return getSafeEncodedUrl(rawUrl)
    }

    private val VIDEO_EXT = Regex("""\.(mkv|mp4|avi|mov|m4v|webm|ts|m2ts|flv|wmv|mpg|mpeg|3gp|m3u8)$""", RegexOption.IGNORE_CASE)

    /** "…/Jailer 2 movie.mkv/" -> "…/Jailer 2 movie.mkv". Directory URLs keep their slash. */
    private fun stripFileTrailingSlash(url: String): String {
        val q = url.indexOfAny(charArrayOf('?', '#')).let { if (it == -1) url.length else it }
        val path = url.substring(0, q)
        if (!path.endsWith("/")) return url
        val stripped = path.trimEnd('/')
        return if (VIDEO_EXT.containsMatchIn(stripped)) stripped + url.substring(q) else url
    }

    private fun sanitizeFallback(trimmed: String): String {
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd == -1) return trimmed

        val scheme = trimmed.substring(0, schemeEnd + 3)
        val afterScheme = trimmed.substring(schemeEnd + 3)

        val slashIdx = afterScheme.indexOf('/')
        if (slashIdx == -1) return trimmed

        val host = afterScheme.substring(0, slashIdx)
        val rawPath = afterScheme.substring(slashIdx)

        val queryIdx = rawPath.indexOf('?')
        val pathOnly = if (queryIdx != -1) rawPath.substring(0, queryIdx) else rawPath
        val queryPart = if (queryIdx != -1) rawPath.substring(queryIdx) else ""

        val sanitizedPath = buildString {
            var i = 0
            val len = pathOnly.length
            while (i < len) {
                val c = pathOnly[i]
                when {
                    c == '%' && i + 2 < len && isHex(pathOnly[i + 1]) && isHex(pathOnly[i + 2]) -> {
                        append(c)
                        append(pathOnly[i + 1])
                        append(pathOnly[i + 2])
                        i += 2
                    }
                    c == ' ' -> append("%20")
                    c == '[' -> append("%5B")
                    c == ']' -> append("%5D")
                    c == '{' -> append("%7B")
                    c == '}' -> append("%7D")
                    c == '|' -> append("%7C")
                    c == '^' -> append("%5E")
                    c == '`' -> append("%60")
                    c == '\\' -> append("%5C")
                    else -> append(c)
                }
                i++
            }
        }

        return "$scheme$host$sanitizedPath$queryPart"
    }

    private fun isHex(c: Char): Boolean {
        return c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    }
}

