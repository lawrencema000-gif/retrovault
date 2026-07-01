package com.retrovault.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.retrovault.core.model.Game
import com.retrovault.core.ui.coverBrush
import com.retrovault.core.ui.theme.PulsarStroke
import com.retrovault.core.ui.theme.PulsarSurface2
import com.retrovault.core.ui.theme.PulsarTextDimmer

/** Grid cover card: gradient (or box art) cover, system badge, title, subtitle. */
@Composable
fun GameCard(game: Game, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4.2f)
                .clip(RoundedCornerShape(14.dp))
                .background(coverBrush(game.id))
        ) {
            if (game.boxArtUrl != null) {
                AsyncImage(
                    model = game.boxArtUrl,
                    contentDescription = game.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // bottom scrim for legibility
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.4f to Color.Transparent,
                            1f to Color(0xD904060C)
                        )
                    )
            )
            // system badge
            SystemBadge(
                text = game.system.shortCode,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
            Text(
                game.title,
                style = MaterialThemeTitle(),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 9.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            game.license,
            fontSize = 10.sp,
            color = PulsarTextDimmer,
        )
    }
}

@Composable
internal fun SystemBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(PulsarSurface2)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// Small helper so we can use the Chakra-Petch title style without importing MaterialTheme here.
@Composable
private fun MaterialThemeTitle() = androidx.compose.material3.MaterialTheme.typography.titleSmall
