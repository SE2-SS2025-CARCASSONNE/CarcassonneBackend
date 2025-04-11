package com.carcassonne.backend.model

data class GameState(
    val gameId: String,
    val players: MutableList<String> = mutableListOf(),
    val board: MutableMap<Position, Tile> = mutableMapOf(),
    var currentPlayerIndex: Int = 0,
    var status: GamePhase = GamePhase.WAITING,
    val tileDeck: MutableList<Tile> = mutableListOf() // The tile deck can be managed here.
) {
    // Switch to the next player
    fun nextPlayer(): String {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        return players[currentPlayerIndex]
    }

    // Get the current player
    fun getCurrentPlayer(): String = players[currentPlayerIndex]

    // Start the game (change status to IN_PROGRESS)
    fun startGame() {
        if (players.size >= 2) {
            status = GamePhase.TILE_PLACEMENT
        } else {
            throw IllegalStateException("At least 2 players are needed to start the game")
        }
    }

    // Finish the game (change status to FINISHED)
    fun finishGame() {
        status = GamePhase.FINISHED
    }

    // Add a player to the game
    fun addPlayer(player: String) {
        if (status == GamePhase.WAITING && players.size < 4) {
            players.add(player)
        } else {
            throw IllegalStateException("Game already started or max players reached")
        }
    }

    // Place a tile on the board
    fun placeTile(tile: Tile, position: Position) {
        if (status != GamePhase.TILE_PLACEMENT) {
            throw IllegalStateException("Game is not in tile placement phase")
        }
        board[position] = tile
    }

    // Shuffle and add tiles to the deck (for a random start)
    fun shuffleTiles() {
        tileDeck.shuffle()
    }

    // Draw a tile from the deck
    fun drawTile(): Tile? {
        return if (tileDeck.isNotEmpty()) tileDeck.removeAt(0) else null
    }

    // Calculate score for a player (example)
    fun calculateScore(player: String): Int {
        // Implement scoring logic here !!!
        return 0
    }
}

enum class GamePhase {
    WAITING,
    TILE_PLACEMENT,
    MEEPLE_PLACEMENT,
    SCORING,
    FINISHED
}
