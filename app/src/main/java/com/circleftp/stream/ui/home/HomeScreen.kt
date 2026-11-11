package com.circleftp.stream.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import com.circleftp.stream.ui.components.TvNavDestination
import com.circleftp.stream.ui.components.TvSideNavigationRail
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.circleftp.stream.R
import com.circleftp.stream.data.api.CircleFtpApiClient
import com.circleftp.stream.data.local.WatchHistoryEntity
import com.circleftp.stream.data.model.PostSummary
import com.circleftp.stream.data.provider.MediaSourceProvider
import com.circleftp.stream.data.provider.ProviderRegistry
import com.circleftp.stream.data.provider.model.MediaItemModel
import com.circleftp.stream.player.CircleDownloadManager
import com.circleftp.stream.player.rememberNetworkSpeed
import com.circleftp.stream.ui.components.ContinueWatchingRow
import com.circleftp.stream.ui.components.TvLegalDisclaimerDialog
import com.circleftp.stream.ui.theme.*

/**
 * The Source Dashboard Hub.
 * Acts as the centralized media engine launcher displaying all available streaming providers
 * in large, visual interactive cards with BDIX/Global indicators.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    apiClient: CircleFtpApiClient? = null,
    onSelectProvider: (String) -> Unit,
    onSelectMedia: (providerId: String, mediaId: String) -> Unit = { prov, id -> },
    onSelectPost: (Long) -> Unit = { onSelectMedia("circleftp", it.toString()) },
    onResumePlayback: (WatchHistoryEntity) -> Unit = {},
    onOpenSearch: () -> Unit,
    onOpenLibrary: () -> Unit = {},
    onOpenLiveTv: () -> Unit = {}
) {
    val context = LocalContext.current
    val providerRegistry = remember { ProviderRegistry.getInstance(context) }
    val providers = remember { providerRegistry.availableProviders }

    val activeDownloads by CircleDownloadManager.pollActiveDownloads(context)
        .collectAsState(initial = emptyList())

    val netSpeed by rememberNetworkSpeed()

    var showLegalDialog by remember { mutableStateOf(false) }

    if (showLegalDialog) {
        TvLegalDisclaimerDialog(
            isFirstLaunch = false,
            onAcceptOrClose = { showLegalDialog = false }
        )
    }

    // Auto-focus Live TV on launch for immediate remote readiness
    val liveTvFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            liveTvFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(CircleDarkBg)
    ) {
        // Left-Side TV Navigation Rail (3 Core Options: Home, Search, Library)
        TvSideNavigationRail(
            selectedDestination = TvNavDestination.HOME,
            onNavigate = { destination ->
                when (destination) {
                    TvNavDestination.HOME -> { /* Already on Home */ }
                    TvNavDestination.SEARCH -> onOpenSearch()
                    TvNavDestination.LIBRARY -> onOpenLibrary()
                }
            }
        )

        // Main 10-Foot Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Header Bar: Logo, Speed Badge, Active Downloads
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo_header),
                        contentDescription = "Circle Stream TV",
                        modifier = Modifier
                            .height(30.dp)
                            .wrapContentWidth(),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1B5E20)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Net Speed",
                                tint = Color(0xFF69F0AE),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = netSpeed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF69F0AE),
                                maxLines = 1
                            )
                        }
                    }
                }

                if (activeDownloads.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CircleRed.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, CircleRed)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = CircleRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${activeDownloads.size} Downloading",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 24.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Primary Experience Selector: Live TV vs Movies & Shows
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
                    Text(
                        text = "What would you like to watch?",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Live TV Card (Auto-focused on TV launch)
                        HeroWatchModeCard(
                            title = "Live TV",
                            subtitle = "89+ Live Channels",
                            badgeText = "BDIX LIVE",
                            accentColor = Color(0xFFFF5252),
                            badgeBgColor = Color(0xFFB71C1C),
                            icon = Icons.Default.LiveTv,
                            focusRequester = liveTvFocusRequester,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenLiveTv
                        )
                        // Movies & Shows Card
                        HeroWatchModeCard(
                            title = "Movies & Shows",
                            subtitle = "Circle FTP VOD",
                            badgeText = "BDIX MIRROR",
                            accentColor = Color(0xFF00E676),
                            badgeBgColor = Color(0xFF004D40),
                            icon = Icons.Default.Movie,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectProvider("circleftp") }
                        )
                    }
                }
            }

            // Continue Watching section (persisted local resume playback)
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueWatchingRow(
                    onResumeItem = onResumePlayback
                )
            }

            // Hub Header Title & Subtitle
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text(
                        text = "Streaming Engines",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Select a source to explore trending catalogs, movies, anime, and shows",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Large Attractive Provider Cards
            items(providers, key = { it.providerId }) { provider ->
                ProviderHubCard(
                    provider = provider,
                    onClick = {
                        if (provider.providerId == "iptvidn") {
                            onOpenLiveTv()
                        } else {
                            onSelectProvider(provider.providerId)
                        }
                    }
                )
            }

            // D-pad accessible Legal & Content Disclaimer Footer Button
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val legalInteractionSource = remember { MutableInteractionSource() }
                    val isLegalFocused by legalInteractionSource.collectIsFocusedAsState()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                color = if (isLegalFocused) CircleRed.copy(alpha = 0.25f) else Color(0xFF1E1E26)
                            )
                            .border(
                                width = if (isLegalFocused) 1.5.dp else 1.dp,
                                color = if (isLegalFocused) CircleRed else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .focusable(interactionSource = legalInteractionSource)
                            .clickable(
                                interactionSource = legalInteractionSource,
                                indication = null,
                                onClick = { showLegalDialog = true }
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Legal & Disclaimer",
                            tint = if (isLegalFocused) Color.White else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Terms of Service & Content Disclaimer",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isLegalFocused) Color.White else TextSecondary
                        )
                    }
                }
            }
        }
    }
    }
}

