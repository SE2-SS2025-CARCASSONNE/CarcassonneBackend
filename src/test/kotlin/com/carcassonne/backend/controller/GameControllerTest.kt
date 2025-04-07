package com.carcassonne.backend.controller

import com.carcassonne.backend.entity.Game
import com.carcassonne.backend.repository.GameRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class GameControllerTest {

    private lateinit var gameRepository: GameRepository
    private lateinit var gameController: GameController

    @BeforeEach
    fun setUp() {
        //Mock GameRepository dependency to avoid using real DB
        gameRepository = mock(GameRepository::class.java)
        //Pass mocked dependency to GameController
        gameController = GameController(gameRepository)
    }

    @Test
    //Server should return "pong" when pinged
    fun pingServerTest() {
        val result = gameController.ping()
        assertEquals("pong", result)
    }

    @Test
    //Server should return gameId and save game in repository
    fun createGameTest() {
        val request = GameController.CreateGameRequest(playerCount = 3)
        val response = gameController.createGame(request)

        assertEquals(6, response.gameId.length)
        verify(gameRepository, times(1)).save(any(Game::class.java))
    }

}