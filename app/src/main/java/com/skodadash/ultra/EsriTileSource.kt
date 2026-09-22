package com.skodadash.ultra

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

class EsriTileSource : OnlineTileSourceBase("EsriWorldImagery", 0, 19, 256, ".jpg", arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + mImageFilenameEnding
    }
}

class OsmTileSource : OnlineTileSourceBase("Mapnik", 0, 19, 256, ".png", arrayOf("https://tile.openstreetmap.org/")) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding
    }
}
