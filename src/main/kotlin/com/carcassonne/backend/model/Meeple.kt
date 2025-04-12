package com.carcassonne.backend.model

data class Meeple(
    val id: String,                 // Eindeutige ID für jeden Meeple
    val playerId: String,           // ID des Spielers, dem der Meeple gehört
    val type: MeepleType,           // Typ des Meeples (zB Knight, Thief, Monk)
    var tileId: String? = null,     // ID der Kachel, auf der der Meeple platziert ist
    var position: MeeplePosition? = null // Position des Meeples auf der Kachel
)

enum class MeepleType {
    KNIGHT,   // Für Städte
    THIEF,    // Für Straßen
    MONK     // Für Klöster, zusätzlich wären noch möglich: FARMER für Felder; ABBOT für Gärten (Erweiterung)
}

enum class MeeplePosition {
    NORTH, EAST, SOUTH, WEST, CENTER // Bereiche auf dem Tile
}
