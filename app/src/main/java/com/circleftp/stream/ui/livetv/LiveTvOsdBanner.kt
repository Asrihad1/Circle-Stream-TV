package com.circleftp.stream.ui.livetv

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.circleftp.stream.data.iptv.TvChannel
import com.circleftp.stream.ui.theme.CircleCard
import com.circleftp.stream.ui.theme.CircleDarkBg
import com.circleftp.stream.ui.theme.CircleRed
import com.circleftp.stream.ui.theme.TextPrimary
import com.circleftp.stream.ui.theme.TextSecondary

/**
 * Transient Android TV On-Screen Display (OSD Banner).
 * Displays channel number (e.g. 014), channel title, category, and live resolution badge.
 * Auto-fades out after 3.5 seconds of inactivity.
 */
@Composable
fun LiveTvOsdBanner(
    channel: TvChannel?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && channel != null,
        enter = fadeIn(tween(200)) + slideInVertically(
            animationSpec = tween(250),
            initialOffsetY = { it }
        ),
        exit = fadeOut(tween(350)) + slideOutVertically(
            animationSpec = tween(300),
            targetOffsetY = { it }
        ),
        modifier = modifier
    ) {
        if (channel == null) return@AnimatedVisibility

        Box(
            modifier = Modifier
                .padding(start = 36.dp, bottom = 36.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xE61A1C22),
                            Color(0xF50D0E12)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Large 3-Digit Channel Number (e.g. 014)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CircleRed.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CircleRed.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = channel.formattedNumber(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // Channel Info Column
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Channel Name
                    Text(
                        text = channel.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )

                    // Category & Live Resolution Badges Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Live Dot + Stream Tag (e.g. HD • Live / FHD • Live)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1B5E20).copy(alpha = 0.7f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF69F0AE))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = channel.streamTag,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE8F5E9)
                                )
                            }
                        }

                        // Category Tag
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = channel.category,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        // Server Node Tag
                        if (channel.serverNode.isNotBlank()) {
                            Text(
                                text = "BDIX",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // TV Remote Quick-Help Hint for older generation / casual users
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "▲ ▼ Switch Channel   •   OK Guide",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

/**
 * Top-right Numeric Keypad Buffer Overlay (e.g. "CH 12").
 * Shown instantaneously while the user is punching digits into the TV remote.
 */
@Composable
fun NumericBufferOverlay(
    buffer: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = buffer.isNotEmpty(),
        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xF20F1115),
            border = androidx.compose.foundation.BorderStroke(2.dp, CircleRed),
            modifier = Modifier.padding(top = 32.dp, end = 36.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "CH",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CircleRed
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buffer,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Transient notification pill for errors or feedback ("CH Not Found", "Channel offline").
 */
@Composable
fun NoticePillOverlay(
    notice: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !notice.isNullOrBlank(),
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xD9B71C1C),
            modifier = Modifier.padding(top = 32.dp)
        ) {
            Text(
                text = notice.orEmpty(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

