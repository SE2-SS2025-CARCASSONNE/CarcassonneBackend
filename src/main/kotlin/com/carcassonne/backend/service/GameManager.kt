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


    /**
     * returns the new Game state
     * gameId is needed to find the game
     * uses the coordinates of the tile's position
     *
     * TODO: adjust request message from frontend to send the desired position's coordinates
     *   - either as tile's position attribute
     *   - or as additional attribute of the GameMessage instance
     */
    fun placeTile(gameId: String, tile: Tile, player: String, position: Pair<Int?, Int?>): GameState? {
        val game = games[gameId] ?: return null
        if (position.first == null || position.second == null) {
            throw IllegalArgumentException("Position can not be null")
        }
        val desiredPositionOnGameBoard = Position(position.first!!, position.second!!)

        if (game.status != GamePhase.TILE_PLACEMENT) {
            throw IllegalStateException("Game is not in tile placement phase")
        }
        val currentPlayer = game.getCurrentPlayer()
        if (currentPlayer != player) {
            throw IllegalStateException("Not player's turn")
        }

        // check whether tile.position is valid -> see helper function below
        if (!isValidPosition(game, tile, desiredPositionOnGameBoard, tile.tileRotation)){
            throw IllegalArgumentException("Position is not valid")
        }
        game.placeTile(tile, desiredPositionOnGameBoard)

//      move game.nextPlayer() call to calculateScore method
        return game
    }

    /**
     * Helper method to determine validity of position in the context of tile placement
     * returns true if tile can be placed at the desired position
     */
    private fun isValidPosition(game: GameState, tile: Tile, position: Position, tileRotation: TileRotation): Boolean {

        val leftNeighborPosition = Position(position.x - 1, position.y)
        val rightNeighborPosition = Position(position.x + 1, position.y)
        val topNeighborPosition = Position(position.x, position.y + 1)
        val bottomNeighborPosition = Position(position.x, position.y - 1)


        val leftNeighbor: Tile? = game.board[leftNeighborPosition]
        val rightNeighbor: Tile? = game.board[rightNeighborPosition]
        val topNeighbor: Tile? = game.board[topNeighborPosition]
        val bottomNeighbor: Tile? = game.board[bottomNeighborPosition]
        val neighbors = mutableListOf(leftNeighbor, rightNeighbor, topNeighbor, bottomNeighbor)

        if(isListOfNulls(neighbors)) {
            return false
        }

        for(placedTile in game.board.values) {
            if (position == placedTile.position) {
                return false
            }
        }
        for (neighbor in neighbors){
            if (neighbor == null) {
                continue
            } else {
                if (neighbor.position == leftNeighborPosition && neighbor.terrainEast != tile.terrainWest) {
                    return false
                }
                if (neighbor.position == rightNeighborPosition && neighbor.terrainWest != tile.terrainEast) {
                    return false
                }
                if (neighbor.position == topNeighborPosition && neighbor.terrainSouth != tile.terrainNorth) {
                    return false
                }
                if (neighbor.position == bottomNeighborPosition && neighbor.terrainNorth != tile.terrainSouth) {
                    return false
                }

            }
        }
        // only return true if more than 0 neighbors exist &&
        // leftNeighbor.terrainEast == tile.terrainWest &&
        // rightNeighbor.terrainWest == tile.terrainEast &&
        // topNeighbor.terrainSouth == tile.terrainNorth &&
        // bottomNeighbor.terrainNorth == tile.terrainSouth
        return true
    }

    /**
     * Helper function to sort out game board positions without neighbors
     * returns true if tile list contains only null values
     */
    private fun isListOfNulls(tiles: MutableList<Tile?>): Boolean {
        for (tile in tiles) {
            if (tile != null) {
                return false
            }
        }
        return true
    }

    fun placeMeeple(gameId: String, playerId: String, meeple: Meeple, position: MeeplePosition): GameState? {
        val game = games[gameId] ?: return null

        // Validierung des Spielstatus
        if (game.status != GamePhase.MEEPLE_PLACEMENT) {
            throw IllegalStateException("Game is not in meeple placement phase")
        }

        val currentPlayer = game.getCurrentPlayer()
        if (currentPlayer != playerId) {
            throw IllegalStateException("Not player's turn")
        }

        // Validierung der Position
        val tile = game.board.entries.find { it.value.id == meeple.tileId }?.value
            ?: throw IllegalStateException("Tile not found on the board")
        if (!isValidMeeplePosition(tile, position)) {
            throw IllegalStateException("Invalid meeple position")
        }

        // Meeple-Platzierung
        meeple.position = position
        game.meeplesOnBoard.add(meeple)

        // Nächster Spielstatus
        game.status = GamePhase.TILE_PLACEMENT //TODO: Mike: Ist das richtig oder müssen wir auf SCORING?
        game.nextPlayer()

        return game
    }

    private fun isValidMeeplePosition(tile: Tile, position: MeeplePosition): Boolean {
        // Beispiel-Validierungslogik (Platzierung auf dem Feld, Stadt, Straße etc.)
        return when (position) {
            MeeplePosition.NORTH -> tile.terrainNorth == TerrainType.CITY || tile.terrainNorth == TerrainType.ROAD
            MeeplePosition.SOUTH -> tile.terrainSouth == TerrainType.FIELD || tile.terrainSouth == TerrainType.MONASTERY
            else -> true // Weitere Validierungen
        }
    }

    fun createGameWithHost(gameId: String, hostName: String): GameState {
        val game = GameState(gameId)
        game.players.add(hostName)
        games[gameId] = game
        return game
    }
}
