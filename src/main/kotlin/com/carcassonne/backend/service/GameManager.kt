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
        val baseTiles = getUniqueTiles()

        // Create full tile set with unique IDs based on count per base tile
        val fullDeck = baseTiles.flatMap { baseTile ->
            List(baseTile.count) { index ->
                val uniqueId = "${baseTile.id}-$index"
                baseTile.copy(id = uniqueId)
            }
        }

        // Shuffle deck with fixed seed for reproducibility
        return fullDeck.shuffled(Random(seed))
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

     /*
     * fun calculatePoints(gameId: String): GameState?
     * {
     *      if(isMonasteryComplete)
     *      {
     *
     *      }
     * }
     *
     *private fun awardPoints(
    game: GameState,
    involvedMeeples: List<Meeple>,
    basePoints: Int,
    featureType: String
) {
    val playerCounts = involvedMeeples.groupBy { it.playerId }.mapValues { it.value.size }
    val maxCount = playerCounts.maxOfOrNull { it.value } ?: 0
    val winners = playerCounts.filter { it.value == maxCount }.keys

    winners.forEach { playerId ->
        val points = when (featureType) {
            "CITY" -> basePoints * 2
            "ROAD" -> basePoints
            else -> 0
        }
        game.players.find { it.id == playerId }?.points += points
     *
     *
     *
     *
      */
     fun calculateScore(gameId: String, placedTile: Tile) {
         val game = games[gameId] ?: throw IllegalArgumentException("Game $gameId is not registered")

         if (game.status != GamePhase.SCORING) {
             throw IllegalStateException("Game is not in scoring phase")
         }
         // Prüfe alle 4 Himmelsrichtungen + Center
         listOf(
             MeeplePosition.N,
             MeeplePosition.E,
             MeeplePosition.S,
             MeeplePosition.W,
             MeeplePosition.C
         ).forEach { direction ->
             // 1. Terraintyp der aktuellen Richtung ermitteln
             val terrainType = placedTile.getTerrainAtOrNull(direction)

             // 2. Nur für CITY/ROAD/MONASTERY weiter prüfen
             if (terrainType in listOf(TerrainType.CITY, TerrainType.ROAD, TerrainType.MONASTERY) || placedTile.hasMonastery) {

                 // 3. Alle verbundenen Tiles des Features finden
                 val featureTiles = getConnectedFeatureTiles(
                     game = game,
                     startTile = placedTile,
                     startPosition = direction
                 )

                 // 4. Abschluss des Features prüfen
                 val isCompleted = when (terrainType) {
                     TerrainType.CITY -> isCityCompleted(game, placedTile)
                     TerrainType.ROAD -> isRoadCompleted(game.board, placedTile.position!!)
                     TerrainType.MONASTERY -> isMonasteryComplete(game.board, placedTile.position!!)
                     else -> false
                 }

                 if (isCompleted) {
                     // 5. Alle Meeples auf dem Feature sammeln
                     val involvedMeeples = featureTiles.flatMap { tilePos ->
                         game.board[tilePos]?.let { tile ->
                             game.meeplesOnBoard.filter { meeple ->
                                 // Prüfe, dass der Meeple auf dem Feature-Typ liegt
                                 meeple.position?.let { meepleDir ->
                                     tile.getTerrainAtOrNull(meepleDir) == terrainType
                                 } ?: false
                             }
                         } ?: emptyList()
                     }.toMutableList()

                     val featureTypeString = when (terrainType) {
                         TerrainType.CITY -> "CITY";
                         TerrainType.ROAD -> "ROAD"
                         TerrainType.MONASTERY -> "MONASTERY"
                         else -> ""
                     }

                     // 6. Punkte vergeben & Meeples entfernen
                     awardPoints(game, involvedMeeples, featureTiles.size, featureTypeString)
                     game.meeplesOnBoard.removeAll(involvedMeeples)
                 }
             }
         }
     }

    private fun awardPoints(
        game: GameState,
        involvedMeeples: MutableList<Meeple>, //MutableList für Konsistenz
        basePoints: Int,  //Weniger Punkte, hilft bei Endgame Logik zum Beispiel ...
        featureType: String //Berechnung für Monestary, Road, City
         ) {
    // Überprüfung ob Meeples vorhanden sind und Punkte vergeben werden können
    if (involvedMeeples.isEmpty()) {
        println("Keine Meeples für $featureType-Scoring")
        return
    }

    // Gruppierung mit Fehlerhandling (Keine Einträge mit Meeple == 0
    val playerCounts = involvedMeeples
        .groupBy { it.playerId }
        .mapValues { (_, meeples) ->
            meeples.also {
                if (it.isEmpty()) println("Kritischer Fehler: Leere Meeple-Gruppe")
            }.size
        }

    // Maximalwert mit Fallback
    val maxCount = playerCounts.maxByOrNull { it.value }?.value ?: run {
        println("Fehler: Keine gültigen Spieler für $featureType")
        return
    }

    // Gewinner ermitteln
    val winners = playerCounts.filter { it.value == maxCount }.keys

    // Punkteberechnung mit enum für Klarheit
    val pointsPerFeature = when (featureType) {
        "CITY" -> {
            var points = basePoints * 2
            // Add shield bonus of 2 points for applicable city tiles
            val tilesWithShield = getTilesWithShield(involvedMeeples, game)
            points += tilesWithShield.size * 2
            points
        }
        "ROAD" -> basePoints
        "MONASTERY" -> basePoints
        else -> {
            println("Ungültiger Feature-Typ: $featureType")
            0
        }
    }

    // Punkte verteilen mit Spieler-Check
    winners.forEach { playerId ->
        val player = game.players.find { it.id == playerId } ?: run {
            println("Fehler: Spieler $playerId existiert nicht")
            return@forEach
        }

        player.score += pointsPerFeature
        println("Punkte vergeben: $pointsPerFeature an $playerId ($featureType)")
    }

    // Debug-Info
    println(
        """Gewinner für $featureType:
           ${winners.joinToString { "$it (${playerCounts[it]} Meeples)" }}"""
    )
}

    fun endGame(gameId: String): String {
        val game = games[gameId] ?: throw IllegalArgumentException("Game not found")

        if (game.status != GamePhase.FINISHED) {
            throw IllegalStateException("Game is not in FINISHED phase")
        }

        val winner = game.players.maxByOrNull { it.score }
            ?: throw IllegalStateException("No players to determine winner")

        println(">>> Winner is: ${winner.id} with ${winner.score} points")
        return winner.id
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
            val involvedMeeples = game.meeplesOnBoard.filter { it.position == MeeplePosition.C && cityTiles.contains(getTilePosition(game, it.tileId)) }

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

        val currentPlayerId = game.getCurrentPlayer()
        val currentPlayer = game.players.find { it.id == currentPlayerId }
            ?: throw IllegalStateException("Current player not found")

        // Validierung des Spielers
        if (currentPlayerId != playerId) {
            throw IllegalStateException("Not player's turn")
        }

        // Überprüfen, ob der Spieler noch Meeples hat
        if (currentPlayer.remainingMeeple <= 0) {
            throw IllegalStateException("No Meeples remaining for placement!") // Rückmeldung an den Spieler auf Englisch
        }

        // Validierung der Position
        val tile = game.board.entries.find { it.value.id == meeple.tileId }?.value
            ?: throw IllegalStateException("Tile not found on the board")
        if (!isValidMeeplePosition(tile, position)) {
            throw IllegalStateException("Invalid meeple position")
        }

        // Prüfung auf bereits vorhandene Meeples im verbundenen Bereich
        val connectedTiles = getConnectedFeatureTiles(game, tile, position)
        if (connectedTiles.isEmpty()) {
            println("No connected feature found for Meeple at position: $position")
        }
        val featureType = if (position == MeeplePosition.C) TerrainType.MONASTERY
        else tile.getTerrainType(position.name)
            ?: throw IllegalStateException("Invalid feature type")
        if (isMeeplePresentOnFeature(game, connectedTiles, featureType)) {
            throw IllegalStateException("Another Meeple is already present on this feature!")
        }

        // Meeple-Platzierung
        meeple.position = position
        game.meeplesOnBoard.add(meeple)

        // Nächster Spielstatus
        game.status = GamePhase.SCORING //Mike: Ist das richtig oder müssen wir auf SCORING? --> Scoring lt. Bespr. mit Jakob/Felix 27.04.2025
        game.nextPlayer() //TODO: Mike: Check mit Jakob/Felix, ob ich überhaupt auf nextPlayer stellen darf!

        return game
    }

    private fun isValidMeeplePosition(tile: Tile, position: MeeplePosition): Boolean {
        if (position == MeeplePosition.C) {
            return tile.isMonastery()
        }

        val rotatedTerrains = tile.getRotatedTerrains()
        val terrain = rotatedTerrains[position.name] ?: return false

        return terrain == TerrainType.CITY || terrain == TerrainType.ROAD
    }

    fun getConnectedFeatureTiles(game: GameState, startTile: Tile, startPosition: MeeplePosition): List<Position> {
        val visited = mutableSetOf<Position>()
        val featureTiles = mutableListOf<Position>()
        val queue = ArrayDeque<Pair<Position, String>>() // Position + Ausgangsrichtung

        val startTilePosition = Position(startTile.position!!.x, startTile.position.y)
        queue.add(startTilePosition to startPosition.name) // Nutze die Richtung als String ("N", "E", etc.)
        while (queue.isNotEmpty()) {
            val (currentPos, fromDirection) = queue.removeFirst()
            if (!visited.add(currentPos)) continue

            val currentTile = game.board[currentPos] ?: continue
            featureTiles.add(currentPos)

            val rotatedTerrains = currentTile.getRotatedTerrains()

            // Prüfe angrenzende Tiles basierend auf der Verbindung und Rotation
            val neighborOffsets = mapOf(
                "N" to Position(currentPos.x, currentPos.y + 1),
                "E" to Position(currentPos.x + 1, currentPos.y),
                "S" to Position(currentPos.x, currentPos.y - 1),
                "W" to Position(currentPos.x - 1, currentPos.y)
            )

            for ((dir, neighborPos) in neighborOffsets) {
                val neighborTile = game.board[neighborPos]
                if (neighborTile == null) continue

                val neighborTerrains = neighborTile.getRotatedTerrains()
                val oppositeDir = when (dir) {
                    "N" -> "S"
                    "E" -> "W"
                    "S" -> "N"
                    "W" -> "E"
                    else -> throw IllegalStateException("Invalid direction")
                }

                // Prüfe, ob die Verbindung zwischen den Tiles passt
                if (rotatedTerrains[dir] == neighborTerrains[oppositeDir]) {
                    queue.add(neighborPos to dir)
                }
            }
        }

        return featureTiles
    }

    fun isMeeplePresentOnFeature(game: GameState, featureTiles: List<Position>, featureType: TerrainType): Boolean {
        return game.meeplesOnBoard.any { meeple ->
            val meepleTilePosition = getTilePosition(game, meeple.tileId)
            val direction = meeple.position?.name ?: throw IllegalStateException("Meeple position is null")
            meepleTilePosition in featureTiles &&
                    game.board[meepleTilePosition]?.getTerrainType(direction) == featureType
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

    fun getUniqueTiles(): List<Tile> {
        // Save all 24 unique base tiles in a list for tile deck generation
        val uniqueTiles = listOf(
            Tile(
                id = "tile-a",
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                hasMonastery = true,
                count = 2
            ),
            Tile(
                id = "tile-b",
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                hasMonastery = true,
                count = 4
            ),
            Tile(
                id = "tile-c",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.CITY,
                terrainWest = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                hasShield = true,
                count = 1
            ),
            Tile(
                id = "tile-d",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.ROAD,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.ROAD,
                tileRotation = TileRotation.NORTH,
                count = 4
            ),
            Tile(
                id = "tile-e",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                count = 5
            ),
            Tile(
                id = "tile-f",
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                hasShield = true,
                count = 2
            ),
            Tile(
                id = "tile-g",
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                count = 1
            ),
            Tile(
                id = "tile-h",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.CITY,
                terrainWest = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                count = 3
            ),
            Tile(
                id = "tile-i",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                count = 2
            ),
            Tile(
                id = "tile-j",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.ROAD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                count = 3
            ),
            Tile(
                id = "tile-k",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.ROAD,
                tileRotation = TileRotation.NORTH,
                count = 3
            ),
            Tile(
                id = "tile-m",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.ROAD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.ROAD,
                tileRotation = TileRotation.NORTH,
                count = 3
            ),
            Tile(
                id = "tile-n",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                count = 3
            ),
            Tile(
                id = "tile-o",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                hasShield = true,
                count = 2
            ),
            Tile(
                id = "tile-p",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.ROAD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                hasShield = true,
                count = 2
            ),
            Tile(
                id = "tile-q",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.ROAD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                count = 3
            ),
            Tile(
                id = "tile-r",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                hasShield = true,
                count = 1
            ),
            Tile(
                id = "tile-s",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                count = 3
            ),
            Tile(
                id = "tile-t",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                hasShield = true,
                count = 2
            ),
            Tile(
                id = "tile-u",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                count = 1
            ),
            Tile(
                id = "tile-v",
                terrainNorth = TerrainType.ROAD,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                count = 8
            ),
            Tile(
                id = "tile-w",
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.ROAD,
                tileRotation = TileRotation.NORTH,
                count = 9
            ),
            Tile(
                id = "tile-x",
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.ROAD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.ROAD,
                tileRotation = TileRotation.NORTH,
                count = 4
            ),
            Tile(
                id = "tile-y",
                terrainNorth = TerrainType.ROAD,
                terrainEast = TerrainType.ROAD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.ROAD,
                tileRotation = TileRotation.NORTH,
                count = 1
            )
        )
        return uniqueTiles
    }

    fun getTilesWithShield(involvedMeeples: List<Meeple>, gameState: GameState): List<Tile> {
        val tilesWithShield = mutableListOf<Tile>()

        // Add all tiles with shield to the list (no need to manually check for city)
        for (tile in gameState.tileDeck) {
            if (tile.hasShield) {
                for (meeple in involvedMeeples) {
                    if (meeple.tileId == tile.id) {
                        tilesWithShield.add(tile)
                        break // Exit inner loop upon match
                    }
                }
            }
        }
        return tilesWithShield
    }
}
