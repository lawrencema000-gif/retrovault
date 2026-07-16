package com.retrovault.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.retrovault.feature.player.EmulatorActivity
import com.retrovault.library.RecentPlays

/**
 * "Continue playing" home-screen widget (P27): the last-played game, one tap to resume
 * (launches straight into the emulator with the auto-save restore). Refreshed by
 * EmulatorActivity whenever a session starts.
 */
class ContinueWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val last = RecentPlays(context).last()
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0D14))
                    .cornerRadius(18.dp)
                    .padding(14.dp)
                    .let { mod ->
                        if (last != null) {
                            val system = runCatching {
                                com.retrovault.core.model.GameSystem.valueOf(last.system)
                            }.getOrDefault(com.retrovault.core.model.GameSystem.PSP)
                            val intent = EmulatorActivity.intent(
                                context, last.gameKey, last.title, system, last.path, resume = true,
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            mod.clickable(actionStartActivity(intent))
                        } else mod
                    },
            ) {
                Text(
                    "PULSAR",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF2A7FFF)),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    ),
                )
                if (last == null) {
                    Text(
                        "Play a game and continue it here",
                        style = TextStyle(color = ColorProvider(Color(0xFF8EA3C8)), fontSize = 12.sp),
                    )
                } else {
                    Text(
                        last.title,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        "Tap to continue · played ${RecentPlays.format(last.totalPlayMs)}",
                        style = TextStyle(color = ColorProvider(Color(0xFF8EA3C8)), fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

class ContinueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueWidget()
}
