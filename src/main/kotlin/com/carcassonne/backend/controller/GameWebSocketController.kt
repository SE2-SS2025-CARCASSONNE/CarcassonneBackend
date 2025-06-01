package com.carcassonne.backend.controller

import com.carcassonne.backend.model.GameMessage
import com.carcassonne.backend.model.GamePhase
import com.carcassonne.backend.model.MeeplePosition
import com.carcassonne.backend.repository.GameRepository
import com.carcassonne.backend.service.GameManager
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller

@Controller
class GameWebSocketController(
    private val gameManager: GameManager,
    private val messagingTemplate: SimpMessagingTemplate,
    private val gameRepository: GameRepository
) {

    @MessageMapping("/game/send") // from client to /app/game/send
    fun handle(@Payload msg: GameMessage) {
        when (msg.type) {
            "join_game" -> {
                val game = gameManager.getOrCreateGame(msg.gameId)

                //Always generate and send updated list
                val playerAlreadyExists = game.findPlayerById(msg.player) != null
                if (!playerAlreadyExists) {
                    game.addPlayer(msg.player)
                }

                val payload = mapOf(
                    "type" to "player_joined",
                    "players" to game.players.map { it.id },
                    "currentPlayer" to game.getCurrentPlayer(),
                    "host" to game.players.firstOrNull()?.id
                )

                //Always send updated list to ALL
                messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", payload)

                //Also send it privately to the newly joined player for guaranteed sync
                messagingTemplate.convertAndSendToUser(
                    msg.player,
                    "/queue/private",
                    payload
                )
            }


            "place_tile" -> {
                val tile = msg.tile
                val x = tile?.position?.x
                val y = tile?.position?.y
                if (tile == null || x == null || y == null) {
                    val error = mapOf(
                        "type" to "error",
                        "message" to "Invalid tile placement data"
                    )
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", error)
                    return
                }
                val position = Pair(x, y)
                // call to placeTile method returns the updated game state
                val game = gameManager.placeTile(msg.gameId, tile, msg.player)
                if (game != null) {
                    val payload = mapOf(
                        "type" to "board_update",
                        "tile" to msg.tile,
                        "player" to msg.player,
                        "nextPlayer" to game.getCurrentPlayer()
                    )
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", payload)
                } else {
                    val error = mapOf("type" to "error", "message" to "Invalid move or not your turn")
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", error)
                }
            }

            "place_meeple" -> {
                val meeple = msg.meeple ?: run {
                    val error = mapOf(
                        "type" to "error",
                        "message" to "Invalid meeple placement data"
                    )
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", error)
                    return
                }

                val game = gameManager.placeMeeple(
                    gameId = msg.gameId,
                    playerId = msg.player,
                    meeple = meeple,
                    position = meeple.position!!
                )

                if (game != null) {
                    val payload = mapOf(
                        "type" to "meeple_placed",
                        "meeple" to meeple,
                        "player" to msg.player,
                        "nextPlayer" to game.getCurrentPlayer() //TODO: Michael: ev. nicht notwendig, abstimmen mit Scoring-Logik
                    )
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", payload)
                } else {
                    val error = mapOf(
                        "type" to "error",
                        "message" to "Invalid meeple placement or not your turn"
                    )
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", error)
                }
            }

            "start_game" -> {
                println(">>> [Backend] Received start_game for ${msg.gameId}")

                val game = gameManager.getOrCreateGame(msg.gameId)
                game.status = GamePhase.TILE_PLACEMENT

                // Update DB
                try {
                    gameRepository.updateStatusByGameCode(msg.gameId, GamePhase.TILE_PLACEMENT.name)
                    println(">>> [Backend] Game status updated in DB")
                } catch (e: Exception) {
                    println(">>> [Backend] ERROR updating DB: ${e.message}")
                }

                // Notify clients
                val payload = mapOf(
                    "type" to "game_started",
                    "gameId" to msg.gameId
                )
                println(">>> [Backend] Sending game_started to /topic/game/${msg.gameId} with $payload")
                messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", payload)
            }
            "DRAW_TILE" -> {
                println(">>> [Backend] Handling DRAW_TILE for ${msg.player} in game ${msg.gameId}")
                val drawnTile = gameManager.drawTileForPlayer(msg.gameId)

                if (drawnTile != null) {
                    val payload = mapOf(
                        "type" to "TILE_DRAWN",
                        "tile" to drawnTile
                    )
                    println(">>> Sending TILE_DRAWN to /topic/game/${msg.gameId}")
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", payload)
                } else {
                    val error = mapOf(
                        "type" to "error",
                        "message" to "No more playable tiles"
                    )
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", error)
                }
            }

            "end_game" -> {
                val game = gameManager.getOrCreateGame(msg.gameId)

                if (game.status != GamePhase.FINISHED) {
                    println(">>> Game is not in FINISHED phase")
                    return
                }

                val winnerId = gameManager.endGame(msg.gameId)

                try {
                    val dbGame = gameRepository.findByGameCode(msg.gameId)
                    if (dbGame != null) {
                        dbGame.winner = winnerId
                        gameRepository.save(dbGame)
                    }
                } catch (e: Exception) {
                    println(">>> Failed to update winner in DB: ${e.message}")
                }
                val players = game.players
                val payload = mapOf(
                    "type" to "game_over",
                    "winner" to winnerId,
                    "scores" to players.map { mapOf("player" to it.id, "score" to it.score) }
                )
                messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", payload)
            }
        }
    }
}