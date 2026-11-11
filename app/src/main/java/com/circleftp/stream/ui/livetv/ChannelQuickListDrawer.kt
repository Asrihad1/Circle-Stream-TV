package com.circleftp.stream.ui.livetv

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.circleftp.stream.data.iptv.TvChannel
import com.circleftp.stream.ui.theme.CircleCard
import com.circleftp.stream.ui.theme.CircleDarkBg
import com.circleftp.stream.ui.theme.CircleRed
import com.circleftp.stream.ui.theme.TextPrimary
import com.circleftp.stream.ui.theme.TextSecondary

/**
 * Slide-in Android TV Channel Quick-List Drawer.
 * Triggered by DPAD_CENTER / ENTER. Allows rapid remote-browsing through all channels.
 */
@Composable
fun ChannelQuickListDrawer(
    isOpen: Boolean,
    channels: List<TvChannel>,
    currentChannel: TvChannel?,
    onSelectChannel: (TvChannel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeChannelFocusRequester = remember { FocusRequester() }

    // Auto-scroll to active channel and request focus immediately on open
    LaunchedEffect(isOpen) {
        if (isOpen) {
            if (currentChannel != null) {
                val idx = channels.indexOfFirst { it.id == currentChannel.id }
                if (idx >= 0) {
                    listState.scrollToItem((idx - 2).coerceAtLeast(0))
                }
            }
            delay(50)
            try {
                activeChannelFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(
            animationSpec = tween(280),
            initialOffsetX = { -it }
        ) + fadeIn(tween(200)),
        exit = slideOutHorizontally(
            animationSpec = tween(250),
            targetOffsetX = { -it }
        ) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(360.dp),
            color = Color(0xF2101217),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TV Channel Guide",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${channels.size} live channels",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Guide",
                            tint = TextSecondary
                        )
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Channel List
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = channels,
                        key = { _, item -> item.id }
                    ) { idx, channel ->
                        val isCurrent = currentChannel?.id == channel.id
                        val isTargetForFocus = isCurrent || (currentChannel == null && idx == 0)
                        var isFocused by remember { mutableStateOf(false) }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isTargetForFocus) Modifier.focusRequester(activeChannelFocusRequester) else Modifier)
                                .clip(RoundedCornerShape(10.dp))
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .clickable { onSelectChannel(channel) },
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isFocused -> CircleRed.copy(alpha = 0.35f)
                                isCurrent -> CircleRed.copy(alpha = 0.15f)
                                else -> Color.White.copy(alpha = 0.04f)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isFocused) 2.dp else if (isCurrent) 1.dp else 0.dp,
                                color = if (isFocused) CircleRed else if (isCurrent) CircleRed.copy(alpha = 0.6f) else Color.Transparent
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Channel Number
                                Text(
                                    text = channel.formattedNumber(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isCurrent || isFocused) CircleRed else TextSecondary
                                )

                                // Channel Details
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = channel.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isCurrent || isFocused) FontWeight.Bold else FontWeight.Medium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = channel.category,
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                }

                                // Stream Tag Badge
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.White.copy(alpha = 0.08f)
                                ) {
                                    Text(
                                        text = channel.streamTag.substringBefore(" •"),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Playing",
                                        tint = CircleRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

