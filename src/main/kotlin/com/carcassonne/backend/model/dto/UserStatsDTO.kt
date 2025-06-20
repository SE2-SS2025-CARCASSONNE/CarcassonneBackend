package com.carcassonne.backend.model.dto

data class UserStatsDTO(
    val totalGames: Int,
    val totalWins: Int,
    val winRatio: Double,
    val highScore: Int
)