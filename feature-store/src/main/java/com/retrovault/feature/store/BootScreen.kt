package com.retrovault.feature.store

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovault.core.ui.theme.PulsarBackgroundBrush
import com.retrovault.core.ui.theme.PulsarBlueBrush
import com.retrovault.core.ui.theme.PulsarSurface2
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarTextFaint
import kotlinx.coroutines.delay

@Composable
fun BootScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1900)
        onFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(PulsarBackgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(PulsarBlueBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "PULSAR",
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                letterSpacing = 10.sp,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "PORTABLE  SYSTEM  PLAYER",
                fontSize = 11.sp,
                letterSpacing = 5.sp,
                fontWeight = FontWeight.SemiBold,
                color = PulsarTextFaint
            )
            Spacer(Modifier.height(44.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .width(220.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = PulsarSurface2
            )
        }
    }
}
