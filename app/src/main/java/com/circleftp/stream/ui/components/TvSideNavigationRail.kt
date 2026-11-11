package com.circleftp.stream.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.circleftp.stream.ui.theme.CircleCard
import com.circleftp.stream.ui.theme.CircleDarkBg
import com.circleftp.stream.ui.theme.CircleRed
import com.circleftp.stream.ui.theme.TextPrimary
import com.circleftp.stream.ui.theme.TextSecondary

enum class TvNavDestination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SEARCH("Search", Icons.Default.Search),
    LIBRARY("Library", Icons.Default.Bookmark)
}

/**
 * 10-Foot Android TV Collapsible Side Navigation Rail.
 *
 * Implements strict 3-option drawer: Home, Search, Library.
 * When any rail item is focused (D-pad Left), it smoothly expands to 180dp and displays text labels.
 * When focus moves back to the content grid (D-pad Right), it collapses to 72dp showing icons only.
 */
@Composable
fun TvSideNavigationRail(
    selectedDestination: TvNavDestination = TvNavDestination.HOME,
    onNavigate: (TvNavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    var isRailFocused by remember { mutableStateOf(false) }

    val railWidth by animateDpAsState(
        targetValue = if (isRailFocused) 180.dp else 72.dp,
        animationSpec = tween(180),
        label = "tv_rail_width"
    )

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
            .onFocusChanged { focusState ->
                isRailFocused = focusState.hasFocus
            },
        color = Color(0xF20F1116),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 20.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            // App Mini Icon + Primary Navigation
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // TV Logo Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp, bottom = 12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = CircleRed,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "TV",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    if (isRailFocused) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "CIRCLE STREAM",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                    }
                }

                // 3 Core Navigation Options
                TvNavRailButton(
                    destination = TvNavDestination.HOME,
                    isSelected = selectedDestination == TvNavDestination.HOME,
                    isExpanded = isRailFocused,
                    onClick = { onNavigate(TvNavDestination.HOME) }
                )
                TvNavRailButton(
                    destination = TvNavDestination.SEARCH,
                    isSelected = selectedDestination == TvNavDestination.SEARCH,
                    isExpanded = isRailFocused,
                    onClick = { onNavigate(TvNavDestination.SEARCH) }
                )
                TvNavRailButton(
                    destination = TvNavDestination.LIBRARY,
                    isSelected = selectedDestination == TvNavDestination.LIBRARY,
                    isExpanded = isRailFocused,
                    onClick = { onNavigate(TvNavDestination.LIBRARY) }
                )
            }
        }
    }
}

@Composable
private fun TvNavRailButton(
    destination: TvNavDestination,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(120),
        label = "rail_button_scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
                color = if (isFocused) CircleRed else if (isSelected) CircleRed.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isFocused -> CircleRed.copy(alpha = 0.25f)
            isSelected -> Color.White.copy(alpha = 0.08f)
            else -> Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = if (isFocused || isSelected) CircleRed else TextSecondary,
                modifier = Modifier.size(22.dp)
            )

            if (isExpanded) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = destination.label,
                    fontSize = 13.sp,
                    fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isFocused || isSelected) Color.White else TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

