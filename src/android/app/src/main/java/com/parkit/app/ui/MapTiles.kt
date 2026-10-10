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
    /** A basemap is two stacked raster layers, same pattern Google Maps/Waze
     * use under the hood: a plain, almost-label-free "canvas" for color/shape,
     * with a separate transparent-PNG "reference" layer of roads+place labels
     * drawn on top. The single-layer World_Street_Map we used before bakes
     * dense labels directly into the base tile — busy, mustard-colored,
     * reads as a 2010-era OSM demo rather than a clean modern map. Esri's
     * Canvas pair gives the same soft, minimal look without that noise. */
    data class Layers(val base: OnlineTileSourceBase, val reference: OnlineTileSourceBase)

    val LIGHT = Layers(
        base = EsriTileSource(
            "EsriLightGrayBase",
            "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/",
        ),
        reference = EsriTileSource(
            "EsriLightGrayRef",
            "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Reference/MapServer/tile/",
        ),
    )

    val DARK = Layers(
        base = EsriTileSource(
            "EsriDarkGrayBase",
            "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/",
        ),
        reference = EsriTileSource(
            "EsriDarkGrayRef",
            "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/",
        ),
    )
}
