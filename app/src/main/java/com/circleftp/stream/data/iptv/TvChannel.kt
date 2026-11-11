package com.circleftp.stream.data.iptv

import kotlinx.serialization.Serializable

/**
 * Android TV Channel entity with physical channel numbering,
 * live resolution badges, and server routing.
 */
@Serializable
data class TvChannel(
    val channelNumber: Int,
    val id: String,
    val name: String,
    val category: String,
    val resolution: String = "1080p",
    val logoUrl: String? = null,
    val streamTag: String = "HD • Live",
    val isActive: Boolean = true,
    val serverNode: String = "103.89.248.26:8082"
) {
    /**
     * Formats channel number into classic 3-digit TV display format (e.g. 001, 014).
     */
    fun formattedNumber(): String = String.format("%03d", channelNumber)

    companion object {
        fun fromIptvChannel(number: Int, iptv: IptvChannel): TvChannel {
            val tag = when {
                iptv.resolution.contains("1920", ignoreCase = true) || iptv.resolution.contains("1080", ignoreCase = true) -> "FHD • Live"
                iptv.resolution.contains("1280", ignoreCase = true) || iptv.resolution.contains("720", ignoreCase = true) -> "HD • Live"
                else -> "SD • Live"
            }
            return TvChannel(
                channelNumber = number,
                id = iptv.id,
                name = iptv.name,
                category = iptv.category,
                resolution = iptv.resolution,
                logoUrl = null,
                streamTag = tag,
                isActive = iptv.isActive,
                serverNode = iptv.serverNode
            )
        }
    }
}

