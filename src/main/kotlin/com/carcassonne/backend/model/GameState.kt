package com.carcassonne.backend.model

data class GameState(
    val gameId: String,
    val players: MutableList<String> = mutableListOf(),
    val board: MutableMap<String, Tile> = mutableMapOf(),
    var currentPlayerIndex: Int = 0,
    var status: String = "WAITING",
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
            status = "IN_PROGRESS"
        } else {
            throw IllegalStateException("At least 2 players are needed to start the game")
        }
    }

    // Finish the game (change status to FINISHED)
    fun finishGame() {
        status = "FINISHED"
    }

    // Add a player to the game
    fun addPlayer(player: String) {
        if (status == "WAITING" && players.size < 4) {
            players.add(player)
        } else {
            throw IllegalStateException("Game already started or max players reached")
        }
    }

    // Place a tile on the board
    fun placeTile(tile: Tile, position: String) {
        if (status != "IN_PROGRESS") {
            throw IllegalStateException("Game is not in progress")
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
