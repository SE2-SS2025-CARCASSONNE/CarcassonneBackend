package com.carcassonne.backend.service

import com.carcassonne.backend.model.*
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class GameManager(
) {
    private val games = mutableMapOf<String, GameState>()

    fun createGameWithHost(gameId: String, hostName: String): GameState {
        val host = Player(hostName, 0, 7, 0)
        val game = GameState(gameId)

        val fullDeck = createShuffledTileDeck(System.currentTimeMillis()).toMutableList()

        val index = fullDeck.indexOfFirst { it.id == "tile-d-0" }
        val startTile = if (index >= 0) {
            fullDeck.removeAt(index)
        } else {
            println("Start tile tile-d-0 not found in deck, using first tile instead")
            fullDeck.removeAt(0)
        }

        game.board[Position(0, 0)] = startTile.copy(position = Position(0, 0))
        println("New game $gameId: start tile ${startTile.id} placed at (0,0)")

        fullDeck.shuffle()
        game.tileDeck = fullDeck

        game.players.add(host)
        games[gameId] = game
        return game
    }

    fun getGame(gameId: String): GameState =
        games[gameId] ?: throw IllegalArgumentException("Game $gameId not found")


    // Function to create a shuffled tile deck
    fun createShuffledTileDeck(seed: Long): List<Tile> {
        val baseTiles = getUniqueTiles()

        val fullDeck = baseTiles.flatMap { baseTile ->
            List(baseTile.count) { index ->
                val uniqueId = "${baseTile.id}-$index"
                baseTile.copy(id = uniqueId)
            }
        }

        val shuffled = fullDeck.shuffled(Random(seed))
        shuffled.forEach { println(" - ${it.id}") }

        return shuffled
    }

    fun drawTileForPlayer(gameId: String): Tile? {
        val game = games[gameId] ?: return null

        println(" Starting tile draw... Deck size: ${game.tileDeck.size}, Discarded: ${game.discardedTiles.size}")

        // If deck is empty, try reshuffling discarded tiles
        if (game.tileDeck.isEmpty() && game.discardedTiles.isNotEmpty()) {
            println(" Reshuffling discarded tiles...")
            game.tileDeck.addAll(game.discardedTiles.shuffled())
            game.discardedTiles.clear()
        }

        while (game.tileDeck.isNotEmpty()) {
            val tile = game.drawTile()!!
            if (canPlaceTileAnywhere(game, tile)) {
                println(" Playable tile drawn: ${tile.id}")
                val validPositions = getAllValidPositions(gameId, tile)
                println("Valid placements for ${tile.id}: $validPositions")
                return tile
            } else {
                if (game.tileDeck.isEmpty()) {
                    println("️ Final tile ${tile.id} is unplayable and will NOT be added back.")
                    // Don't add to discardedTiles

                } else {
                    println(" Tile ${tile.id} discarded (no valid position)")
                    game.discardedTiles.add(tile)
                }
            }
        }

        println(">>> No more playable tiles left.")
        game.finishGame()

        val winnerId = endGame(gameId)
        val scores = game.players.map {
            mapOf("player" to it.id, "score" to it.score)
        }

        val payload = mapOf(
            "type" to "game_over",
            "winner" to winnerId,
            "scores" to scores
        )

        println(">>> [Backend] Broadcasting game_over: $payload")
        return null
    }

    fun canPlaceTileAnywhere(game: GameState, tile: Tile): Boolean {
        val potentialSpots = game.board.keys.flatMap { pos ->
            listOf(
                Position(pos.x, pos.y - 1),
                Position(pos.x + 1, pos.y),
                Position(pos.x, pos.y + 1),
                Position(pos.x - 1, pos.y)
            )
        }.filter { it !in game.board.keys }.toSet()

        println(" Checking tile ${tile.id} for ${potentialSpots.size} possible positions...")

        for (spot in potentialSpots) {
            for (rotation in TileRotation.values()) {
                val rotatedTile = tile.copy(tileRotation = rotation, position = spot)
                val valid = isValidPosition(game, rotatedTile, spot)
                println(" - Trying ${tile.id} at $spot with $rotation: $valid")
                if (valid) {
                    return true
                }
            }
        }

        println(" No valid placement found for ${tile.id}")
        return false
    }

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
                 val startEdge = Edge(placedTile.position!!, direction.shortCode)
                 val featureTiles = collectFeatureTiles(
                     board = game.board,
                     start = startEdge,
                     type = terrainType!!
                 )

                 // 4. Abschluss des Features prüfen
                 val isCompleted = when (terrainType) {
                     TerrainType.CITY -> isCityCompleted(
                         game.board,
                         Edge(placedTile.position, direction.shortCode)
                     )
                     TerrainType.ROAD -> isRoadCompleted(
                         game.board,
                         Edge(placedTile.position, direction.shortCode)
                     )
                     TerrainType.MONASTERY -> isMonasteryComplete(
                         game.board, placedTile.position
                     )
                     else -> false
                 }

                 if (isCompleted) {
                     // 5. Alle Meeples auf dem Feature sammeln
                     val startEdge = Edge(placedTile.position, direction.shortCode)

                     val involvedMeeples = game.meeplesOnBoard
                         .filter { meeple ->
                             val pos = getTilePosition(game, meeple.tileId) ?: return@filter false
                             val meeplePos = meeple.position    ?: return@filter false
                             val tile = game.board[pos]    ?: return@filter false
                             if (tile.getTerrainAtOrNull(meeplePos) != terrainType) return@filter false

                             if (meeplePos == MeeplePosition.C) {
                                 true
                             } else {
                                 edgesConnected(
                                     board  = game.board,
                                     start  = startEdge,
                                     target = Edge(pos, meeplePos.name),
                                     type   = terrainType
                                 )
                             }
                         }
                         .toMutableList()

                     val featureTypeString = when (terrainType) {
                         TerrainType.CITY -> "CITY"
                         TerrainType.ROAD -> "ROAD"
                         TerrainType.MONASTERY -> "MONASTERY"
                         else -> ""
                     }

                     // 6. Punkte vergeben, Meeples vom Brett entfernen und den Spielern zurückgeben
                     awardPoints(game, involvedMeeples, featureTiles.size, featureTypeString, featureTiles.toList())

                     if (involvedMeeples.isNotEmpty()) {
                         game.meeplesOnBoard.removeAll(involvedMeeples)
                         involvedMeeples.forEach { meeple ->
                             game.players.first { it.id == meeple.playerId }.remainingMeeple++
                         }
                     }
                 }
             }
         }
         val centre = placedTile.position!!
         listOf(
             0 to 0,
             -1 to -1, 0 to -1, 1 to -1,
             -1 to  0,          1 to  0,
             -1 to  1, 0 to  1, 1 to  1
         ).forEach { (dx, dy) ->
             val pos = Position(centre.x + dx, centre.y + dy)
             scoreMonastery(game, pos)
         }
     }

    private fun awardPoints(
        game: GameState,
        involvedMeeples: MutableList<Meeple>, //MutableList für Konsistenz
        basePoints: Int,  //Weniger Punkte, hilft bei Endgame Logik zum Beispiel ...
        featureType: String, //Berechnung für Monestary, Road, City
        featureTiles: List<Position>
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
            // Add shield bonus of 2 points for applicable city tiles
            val shieldCount = featureTiles.count { pos ->
                game.board[pos]?.hasShield == true
            }
            basePoints * 2 + shieldCount * 2
        }

        "ROAD" -> basePoints

        "MONASTERY" -> {
            val center = getTilePosition(game, involvedMeeples.first().tileId)!!
            val ring = listOf(
                -1 to -1,  0 to -1,  1 to -1,
                -1 to  0,            1 to  0,
                -1 to  1,  0 to  1,  1 to  1
            ).count { (dx, dy) ->
                game.board.containsKey(Position(center.x + dx, center.y + dy))
            }
            val pts = 1 + ring
            println("Monastery @ $center finished: $ring neighbours, $pts pts")
            pts
        }

        else -> {
            println("Ungültiger Feature-Typ: $featureType")
            0
        }
    }

    // Nur ein eindeutiger Gewinner erhält Punkte; bei Gleichstand niemand
    if (winners.size == 1) {
        val winnerId = winners.first()
        val player = game.players.find { it.id == winnerId }
        if (player != null) {
            player.score += pointsPerFeature
            println("Punkte vergeben: $pointsPerFeature an $winnerId ($featureType)")
        } else {
            println("Fehler: Gewinner $winnerId existiert nicht")
        }
    } else {
        println("Gleichstand bei $featureType - keine Punkte vergeben")
    }
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
        if (!isValidPosition(game, tile, tile.position)){
            throw IllegalArgumentException("You can't place the tile here!")
        }
        game.placeTile(tile, tile.position)

        if (tile.hasMonastery) {
            if (isMonasteryComplete(game.board, tile.position)) println("Monastery is completed at ${tile.position}")
        }

        if (tile.isRoad()) {
            val pos = tile.position
            val roadDirs = tile
            .getRotatedTerrains()
            .filterValues { it == TerrainType.ROAD }
            .keys
            if (roadDirs.any { dir ->
                isRoadCompleted(
                    board = game.board,
                    start = Edge(pos, dir)
                    )
                }) {
                println("Road is completed at $pos")
            }
        }

        if (tile.isCity()) {
            val pos = tile.position
            val cityDirs = tile
                .getRotatedTerrains()
                .filterValues { it == TerrainType.CITY }
                .keys
            if (cityDirs.any { dir ->
                    isCityCompleted(
                        board = game.board,
                        start = Edge(pos, dir)
                    )
                }) {
                println("City is completed at $pos")
            }
        }

        //game.nextPlayer() move to endTurn logic
        game.status = GamePhase.MEEPLE_PLACEMENT
        return game
    }

    /**
     * Helper method to determine validity of position in the context of tile placement
     * returns true if tile can be placed at the desired position
     */
    fun getAllValidPositions(gameId: String, tile: Tile): List<Triple<Position, TileRotation, Boolean>> {
        val game = games[gameId] ?: throw IllegalArgumentException("Game not found")
        val validPlacements = mutableListOf<Triple<Position, TileRotation, Boolean>>()

        val potentialSpots = game.board.keys.flatMap { pos ->
            listOf(
                Position(pos.x + 1, pos.y),
                Position(pos.x - 1, pos.y),
                Position(pos.x, pos.y - 1),
                Position(pos.x, pos.y + 1)
            )
        }.filter { it !in game.board.keys }.toSet()

        for (spot in potentialSpots) {
            for (rotation in TileRotation.values()) {
                val rotatedTile = tile.copy(tileRotation = rotation, position = spot)
                val isValid = isValidPosition(game, rotatedTile, spot)
                if (isValid) {
                    validPlacements.add(Triple(spot, rotation, true))
                }
            }
        }
        return validPlacements
    }

    private fun isValidPosition(game: GameState, tile: Tile, position: Position?): Boolean {
        if (position == null){
            throw IllegalArgumentException("Position can not be null")
        }

        val terrains = tile.getRotatedTerrains()

        val neighbors = mapOf(
            Position(position.x, position.y - 1) to "N",
            Position(position.x + 1, position.y) to "E",
            Position(position.x, position.y + 1) to "S",
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

    private fun isRoadCompleted(board: Map<Position, Tile>, start: Edge): Boolean {
        val visited = mutableSetOf<Edge>()
        val edgeQueue = ArrayDeque<Edge>()
        edgeQueue += start

        while (edgeQueue.isNotEmpty()) {
            val edge = edgeQueue.removeFirst()
            if (!visited.add(edge)) continue

            val (pos, dir) = edge
            val tile = board[pos] ?: continue
            val terrains = tile.getRotatedTerrains()
            val center = tile.terrainCenter

            val neighborPos = neighbor(pos, dir)
            val neighborTerr = board[neighborPos]?.getRotatedTerrains()
            if (neighborTerr?.get(opposite(dir)) == TerrainType.ROAD) {
                edgeQueue += Edge(neighborPos, opposite(dir))
            } else {
                return false
            }

            if (center == TerrainType.ROAD) {
                listOf("N","E","S","W")
                    .filter { it != dir && terrains[it] == TerrainType.ROAD }
                    .forEach { edgeQueue += Edge(pos, it) }
            }
        }
        return true
    }

    private fun isCityCompleted(board: Map<Position, Tile>, start: Edge): Boolean {
        val visited = mutableSetOf<Edge>()
        val open = mutableSetOf<Edge>()
        val edgeQueue = ArrayDeque<Edge>()
        edgeQueue += start

        while (edgeQueue.isNotEmpty()) {
            val edge = edgeQueue.removeFirst()
            if (!visited.add(edge)) continue

            val (pos, dir) = edge
            val tile = board[pos] ?: continue
            val terrains = tile.getRotatedTerrains()
            val center = tile.terrainCenter

            val neighborPos = neighbor(pos, dir)
            val neighborTerr = board[neighborPos]?.getRotatedTerrains()
            if (neighborTerr?.get(opposite(dir)) == TerrainType.CITY) {
                edgeQueue += Edge(neighborPos, opposite(dir))
            } else {
                open += edge
            }

            if (center == TerrainType.CITY) {
                listOf("N","E","S","W")
                    .filter { it != dir && terrains[it] == TerrainType.CITY }
                    .forEach { edgeQueue += Edge(pos, it) }
            }
        }
        return open.isEmpty()
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
            throw IllegalStateException("You don't have any meeples left to place!") // Displayed to player in toast
        }

        // Validierung der Position
        val tile = game.board.entries.find { it.value.id == meeple.tileId }?.value
            ?: throw IllegalStateException("Tile not found on the board")
        if (!isValidMeeplePosition(tile, position)) {
            throw IllegalStateException("You can't place the meeple here!")
        }

        // Prüfung auf bereits vorhandene Meeples im verbundenen Bereich
        if (isMeeplePresentOnFeature(game, tile, position)) {
            throw IllegalStateException("Road or city is already occupied!")
        }

        // Meeple-Platzierung
        meeple.position = position
        game.meeplesOnBoard.add(meeple)

        // Meeple-Anzahl aktualisieren
        currentPlayer.remainingMeeple--

        // Nächster Spielstatus
        game.status = GamePhase.SCORING

        return game
    }

    private fun isValidMeeplePosition(tile: Tile, pos: MeeplePosition): Boolean {
        val terrain = tile.getTerrainAtOrNull(pos) ?: return false
        return if (pos == MeeplePosition.C) {
            terrain != TerrainType.FIELD
        } else {
            terrain == TerrainType.ROAD || terrain == TerrainType.CITY
        }
    }

    private fun isMeeplePresentOnFeature(game: GameState, startTile: Tile, startPos: MeeplePosition): Boolean {
        val type = startTile.getTerrainAtOrNull(startPos) ?: return false
        val startEdge = Edge(startTile.position!!, startPos.name)

        return game.meeplesOnBoard.any { m ->
            val pos = getTilePosition(game, m.tileId) ?: return@any false
            val dir = m.position?.name ?: return@any false
            val edge = Edge(pos, dir)

            edgesConnected(game.board, startEdge, edge, type)
        }
    }


    fun getConnectedFeatureTiles(game: GameState, startTile: Tile, startPos: MeeplePosition): List<Position> {
        val featureType = startTile.getTerrainAtOrNull(startPos)
            ?: return emptyList()

        val visitedEdges = mutableSetOf<Edge>()
        val featureTiles = mutableSetOf<Position>()
        val edgeQueue = ArrayDeque<Edge>()

        val originPos = startTile.position!!
        edgeQueue += Edge(originPos, startPos.name)

        if (startPos != MeeplePosition.C) {
            edgeQueue += Edge(originPos, startPos.name)
        } else {
            startTile.getRotatedTerrains()
                .filterValues { it == featureType }
                .keys
                .forEach { dir -> edgeQueue += Edge(originPos, dir) }
        }

        while(edgeQueue.isNotEmpty()){
            val (pos, dir) = edgeQueue.removeFirst()
            if (!visitedEdges.add(Edge(pos, dir))) continue
            featureTiles += pos

            val tile = game.board[pos] ?: continue
            val terrains = tile.getRotatedTerrains()
            val center = tile.terrainCenter

            if (terrains[dir] == featureType) {
                val neighborPos = neighbor(pos, dir)
                val neighborTile = game.board[neighborPos]
                if (neighborTile?.getRotatedTerrains()?.get(opposite(dir)) == featureType) {
                    edgeQueue += Edge(neighborPos, opposite(dir))
                }
            }

            if (center == featureType) {
                listOf("N","E","S","W")
                    .filter { it != dir && terrains[it] == featureType }
                    .forEach { edgeQueue += Edge(pos, it) }
            }
        }
        return featureTiles.toList()
    }

    private data class Edge(val pos: Position, val dir: String)

    private fun opposite(d: String) = when (d) {
        "N" -> "S"
        "S" -> "N"
        "E" -> "W"
        else -> "E" }

    private fun neighbor(p: Position, d: String) = when (d) {
        "N" -> Position(p.x, p.y - 1)
        "S" -> Position(p.x, p.y + 1)
        "E" -> Position(p.x + 1, p.y)
        else -> Position(p.x - 1, p.y) // W
    }

    private fun scoreMonastery(game: GameState, centrePos: Position) {
        val centreTile = game.board[centrePos] ?: return
        if (!centreTile.hasMonastery) return
        if (!isMonasteryComplete(game.board, centrePos)) return   // still open

        val monks = game.meeplesOnBoard.filter {
            it.tileId == centreTile.id && it.position == MeeplePosition.C
        }
        if (monks.isEmpty()) return

        val pts = 9
        val winner = game.players.first { it.id == monks.first().playerId }
        winner.score += pts
        println("Monastery @ $centrePos completed → ${winner.id} +$pts")

        game.meeplesOnBoard.removeAll(monks)
        monks.forEach { game.players.first { p -> p.id == it.playerId }.remainingMeeple++ }
    }


    private fun edgesConnected(board: Map<Position, Tile>, start: Edge, target: Edge, type: TerrainType) : Boolean {
        val visited = mutableSetOf<Edge>()
        val edgeQueue = ArrayDeque<Edge>()
        edgeQueue += start

        while (edgeQueue.isNotEmpty()) {
            val (p, d) = edgeQueue.removeFirst()
            if (!visited.add(Edge(p, d))) continue
            if (p == target.pos && d == target.dir) return true

            val tile = board[p] ?: continue
            val terrains = tile.getRotatedTerrains()
            val centreIsFeature = tile.terrainCenter == type

            if (terrains[d] == type) {
                val neighborPos = neighbor(p, d)
                val neighborTerr = board[neighborPos]?.getRotatedTerrains()
                if (neighborTerr?.get(opposite(d)) == type) {
                    edgeQueue += Edge(neighborPos, opposite(d))
                }
            }

            if (centreIsFeature) {
                if (d != "C") edgeQueue += Edge(p, "C")
                else {
                    listOf("N","E","S","W")
                        .filter { terrains[it] == type }
                        .forEach { edgeQueue += Edge(p, it) }
                }
            }
        }
        return false
    }

    private fun collectFeatureTiles(
        board: Map<Position, Tile>,
        start: Edge,
        type: TerrainType
    ): Set<Position> {
        val seen   = mutableSetOf<Edge>()
        val tiles  = mutableSetOf<Position>()
        val q      = ArrayDeque<Edge>()
        q += start

        while (q.isNotEmpty()) {
            val (p, d) = q.removeFirst()
            if (!seen.add(Edge(p, d))) continue
            tiles += p

            val tile = board[p] ?: continue
            val terrains = tile.getRotatedTerrains()
            val centreOk = tile.terrainCenter == type

            if (terrains[d] == type) {
                val np = neighbor(p, d)
                val nt = board[np]?.getRotatedTerrains()
                if (nt?.get(opposite(d)) == type) {
                    q += Edge(np, opposite(d))
                }
            }

            if (centreOk) {
                listOf("N","E","S","W")
                    .filter { it != d && terrains[it] == type }
                    .forEach { q += Edge(p, it) }
            }
        }
        return tiles
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
                terrainCenter = TerrainType.MONASTERY,
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
                terrainCenter = TerrainType.MONASTERY,
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
                terrainCenter = TerrainType.CITY,
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
                terrainCenter = TerrainType.ROAD,
                tileRotation = TileRotation.NORTH,
                count = 4
            ),
            Tile(
                id = "tile-e",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                count = 5
            ),
            Tile(
                id = "tile-f",
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.CITY,
                terrainCenter = TerrainType.CITY,
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
                terrainCenter = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                count = 1
            ),
            Tile(
                id = "tile-h",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.CITY,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                count = 3
            ),
            Tile(
                id = "tile-i",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.CITY,
                terrainCenter = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                count = 2
            ),
            Tile(
                id = "tile-j",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.ROAD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.ROAD,
                tileRotation = TileRotation.NORTH,
                count = 3
            ),
            Tile(
                id = "tile-k",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.ROAD,
                terrainCenter = TerrainType.ROAD,
                tileRotation = TileRotation.NORTH,
                count = 3
            ),
            Tile(
                id = "tile-m",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.ROAD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.ROAD,
                terrainCenter = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                count = 3
            ),
            Tile(
                id = "tile-n",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                count = 3
            ),
            Tile(
                id = "tile-o",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                hasShield = true,
                count = 2
            ),
            Tile(
                id = "tile-r",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.CITY,
                terrainCenter = TerrainType.CITY,
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
                terrainCenter = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                count = 3
            ),
            Tile(
                id = "tile-t",
                terrainNorth = TerrainType.CITY,
                terrainEast = TerrainType.CITY,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.CITY,
                terrainCenter = TerrainType.CITY,
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
                terrainCenter = TerrainType.CITY,
                tileRotation = TileRotation.NORTH,
                count = 1
            ),
            Tile(
                id = "tile-v",
                terrainNorth = TerrainType.ROAD,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.ROAD,
                tileRotation = TileRotation.NORTH,
                count = 8
            ),
            Tile(
                id = "tile-w",
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.ROAD,
                terrainCenter = TerrainType.ROAD,
                tileRotation = TileRotation.NORTH,
                count = 9
            ),
            Tile(
                id = "tile-x",
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.ROAD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.ROAD,
                terrainCenter = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                count = 4
            ),
            Tile(
                id = "tile-y",
                terrainNorth = TerrainType.ROAD,
                terrainEast = TerrainType.ROAD,
                terrainSouth = TerrainType.ROAD,
                terrainWest = TerrainType.ROAD,
                terrainCenter = TerrainType.FIELD,
                tileRotation = TileRotation.NORTH,
                count = 1
            )
        )
        return uniqueTiles
    }
}
