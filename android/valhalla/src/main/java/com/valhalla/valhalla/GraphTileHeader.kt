package com.valhalla.valhalla

/** The tileset identity fields of Valhalla's `baldr::GraphTileHeader`. */
data class GraphTileHeader(
    val graphId: GraphId,
    val version: String,
    val datasetId: Long,
    val dateCreated: Int,
)
