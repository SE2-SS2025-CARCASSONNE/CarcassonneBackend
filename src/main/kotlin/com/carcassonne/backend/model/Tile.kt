package com.carcassonne.backend.model
data class Tile(
    val id: String,
    val terrainNorth: TerrainType,
    val terrainEast: TerrainType,
    val terrainSouth: TerrainType,
    val terrainWest: TerrainType,
    val tileRotation: TileRotation,
    val position: Position? = null,
)

enum class TerrainType {
    ROAD,
    CITY,
    MONASTERY,
    FIELD,
    RIVER,
    GARDEN
}

enum class TileRotation {
    NORTH,
    EAST,
    SOUTH,
    WEST
}