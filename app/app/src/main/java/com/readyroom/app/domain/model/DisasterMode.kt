package com.readyroom.app.domain.model

import androidx.compose.ui.graphics.Color

/**
 * A hazard type the user can scan a room for.
 *
 * All modes are selectable: choosing one changes the guidance shown, not what
 * the app can detect. [available] records whether a trained detector actually
 * covers the mode, which today is only [EARTHQUAKE] — the others have guidance
 * but no model behind them yet.
 *
 * [advice] is the official guidance line shown in the advice card. It is
 * deliberately short and imperative; it is not model output and must not be
 * generated. Every entry needs a source before it ships.
 */
enum class DisasterMode(
    val label: String,
    val blurb: String,
    val advice: String,
    val accent: Color,
    val available: Boolean = false,
) {
    EARTHQUAKE(
        label = "Earthquake",
        blurb = "Drop, Cover and Hold On",
        advice = "Stay away from tall furniture and glass. Get under a sturdy " +
            "table and hold on until the shaking stops.",
        accent = Color(0xFFE8763A),
        available = true,
    ),
    FLOOD(
        label = "Flood",
        blurb = "Move to higher ground",
        advice = "Go higher, never lower. Do not shelter under furniture. " +
            "Never drive through floodwater.",
        accent = Color(0xFF3A8FE8),
    ),
    CYCLONE(
        label = "Cyclone",
        blurb = "Shelter in the smallest room",
        advice = "Shelter in the strongest, smallest internal room, away from " +
            "windows. Stay inside until authorities say it is safe.",
        accent = Color(0xFF6B5FE8),
    ),
    FIRE(
        label = "Fire",
        blurb = "Get out and stay out",
        advice = "Never hide. Get out, stay out, and call 000. Close doors " +
            "behind you if it is safe to do so.",
        accent = Color(0xFFE83A3A),
    ),
}
