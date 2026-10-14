package com.parkit.app.ui

import android.content.Context

/** ParkIt's own vector map style — the thing that actually makes the map
 * read like Google Maps/Waze rather than a raster road atlas. Every real
 * attempt to restyle Esri's pre-rendered JPG tiles (grayscale+tint filters,
 * a second label overlay) was fighting pixels someone else already drew;
 * vector tiles hand us the raw geometry (roads by class, parks, water,
 * buildings, street names) and *we* decide how each one looks.
 *
 * Tiles + glyphs come from OpenFreeMap: free, keyless, no account — same
 * constraint every map source in this app has had to meet. One template
 * (assets/map_style.json) with color tokens, two palettes, so light and dark
 * can never drift apart structurally. */
object MapStyle {
    private val LIGHT = mapOf(
        "land" to "#F3F2EF",
        "park" to "#CFEAC6",
        "sand" to "#F5EED9",
        "water" to "#A6D2F2",
        "hospital" to "#F8E3E3",
        "school" to "#F3EEDF",
        "commercial" to "#EFEBF3",
        "building" to "#E6E3DE",
        "buildingOutline" to "#D9D5CF",
        "path" to "#C9C6C0",
        "road" to "#FFFFFF",
        "roadCasing" to "#DCDAD5",
        "arterial" to "#FFF1B8",
        "arterialCasing" to "#E8D27C",
        "motorway" to "#F9CF6E",
        "motorwayCasing" to "#DDA73F",
        "rail" to "#C4C1BB",
        "boundary" to "#A9A49C",
        "label" to "#3C4043",
        "labelFaint" to "#80868B",
        "roadLabel" to "#5F6368",
        "waterLabel" to "#4A7FB0",
        "halo" to "#FFFFFF",
    )

    private val DARK = mapOf(
        "land" to "#1D222B",
        "park" to "#1E3127",
        "sand" to "#2A2A24",
        "water" to "#0F2236",
        "hospital" to "#2E2228",
        "school" to "#2A2A26",
        "commercial" to "#242530",
        "building" to "#262C36",
        "buildingOutline" to "#2E3540",
        "path" to "#3A414D",
        "road" to "#363D4A",
        "roadCasing" to "#262B34",
        "arterial" to "#4A4F5C",
        "arterialCasing" to "#2A2F38",
        "motorway" to "#7A6A3E",
        "motorwayCasing" to "#3A3426",
        "rail" to "#3A414D",
        "boundary" to "#5A6270",
        "label" to "#D5D9E0",
        "labelFaint" to "#8A93A2",
        "roadLabel" to "#A9B1BE",
        "waterLabel" to "#6C93B8",
        "halo" to "#1D222B",
    )

    /** Street names in the app's own language, falling back to the local
     * name — in Israel that's Hebrew, so a Hebrew UI always gets Hebrew
     * streets, and an English UI gets English where OSM has it. */
    private fun nameExpression(language: String): String = if (language == "he" || language == "iw") {
        """["coalesce", ["get", "name:he"], ["get", "name"]]"""
    } else {
        """["coalesce", ["get", "name:en"], ["get", "name_en"], ["get", "name"]]"""
    }

    fun json(context: Context, dark: Boolean, language: String): String {
        var style = context.assets.open("map_style.json").bufferedReader().use { it.readText() }
        (if (dark) DARK else LIGHT).forEach { (token, color) -> style = style.replace("{{$token}}", color) }
        return style.replace("{{name}}", nameExpression(language))
    }
}
