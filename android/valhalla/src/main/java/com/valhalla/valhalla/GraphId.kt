package com.valhalla.valhalla

/** Valhalla's `baldr::GraphId`: a hierarchy level, a tile within it, and an id within the tile. */
data class GraphId(val level: Int, val tileId: Int, val id: Int = 0)
