package com.carcassonne.backend.controller

import com.carcassonne.backend.model.GameMessage
import com.carcassonne.backend.model.GamePhase
import com.carcassonne.backend.model.Position
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
        println(" Received message type: ${msg.type}")
        println(" Payload: $msg")

        when (msg.type) {
            "join_game" -> {
                val game = gameManager.getGame(msg.gameId)

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

                game.board.forEach { (position, tile) ->
                    val tilePayload = mapOf(
                        "id" to tile.id,
                        "terrainNorth" to tile.terrainNorth,
                        "terrainEast" to tile.terrainEast,
                        "terrainSouth" to tile.terrainSouth,
                        "terrainWest" to tile.terrainWest,
                        "tileRotation" to tile.tileRotation.name,
                        "hasMonastery" to tile.hasMonastery,
                        "hasShield" to tile.hasShield,
                        "position" to mapOf("x" to position.x, "y" to position.y)
                    )
                    val boardUpdate = mapOf(
                        "type" to "board_update",
                        "tile" to tilePayload,
                        "player" to mapOf("id" to msg.player)
                    )
                    messagingTemplate.convertAndSendToUser(msg.player, "/queue/private", boardUpdate)
                }

            }

            "place_tile" -> {
                try {
                    val game = gameManager.placeTile(msg.gameId!!, msg.tile!!, msg.player!!)
                    val tile = msg.tile!!
                    val position  = tile.position!!

                    val payload = mapOf(
                        "id" to tile.id,
                        "terrainNorth" to tile.terrainNorth,
                        "terrainEast" to tile.terrainEast,
                        "terrainSouth" to tile.terrainSouth,
                        "terrainWest" to tile.terrainWest,
                        "tileRotation" to tile.tileRotation.name,
                        "hasMonastery" to tile.hasMonastery,
                        "hasShield" to tile.hasShield,
                        "position" to mapOf("x" to position.x, "y" to position.y)
                    )

                    val boardUpdatePayload = mapOf(
                        "type" to "board_update",
                        "tile" to payload,
                        "player" to mapOf("id" to msg.player)
                    )
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", boardUpdatePayload)
                } catch(e: Exception) {
                    messagingTemplate.convertAndSend(
                        "/topic/game/${msg.gameId}",
                        mapOf("type" to "error", "message" to e.message)
                    )
                }
            }

            "calculate_score" -> {
                try {
                    val game = gameManager.getGame(msg.gameId)
                    val tile = msg.tile

                    if (tile == null) {
                        val error = mapOf(
                            "type" to "error",
                            "message" to "Tile required for scoring"
                        )
                        messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", error)
                        return
                    }

                    // Setze Spielstatus SCORING – falls nicht schon geschehen
                    game.status = GamePhase.SCORING

                    // Punkte berechnen
                    gameManager.calculateScore(msg.gameId, tile)

                    // Scores an alle Clients senden
                    val payload = mapOf(
                        "type" to "score_update",
                        "scores" to game.players.map { mapOf("player" to it.id, "score" to it.score) }
                    )
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", payload)

                } catch (e: Exception) {
                    val error = mapOf("type" to "error", "message" to "Scoring failed: ${e.message}")
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", error)
                }
            }

            "calculate_score" -> {
                try {
                    val game = gameManager.getGame(msg.gameId)
                    val tile = msg.tile

                    if (tile == null) {
                        val error = mapOf(
                            "type" to "error",
                            "message" to "Tile required for scoring"
                        )
                        messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", error)
                        return
                    }

                    // Setze Spielstatus SCORING – falls nicht schon geschehen
                    game.status = GamePhase.SCORING

                    // Punkte berechnen
                    gameManager.calculateScore(msg.gameId, tile)

                    // Scores an alle Clients senden
                    val payload = mapOf(
                        "type" to "score_update",
                        "scores" to game.players.map { mapOf("player" to it.id, "score" to it.score) }
                    )
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", payload)

                } catch (e: Exception) {
                    val error = mapOf("type" to "error", "message" to "Scoring failed: ${e.message}")
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", error)
                }
            }

            "start_game" -> {
                println(">>> [Backend] Received start_game for ${msg.gameId}")
                val game = gameManager.getGame(msg.gameId)
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

                val startTile = game.board[Position(0, 0)]
                    ?: throw IllegalStateException("Starting tile missing in game ${msg.gameId}")

                val tilePayload = mapOf(
                    "id"           to startTile.id,
                    "terrainNorth" to startTile.terrainNorth,
                    "terrainEast"  to startTile.terrainEast,
                    "terrainSouth" to startTile.terrainSouth,
                    "terrainWest"  to startTile.terrainWest,
                    "tileRotation" to startTile.tileRotation.name,
                    "hasMonastery" to startTile.hasMonastery,
                    "hasShield"    to startTile.hasShield,
                    "position"     to mapOf("x" to 0, "y" to 0)
                )
                val boardUpdate = mapOf(
                    "type"   to "board_update",
                    "tile"   to tilePayload,
                    "player" to mapOf("id" to game.players.first().id)
                )
                println(">>> [Backend] Sending initial board_update: $boardUpdate")
                messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", boardUpdate)
                messagingTemplate.convertAndSendToUser(msg.player, "/queue/private", boardUpdate)
            }

            "DRAW_TILE" -> {
                println(">>> [Backend] Handling DRAW_TILE for ${msg.player} in game ${msg.gameId}")
                val drawnTile = gameManager.drawTileForPlayer(msg.gameId)

                if (drawnTile != null) {
                    val validPlacements = gameManager.getAllValidPositions(msg.gameId!!, drawnTile)

                    val validPlacementsJson = validPlacements.map { (pos, rotation, _) ->
                        mapOf(
                            "position" to mapOf("x" to pos.x, "y" to pos.y),
                            "rotation" to rotation.name
                        )
                    }

                    val payload = mapOf(
                        "type" to "TILE_DRAWN",
                        "tile" to drawnTile,
                        "validPlacements" to validPlacementsJson
                    )

                    println("Valid placements for ${drawnTile.id}: $validPlacementsJson")
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
                val game = gameManager.getGame(msg.gameId)

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