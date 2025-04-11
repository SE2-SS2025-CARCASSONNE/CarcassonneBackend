package com.carcassonne.backend.service

import com.carcassonne.backend.model.*
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
        if (game.status != GamePhase.TILE_PLACEMENT) {
            throw IllegalStateException("Game is not in tile placement phase")
        }
        val currentPlayer = game.getCurrentPlayer()
        if (currentPlayer != player) {
            throw IllegalStateException("Not player's turn")
        }
        val currentTile = game.tileDeck.removeFirst() ?: throw NoSuchElementException("Tile not found")

        // check whether tile.position is valid -> see helper function below
        if (!isValidPosition(game, currentTile, currentTile.position, currentTile.tileRotation)){
            throw IllegalStateException("Tile is not in tile placement phase")
        }

        game.board.set(tile.position!!, currentTile)
        game.status = GamePhase.MEEPLE_PLACEMENT

        game.nextPlayer()
        return game
    }

    private fun isValidPosition(game: GameState, tile: Tile, position: Position?, tileRotation: TileRotation): Boolean {
        return true
    }

    fun createGameWithHost(gameId: String, hostName: String): GameState {
        val game = GameState(gameId)
        game.players.add(hostName)
        games[gameId] = game
        return game
    }
}
