package com.carcassonne.backend.controller

import com.carcassonne.backend.model.GameState
import com.carcassonne.backend.service.GameManager
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/game")
@Tag(name = "Game State", description = "Game retrieval endpoints")
class GameRestController(
    private val gameManager: GameManager
) {
    @GetMapping("/{gameId}")
    fun getGame(@PathVariable gameId: String): ResponseEntity<GameState> {
        return ResponseEntity.ok(gameManager.getOrCreateGame(gameId))
    }
}
