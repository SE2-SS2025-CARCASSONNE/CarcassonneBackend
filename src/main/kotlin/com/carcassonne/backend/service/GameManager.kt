package com.carcassonne.backend.service

import com.carcassonne.backend.model.GameState
import com.carcassonne.backend.model.TerrainType
import com.carcassonne.backend.model.Tile
import com.carcassonne.backend.model.TileRotation
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class GameManager {
    private val games = mutableMapOf<String, GameState>()

    fun getOrCreateGame(gameId: String): GameState =
        games.getOrPut(gameId) { GameState(gameId) }

    // Function to create a shuffled tile deck
    fun createShuffledTileDeck(seed: Long): List<Tile> {
        // Generate a predefined set of tiles (for demonstration)
        val predefinedTiles = mutableListOf<Tile>()

        // Populate predefined tiles
        TerrainType.values().forEach { terrain ->
            // For simplicity, create 5 tiles of each terrain type
            repeat(5) {
                predefinedTiles.add(
                    Tile(
                        id = "${terrain}_${it}",
                        terrainNorth = terrain,
                        terrainEast = terrain,
                        terrainSouth = terrain,
                        terrainWest = terrain,
                        tileRotation = TileRotation.NORTH
                    )
                )
            }
        }

        // Shuffle tiles using a seed for consistent randomness
        val shuffledDeck = predefinedTiles.shuffled(Random(seed))

        return shuffledDeck
    }

    // Function to draw a tile for the current player
    fun drawTileForPlayer(gameId: String): Tile? {
        val game = games[gameId] ?: return null
        val tile = game.drawTile() // Use the drawTile method from GameState to get a tile
        if (tile != null) {
            return tile
        }
        return null // Return null if no tile is available
    }




    fun placeTile(gameId: String, tile: Tile, player: String): GameState? {
        val game = games[gameId] ?: return null
        if (game.getCurrentPlayer() != player) return null

       // val key = "${tile.x},${tile.y}"
        //game.board[key] = tile
        game.nextPlayer()
        return game
    }
    fun createGameWithHost(gameId: String, hostName: String): GameState {
        val game = GameState(gameId)
        game.players.add(hostName)
        games[gameId] = game
        return game
    }
}
