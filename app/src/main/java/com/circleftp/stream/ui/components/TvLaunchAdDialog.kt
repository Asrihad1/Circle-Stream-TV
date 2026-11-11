package com.circleftp.stream.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.circleftp.stream.ui.theme.CircleCard
import com.circleftp.stream.ui.theme.CircleDarkBg
import com.circleftp.stream.ui.theme.CircleRed
import com.circleftp.stream.ui.theme.TextPrimary
import com.circleftp.stream.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * 10-Foot TV-Friendly Launch Intro Ad Dialog.
 *
 * Designed specifically for Android TV remotes:
 * - Shows exactly once per cold app launch session.
 * - Auto-focuses the [ Close ] button immediately so a single remote 'OK' click dismisses it.
 * - Hardware remote BACK button immediately dismisses.
 * - 5-second gentle auto-dismiss countdown.
 */
@Composable
fun TvLaunchAdDialog(
    onDismiss: () -> Unit
) {
    val closeButtonFocusRequester = remember { FocusRequester() }
    var countdownSeconds by remember { mutableIntStateOf(5) }
    var isCloseFocused by remember { mutableStateOf(false) }

    // Auto-focus the close button immediately on appearance
    LaunchedEffect(Unit) {
        try {
            closeButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}

        // 5-second countdown timer
        while (countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds--
        }
        onDismiss()
    }

    // Hardware remote BACK button closes dialog cleanly
    BackHandler {
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(560.dp)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                CircleRed.copy(alpha = 0.6f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ),
                color = Color(0xF212141C),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Sponsor Tag Pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1B5E20).copy(alpha = 0.8f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFF69F0AE),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SPONSORED PARTNER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE8F5E9),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title
                    Text(
                        text = "Circle Stream TV",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "High-Speed BDIX Video Streaming & Live Broadcasts",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // 16:9 Promotional Showcase Banner Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF2A080C),
                                        Color(0xFF161922),
                                        Color(0xFF0D0F14)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = CircleRed.copy(alpha = 0.2f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.LiveTv,
                                            contentDescription = null,
                                            tint = CircleRed,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = "100+ Live Channels • Movies & Shows",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Zero Buffering • Dedicated BDIX Peering Engine",
                                        fontSize = 12.sp,
                                        color = Color(0xFF69F0AE)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Enjoy seamless entertainment on your living room screen",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Auto-Focused Remote Dismiss Button
                    val scale by animateFloatAsState(
                        targetValue = if (isCloseFocused) 1.05f else 1.0f,
                        animationSpec = tween(150),
                        label = "ad_close_scale"
                    )

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .focusRequester(closeButtonFocusRequester)
                            .onFocusChanged { isCloseFocused = it.isFocused }
                            .focusable(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCloseFocused) CircleRed else CircleRed.copy(alpha = 0.85f)
                        ),
                        border = if (isCloseFocused) BorderStroke(2.5.dp, Color.White) else null,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Ad",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (countdownSeconds > 0) "Close & Continue (${countdownSeconds}s)" else "Close & Continue",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Press OK or BACK on remote to skip",
                        fontSize = 11.sp,
                        color = TextSecondary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

