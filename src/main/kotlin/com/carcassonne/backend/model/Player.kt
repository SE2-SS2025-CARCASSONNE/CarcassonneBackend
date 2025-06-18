package com.carcassonne.backend.model

data class Player(
    val id: String,
    var score: Int,
    var remainingMeeple: Int,
    val user_id: Int?
)

