package com.indhg.aiforcoyote.ui.onboarding

import android.content.Context
import com.indhg.aiforcoyote.BuildConfig

/** SharedPreferences 持久化：首次空则自动开引导；完成后写入 versionName。 */
object TourPrefs {
    private const val PREFS = "coyote_tour"
    const val KEY_DONE = "tour_done_guide1"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDone(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (!p.contains(KEY_DONE)) return false
        return try {
            p.getBoolean(KEY_DONE, false) || !p.getString(KEY_DONE, null).isNullOrBlank()
        } catch (_: ClassCastException) {
            !p.getString(KEY_DONE, null).isNullOrBlank()
        }
    }

    fun markDone(ctx: Context) {
        prefs(ctx).edit()
            .putString(KEY_DONE, "done:${BuildConfig.VERSION_NAME}")
            .apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_DONE).apply()
    }
}
