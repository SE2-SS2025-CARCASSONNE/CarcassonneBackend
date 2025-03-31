package com.carcassonne.backend.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "games")
data class Game(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0, // Unique game ID

    val status: String = "", // E.g., "WAITING", "IN_PROGRESS", "FINISHED"
    val createdAt: Instant = Instant.now() // For filtering, history, stats
)
