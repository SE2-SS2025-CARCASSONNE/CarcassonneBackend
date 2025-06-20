package com.carcassonne.backend.controller

import com.carcassonne.backend.model.GameMessage
import com.carcassonne.backend.model.GamePhase
import com.carcassonne.backend.model.GameState
import com.carcassonne.backend.model.Position
import com.carcassonne.backend.model.Tile
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
                        "terrainCenter" to tile.terrainCenter,
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

                // Deck counter initialization
                messagingTemplate.convertAndSend("/topic/game/${msg.gameId}",
                    mapOf(
                        "type" to "deck_update",
                        "deckRemaining" to game.tileDeck.size
                    )
                )

                val startTile = game.board[Position(0, 0)]
                    ?: throw IllegalStateException("Starting tile missing in game ${msg.gameId}")

                val tilePayload = mapOf(
                    "id"           to startTile.id,
                    "terrainNorth" to startTile.terrainNorth,
                    "terrainEast"  to startTile.terrainEast,
                    "terrainSouth" to startTile.terrainSouth,
                    "terrainWest"  to startTile.terrainWest,
                    "terrainCenter" to startTile.terrainCenter,
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
            }

            "DRAW_TILE" -> {
                // Enforce that only current player can act
                val game  = authorizeTurn(msg) ?: return

                if (game.tileDrawnThisTurn) {
                    messagingTemplate.convertAndSendToUser(
                        msg.player,
                        "/queue/private",
                        mapOf("type" to "error",
                            "message" to "You have already drawn a tile"))
                    return
                }

                println(">>> [Backend] Handling DRAW_TILE for ${msg.player} in game ${msg.gameId}")
                val drawnTile  = gameManager.drawTileForPlayer(msg.gameId) ?: run {
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}",
                        mapOf("type" to "error", "message" to "No more playable tiles"))
                    return
                }

                game.tileDrawnThisTurn = true

                val validPlacementsJson = gameManager
                    .getAllValidPositions(msg.gameId, drawnTile)
                    .map { (pos, rotation, _) ->
                        mapOf(
                            "position" to mapOf("x" to pos.x, "y" to pos.y),
                            "rotation" to rotation.name
                        )
                    }

                val payload = mapOf(
                    "type"            to "TILE_DRAWN",
                    "tile"            to drawnTile,
                    "validPlacements" to validPlacementsJson
                )

                println("Valid placements for ${drawnTile.id}: $validPlacementsJson")
                println("Deck now has ${game.tileDeck.size} tiles left")

                // Private drawn tile update only to current player
                messagingTemplate.convertAndSendToUser(msg.player,"/queue/private", payload)

                // Public deck counter update to all players
                messagingTemplate.convertAndSend("/topic/game/${msg.gameId}",
                    mapOf(
                        "type" to "deck_update",
                        "deckRemaining" to game.tileDeck.size
                    )
                )
            }

            "place_tile" -> {
                authorizeTurn(msg) ?: return

                try {
                    val game = gameManager.placeTile(
                        msg.gameId!!,
                        msg.tile!!,
                        msg.player!!
                    )

                    game?.let { g ->
                        gameRepository.updateStatusByGameCode(
                            msg.gameId,
                            g.status.name
                        )

                        val tile = msg.tile!!
                        val pos = tile.position!!

                        val tileJson = mapOf(
                            "id"           to tile.id,
                            "terrainNorth" to tile.terrainNorth,
                            "terrainEast"  to tile.terrainEast,
                            "terrainSouth" to tile.terrainSouth,
                            "terrainWest"  to tile.terrainWest,
                            "terrainCenter" to tile.terrainCenter,
                            "tileRotation" to tile.tileRotation.name,
                            "hasMonastery" to tile.hasMonastery,
                            "hasShield"    to tile.hasShield,
                            "position"     to mapOf("x" to pos.x, "y" to pos.y)
                        )

                        val boardUpdate = mapOf(
                            "type"      to "board_update",
                            "tile"      to tileJson,
                            "player"    to mapOf("id" to msg.player),
                            "gamePhase" to g.status.name
                        )

                        messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", boardUpdate)

                        if (g.tileDeck.isEmpty()) {
                            println(">>> Game ended: no tiles left after place_tile")
                            g.finishGame()
                            val winnerId = gameManager.endGame(msg.gameId!!)
                            val payload = mapOf(
                                "type" to "game_over",
                                "winner" to winnerId,
                                "scores" to g.players.map { mapOf("player" to it.id, "score" to it.score) }
                            )
                            messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", payload)
                        }
                    }
                } catch (e: Exception) {
                    messagingTemplate.convertAndSend(
                        "/topic/game/${msg.gameId}",
                        mapOf("type" to "error", "message" to e.message)
                    )
                }
            }

            "place_meeple" -> {
                // 0) Nur aktueller Spieler darf diese Nachricht auslösen
                val oldGame = authorizeTurn(msg) ?: return

                // 0.5) Meeple Platzierung nur auf das aktuelle Tile erlauben
                val lastTile = oldGame.board.entries.lastOrNull()?.value
                if (lastTile != null && msg.meeple?.tileId != lastTile.id) {
                    messagingTemplate.convertAndSendToUser(
                        msg.player, "/queue/private",
                        mapOf("type" to "error", "message" to "You can only place meeples on the current tile!")
                    )
                    return
                }

                // 1) Meeple-Daten validieren
                val meeple = msg.meeple ?: run {
                    messagingTemplate.convertAndSendToUser(
                        msg.player, "/queue/private",
                        mapOf("type" to "error", "message" to "Invalid meeple placement data")
                    )
                    return
                }

                // 2) Position existiert?
                val meeplePos = meeple.position ?: run {
                    messagingTemplate.convertAndSendToUser(
                        msg.player, "/queue/private",
                        mapOf("type" to "error", "message" to "Invalid meeple position")
                    )
                    return
                }

                // 3) Versuch, im GameManager zu platzieren (kann Exception werfen)
                val game = try {
                    gameManager.placeMeeple(
                        gameId   = msg.gameId!!,
                        playerId = msg.player!!,
                        meeple   = meeple,
                        position = meeplePos
                    )
                } catch (e: Exception) {
                    messagingTemplate.convertAndSendToUser(
                        msg.player, "/queue/private",
                        mapOf("type" to "error", "message" to e.message.orEmpty())
                    )
                    return
                }

                if (game != null) {
                    // 4) Persistenter Status
                    gameRepository.updateStatusByGameCode(
                        msg.gameId,
                        game.status.name
                    )

                    // 5) Daten für public-Update zusammenstellen
                    val placingPlayer = game.players.first { it.id == msg.player }
                    val tileEntry = game.board.entries.first { it.value.id == meeple.tileId }
                    val tilePos = tileEntry.key

                    val payload = mapOf(
                        "type"            to "meeple_placed",
                        "meeple"          to mapOf(
                            "id"       to meeple.id,
                            "playerId" to meeple.playerId,
                            "tileId"   to meeple.tileId,
                            "position" to meeplePos.name,
                            "x"        to tilePos.x,
                            "y"        to tilePos.y
                        ),
                        "player"          to msg.player,
                        "remainingMeeple" to placingPlayer.remainingMeeple,
                        "gamePhase"       to game.status.name
                    )
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", payload)

                    // 6) Runde abschließen
                    finalizeTurn(game, tileEntry.value)

                } else {
                    // 7) Ungültige Platzierung (anderer Grund)
                    messagingTemplate.convertAndSendToUser(
                        msg.player, "/queue/private",
                        mapOf("type" to "error", "message" to "Invalid meeple placement or not your turn")
                    )
                }
            }

            "skip_meeple" -> {
                val game = authorizeTurn(msg) ?: return
                if (game.status != GamePhase.MEEPLE_PLACEMENT) return

                val placedTile = game.board.entries.last().value
                finalizeTurn(game, placedTile)

                // After finalizing turn, check if game ended
                if (game.tileDeck.isEmpty()) {
                    println(">>> Game ended: no tiles left after skip_meeple")
                    game.finishGame()
                    val winnerId = gameManager.endGame(msg.gameId!!)
                    val payload = mapOf(
                        "type" to "game_over",
                        "winner" to winnerId,
                        "scores" to game.players.map { mapOf("player" to it.id, "score" to it.score) }
                    )
                    messagingTemplate.convertAndSend("/topic/game/${msg.gameId}", payload)
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

    private fun finalizeTurn(game: GameState, lastTile: Tile) {
        // Recalculate scores and return meeples back to players
        game.status = GamePhase.SCORING
        val before = game.meeplesOnBoard.toList()

        gameManager.calculateScore(game.gameId, lastTile)
        val after = game.meeplesOnBoard.toList()
        val removedMeeples = before.filter { meeple -> meeple !in after }

        // Turn completed -> back to tile placement & switch to next player
        game.status = GamePhase.TILE_PLACEMENT
        game.nextPlayer()
        game.tileDrawnThisTurn = false

        // Broadcast new scores, meeple counts and next player
        val scorePayload = mapOf(
            "type" to "score_update",
            "scores" to game.players.map { p ->
                mapOf(
                    "player" to p.id,
                    "score" to p.score,
                    "remainingMeeple" to p.remainingMeeple
                )
            },
            "nextPlayer" to game.getCurrentPlayer(),
            "gamePhase"  to game.status.name
        )
        messagingTemplate.convertAndSend("/topic/game/${game.gameId}", scorePayload)

        if (removedMeeples.isNotEmpty()) {
            val removedIds = removedMeeples.map { it.id }
            val removePayload = mapOf(
                "type" to "meeple_removed",
                "ids"  to removedIds
            )
            messagingTemplate.convertAndSend("/topic/game/${game.gameId}", removePayload)
        }
    }

    private fun authorizeTurn(msg: GameMessage): GameState? {
        // Enforce that only the current player can act
        val game = gameManager.getGame(msg.gameId)
        if (msg.player != game.getCurrentPlayer()) {
            messagingTemplate.convertAndSendToUser(
                msg.player,
                "/queue/private",
                mapOf("type" to "error", "message" to "Not your turn")
            )
            return null
        }
        return game
    }
}