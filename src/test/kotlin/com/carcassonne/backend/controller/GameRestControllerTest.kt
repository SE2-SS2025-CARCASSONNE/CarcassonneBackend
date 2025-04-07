package com.carcassonne.backend.controller

import com.carcassonne.backend.model.GameState
import com.carcassonne.backend.service.GameManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.ResponseEntity

class GameRestControllerTest {

    private lateinit var gameManager: GameManager
    private lateinit var gameRestController: GameRestController

    @BeforeEach
    fun setUp() {
        //Mock GameManager dependency to avoid using real service
        gameManager = mock(GameManager::class.java)
        //Pass mocked dependency to GameRestController
        gameRestController = GameRestController(gameManager)
    }

    @Test
    //Server should return GameState corresponding to gameId
    fun getGameTest() {
        val exGameId = "A5KF01"
        val gameState = GameState(gameId = exGameId)

        `when`(gameManager.getOrCreateGame(exGameId)).thenReturn(gameState)
        val response: ResponseEntity<GameState> = gameRestController.getGame(exGameId)

        assertEquals(200, response.statusCode.value())
        assertEquals(gameState, response.body)
        verify(gameManager, times(1)).getOrCreateGame(exGameId)
    }

}