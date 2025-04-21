package com.carcassonne.backend.service

import com.carcassonne.backend.model.*
import org.junit.jupiter.api.AfterEach
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
        val game = gameManager.getOrCreateGame("test-game")
        assertEquals("test-game", game.gameId)

        val sameGame = gameManager.getOrCreateGame("test-game")
        assertSame(game, sameGame, "Should return the same instance for same gameId")
    }

    @Test
    fun `drawTileForPlayer should return tile if deck has tiles`() {
        val game = gameManager.getOrCreateGame("game-draw")
        val tile = Tile(
            "${TerrainType.CITY}_${this}",
            TerrainType.CITY, TerrainType.CITY,
            TerrainType.CITY, TerrainType.ROAD,
            TileRotation.NORTH,
            Position(0,0)
        )
        game.tileDeck.add(tile)

        val drawn = gameManager.drawTileForPlayer("game-draw")
        assertNotNull(drawn)
    }

    @Test
    fun `drawTileForPlayer should return null if deck is empty`() {
        val game = gameManager.getOrCreateGame("empty-draw")
        game.tileDeck.clear()

        val result = gameManager.drawTileForPlayer("empty-draw")
        assertNull(result)
    }

    @Test
    fun `placeTile should place tile if first tile`() {
        val gameId = "game-1"
        val game = gameManager.getOrCreateGame(gameId)
        game.players.addAll(listOf("Player1", "Player2"))
        game.startGame()

        val tile = Tile(
            "${TerrainType.CITY}_${this}",
            TerrainType.CITY, TerrainType.CITY,
            TerrainType.CITY, TerrainType.ROAD,
            TileRotation.NORTH,
            Position(0,0)
        )
        val updated = gameManager.placeTile(
            gameId, tile, "Player1"
        )

        assertNotNull(updated)
        assertEquals(tile, updated?.board?.get(Position(0,0)))
    }

    @Test
    fun `placeTile should place tile if valid`() {
        val gameId = "game-1"
        val game = gameManager.getOrCreateGame(gameId)
        game.players.addAll(listOf("Player1", "Player2"))
        game.startGame()

        val tile1 = Tile(
            "${TerrainType.CITY}_${this}",
            TerrainType.ROAD, TerrainType.CITY,
            TerrainType.CITY, TerrainType.CITY,
            TileRotation.NORTH,
            Position(0,0)
        )
        val tile2 = Tile(
            "${TerrainType.CITY}_${this}",
            TerrainType.FIELD, TerrainType.FIELD,
            TerrainType.ROAD, TerrainType.FIELD,
            TileRotation.NORTH,
            Position(0,1),
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
        assertEquals(tile1, updated?.board?.get(Position(0,0)))
        assertEquals(tile2, updated?.board?.get(Position(0,1)))
    }

    @Test
    fun `should reject tile placement if not current player`() {
        val gameId = "wrong-turn"
        val game = gameManager.getOrCreateGame(gameId)
        game.players.addAll(listOf("Player1", "Player2"))
        game.startGame()

        val tile = Tile(
            "${TerrainType.CITY}_${this}",
            TerrainType.CITY, TerrainType.ROAD,
            TerrainType.CITY, TerrainType.ROAD,
            TileRotation.NORTH,
            Position(1,0)
        )
        val result: () -> Unit = { gameManager.placeTile(gameId, tile, "Player2") } // Wrong turn

        assertFailsWith<IllegalStateException>("Not player's turn", block = result)
        assertFalse(game.board.containsKey(Position(1,0)), "Tile should not be placed")
    }

    @Test
    fun `should reject tile placement if not in tile placement phase`() {
        val gameId = "wrong-phase"
        val game = gameManager.getOrCreateGame(gameId)
        game.players.addAll(listOf("Player1", "Player2"))
        game.status = GamePhase.WAITING

        val tile = Tile(
            "${TerrainType.ROAD}_${this}",
            TerrainType.ROAD, TerrainType.ROAD,
            TerrainType.ROAD, TerrainType.ROAD,
            TileRotation.NORTH,
            Position(0,0)
        )
        val result: () -> Unit = { gameManager.placeTile(gameId, tile, "Player1") } // Wrong phase/game status

        assertFailsWith<IllegalStateException>("Game is not in tile placement phase", block = result)
        assertFalse(game.board.containsKey(Position(0,0)), "Tile should not be placed")
    }

    @Test
    fun `should reject tile placement if invalid position`() {
        val gameId = "invalid-position"
        val game = gameManager.getOrCreateGame(gameId)
        game.players.addAll(listOf("Player1", "Player2"))
        game.startGame()

        val tile = Tile(
            "${TerrainType.ROAD}_${this}",
            TerrainType.ROAD, TerrainType.ROAD,
            TerrainType.ROAD, TerrainType.ROAD,
            TileRotation.NORTH,
            Position(5,5)
        )
        val result: () -> Unit = { gameManager.placeTile(gameId, tile, "Player1") } // Wrong phase/game status

        assertFailsWith<IllegalArgumentException>("Position is invalid", block = result)
        assertFalse(game.board.containsKey(Position(5,5)), "Tile should not be placed")
    }
}