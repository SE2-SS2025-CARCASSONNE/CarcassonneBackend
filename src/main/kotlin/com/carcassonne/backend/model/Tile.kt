package com.carcassonne.backend.model
data class Tile(
    val id: String,
    val terrainNorth: TerrainType,
    val terrainEast: TerrainType,
    val terrainSouth: TerrainType,
    val terrainWest: TerrainType,
    val tileRotation: TileRotation,
    val position: Position? = null,
    val hasMonastery: Boolean = false,
    val hasShield: Boolean = false,
    val count: Int = 1
)

fun Tile.getAllTerrains(): Set<TerrainType> = setOf(
    terrainNorth, terrainEast, terrainSouth, terrainWest
)

fun Tile.isRoad(): Boolean = getAllTerrains().contains(TerrainType.ROAD)
fun Tile.isCity(): Boolean = getAllTerrains().contains(TerrainType.CITY)

fun Tile.isMonastery(): Boolean {
    return hasMonastery
}

fun Tile.getRotatedTerrains(): Map<String, TerrainType> {
    return when (tileRotation) {
        TileRotation.NORTH -> mapOf(
            "N" to terrainNorth,
            "E" to terrainEast,
            "S" to terrainSouth,
            "W" to terrainWest
        )
        TileRotation.EAST -> mapOf(
            "N" to terrainWest,
            "E" to terrainNorth,
            "S" to terrainEast,
            "W" to terrainSouth
        )
        TileRotation.SOUTH -> mapOf(
            "N" to terrainSouth,
            "E" to terrainWest,
            "S" to terrainNorth,
            "W" to terrainEast
        )
        TileRotation.WEST -> mapOf(
            "N" to terrainEast,
            "E" to terrainSouth,
            "S" to terrainWest,
            "W" to terrainNorth
        )
    }
}

fun Tile.getTerrainType(direction: String): TerrainType? {
    // Nutze die bereits bestehende Rotationslogik
    val rotatedTerrains = this.getRotatedTerrains()

    if (!rotatedTerrains.containsKey(direction)) {
        return null
    }

    return rotatedTerrains[direction] // Gibt den TerrainType für die angegebene Richtung zurück
}

fun Tile.getTerrainAtOrNull(pos: MeeplePosition): TerrainType? {
    // Zuerst die Rotationslogik für N, E, S, W wiederverwenden
    val terrains = getRotatedTerrains()
    return when (pos) {
        MeeplePosition.N -> terrains["N"]
        MeeplePosition.E -> terrains["E"]
        MeeplePosition.S -> terrains["S"]
        MeeplePosition.W -> terrains["W"]
        MeeplePosition.C -> when {
            // Monastery sitzt immer in der Mitte
            hasMonastery -> TerrainType.MONASTERY

            // gerade durchgehende Straße West↔Ost
            terrains["W"] == TerrainType.ROAD && terrains["E"] == TerrainType.ROAD -> TerrainType.ROAD
            // gerade durchgehende Straße Nord↔Süd
            terrains["N"] == TerrainType.ROAD && terrains["S"] == TerrainType.ROAD -> TerrainType.ROAD

            // zweigeteilte Stadtstücke, z.B. N↔E, E↔S, S↔W, W↔N
            terrains["N"] == TerrainType.CITY && terrains["E"] == TerrainType.CITY -> TerrainType.CITY
            terrains["E"] == TerrainType.CITY && terrains["S"] == TerrainType.CITY -> TerrainType.CITY
            terrains["S"] == TerrainType.CITY && terrains["W"] == TerrainType.CITY -> TerrainType.CITY
            terrains["W"] == TerrainType.CITY && terrains["N"] == TerrainType.CITY -> TerrainType.CITY

            else -> null
        }
    }
}


enum class TerrainType {
    ROAD,
    CITY,
    MONASTERY,
    FIELD
}

enum class TileRotation {
    NORTH,
    EAST,
    SOUTH,
    WEST
}