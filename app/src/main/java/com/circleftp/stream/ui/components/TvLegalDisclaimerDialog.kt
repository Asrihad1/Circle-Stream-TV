package com.circleftp.stream.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * 10-Foot TV-Optimized Legal & Content Disclaimer Dialog.
 *
 * Implements strict D-pad focus management:
 * - Auto-focuses the primary action button on open ("I Agree & Continue" or "Close").
 * - Clear high-contrast focus highlight (Netflix red + white border) on TV remote focus.
 * - Prevents bypassing on first-launch while allowing smooth dismissal on subsequent views.
 */
@Composable
fun TvLegalDisclaimerDialog(
    isFirstLaunch: Boolean,
    onAcceptOrClose: () -> Unit
) {
    val buttonFocusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(Unit) {
        delay(50)
        try {
            buttonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    if (!isFirstLaunch) {
        BackHandler {
            onAcceptOrClose()
        }
    }

    Dialog(
        onDismissRequest = { if (!isFirstLaunch) onAcceptOrClose() },
        properties = DialogProperties(
            dismissOnBackPress = !isFirstLaunch,
            dismissOnClickOutside = !isFirstLaunch,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(0.85f)
                .background(Color(0xFF16161C), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(
                text = "Terms of Service & Content Disclaimer",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "1. Content & DMCA Disclaimer (Non-Affiliation)",
                    color = Color(0xFFE50914),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Circle Stream TV is strictly a client-side media player and directory index interface for local ISP and BDIX servers. Circle Stream TV does not host, upload, store, transcode, or control any video files or live television streams on its own servers. All media links are indexed automatically from third-party network nodes. We respect intellectual property rights and comply with copyright takedown requests directed to the respective upstream hosting providers.",
                    color = Color(0xFFD0D0D6),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Text(
                    text = "2. Terms of Use",
                    color = Color(0xFFE50914),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "This application is provided 'as-is' for personal, non-commercial viewing on authorized local networks. Users are solely responsible for verifying that their access to local ISP/BDIX media sources complies with applicable local laws.",
                    color = Color(0xFFD0D0D6),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Text(
                    text = "3. Privacy & Local Data",
                    color = Color(0xFFE50914),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Circle Stream TV does not require user accounts and collects zero personal information (no names, emails, contacts, or payment data). Watch history and playback progress are stored strictly locally on your TV device. Anonymous version checks are performed via Google Firebase Remote Config solely to deliver application updates.",
                    color = Color(0xFFD0D0D6),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.End)
                    .focusRequester(buttonFocusRequester)
                    .background(
                        color = if (isFocused) Color(0xFFE50914) else Color(0xFF2C2C35),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = if (isFocused) 2.dp else 0.dp,
                        color = if (isFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .focusable(interactionSource = interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onAcceptOrClose
                    )
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (isFirstLaunch) "I Agree & Continue" else "Close",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

