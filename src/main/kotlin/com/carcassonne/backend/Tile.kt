package com.carcassonne.backend

import jakarta.persistence.*

@Entity
@Table(name = "tiles")
data class Tile(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val tileId: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val rotation: Int = 0,
    val type: String = ""
)
