package com.carcassonne.backend

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "moves")
data class Move(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val turnNumber: Int = 0,
    val action: String = "",
    val timestamp: Instant = Instant.now()
)
