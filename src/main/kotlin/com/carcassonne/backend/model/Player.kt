package com.carcassonne.backend.model

data class Player(
    val id: String,
    val score: Int,
    val remainingMeeple: Int,
    val user_id: Int?
)