/**
 * Large, interactive Source Hub Card for a specific streaming engine.
 */
@Composable
fun ProviderHubCard(
    provider: MediaSourceProvider,
    onClick: () -> Unit
) {
    val meta = remember(provider.providerId) { getProviderVisualMeta(provider) }
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1.0f,
        animationSpec = tween(150),
        label = "provider_card_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                brush = if (isFocused) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            meta.accentColor,
                            CircleRed
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            meta.accentColor.copy(alpha = 0.6f),
                            CircleCard
                        )
                    )
                },
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) CircleCard.copy(alpha = 0.95f) else CircleCard
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            meta.accentColor.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        radius = 450f
                    )
                )
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Provider Icon in tinted badge
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(meta.accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = meta.icon,
                            contentDescription = provider.providerName,
                            tint = meta.accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Network/Peering Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = meta.badgeBgColor
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (provider.isBdixOnly) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = meta.badgeTextColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = meta.badgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = meta.badgeTextColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Provider Name & Subtitle
                Text(
                    text = provider.providerName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = meta.subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = meta.accentColor
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Description
                Text(
                    text = meta.description,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Call to action button/pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Explore Catalog",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Explore",
                        tint = meta.accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private data class ProviderVisualMeta(
    val icon: ImageVector,
    val accentColor: Color,
    val badgeText: String,
    val badgeBgColor: Color,
    val badgeTextColor: Color,
    val subtitle: String,
    val description: String
)

private fun getProviderVisualMeta(provider: MediaSourceProvider): ProviderVisualMeta {
    return when (provider.providerId.lowercase()) {
        "circleftp", "circle_ftp" -> ProviderVisualMeta(
            icon = Icons.Default.Bolt,
            accentColor = Color(0xFF00E676),
            badgeText = "BDIX Wi-Fi Only",
            badgeBgColor = Color(0xFF004D40),
            badgeTextColor = Color(0xFF69F0AE),
            subtitle = "Local High-Speed Mirror",
            description = "Direct HTTP .mp4/.mkv streams with dedicated BDIX buffer and fast timeline seeking."
        )
        "iptvidn", "live_tv" -> ProviderVisualMeta(
            icon = Icons.Default.LiveTv,
            accentColor = Color(0xFFFF5252),
            badgeText = "BDIX Live TV • 1080p",
            badgeBgColor = Color(0xFFB71C1C),
            badgeTextColor = Color(0xFFFFCDD2),
            subtitle = "140+ HD Live Broadcasts",
            description = "Live Sports, Cricket, Football, News & Entertainment with instant stream switching."
        )
        else -> ProviderVisualMeta(
            icon = Icons.Default.Tv,
            accentColor = CircleRed,
            badgeText = if (provider.isBdixOnly) "BDIX Wi-Fi Only" else "Global",
            badgeBgColor = CircleSurface,
            badgeTextColor = TextPrimary,
            subtitle = "Streaming Engine",
            description = "Aggregated media provider."
        )
    }
}

/**
 * Large, prominent Watch Mode selector card on the Home Screen.
 */
@Composable
fun HeroWatchModeCard(
    title: String,
    subtitle: String,
    badgeText: String,
    accentColor: Color,
    badgeBgColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label = "hero_card_scale"
    )

    Card(
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                brush = if (isFocused) {
                    Brush.verticalGradient(
                        colors = listOf(
                            CircleRed,
                            Color.White
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.5f),
                            CircleCard
                        )
                    )
                },
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) CircleCard.copy(alpha = 0.95f) else CircleCard
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeBgColor
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Watch Now",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// Common Media Cards used by CatalogScreen, SearchScreen, and CategoryScreen
// ---------------------------------------------------------------------------------

@Composable
fun UnifiedMediaCard(item: MediaItemModel, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // 1. "Bump Up" Scale Animation
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "card_scale"
    )

    // 2. "Light Stroke" Border Animation
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "card_border"
    )

    Column(
        modifier = Modifier
            .width(130.dp)
            .zIndex(if (isFocused) 1f else 0f) // Prevents scaling under neighboring cards
            .scale(scale)
            .border(width = if (isFocused) 3.dp else 0.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) {
                onClick()
            }
    ) {
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(190.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CircleCard)
        ) {
            AsyncImage(
                model = item.posterUrl ?: item.bannerUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (!item.quality.isNullOrBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(topStart = 4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = item.quality,
                        fontSize = 9.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            if (item.isBdix) {
                Surface(
                    color = CircleRed.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomEnd = 4.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "BDIX",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            color = if (isFocused) Color.White else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!item.releaseYear.isNullOrBlank()) {
            Text(
                text = item.releaseYear,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun MediaCard(post: PostSummary, onClick: () -> Unit) {
    val model = remember(post) {
        MediaItemModel(
            id = post.id.toString(),
            title = post.displayTitle,
            posterUrl = post.getPosterUrl(),
            quality = post.quality,
            releaseYear = post.year,
            providerId = "circleftp",
            isBdix = true
        )
    }
    UnifiedMediaCard(item = model, onClick = onClick)
}
