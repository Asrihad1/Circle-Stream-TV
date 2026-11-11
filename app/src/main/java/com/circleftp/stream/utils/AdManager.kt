package com.circleftp.stream.utils

import android.content.Context

/**
 * TV Monetization Manager.
 * Suppresses touch-only mobile interstitials to prevent trapping TV remote users.
 */
object AdManager {
    var hasShownLaunchAdThisSession: Boolean = false
        private set

    fun shouldShowLaunchAd(): Boolean {
        return !hasShownLaunchAdThisSession
    }

    fun markLaunchAdShown() {
        hasShownLaunchAdThisSession = true
    }

    fun showInterstitialIfEligible(context: Context, onContinue: () -> Unit) {
        // TV-Dedicated Fork: Skip touch-only mobile interstitials to preserve 10-foot remote UX
        onContinue()
    }
}


