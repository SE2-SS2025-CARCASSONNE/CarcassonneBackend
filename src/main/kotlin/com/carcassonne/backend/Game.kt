package com.carcassonne.backend

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "games")
data class Game(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val status: String = "",
    val createdAt: Instant = Instant.now()
)
