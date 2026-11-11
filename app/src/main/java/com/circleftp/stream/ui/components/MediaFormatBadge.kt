package com.circleftp.stream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 1. Helper to get the extension
fun getFileExtension(url: String): String {
    return try {
        if (url.isBlank()) return "FHD"
        val clean = url.substringBefore('?').substringBefore('#')
        val lastSegment = clean.substringAfterLast('/')
        if (!lastSegment.contains('.')) return "FHD"
        val ext = lastSegment.substringAfterLast('.').take(3).uppercase()
        if (ext.isBlank()) "FHD" else ext
    } catch (e: Exception) {
        "FHD" // Fallback if no extension is found
    }
}

// 2. The UI Badge Component
@Composable
fun MediaFormatBadge(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val extension = getFileExtension(videoUrl)
    val isMkv = extension == "MKV"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 8.dp)
    ) {
        // The File Extension Badge (e.g., [ MP4 ] or [ MKV ])
        Box(
            modifier = Modifier
                .background(Color(0xFFE50914), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = extension,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // The Warning Text for MKV files
        if (isMkv) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "⚠️ Seeking may be unsupported",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

