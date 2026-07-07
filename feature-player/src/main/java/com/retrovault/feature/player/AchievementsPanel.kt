package com.retrovault.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** One achievement, parsed from the native rc_client list JSON. state: 2 = unlocked. */
data class Achievement(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val unlocked: Boolean,
    val progress: String,
)

/** "Unlocked X of Y (P of T pts)" summary, parsed from the native game-summary JSON. */
data class AchievementSummary(
    val title: String,
    val numCore: Int,
    val numUnlocked: Int,
    val pointsCore: Int,
    val pointsUnlocked: Int,
)

object AchievementsJson {
    fun parseList(json: String): List<Achievement> {
        val out = ArrayList<Achievement>()
        val buckets = runCatching { JSONObject(json).optJSONArray("buckets") }.getOrNull() ?: return out
        for (b in 0 until buckets.length()) {
            val achs = buckets.getJSONObject(b).optJSONArray("achievements") ?: continue
            for (i in 0 until achs.length()) {
                val a = achs.getJSONObject(i)
                out += Achievement(
                    id = a.optInt("id"),
                    title = a.optString("title"),
                    description = a.optString("description"),
                    points = a.optInt("points"),
                    unlocked = a.optInt("unlocked") != 0 || a.optInt("state") == 2,
                    progress = a.optString("progress"),
                )
            }
        }
        return out
    }

    fun parseSummary(json: String): AchievementSummary? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (!o.has("num_core")) return null
        return AchievementSummary(
            title = o.optString("title"),
            numCore = o.optInt("num_core"),
            numUnlocked = o.optInt("num_unlocked"),
            pointsCore = o.optInt("points_core"),
            pointsUnlocked = o.optInt("points_unlocked"),
        )
    }
}

/**
 * Per-game RetroAchievements list + summary. Fed the native snapshot JSON (empty until an RA
 * account is logged in and the game is identified — the live path is staged).
 */
@Composable
fun AchievementsPanel(listJson: String, summaryJson: String, modifier: Modifier = Modifier) {
    val summary = AchievementsJson.parseSummary(summaryJson)
    val achievements = AchievementsJson.parseList(listJson)
    Column(modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Achievements", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (summary != null) {
            Spacer(Modifier.size(4.dp))
            Text(
                "Unlocked ${summary.numUnlocked} of ${summary.numCore} · " +
                    "${summary.pointsUnlocked} of ${summary.pointsCore} pts",
                fontSize = 13.sp, modifier = Modifier.alpha(0.7f),
            )
        }
        Spacer(Modifier.size(12.dp))
        if (achievements.isEmpty()) {
            Text(
                "Sign in to RetroAchievements to earn achievements for supported games.",
                fontSize = 13.sp, modifier = Modifier.alpha(0.6f),
            )
        } else {
            achievements.forEach { AchievementRow(it) }
        }
    }
}

@Composable
private fun AchievementRow(a: Achievement) {
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.alpha(if (a.unlocked) 1f else 0.55f)) {
            Text(a.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(a.description, fontSize = 12.sp, modifier = Modifier.alpha(0.75f))
            if (a.progress.isNotEmpty() && !a.unlocked) {
                Text(a.progress, fontSize = 11.sp, modifier = Modifier.alpha(0.6f))
            }
        }
        Text(if (a.unlocked) "★ ${a.points}" else "${a.points}", fontSize = 13.sp)
    }
}
