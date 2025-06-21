package com.carcassonne.backend.controller

import com.carcassonne.backend.model.GameMessage
import com.carcassonne.backend.model.GameState
import com.carcassonne.backend.model.Player
import com.carcassonne.backend.repository.GameRepository
import com.carcassonne.backend.repository.UserRepository
import com.carcassonne.backend.service.GameManager
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.messaging.simp.SimpMessagingTemplate

class GameWebSocketControllerTest {

    private val mockGameManager = mock<GameManager>()
    private val mockMessagingTemplate = mock<SimpMessagingTemplate>()
    private val mockGameRepository = mock<GameRepository>()
    private val mockUserRepository = mock<UserRepository>()

    private val controller = GameWebSocketController(
        mockGameManager,
        mockMessagingTemplate,
        mockGameRepository,
        mockUserRepository
    )

    @Test
    fun `handle join_game sends player_joined message to all and privately`() {
        // Arrange
        val gameId = "testgame"
        val playerName = "Player"

        val player = Player(id = playerName, score = 0, remainingMeeple = 7, user_id = null)
        val playersList = mutableListOf(player)

        val mockGameState = mock<GameState> {
            on { players } doReturn playersList
            on { getCurrentPlayer() } doReturn playerName
        }

        whenever(mockGameManager.getGame(gameId)).thenReturn(mockGameState)

        val message = GameMessage(
            type = "join_game",
            gameId = gameId,
            player = playerName
        )

        // Act
        controller.handle(message)

        // Assert: verify broadcast to all
        argumentCaptor<Any>().apply {
            verify(mockMessagingTemplate).convertAndSend(eq("/topic/game/$gameId"), capture())

            val payload = firstValue as Map<*, *>
            assert(payload["type"] == "player_joined")
            assert(payload["currentPlayer"] == playerName)
            assert(payload["host"] == playerName)
            assert((payload["players"] as List<*>).contains(playerName))
        }

        // Assert: verify private message sent to user
        verify(mockMessagingTemplate).convertAndSendToUser(
            eq(playerName),
            eq("/queue/private"),
            check {
                val payload = it as Map<*, *>
                assert(payload["type"] == "player_joined")
                assert(payload["host"] == playerName)
            }
        )
    }
}
