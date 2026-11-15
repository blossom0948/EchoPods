package me.kavishdevar.librepods

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/** Google Play review entry point, kept out of non-Play variants. */
object PlayReviewPrompt {
    @JvmStatic
    fun request(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                manager.launchReviewFlow(activity, task.result)
            }
        }
    }
}
