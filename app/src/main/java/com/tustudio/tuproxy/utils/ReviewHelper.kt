package com.tustudio.tuproxy.utils

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Play In-App Review: prompts on the 3rd, 10th and 25th app open.
 * Quota is enforced by Play; calls outside quota are silently ignored.
 */
object ReviewHelper {
    private const val PREF = "tuproxy_prefs"
    private const val KEY_LAUNCHES = "launches"
    private val PROMPT_AT = setOf(3, 10, 25)

    fun onAppForeground(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE)
        val n = prefs.getInt(KEY_LAUNCHES, 0) + 1
        prefs.edit().putInt(KEY_LAUNCHES, n).apply()
        if (n in PROMPT_AT) prompt(activity)
    }

    private fun prompt(activity: Activity) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    try {
                        manager.launchReviewFlow(activity, task.result)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
