package com.carcassonne.backend

import jakarta.persistence.*

@Entity
@Table(name = "game_players")
data class GamePlayer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val score: Int = 0,
    val remainingMeeples: Int = 7
)
