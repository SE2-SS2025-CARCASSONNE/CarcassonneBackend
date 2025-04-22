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
            if (!canPlaceTileAnywhere(game, tile)) println("No legal placement possible. Skipping tile")
            return tile
        }
        return null // Return null if no tile is available
    }

    fun canPlaceTileAnywhere(game: GameState, tile: Tile): Boolean {
        val terrainMap = tile.getRotatedTerrains()

        // Try placing the tile at every empty spot next to existing tiles
        val potentialSpots = game.board.keys.flatMap { pos ->
            listOf(
                Position(pos.x + 1, pos.y),
                Position(pos.x - 1, pos.y),
                Position(pos.x, pos.y + 1),
                Position(pos.x, pos.y - 1)
            )
        }.filter { it !in game.board.keys }.toSet()

        for (spot in potentialSpots) {
            for (rotation in TileRotation.values()) {
                val rotatedTile = tile.copy(tileRotation = rotation, position = spot)
                if (isValidPosition(game, rotatedTile, spot, rotation)) {
                    return true
                }
            }
        }

        return false
    }


    /**
     * returns the new Game state
     * @param gameId is needed to find the game
     */
    //Parameter input Change Datatype Player to String
    fun placeTile(gameId: String, tile: Tile, playerId: String): GameState? {
        val game = games[gameId] ?: throw IllegalArgumentException("Game $gameId is not registered")

        if (game.status != GamePhase.TILE_PLACEMENT) {
            throw IllegalStateException("Game is not in tile placement phase")
        }
        val currentPlayerId = game.getCurrentPlayer()
        // currentPlayer.id != player.id (Datatype Player to String
        if (currentPlayerId != playerId) {
            throw IllegalStateException("Not player's turn")
        }

        if (tile.position == null){
            throw IllegalArgumentException("Tile position required")
        }

        // check whether tile.position is valid -> see helper function below
        if (!isValidPosition(game, tile, tile.position, tile.tileRotation)){
            throw IllegalArgumentException("Position is invalid")
        }
        game.placeTile(tile, tile.position!!)

        if (tile.hasMonastery) {
            if (isMonasteryComplete(game.board, tile.position!!)) println("Monastery is completed at ${tile.position}")
        }

        if (tile.isRoad()) {
            if (isRoadCompleted(game.board, tile.position!!)) {
                println("Road is completed at ${tile.position}")
                // Score road points here
            }
        }

        if (tile.isCity()) {
            if (isCityCompleted(game, tile)) {
                println("City is completed at ${tile.position}")
            }
        }

        //game.nextPlayer() move to endTurn logic
        return game
    }

    /**
     * Helper method to determine validity of position in the context of tile placement
     * returns true if tile can be placed at the desired position
     */
    private fun isValidPosition(game: GameState, tile: Tile, position: Position?, tileRotation: TileRotation): Boolean {
        if (position == null){
            throw IllegalArgumentException("Position can not be null")
        }
        val rotatedTile = tile.copy(tileRotation = tileRotation)
        val terrains = rotatedTile.getRotatedTerrains()

        val neighbors = mapOf(
            Position(position.x, position.y + 1) to "N",
            Position(position.x + 1, position.y) to "E",
            Position(position.x, position.y - 1) to "S",
            Position(position.x - 1, position.y) to "W"
        )


        var hasAdjacent = false

        for ((neighborPos, direction) in neighbors) {
            val neighbor = game.board[neighborPos] ?: continue
            hasAdjacent = true

            val neighborTerrains = neighbor.getRotatedTerrains()

            when (direction) {
                "N" -> if (terrains["N"] != neighborTerrains["S"]) return false
                "E" -> if (terrains["E"] != neighborTerrains["W"]) return false
                "S" -> if (terrains["S"] != neighborTerrains["N"]) return false
                "W" -> if (terrains["W"] != neighborTerrains["E"]) return false
            }
        }

        // Disallow isolated tiles except center
        return hasAdjacent || position == Position(0, 0)
    }

    private fun isMonasteryComplete(board: Map<Position, Tile>, position: Position): Boolean {
        val adjacentOffsets = listOf(
            Pair(-1, -1), Pair(0, -1), Pair(1, -1),
            Pair(-1, 0),              Pair(1, 0),
            Pair(-1, 1),  Pair(0, 1), Pair(1, 1)
        )

        return adjacentOffsets.all { (dx, dy) ->
            val adjacentPos = Position(position.x + dx, position.y + dy)
            board.containsKey(adjacentPos)
        }
    }

    private fun isRoadCompleted(board: Map<Position, Tile>, position: Position): Boolean {
        val tile = board[position] ?: return false
        val terrains = tile.getRotatedTerrains()

        return listOf("N", "E", "S", "W").all { dir ->
            if (terrains[dir] == TerrainType.ROAD) {
                val neighborPos = getNeighborPosition(position, dir)
                val neighbor = board[neighborPos]
                val neighborTerrains = neighbor?.getRotatedTerrains()
                val opposite = getOppositeDirection(dir)
                if (neighborTerrains?.get(opposite) != TerrainType.ROAD) {
                    return false
                }
            }
            true
        }
    }

    private fun getNeighborPosition(position: Position, direction: String): Position = when (direction) {
        "N" -> Position(position.x, position.y + 1)
        "E" -> Position(position.x + 1, position.y)
        "S" -> Position(position.x, position.y - 1)
        "W" -> Position(position.x - 1, position.y)
        else -> position
    }

    private fun getOppositeDirection(direction: String): String = when (direction) {
        "N" -> "S"
        "S" -> "N"
        "E" -> "W"
        "W" -> "E"
        else -> direction
    }

    fun isCityCompleted(game: GameState, placedTile: Tile): Boolean {
        val visited = mutableSetOf<Position>()
        val cityTiles = mutableSetOf<Position>()
        var openEdges = 0

        val queue = ArrayDeque<Pair<Position, String>>() // Position + direction that led here

        // Start from the tile that was placed
        queue.add(Position(placedTile.position!!.x, placedTile.position.y) to "ALL")

        while (queue.isNotEmpty()) {
            val (pos, fromDirection) = queue.removeFirst()
            if (!visited.add(pos)) continue

            val currentTile = game.board[pos] ?: continue
            val terrains = currentTile.getRotatedTerrains()

            cityTiles.add(pos)

            val neighborOffsets = mapOf(
                "N" to Position(pos.x, pos.y + 1),
                "E" to Position(pos.x + 1, pos.y),
                "S" to Position(pos.x, pos.y - 1),
                "W" to Position(pos.x - 1, pos.y)
            )

            for ((dir, neighborPos) in neighborOffsets) {
                val opposite = when (dir) {
                    "N" -> "S"; "S" -> "N"
                    "E" -> "W"; "W" -> "E"
                    else -> "ALL"
                }

                val terrain = terrains[dir]
                if (terrain != TerrainType.CITY) continue

                val neighbor = game.board[neighborPos]
                if (neighbor == null) {
                    openEdges++
                } else {
                    val neighborTerrains = neighbor.getRotatedTerrains()
                    if (neighborTerrains[opposite] == TerrainType.CITY) {
                        queue.add(neighborPos to dir)
                    } else {
                        openEdges++ // Adjacent terrain does not match
                    }
                }
            }
        }

        if (openEdges == 0) {
            val involvedMeeples = game.meeplesOnBoard.filter { it.position == MeeplePosition.CENTER && cityTiles.contains(getTilePosition(game, it.tileId)) }

            val playerMeepleCount = involvedMeeples.groupingBy { it.playerId }.eachCount()
            val maxMeeples = playerMeepleCount.maxByOrNull { it.value }?.value ?: 0
            val winners = playerMeepleCount.filterValues { it == maxMeeples }.keys

            val pointsPerTile = 2
            val points = cityTiles.size * pointsPerTile

            // Update scores and remove meeples
            for (winner in winners) {
                println("Awarding $points points to $winner")
                // Implement score tracking if not already available
            }

            game.meeplesOnBoard.removeAll(involvedMeeples)
            return true
        }
        return false
    }

    private fun getTilePosition(game: GameState, tileId: String?): Position? {
        return game.board.entries.find { it.value.id == tileId }?.key
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
        // Datatype Player to String ?
         val host = Player(hostName,0,8,0)
        val game = GameState(gameId)
        game.players.add(host) //host übergabe
        games[gameId] = game
        return game
    }
}
