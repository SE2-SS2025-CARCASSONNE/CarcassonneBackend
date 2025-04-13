package com.carcassonne.backend.controller

import com.carcassonne.backend.model.GameState
import com.carcassonne.backend.service.GameManager
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/game")
@Tag(name = "Game", description = "Game retrieval endpoints")
class GameRestController(
    private val gameManager: GameManager
) {
    @Operation(summary = "Retrieve game by id")
    @GetMapping("/{gameId}")
    fun getGame(
        @PathVariable gameId: String,
        @RequestHeader(value = "Authorization", required = false) authHeader: String?
    ): ResponseEntity<GameState> {
        println(">>> [DEBUG] GET /api/game/$gameId")
        println(">>> [DEBUG] Authorization Header: $authHeader")

        val game = gameManager.getOrCreateGame(gameId)
        return ResponseEntity.ok(game)
    }
}
