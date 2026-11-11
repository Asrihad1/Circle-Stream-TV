package com.circleftp.stream

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.circleftp.stream.data.api.CircleFtpApiClient
import com.circleftp.stream.data.config.ConfigManager
import com.circleftp.stream.data.iptv.IptvStreamResolver
import com.circleftp.stream.data.provider.ProviderRegistry
import com.circleftp.stream.player.ActivePlayerSession
import com.circleftp.stream.ui.catalog.CatalogScreen
import com.circleftp.stream.ui.components.TvLaunchAdDialog
import com.circleftp.stream.ui.components.TvLegalDisclaimerDialog
import com.circleftp.stream.ui.detail.DetailScreen
import com.circleftp.stream.ui.home.HomeScreen
import com.circleftp.stream.ui.library.LibraryScreen
import android.view.KeyEvent
import com.circleftp.stream.ui.livetv.LivePlayerView
import com.circleftp.stream.ui.livetv.LiveTvManager
import com.circleftp.stream.ui.livetv.LiveTvScreen
import com.circleftp.stream.ui.player.PlayerScreen
import com.circleftp.stream.ui.search.SearchScreen
import com.circleftp.stream.ui.theme.CircleCard
import com.circleftp.stream.ui.theme.CircleRed
import com.circleftp.stream.ui.theme.CircleStreamTheme
import com.circleftp.stream.ui.theme.TextPrimary
import com.circleftp.stream.ui.theme.TextSecondary
import com.circleftp.stream.updater.AppUpdater
import com.circleftp.stream.utils.AdManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Pass through volume keys to system audio
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            return super.onKeyDown(keyCode, event)
        }

        // Intercept TV remote actions when the live player screen is active
        if (LiveTvManager.isLivePlayerActive) {
            if (LiveTvManager.handleKeyDown(keyCode, event)) {
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Runtime Security & Anti-Crack Protection off the main UI thread
        lifecycleScope.launch(Dispatchers.IO) {
            if (com.circleftp.stream.security.AntiReverseGuard.isDebuggerConnected() ||
                com.circleftp.stream.security.AntiReverseGuard.isFridaActive() ||
                com.circleftp.stream.security.AntiReverseGuard.isFridaPortOpen() ||
                !com.circleftp.stream.security.AntiReverseGuard.isSignatureValid(this@MainActivity)
            ) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Security Alert: App integrity compromised", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }

        enableEdgeToEdge()

        setContent {
            CircleStreamTheme {
                CircleStreamApp()
            }
        }
    }
}

@Composable
fun CircleStreamApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val apiClient = remember { CircleFtpApiClient() }
    val configManager = remember { ConfigManager.getInstance(context) }
    val providerRegistry = remember { ProviderRegistry.getInstance(context, configManager) }

    // Firebase Remote Config Auto-Updater State
    var updateDownloadUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Asynchronously fetch remote provider domains
        configManager.fetchRemoteConfig()

        // Firebase Remote Config Auto-Update check
        AppUpdater.checkForUpdates(currentVersionCode = BuildConfig.VERSION_CODE.toLong()) { apkUrl ->
            updateDownloadUrl = apkUrl
        }
    }

    // First-Launch Legal & Content Disclaimer Dialog
    val prefs = remember { context.getSharedPreferences("circle_stream_prefs", Context.MODE_PRIVATE) }
    var showFirstLaunchLegal by remember {
        mutableStateOf(!prefs.getBoolean("legal_accepted_v1", false))
    }

    if (showFirstLaunchLegal) {
        TvLegalDisclaimerDialog(
            isFirstLaunch = true,
            onAcceptOrClose = {
                prefs.edit().putBoolean("legal_accepted_v1", true).apply()
                showFirstLaunchLegal = false
            }
        )
    }

    // Android TV Intro Launch Ad Dialog (Shows exactly once per session after legal check)
    var showLaunchAd by remember { mutableStateOf(AdManager.shouldShowLaunchAd()) }
    if (!showFirstLaunchLegal && showLaunchAd) {
        TvLaunchAdDialog(
            onDismiss = {
                showLaunchAd = false
                AdManager.markLaunchAdShown()
            }
        )
    }

    // Persistent Mandatory Update Dialog via Firebase Remote Config
    updateDownloadUrl?.let { apkUrl ->
        val safeUrl = remember(apkUrl) { AppUpdater.enforceHttps(apkUrl) }
        var isDownloading by remember { mutableStateOf(false) }
        var progressPercent by remember { mutableStateOf(0) }
        var downloadError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                // Persistent dialog: Dismissal disabled for mandatory updates
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = {
                Text(
                    text = "Update Available",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isDownloading) {
                            "Downloading update... $progressPercent%"
                        } else {
                            "A new version of Circle Stream is available! Please update to continue."
                        },
                        fontSize = 15.sp,
                        color = TextSecondary
                    )
                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = CircleRed,
                            trackColor = Color.DarkGray
                        )
                    }
                    if (downloadError != null) {
                        Text(
                            text = downloadError ?: "",
                            fontSize = 12.sp,
                            color = CircleRed
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isDownloading) {
                            isDownloading = true
                            downloadError = null
                            AppUpdater.downloadAndInstall(
                                context = context,
                                apkUrl = safeUrl,
                                onProgress = { progress ->
                                    progressPercent = progress
                                },
                                onError = { error ->
                                    isDownloading = false
                                    downloadError = "Download failed: $error. Opening browser fallback..."
                                    Toast.makeText(context, "Download failed, opening browser...", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CircleRed,
                        disabledContainerColor = CircleRed.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isDownloading) "Downloading ($progressPercent%)..." else "Update Now",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            containerColor = CircleCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                apiClient = apiClient,
                onSelectProvider = { providerId ->
                    if (providerId == "iptvidn") {
                        LiveTvManager.resumeLastChannelOrDefault(context)
                        navController.navigate("live_player")
                    } else {
                        navController.navigate("catalog/$providerId")
                    }
                },
                onSelectMedia = { providerId, mediaId ->
                    if (providerId == "iptvidn") {
                        LiveTvManager.playChannelById(mediaId)
                        navController.navigate("live_player")
                    } else {
                        navController.navigate("detail/$providerId/$mediaId")
                    }
                },
                onSelectPost = { postId ->
                    navController.navigate("detail/circleftp/$postId")
                },
                onResumePlayback = { historyItem ->
                    AdManager.showInterstitialIfEligible(context) {
                        val resolvedPostId = historyItem.parentSeriesId?.toLongOrNull()
                            ?: historyItem.mediaId.removePrefix("series_").removePrefix("movie_").toLongOrNull()
                            ?: 0L
                        ActivePlayerSession.set(
                            mediaId = historyItem.mediaId,
                            title = historyItem.title,
                            streamUrl = historyItem.streamUrl,
                            postId = resolvedPostId,
                            posterUrl = historyItem.posterUrl,
                            isSeries = historyItem.isSeries,
                            seasonNumber = if (historyItem.isSeries) historyItem.seasonNumber else null,
                            episodeNumber = if (historyItem.isSeries) historyItem.episodeNumber else null,
                            parentSeriesId = if (historyItem.isSeries) historyItem.parentSeriesId else null,
                            index = if (historyItem.isSeries && historyItem.episodeNumber != null) {
                                (historyItem.episodeNumber - 1).coerceAtLeast(0)
                            } else {
                                -1
                            }
                        )
                        navController.navigate("player")
                    }
                },
                onOpenSearch = {
                    navController.navigate("search")
                },
                onOpenLibrary = {
                    navController.navigate("library")
                },
                onOpenLiveTv = {
                    LiveTvManager.resumeLastChannelOrDefault(context)
                    navController.navigate("live_player")
                }
            )
        }

        composable("livetv") {
            LivePlayerView(
                onBack = { navController.popBackStack() }
            )
        }

        composable("live_player") {
            LivePlayerView(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "catalog/{providerId}",
            arguments = listOf(navArgument("providerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString("providerId") ?: "circleftp"
            CatalogScreen(
                providerId = providerId,
                onBack = { navController.popBackStack() },
                onSelectMedia = { provId, mediaId ->
                    navController.navigate("detail/$provId/$mediaId")
                }
            )
        }

        composable(
            route = "detail/{providerId}/{mediaId}",
            arguments = listOf(
                navArgument("providerId") { type = NavType.StringType },
                navArgument("mediaId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString("providerId") ?: "circleftp"
            val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
            DetailScreen(
                providerId = providerId,
                mediaId = mediaId,
                onBack = { navController.popBackStack() },
                onPlay = { _, _ ->
                    navController.navigate("player")
                }
            )
        }

        composable(
            route = "detail/{postId}",
            arguments = listOf(navArgument("postId") { type = NavType.LongType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getLong("postId") ?: 0L
            DetailScreen(
                providerId = "circleftp",
                mediaId = postId.toString(),
                postId = postId,
                onBack = { navController.popBackStack() },
                onPlay = { _, _ ->
                    ActivePlayerSession.postId = postId
                    navController.navigate("player")
                }
            )
        }

        composable(route = "player") {
            PlayerScreen(
                onClose = { navController.popBackStack() }
            )
        }

        composable("search") {
            SearchScreen(
                apiClient = apiClient,
                onBack = { navController.popBackStack() },
                onSelectMedia = { providerId, mediaId ->
                    navController.navigate("detail/$providerId/$mediaId")
                },
                onSelectPost = { postId ->
                    navController.navigate("detail/circleftp/$postId")
                }
            )
        }

        composable("library") {
            LibraryScreen(
                onBack = { navController.popBackStack() },
                onPlayMedia = { historyItem ->
                    val resolvedPostId = historyItem.parentSeriesId?.toLongOrNull()
                        ?: historyItem.mediaId.removePrefix("series_").removePrefix("movie_").toLongOrNull()
                        ?: 0L
                    ActivePlayerSession.set(
                        mediaId = historyItem.mediaId,
                        title = historyItem.title,
                        streamUrl = historyItem.streamUrl,
                        postId = resolvedPostId,
                        posterUrl = historyItem.posterUrl,
                        isSeries = historyItem.isSeries,
                        seasonNumber = if (historyItem.isSeries) historyItem.seasonNumber else null,
                        episodeNumber = if (historyItem.isSeries) historyItem.episodeNumber else null,
                        parentSeriesId = if (historyItem.isSeries) historyItem.parentSeriesId else null,
                        index = if (historyItem.isSeries && historyItem.episodeNumber != null) {
                            (historyItem.episodeNumber - 1).coerceAtLeast(0)
                        } else {
                            -1
                        }
                    )
                    navController.navigate("player")
                },
                onPlayWatchLater = { item ->
                    val resolvedPostId = item.mediaId.removePrefix("series_").removePrefix("movie_").toLongOrNull() ?: 0L
                    ActivePlayerSession.set(
                        mediaId = item.mediaId,
                        title = item.title,
                        streamUrl = item.streamUrl,
                        postId = resolvedPostId,
                        posterUrl = item.posterUrl,
                        isSeries = item.isSeries
                    )
                    navController.navigate("player")
                },
                onSelectPostId = { postId ->
                    navController.navigate("detail/$postId")
                }
            )
        }
    }
}
