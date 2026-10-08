package com.parkit.app.ui

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/** Stock osmdroid ships only the raw OSM "Mapnik" style — dense labels, harsh
 * colors, looks like a 2008 map demo. CartoDB's free basemaps (the obvious
 * next pick) now require a signup API key, so instead we use Esri's World
 * basemaps — free, no signup/key, and long-established for exactly this use
 * (same tiles behind countless Leaflet/esri-leaflet demos). They use a
 * z/y/x URL order (reversed from the standard XYZ scheme osmdroid's
 * XYTileSource assumes), hence the custom getTileURLString() override. */
private class EsriTileSource(name: String, baseUrl: String) : OnlineTileSourceBase(
    name,
    0,
    19,
    256,
    "",
    arrayOf(baseUrl),
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return baseUrl + "$zoom/$y/$x"
    }
}

object MapTiles {
    // Colorful streets/labels/parks — the closest free, keyless match to
    // Google Maps/Waze's default look.
    val VOYAGER: OnlineTileSourceBase = EsriTileSource(
        "EsriWorldStreet",
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/",
    )

    // Muted dark canvas — Esri's equivalent of Google Maps' night mode.
    val DARK_MATTER: OnlineTileSourceBase = EsriTileSource(
        "EsriWorldDarkGray",
        "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/",
    )
}
