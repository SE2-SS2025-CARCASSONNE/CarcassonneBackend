package com.carcassonne.backend.model

data class Meeple(
    val id: String,                 // Eindeutige ID für jeden Meeple
    val playerId: String,           // ID des Spielers, dem der Meeple gehört
    var tileId: String? = null,     // ID der Kachel, auf der der Meeple platziert ist
    var position: MeeplePosition? = null // Position des Meeples auf der Kachel
)

enum class MeeplePosition(val shortCode: String) {
    N("N"), // North
    E("E"), // East
    S("S"), // South
    W("W"), // West
    C("C"); // Center (Monastery)
}
