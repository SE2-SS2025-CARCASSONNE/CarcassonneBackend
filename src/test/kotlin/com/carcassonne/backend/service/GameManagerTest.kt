package com.carcassonne.backend.service

import com.carcassonne.backend.model.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class GameManagerTest {
    private lateinit var gameManager: GameManager

    @BeforeEach
    fun setup() {
        gameManager = GameManager()
    }

    @Test
    fun `should create and return new game if not exists`() {
        val game = gameManager.createGameWithHost("test-game", "host")
        assertEquals("test-game", game.gameId)

        val sameGame = gameManager.getGame("test-game")
        assertSame(game, sameGame, "Should return the same instance for same gameId")
    }
    @Test
    fun `should generate tile deck with correct count and valid terrains`() {
        val deck = gameManager.createShuffledTileDeck(seed = 99L)
        val expectedCount = gameManager.getUniqueTiles().sumOf { it.count }
        assertEquals(expectedCount, deck.size, "Expected $expectedCount tiles in deck")

        deck.forEach { tile ->
            assertTrue(tile.terrainNorth in TerrainType.entries.toTypedArray())
            assertTrue(tile.terrainEast in TerrainType.entries.toTypedArray())
            assertTrue(tile.terrainSouth in TerrainType.entries.toTypedArray())
            assertTrue(tile.terrainWest in TerrainType.entries.toTypedArray())
        }
    }

    @Test
    fun `tile deck should be consistent with same seed`() {
        val seed = 42L
        val deck1 = gameManager.createShuffledTileDeck(seed)
        val deck2 = gameManager.createShuffledTileDeck(seed)
        assertEquals(deck1, deck2, "Decks with same seed should be identical")
    }

    @Test
    fun `tile deck should differ with different seeds`() {
        val deck1 = gameManager.createShuffledTileDeck(seed = 1L)
        val deck2 = gameManager.createShuffledTileDeck(seed = 2L)
        assertNotEquals(deck1, deck2, "Decks with different seeds should be different")
    }

    @Test
    fun `tile IDs should be unique in the generated deck`() {
        val deck = gameManager.createShuffledTileDeck(seed = 123L)
        val ids = deck.map { it.id }
        val uniqueIds = ids.toSet()
        assertEquals(deck.size, uniqueIds.size, "All tile IDs should be unique")
    }


    @Test
    fun `drawTileForPlayer should return tile if deck has placeable tile`() {
        val game = gameManager.createGameWithHost("game-draw", "host")

        // Place a starting tile at (0,0) so something is on the board
        game.board[Position(0, 0)] = Tile(
            id = "start-tile",
            terrainNorth = TerrainType.ROAD,
            terrainEast = TerrainType.FIELD,
            terrainSouth = TerrainType.FIELD,
            terrainWest = TerrainType.FIELD,
            terrainCenter = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH
        )

        // Add a tile that can connect to that starting tile
        val tile = Tile(
            id = "test-tile",
            terrainNorth = TerrainType.FIELD,
            terrainEast = TerrainType.ROAD,
            terrainSouth = TerrainType.FIELD,
            terrainWest = TerrainType.FIELD,
            terrainCenter = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH
        )
        game.tileDeck.add(tile)

        val drawn = gameManager.drawTileForPlayer("game-draw")
        assertNotNull(drawn)
    }

    @Test
    fun `drawTileForPlayer should return null if deck is empty`() {
        val game = gameManager.createGameWithHost("empty-draw", "host")
        game.tileDeck.clear()

        val result = gameManager.drawTileForPlayer("empty-draw")
        assertNull(result)
    }

    @Test
    fun `placeTile should place tile if first tile`() {
        val gameId = "game-1"
        val game = gameManager.createGameWithHost(gameId, "Player1")
        game.addPlayer("Player2")

        game.startGame()

        val tile = Tile(
            "${TerrainType.CITY}_${this}",
            TerrainType.CITY, TerrainType.CITY,
            TerrainType.CITY, TerrainType.ROAD,
            terrainCenter = TerrainType.FIELD,
            TileRotation.NORTH,
            Position(0,0)
        )
        val updated = gameManager.placeTile(
            gameId, tile, "Player1"
        )

        assertNotNull(updated)
        assertEquals(tile, updated.board[Position(0,0)])
    }

    @Test
    fun `placeTile should place tile if valid`() {
        val gameId = "game-1"
        val game = gameManager.createGameWithHost(gameId, "Player1")
        game.addPlayer("Player2")
        game.startGame()

        val tile1 = Tile(
            "${TerrainType.CITY}_${this}",
            TerrainType.ROAD, TerrainType.CITY,
            TerrainType.CITY, TerrainType.CITY,
            terrainCenter = TerrainType.FIELD,
            TileRotation.NORTH,
            Position(0,-1)
        )
        val tile2 = Tile(
            "${TerrainType.CITY}_${this}",
            TerrainType.FIELD, TerrainType.FIELD,
            TerrainType.ROAD, TerrainType.CITY,
            terrainCenter = TerrainType.FIELD,
            TileRotation.NORTH,
            Position(1,-1),
            true
        )
        // simulate first round
        gameManager.placeTile(
            gameId, tile1, "Player1"
        )
        game.nextPlayer()
        game.status = GamePhase.TILE_PLACEMENT

        // second round
        val updated = gameManager.placeTile(
            gameId, tile2, "Player2"
        )

        assertNotNull(updated)
        assertEquals(tile1, updated.board[Position(0,-1)])
        assertEquals(tile2, updated.board[Position(1,-1)])
    }

    @Test
    fun `should reject tile placement if not current player`() {
        val gameId = "wrong-turn"
        val game = gameManager.createGameWithHost(gameId, "Player1")
        game.addPlayer("Player2")
        game.startGame()

        val tile = Tile(
            "${TerrainType.CITY}_${this}",
            TerrainType.CITY, TerrainType.ROAD,
            TerrainType.CITY, TerrainType.ROAD,
            terrainCenter = TerrainType.FIELD,
            TileRotation.NORTH,
            Position(1,0)
        )
        val result: () -> Unit = { gameManager.placeTile(gameId, tile, "Player2") } // Wrong turn

        assertFailsWith<IllegalStateException>("Not player's turn", block = result)
        assertFalse(game.board.containsKey(Position(1,0)), "Tile should not be placed")
    }

    @Test
    fun `should reject tile placement if invalid position`() {
        val gameId = "invalid-position"
        val game = gameManager.createGameWithHost(gameId, "Player1")
        game.addPlayer("Player2")
        game.startGame()

        game.status = GamePhase.TILE_PLACEMENT // ✅ Required for testing position

        val tile = Tile(
            id = "invalid-road",
            terrainNorth = TerrainType.ROAD,
            terrainEast = TerrainType.ROAD,
            terrainSouth = TerrainType.ROAD,
            terrainWest = TerrainType.ROAD,
            terrainCenter = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH,
            position = Position(5, 5)
        )

        val result: () -> Unit = { gameManager.placeTile(gameId, tile, "Player1") }

        val exception = assertFailsWith<IllegalArgumentException> { result() }
        assertEquals("You can't place the tile here!", exception.message)
        assertFalse(game.board.containsKey(Position(5, 5)))
    }

    @Test
    fun `should place meeple on valid city feature`() {
        val gameId = "meeple-test"
        val game = gameManager.createGameWithHost(gameId, "Player1")
        game.addPlayer("Player2")
        game.startGame()

        val tile = Tile(
            id = "tile1",
            terrainNorth = TerrainType.CITY, terrainEast = TerrainType.CITY,
            terrainSouth = TerrainType.CITY, terrainWest = TerrainType.ROAD,
            tileRotation = TileRotation.NORTH,
            terrainCenter = TerrainType.FIELD,
            position = Position(0, 0)
        )
        val tile2 = Tile(
            id = "tile2",
            terrainNorth = TerrainType.CITY, terrainEast = TerrainType.CITY,
            terrainSouth = TerrainType.CITY, terrainWest = TerrainType.ROAD,
            tileRotation = TileRotation.NORTH,
            terrainCenter = TerrainType.FIELD,
            position = Position(0, 1) // Direkt nördlich von tile1
        )
        gameManager.placeTile(gameId, tile, "Player1")
        game.nextPlayer()
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId, tile2, "Player2")

        val meeple = Meeple(id = "meeple1", playerId = "Player2", tileId = tile2.id)

        val updatedGameState = gameManager.placeMeeple(gameId, "Player2", meeple, MeeplePosition.N)

        // Sicherstellen, dass der Meeple erfolgreich platziert wurde
        assertNotNull(updatedGameState, "Meeple should be placed successfully")

        // Optional: Überprüfen, ob der Meeple tatsächlich in der GameState-Liste ist
        assertTrue(updatedGameState.meeplesOnBoard.contains(meeple))
    }

    @Test
    fun `should NOT place meeple on invalid field feature`() {
        val gameId = "meeple-negative-test"
        val game = gameManager.createGameWithHost(gameId, "Player1")
        game.addPlayer("Player2")
        game.startGame()

        val tile = Tile(
            id = "tile-invalid",
            terrainNorth = TerrainType.FIELD, terrainEast = TerrainType.CITY,
            terrainSouth = TerrainType.FIELD, terrainWest = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH,
            terrainCenter = TerrainType.FIELD,
            position = Position(0, 0)
        )
        gameManager.placeTile(gameId, tile, "Player1")

        val meeple = Meeple(id = "meeple-invalid", playerId = "Player1", tileId = tile.id)

        assertThrows<IllegalStateException>("Invalid meeple position") {
            gameManager.placeMeeple(gameId, "Player1", meeple, MeeplePosition.N)
        }
    }

    @Test
    fun `other player should NOT place a second meeple on the same feature`() {
        val gameId = "meeple-duplicate-test"
        val game = gameManager.createGameWithHost(gameId, "Player1")
        game.addPlayer("Player2")
        game.startGame()

        // Beispiel-Tile: Stadt im Osten, Feld in den anderen Bereichen
        val tile = Tile(
            id = "tile-conflict",
            terrainNorth = TerrainType.FIELD, terrainEast = TerrainType.CITY,
            terrainSouth = TerrainType.FIELD, terrainWest = TerrainType.ROAD,
            tileRotation = TileRotation.NORTH,
            terrainCenter = TerrainType.FIELD,
            position = Position(0, 0)
        )
        gameManager.placeTile(gameId, tile, "Player1")

        // Erster Meeple wird korrekt gesetzt
        val meeple1 = Meeple(id = "meeple1", playerId = "Player1", tileId = tile.id)
        val gameStateAfterFirstMeeple = gameManager.placeMeeple(gameId, "Player1", meeple1, MeeplePosition.E)

        assertNotNull(gameStateAfterFirstMeeple, "First meeple should be placed successfully")
        assertTrue(gameStateAfterFirstMeeple.meeplesOnBoard.contains(meeple1))

        // Zweiter Spieler versucht, einen Meeple auf dieselbe Position zu setzen
        val meeple2 = Meeple(id = "meeple2", playerId = "Player2", tileId = tile.id)

        assertThrows<IllegalStateException>("Meeple position already occupied") {
            gameManager.placeMeeple(gameId, "Player2", meeple2, MeeplePosition.E)
        }
    }

    @Test
    fun `should NOT allow same player to place two meeples on the same tile`() {
        val gameId = "meeple-double-test"
        val game = gameManager.createGameWithHost(gameId, "Player1")
        game.addPlayer("Player2")
        game.startGame()

        // Tile mit Stadt im Osten, Road im Westen
        val tile = Tile(
            id = "tile-double",
            terrainNorth = TerrainType.FIELD, terrainEast = TerrainType.CITY,
            terrainSouth = TerrainType.FIELD, terrainWest = TerrainType.ROAD,
            tileRotation = TileRotation.NORTH,
            terrainCenter = TerrainType.FIELD,
            position = Position(0, 0)
        )
        gameManager.placeTile(gameId, tile, "Player1")

        // Erster Meeple wird erfolgreich auf Osten (CITY) gesetzt
        val meeple1 = Meeple(id = "meeple1", playerId = "Player1", tileId = tile.id)
        val gameStateAfterFirstMeeple = gameManager.placeMeeple(gameId, "Player1", meeple1, MeeplePosition.E)

        assertNotNull(gameStateAfterFirstMeeple, "First meeple should be placed successfully")
        assertTrue(gameStateAfterFirstMeeple.meeplesOnBoard.contains(meeple1))

        // Derselbe Spieler versucht, einen zweiten Meeple auf Westen (ROAD) zu setzen
        val meeple2 = Meeple(id = "meeple2", playerId = "Player1", tileId = tile.id)

        assertThrows<IllegalStateException>("Player should NOT be allowed to place two meeples on the same tile") {
            gameManager.placeMeeple(gameId, "Player1", meeple2, MeeplePosition.W)
        }
    }

    @Test
    fun `should correctly place meeple on rotated tile`() {
        val gameId = "meeple-rotation-test"
        val game = gameManager.createGameWithHost(gameId, "Player1")
        game.addPlayer("Player2")
        game.startGame()

        // Tile mit Stadt ursprünglich im Süden, aber gedreht nach Norden
        val tileRotated = Tile(
            id = "tile-rotated",
            terrainNorth = TerrainType.FIELD, terrainEast = TerrainType.FIELD,
            terrainSouth = TerrainType.CITY, terrainWest = TerrainType.FIELD,
            tileRotation = TileRotation.SOUTH, // 🔄 Tile wird gedreht!
            terrainCenter = TerrainType.FIELD,
            position = Position(0, 0)
        )
        gameManager.placeTile(gameId, tileRotated, "Player1")

        // Statt Süden müssen wir jetzt Norden wählen, weil das ursprüngliche TerrainSouth nach Norden rotiert wurde
        val meeple = Meeple(id = "meeple-rotated", playerId = "Player1", tileId = tileRotated.id)

        val updatedGameState = gameManager.placeMeeple(gameId, "Player1", meeple, MeeplePosition.N)

        // Erwartung: Meeple sollte erfolgreich platziert werden
        assertNotNull(updatedGameState, "Meeple should be placed successfully on rotated tile")
        assertTrue(updatedGameState.meeplesOnBoard.contains(meeple))
    }

    @Test
    fun `should reduce remainingMeeples after valid first meeple placement`() {
        val gameId = "meeple-reduction-test"
        val game = gameManager.createGameWithHost(gameId, "Player1")

        // Spiel vorbereiten
        game.addPlayer("Player2")
        game.startGame()

        val player = game.players.first { it.id == "Player1" }
        assertEquals(7, player.remainingMeeple, "Initialer Meeple-Stand sollte 7 sein")

        // Platzieren eines Tiles
        val tile = Tile(
            id = "tile-start",
            terrainNorth = TerrainType.FIELD,
            terrainEast = TerrainType.ROAD,
            terrainSouth = TerrainType.CITY,
            terrainWest = TerrainType.FIELD,
            terrainCenter = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH,
            position = Position(0, 0)
        )
        gameManager.placeTile(gameId, tile, "Player1")

        // Meeple setzen
        val meeple = Meeple(id = "meeple-001", playerId = "Player1", tileId = tile.id)
        gameManager.placeMeeple(gameId, "Player1", meeple, MeeplePosition.S)

        // Überprüfen, ob der Meeple-Zähler reduziert wurde
        val updatedPlayer = game.players.first { it.id == "Player1" }
        assertEquals(6, updatedPlayer.remainingMeeple, "Meeple-Zähler sollte nach Platzierung 6 sein")
    }

    @Test
    fun `should not allow meeple placement when player has no meeples left`() {
        val gameId = "meeple-no-meeple-test"
        val game = gameManager.createGameWithHost(gameId, "Player1")

        // Füge zwei Spieler hinzu, damit das Spiel korrekt läuft
        game.addPlayer("Player2")
        game.startGame()

        // Spieler1 hat keine Meeples mehr
        val player = game.players.first { it.id == "Player1" }
        val playerWithoutMeeples = player.copy(remainingMeeple = 0)

        // Aktualisiere die Spieler-Liste im Spiel
        game.players.find { it.id == playerWithoutMeeples.id }?.let { existingPlayer ->
            val index = game.players.indexOf(existingPlayer)
            game.players[index] = playerWithoutMeeples
        }

        val tile = Tile(
            id = "tile-test",
            terrainNorth = TerrainType.CITY, terrainEast = TerrainType.FIELD,
            terrainSouth = TerrainType.ROAD, terrainWest = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH,
            terrainCenter = TerrainType.CITY,
            position = Position(0, 0)
        )
        gameManager.placeTile(gameId, tile, "Player1")

        val meeple = Meeple(id = "meeple-test", playerId = "Player1", tileId = tile.id)

        // Versuch, einen Meeple zu platzieren → sollte fehlschlagen!
        val exception = assertThrows<IllegalStateException> {
            gameManager.placeMeeple(gameId, "Player1", meeple, MeeplePosition.N)
        }
        assertEquals("You don't have any meeples left to place!", exception.message)
    }

    @Test
    fun `should not allow meeple placement on ROAD if another meeple is on connected road segment`() {
        val gameId = "road-meeple-test"
        val game = gameManager.createGameWithHost(gameId, "Player1")

        // Zwei Spieler hinzufügen
        game.addPlayer("Player2")
        game.startGame()

        // Erstes Tile mit Straße, auf das bereits ein Meeple gesetzt wird
        val tileWithMeeple = Tile(
            id = "tile-road-1",
            terrainNorth = TerrainType.FIELD, terrainEast = TerrainType.ROAD,
            terrainSouth = TerrainType.FIELD, terrainWest = TerrainType.ROAD,
            tileRotation = TileRotation.NORTH,
            terrainCenter = TerrainType.ROAD,
            position = Position(0, 1)
        )
        gameManager.placeTile(gameId, tileWithMeeple, "Player1")

        val existingMeeple = Meeple(id = "meeple-existing", playerId = "Player1", tileId = tileWithMeeple.id, position = MeeplePosition.W)
        game.meeplesOnBoard.add(existingMeeple) // Simuliere bereits gesetzten Meeple

        // Zweites Tile mit einer angrenzenden Straße
        val adjacentTile = Tile(
            id = "tile-road-2",
            terrainNorth = TerrainType.FIELD, terrainEast = TerrainType.ROAD,
            terrainSouth = TerrainType.FIELD, terrainWest = TerrainType.ROAD,
            tileRotation = TileRotation.NORTH,
            terrainCenter = TerrainType.FIELD,
            position = Position(1, 1) // Direkt rechts daneben
        )
        game.nextPlayer()
        game.status = GamePhase.TILE_PLACEMENT
        gameManager.placeTile(gameId, adjacentTile, "Player2")

        // Spieler2 versucht jetzt, einen Meeple auf die Straße zu setzen → sollte blockiert werden!
        val newMeeple = Meeple(id = "meeple-new", playerId = "Player2", tileId = adjacentTile.id)

        val exception = assertThrows<IllegalStateException> {
            gameManager.placeMeeple(gameId, "Player2", newMeeple, MeeplePosition.W)
        }
        assertEquals("Road or city is already occupied!", exception.message)
    }

    @Test
    fun `should correctly place meeple on monastery and reject placement on non-monastery tile`() {
        val gameId = "meeple-monastery-test"
        val game = gameManager.createGameWithHost(gameId, "Player1")

        game.addPlayer("Player2")
        game.startGame()

        // Tile mit Kloster
        val monasteryTile = Tile(
            id = "tile-monastery",
            terrainNorth = TerrainType.FIELD, terrainEast = TerrainType.FIELD,
            terrainSouth = TerrainType.FIELD, terrainWest = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH,
            terrainCenter = TerrainType.MONASTERY,
            position = Position(0, 0),
            hasMonastery = true
        )

        gameManager.placeTile(gameId, monasteryTile, "Player1")

        // Fall 1: Valid Meeple Placement auf Kloster
        val meepleValid = Meeple(id = "meeple-valid", playerId = "Player1", tileId = monasteryTile.id)
        val updatedGameState = gameManager.placeMeeple(gameId, "Player1", meepleValid, MeeplePosition.C)

        assertNotNull(updatedGameState, "Meeple should be placed successfully")
        assertTrue(updatedGameState.meeplesOnBoard.contains(meepleValid))

        game.nextPlayer()
        game.status = GamePhase.TILE_PLACEMENT

        // Tile ohne Kloster
        val normalTile = Tile(
            id = "tile-no-monastery",
            terrainNorth = TerrainType.CITY, terrainEast = TerrainType.FIELD,
            terrainSouth = TerrainType.FIELD, terrainWest = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH,
            terrainCenter = TerrainType.FIELD,
            position = Position(1, 0),
            hasMonastery = false
        )
        gameManager.placeTile(gameId, normalTile, "Player2")

        // Fall 2: Invalid Meeple Placement auf Nicht-Kloster
        val meepleInvalid = Meeple(id = "meeple-invalid", playerId = "Player2", tileId = normalTile.id)

        assertThrows<IllegalStateException>("Invalid meeple position") {
            gameManager.placeMeeple(gameId, "Player2", meepleInvalid, MeeplePosition.C)
        }
    }

    @Test
    fun `score completed city with meeples`() {
        val gameId = "scoring-test-city"
        val game = gameManager.createGameWithHost(gameId, "Player1")

        game.addPlayer("Player2")
        game.startGame()

        game.status = GamePhase.TILE_PLACEMENT

        val cityTile = Tile(
            id = "city-1",
            terrainNorth = TerrainType.CITY,
            terrainEast = TerrainType.FIELD,
            terrainSouth = TerrainType.FIELD,
            terrainWest = TerrainType.ROAD,
            terrainCenter = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH,
            position = Position(1, 0)
        )
        val cityTile2 = Tile(
            id = "city-2",
            terrainNorth = TerrainType.FIELD,
            terrainEast = TerrainType.FIELD,
            terrainSouth = TerrainType.CITY,
            terrainWest = TerrainType.FIELD,
            terrainCenter = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH,
            position = Position(1, -1)
        )
        // Spiel initialisieren
        // Tile und Meeple platzieren
        gameManager.placeTile(gameId, cityTile, "Player1")
        gameManager.placeMeeple(
            gameId,
            "Player1",
            Meeple(id = "m1", playerId = "Player1", tileId = "city-1"),
            MeeplePosition.N
        )
        game.nextPlayer()
        game.status = GamePhase.TILE_PLACEMENT
        //Runde 2
        gameManager.placeTile(gameId,cityTile2,"Player2")
        //Meeple Placement künstlich skippen
        game.status = GamePhase.SCORING
        gameManager.calculateScore(gameId,cityTile2)
        // Assert
        assertEquals(4, game.players[0].score) // 1 Tile × 2 Punkte
        assertTrue(game.meeplesOnBoard.none { it.id == "m1" })
    }

    @Test
    fun `do not score incomplete road`() {
        val gameId = "scoring-test-road"
        val game = gameManager.createGameWithHost(gameId, "Player1")

        game.addPlayer("Player2")
        game.startGame()

        game.status = GamePhase.TILE_PLACEMENT

        val roadTile = Tile(
            id = "road-1",
            terrainEast = TerrainType.ROAD,
            terrainWest = TerrainType.FIELD,
            terrainNorth = TerrainType.FIELD,
            terrainSouth = TerrainType.FIELD,
            terrainCenter = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH,
            position = Position(0, 0)
        )
        // Tile & Meeple platzieren
        gameManager.placeTile(gameId, roadTile, "Player1")
        gameManager.placeMeeple(
            gameId,
            "Player1",
            Meeple(id = "m1", playerId = "Player1", tileId = "road-1"),
            MeeplePosition.E
        )

        // Scoring direkt auslösen
        game.status = GamePhase.SCORING
        gameManager.calculateScore(gameId, roadTile)
        //Straße sollte nicht gewertet werden - da nicht vollständig
        assertEquals(0, game.players[0].score)
        assertTrue(game.meeplesOnBoard.any { it.id == "m1" })
    }
    @Test
    fun `score completed monastery but place monestary not at last`() {
        val gameId = "scoring-test-monastery-different-order"
        val game = gameManager.createGameWithHost(gameId, "Player1")

        game.addPlayer("Player2")
        game.startGame()
        game.status = GamePhase.TILE_PLACEMENT

        // 1. Start-Tile bei (0,0) bereits platziert mit (CITY, ROAD, FIELD, ROAD)

        gameManager.placeTile(gameId,
            Tile(
                id = "start-tile",
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(0, 1)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId,
            Tile(
                id = "tile-east",
                terrainWest = TerrainType.FIELD, // Verbindung zu start-tile
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(1, 1)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId,
            Tile(
                id = "tile-north",
                terrainSouth = TerrainType.FIELD, // Verbindung zu tile-east
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(2, 1)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId,
            Tile(
                id = "tile-west",
                terrainEast = TerrainType.FIELD, // Verbindung zu tile-north
                terrainNorth = TerrainType.FIELD,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(0, 2)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT

        // Kloster-Tile in der Mitte (1,2)
        val monasteryTile = Tile(
            id = "monastery-tile",
            hasMonastery = true,
            terrainNorth = TerrainType.FIELD, // Verbindung zu tile-north-2
            terrainEast = TerrainType.FIELD, // Verbindung zu tile-east-2
            terrainSouth = TerrainType.FIELD, // Verbindung zu tile-east
            terrainWest = TerrainType.FIELD, // Verbindung zu tile-west
            terrainCenter = TerrainType.MONASTERY,
            tileRotation = TileRotation.NORTH,
            position = Position(1, 2)
        )
        gameManager.placeTile(gameId, monasteryTile, "Player1")

        //  Meeple platzieren
        gameManager.placeMeeple(
            gameId,
            "Player1",
            Meeple(id = "m1", playerId = "Player1", tileId = "monastery-tile"),
            MeeplePosition.C
        )
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId,
            Tile(
                id = "tile-east-2",
                terrainWest = TerrainType.FIELD, // Verbindung zu tile-north
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(2, 2)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId,
            Tile(
                id = "tile-north-2",
                terrainSouth = TerrainType.FIELD, // Verbindung zu tile-north
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(0, 3)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId,
            Tile(
                id = "tile-north-3",
                terrainSouth = TerrainType.FIELD, // Verbindung zu tile-north
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(1, 3)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT
        val lastTile = Tile(
            id = "tile-south-west",
            terrainSouth = TerrainType.FIELD, // Verbindung zu tile-north
            terrainNorth = TerrainType.FIELD,
            terrainEast = TerrainType.FIELD,
            terrainWest = TerrainType.FIELD,
            terrainCenter = TerrainType.MONASTERY,
            tileRotation = TileRotation.NORTH,
            position = Position(2, 3)
        )
        gameManager.placeTile(gameId, lastTile, "Player1")


        // Scoring
        game.status = GamePhase.SCORING
        gameManager.calculateScore(gameId, lastTile)

        // Assert
        assertEquals(9, game.players[0].score) // 8 Nachbarn + Kloster selbst
        assertTrue(game.meeplesOnBoard.none { it.id == "m1" })
    }
    @Test
    fun `score completed monastery`() {
        val gameId = "scoring-test-monastery"
        val game = gameManager.createGameWithHost(gameId, "Player1")

        game.addPlayer("Player2")
        game.startGame()
        game.status = GamePhase.TILE_PLACEMENT

        // 1. Start-Tile bei (0,0) bereits platziert mit (CITY, ROAD, FIELD, ROAD)

        gameManager.placeTile(gameId,
            Tile(
                id = "start-tile",
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(0, 1)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId,
            Tile(
                id = "tile-east",
                terrainWest = TerrainType.FIELD, // Verbindung zu start-tile
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(1, 1)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId,
            Tile(
                id = "tile-north",
                terrainSouth = TerrainType.FIELD, // Verbindung zu tile-east
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(2, 1)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId,
            Tile(
                id = "tile-west",
                terrainEast = TerrainType.FIELD, // Verbindung zu tile-north
                terrainNorth = TerrainType.FIELD,
                terrainSouth = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(0, 2)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId,
            Tile(
                id = "tile-east-2",
                terrainWest = TerrainType.FIELD, // Verbindung zu tile-north
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainSouth = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(2, 2)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId,
            Tile(
                id = "tile-north-2",
                terrainSouth = TerrainType.FIELD, // Verbindung zu tile-north
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(0, 3)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId,
            Tile(
                id = "tile-north-3",
                terrainSouth = TerrainType.FIELD, // Verbindung zu tile-north
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(1, 3)
            ),
            "Player1"
        )
        game.status = GamePhase.TILE_PLACEMENT

        gameManager.placeTile(gameId,
            Tile(
                id = "tile-south-west",
                terrainSouth = TerrainType.FIELD, // Verbindung zu tile-north
                terrainNorth = TerrainType.FIELD,
                terrainEast = TerrainType.FIELD,
                terrainWest = TerrainType.FIELD,
                terrainCenter = TerrainType.MONASTERY,
                tileRotation = TileRotation.NORTH,
                position = Position(2, 3)
            ),
            "Player1")

        game.status = GamePhase.TILE_PLACEMENT

        // 7. Kloster-Tile in der Mitte (1,2)
        val monasteryTile = Tile(
            id = "monastery-tile",
            hasMonastery = true,
            terrainNorth = TerrainType.FIELD, // Verbindung zu tile-north-2
            terrainEast = TerrainType.FIELD, // Verbindung zu tile-east-2
            terrainSouth = TerrainType.FIELD, // Verbindung zu tile-east
            terrainWest = TerrainType.FIELD, // Verbindung zu tile-west
            terrainCenter = TerrainType.MONASTERY,
            tileRotation = TileRotation.NORTH,
            position = Position(1, 2)
        )
        gameManager.placeTile(gameId, monasteryTile, "Player1")

        // 8. Meeple platzieren
        gameManager.placeMeeple(
            gameId,
            "Player1",
            Meeple(id = "m1", playerId = "Player1", tileId = "monastery-tile"),
            MeeplePosition.C
        )

        // 9. Scoring
        game.status = GamePhase.SCORING
        gameManager.calculateScore(gameId, monasteryTile)

        // Assert
        assertEquals(9, game.players[0].score) // 8 Nachbarn + Kloster selbst
        assertTrue(game.meeplesOnBoard.none { it.id == "m1" })
       }

    @Test
    fun `throw IllegalStateException when scoring in wrong phase`() {
        val gameId = "scoring-exception-test"
        val game = gameManager.createGameWithHost(gameId, "Player1")

        // Spieler hinzufügen und Spiel starten
        game.addPlayer("Player2")
        game.startGame()

        // Tile in Phase TILE_PLACEMENT platzieren
        val tile = Tile(
            id = "test-tile",
            terrainNorth = TerrainType.FIELD,
            terrainEast = TerrainType.FIELD,
            terrainSouth = TerrainType.FIELD,
            terrainWest = TerrainType.FIELD,
            terrainCenter = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH,
            position = Position(0, 0)
        )
        gameManager.placeTile(gameId, tile, "Player1")

        // Phase explizit auf Tile-Placement belassen um Exception zu provozieren
        game.status = GamePhase.TILE_PLACEMENT

        // Act & Assert: Exception prüfen
        val exception = assertThrows<IllegalStateException> {
            gameManager.calculateScore(gameId, tile)
        }
        //Richtige Exception Nachricht muss kocmmen
        assertEquals("Game is not in scoring phase", exception.message)
    }

    @Test
    fun `endGame returns winner with highest score`() {
        val gameId = "endgame-test"
        val game = gameManager.createGameWithHost(gameId, "host")

        game.players.add(Player(id = "Player1", user_id = 1, score = 10, remainingMeeple = 5))
        game.players.add(Player(id = "Player2", user_id = 2, score = 15, remainingMeeple = 5))
        game.status = GamePhase.FINISHED
        val winner = gameManager.endGame(gameId)

        assertEquals("Player2", winner, "Expected Player2 to be the winner with highest score")
    }

    @Test
    fun `endGame throws exception if game is not finished`() {
        val gameId = "not-finished"
        val game = gameManager.createGameWithHost(gameId, "host")
        game.players.add(Player(id = "Player1", user_id = 1, score = 5, remainingMeeple = 5))
        game.status = GamePhase.TILE_PLACEMENT

        val exception = assertFailsWith<IllegalStateException> {
            gameManager.endGame(gameId)
        }
        assertEquals("Game is not in FINISHED phase", exception.message)
    }

    @Test
    fun `endGame throws exception if game has no players`() {
        val gameId = "no-players"
        val game = gameManager.createGameWithHost(gameId, "host")
        game.players.remove(game.findPlayerById("host"))
        game.status = GamePhase.FINISHED

        val exception = assertFailsWith<IllegalStateException> {
            gameManager.endGame(gameId)
        }
        assertEquals("No players to determine winner", exception.message)
    }
    @Test
    fun `tile count per type matches definition in base tiles`() {
        val baseTiles = gameManager.getUniqueTiles()
        val fullDeck = gameManager.createShuffledTileDeck(seed = 777L)

        // Extract base ID by removing the unique suffix (e.g., "tile-a-0" -> "tile-a")
        val grouped = fullDeck.groupingBy { it.id.substringBeforeLast("-") }.eachCount()

        baseTiles.forEach { baseTile ->
            val expected = baseTile.count
            val actual = grouped[baseTile.id] ?: 0
            assertEquals(expected, actual, "Mismatch in tile count for tile ${baseTile.id}")
        }
    }
    @Test
    fun `drawTile reshuffles discarded tiles if deck is empty`() {
        val gameId = "reshuffle-test"
        val game = gameManager.createGameWithHost(gameId, "host")

        // Place a starting tile at (0, 0)
        game.board[Position(0, 0)] = Tile(
            id = "start-tile",
            terrainNorth = TerrainType.ROAD,
            terrainEast = TerrainType.FIELD,
            terrainSouth = TerrainType.FIELD,
            terrainWest = TerrainType.FIELD,
            terrainCenter = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH,
            position = Position(0, 0)
        )

        // This tile is compatible to be placed east of (0, 0) (FIELD-to-FIELD)
        val tile = Tile(
            id = "discarded-tile-1",
            terrainNorth = TerrainType.FIELD,
            terrainEast = TerrainType.FIELD,
            terrainSouth = TerrainType.FIELD,
            terrainWest = TerrainType.FIELD,
            terrainCenter = TerrainType.FIELD,
            tileRotation = TileRotation.NORTH
        )

        // Simulate empty deck and discarded pile
        game.tileDeck.clear()
        game.discardedTiles.clear()
        game.discardedTiles.add(tile)

        val drawnTile = gameManager.drawTileForPlayer(gameId)

        assertNotNull(drawnTile, "Expected a playable tile to be drawn from reshuffled discarded tiles")
        assertEquals(tile.id, drawnTile.id, "Expected the reshuffled tile to match the discarded one")
    }

}