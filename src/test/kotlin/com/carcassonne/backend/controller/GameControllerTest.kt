package com.carcassonne.backend.controller

import com.carcassonne.backend.entity.Game
import com.carcassonne.backend.repository.GameRepository
import com.carcassonne.backend.repository.UserRepository
import com.carcassonne.backend.service.GameManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import java.time.Instant

class GameControllerTest {

    private lateinit var gameRepository: GameRepository
    private lateinit var userRepository: UserRepository
    private lateinit var gameManager: GameManager
    private lateinit var gameController: GameController
    private lateinit var userDetails: UserDetails

    @BeforeEach
    fun setUp() {
        gameRepository = mock(GameRepository::class.java)
        userRepository = mock(UserRepository::class.java)
        gameManager = mock(GameManager::class.java)

        gameController = GameController(gameRepository, userRepository, gameManager)

        userDetails = User("max", "muster123", emptyList())
    }

    @Test
    fun pingServerTest() {
        val result = gameController.ping()
        assertEquals("pong", result)
    }

    @Test
    fun createGameTest() {
        val request = GameController.CreateGameRequest(playerCount = 3)
        val response = gameController.createGame(request, userDetails)

        assertNotNull(response.gameId)
        assertEquals(6, response.gameId.length)
        verify(gameRepository, times(1)).save(any(Game::class.java))
    }

    @Test
    fun createGameCorrectSaveTest() {
        val request = GameController.CreateGameRequest(playerCount = 2)
        val captor = ArgumentCaptor.forClass(Game::class.java)

        gameController.createGame(request, userDetails)
        verify(gameRepository).save(captor.capture())

        val gameSave = captor.value
        assertEquals(6, gameSave.gameCode.length)
        assertEquals("WAITING", gameSave.status)
        assertNotNull(gameSave.createdAt)
        assertTrue(gameSave.createdAt.isBefore((Instant.now()).plusSeconds(1)))
    }
}