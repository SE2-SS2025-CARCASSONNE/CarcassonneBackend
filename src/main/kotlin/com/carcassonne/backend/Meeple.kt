package com.carcassonne.backend

import jakarta.persistence.*

@Entity
@Table(name = "meeples")
data class Meeple(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val segmentType: String = "", // e.g., "Stadt", "Straße", "Kloster"
    val segmentPosition: String = "" // e.g., "Nord", "Ost", "Süd", "West"
)
