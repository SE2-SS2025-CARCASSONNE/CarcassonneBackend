package com.carcassonne.backend.service

import com.carcassonne.backend.model.GameState
import com.carcassonne.backend.model.TerrainType
import com.carcassonne.backend.model.Tile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class GameManagerTest {
    private val gameManager = GameManager()

    @Test
    fun `should create the correct number of tiles`() {
        val seed = 1L
        val deck = gameManager.createShuffledTileDeck(seed)

        val expectedTileCount = TerrainType.entries.size * 5
        assertEquals(expectedTileCount, deck.size, "Deck should contain $expectedTileCount tiles")
    }

    @Test
    fun `should generate the same deck with same seed`() {
        val seed = 42L
        val deck1 = gameManager.createShuffledTileDeck(seed)
        val deck2 = gameManager.createShuffledTileDeck(seed)

        assertEquals(deck1, deck2, "Decks generated with the same seed should be equal")
    }

    @Test
    fun `should generate different decks with different seeds`() {
        val deck1 = gameManager.createShuffledTileDeck(seed = 100L)
        val deck2 = gameManager.createShuffledTileDeck(seed = 200L)

        assertNotEquals(deck1, deck2, "Decks generated with different seeds should not be equal")
    }

    @Test
    fun `should contain only valid terrain types`() {
        val seed = 5L
        val deck = gameManager.createShuffledTileDeck(seed)

        deck.forEach { tile ->
            assertTrue(TerrainType.entries.toTypedArray().contains(tile.terrainNorth), "Invalid terrain on NORTH side")
            assertTrue(TerrainType.entries.toTypedArray().contains(tile.terrainEast), "Invalid terrain on EAST side")
            assertTrue(TerrainType.entries.toTypedArray().contains(tile.terrainSouth), "Invalid terrain on SOUTH side")
            assertTrue(TerrainType.entries.toTypedArray().contains(tile.terrainWest), "Invalid terrain on WEST side")
        }
    }
}