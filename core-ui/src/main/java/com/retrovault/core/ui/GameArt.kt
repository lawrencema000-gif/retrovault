package com.retrovault.core.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Pulsar-style cover gradients; a game deterministically maps to one so covers look
// designed even before real box art is hosted.
private val coverPairs = listOf(
    Color(0xFF1C3FB0) to Color(0xFF0A1330),
    Color(0xFF7A2FB0) to Color(0xFF160A30),
    Color(0xFFB02F3F) to Color(0xFF300A10),
    Color(0xFF2FB0A0) to Color(0xFF0A2A2A),
    Color(0xFFB06A2F) to Color(0xFF301A0A),
    Color(0xFFB02F8A) to Color(0xFF300A24),
    Color(0xFF2F9AB0) to Color(0xFF0A2530),
    Color(0xFF2F6AB0) to Color(0xFF0A1A30),
)

/** Deterministic diagonal cover gradient from a stable seed (e.g. game id). */
fun coverBrush(seed: String): Brush {
    val i = (seed.hashCode() and Int.MAX_VALUE) % coverPairs.size
    val (a, b) = coverPairs[i]
    return Brush.linearGradient(listOf(a, b))
}
