package com.valhalla.valhalla

/** One tile of the hierarchy, and the path it is fetched by. */
data class TileRef(
    /** Hierarchy level. 0 is 4 degrees, 1 is 1 degree, 2 is 0.25 degrees. */
    val level: Int,
    /** Tile id within that level. */
    val id: Int,
    /**
     * What `mjolnir.tile_url`'s `{tilePath}` is replaced with, e.g. `2/000/818/660.gph`.
     *
     * Always the uncompressed name. With `tile_url_gz` on, the cached file is `.gph.gz`, but
     * valhalla fetches it by the plain name.
     */
    val path: String,
)
