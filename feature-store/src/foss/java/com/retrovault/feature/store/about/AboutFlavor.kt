package com.retrovault.feature.store.about

import android.os.Handler
import android.os.Looper
import com.retrovault.feature.store.UpdateCheck
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * foss / direct-APK channel: a strictly user-initiated update check is offered (F-Droid tolerates
 * manual checks; the GitHub-direct channel needs one). It only ever OPENS the releases web page —
 * never downloads or installs anything.
 */
fun updateCheckAvailable(): Boolean = true

/** foss build ships zero crash-reporting code, so there is nothing to opt into. */
fun crashReportToggleAvailable(): Boolean = false

/**
 * Query GitHub's releases/latest once, compare against [currentVersion], and deliver the result on
 * the main thread. 404 = no releases published yet (today's state) — reported as [UpdateResult.NoReleases].
 */
fun checkForUpdate(currentVersion: String, onResult: (UpdateResult) -> Unit) {
    val main = Handler(Looper.getMainLooper())
    Thread({
        val result = runCatching {
            val conn = URL(UpdateCheck.RELEASES_API).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                when (conn.responseCode) {
                    200 -> {
                        val tag = JSONObject(conn.inputStream.bufferedReader().readText())
                            .optString("tag_name")
                        if (tag.isNotEmpty() && UpdateCheck.isNewer(tag, currentVersion)) {
                            UpdateResult.UpdateAvailable(tag)
                        } else {
                            UpdateResult.UpToDate
                        }
                    }
                    404 -> UpdateResult.NoReleases
                    else -> UpdateResult.Error
                }
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(UpdateResult.Error)
        main.post { onResult(result) }
    }, "UpdateCheck").start()
}
