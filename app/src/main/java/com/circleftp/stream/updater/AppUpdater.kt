package com.circleftp.stream.updater

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.circleftp.stream.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Singleton class responsible for checking sideloaded APK updates
 * via Firebase Remote Config and downloading/installing them via DownloadManager.
 */
object AppUpdater {

    private const val TAG = "AppUpdater"
    private const val UPDATE_FILE_NAME = "circlestream_update.apk"

    // Remote Config parameter keys
    const val KEY_LATEST_VERSION_CODE = "latest_version_code"
    const val KEY_APK_DOWNLOAD_URL = "apk_download_url"

    private val updaterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Enforces HTTPS on the given URL.
     * Converts http:// to https:// or prepends https:// if scheme is missing.
     */
    fun enforceHttps(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
            trimmed.isNotBlank() -> "https://$trimmed"
            else -> ""
        }
    }

    /**
     * Initializes Firebase Remote Config, fetches and activates values,
     * and evaluates if a newer version is available.
     *
     * @param currentVersionCode The current application version code (e.g. from BuildConfig.VERSION_CODE)
     * @param onUpdateAvailable Callback invoked with the APK download URL if latest_version_code > currentVersionCode
     */
    fun checkForUpdates(
        currentVersionCode: Long,
        onUpdateAvailable: (String) -> Unit
    ) {
        val remoteConfig = FirebaseRemoteConfig.getInstance()

        // 1-hour cache for production to protect Firebase quota
        val minimumFetchInterval = 3600L

        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(minimumFetchInterval)
            .build()

        remoteConfig.setConfigSettingsAsync(configSettings)

        // Set safe default values
        val defaultValues = mapOf<String, Any>(
            KEY_LATEST_VERSION_CODE to currentVersionCode,
            KEY_APK_DOWNLOAD_URL to ""
        )
        remoteConfig.setDefaultsAsync(defaultValues)

        Log.d(TAG, "Fetching Firebase Remote Config with interval ${minimumFetchInterval}s (currentVersionCode: $currentVersionCode)...")

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val latestVersionCode = remoteConfig.getLong(KEY_LATEST_VERSION_CODE)
                val rawUrl = remoteConfig.getString(KEY_APK_DOWNLOAD_URL)
                val apkDownloadUrl = enforceHttps(rawUrl)

                Log.d(
                    TAG,
                    "Remote Config fetched successfully. latest_version_code=$latestVersionCode, " +
                            "currentVersionCode=$currentVersionCode, apk_download_url=$apkDownloadUrl"
                )

                if (latestVersionCode > currentVersionCode && apkDownloadUrl.isNotBlank()) {
                    Log.i(TAG, "New update found! Version $latestVersionCode > $currentVersionCode. Triggering update callback.")
                    onUpdateAvailable(apkDownloadUrl)
                } else {
                    Log.d(TAG, "App is up to date (remote $latestVersionCode <= current $currentVersionCode).")
                }
            } else {
                val exception = task.exception
                Log.e(TAG, "Failed to fetch and activate Firebase Remote Config: ${exception?.message}", exception)
            }
        }
    }

    /**
     * Convenience overload using the app's compiled [BuildConfig.VERSION_CODE].
     */
    fun checkForUpdates(onUpdateAvailable: (String) -> Unit) {
        checkForUpdates(
            currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
            onUpdateAvailable = onUpdateAvailable
        )
    }

    /**
     * Downloads the update APK using Android's DownloadManager, polls download progress,
     * and triggers the system package installer via FileProvider and REQUEST_INSTALL_PACKAGES.
     * Falls back to browser ACTION_VIEW if DownloadManager is unavailable or fails.
     */
    fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val safeUrl = enforceHttps(apkUrl)
        if (safeUrl.isBlank()) {
            val err = "Download URL is invalid or empty"
            Log.e(TAG, err)
            onError(err)
            return
        }

        val appContext = context.applicationContext

        updaterScope.launch {
            try {
                val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (downloadManager == null) {
                    throw IllegalStateException("DownloadManager system service is unavailable on this device")
                }

                val downloadDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: appContext.filesDir
                val apkFile = File(downloadDir, UPDATE_FILE_NAME)

                // Clean up any stale partial APK file
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                val request = DownloadManager.Request(Uri.parse(safeUrl)).apply {
                    setTitle("Circle Stream TV Update")
                    setDescription("Downloading latest update...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                    setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, UPDATE_FILE_NAME)
                }

                val downloadId = downloadManager.enqueue(request)
                Log.d(TAG, "Enqueued download with ID $downloadId for $safeUrl")

                var isDownloading = true
                var pollAttempts = 0
                val maxAttempts = 1200 // 10 minutes at 500ms intervals

                while (isDownloading && pollAttempts < maxAttempts) {
                    delay(500)
                    pollAttempts++

                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)

                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1

                        val bytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val downloadedBytes = if (bytesIndex >= 0) cursor.getLong(bytesIndex) else 0L
                        val totalBytes = if (totalIndex >= 0) cursor.getLong(totalIndex) else -1L

                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            withContext(Dispatchers.Main) {
                                onProgress(percent)
                            }
                        }

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                isDownloading = false
                                cursor.close()
                                Log.i(TAG, "Download completed successfully: ${apkFile.absolutePath}")
                                withContext(Dispatchers.Main) {
                                    onProgress(100)
                                    installApkFile(appContext, apkFile)
                                }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                isDownloading = false
                                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                                cursor.close()
                                val errorMsg = "Download failed (reason code: $reason)"
                                Log.e(TAG, errorMsg)
                                withContext(Dispatchers.Main) {
                                    onError(errorMsg)
                                    fallbackToBrowser(appContext, safeUrl)
                                }
                            }
                            else -> {
                                cursor.close()
                            }
                        }
                    } else {
                        cursor?.close()
                    }
                }

                if (pollAttempts >= maxAttempts && isDownloading) {
                    val timeoutMsg = "Download timed out after 10 minutes"
                    Log.e(TAG, timeoutMsg)
                    withContext(Dispatchers.Main) {
                        onError(timeoutMsg)
                        fallbackToBrowser(appContext, safeUrl)
                    }
                }

            } catch (t: Throwable) {
                Log.e(TAG, "DownloadManager failed: ${t.message}. Falling back to browser ACTION_VIEW.", t)
                withContext(Dispatchers.Main) {
                    onError(t.message ?: "Download failed")
                    fallbackToBrowser(appContext, safeUrl)
                }
            }
        }
    }

    /**
     * Launches the system package installer for the downloaded APK file
     * using FileProvider and REQUEST_INSTALL_PACKAGES.
     */
    fun installApkFile(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Log.e(TAG, "Cannot install: APK file does not exist or is empty: ${apkFile.absolutePath}")
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            Log.i(TAG, "Launching package installer with content URI: $apkUri")
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer for ${apkFile.absolutePath}", e)
            Toast.makeText(context, "Failed to launch installer. Please grant install permissions.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Fallback to launching the URL via browser intent if DownloadManager is disabled or fails.
     */
    fun fallbackToBrowser(context: Context, url: String) {
        val safeUrl = enforceHttps(url)
        try {
            Log.i(TAG, "Falling back to browser ACTION_VIEW with $safeUrl")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch browser intent for $safeUrl", e)
            Toast.makeText(context, "Could not open browser for update download.", Toast.LENGTH_LONG).show()
        }
    }
}
