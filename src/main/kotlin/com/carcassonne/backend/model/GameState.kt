package com.carcassonne.backend.model

data class GameState(
    val gameId: String,
    val players: MutableList<Player> = mutableListOf(),
    val board: MutableMap<Position, Tile> = mutableMapOf(),
    var currentPlayerIndex: Int = 0,
    var status: GamePhase = GamePhase.WAITING,
    var discardedTiles: MutableList<Tile> = mutableListOf(),
    var tileDeck: MutableList<Tile> = mutableListOf(), // The tile deck can be managed here.
    val meeplesOnBoard: MutableList<Meeple> = mutableListOf(), // Liste der platzierten Meeples
    var tileDrawnThisTurn: Boolean = false,
    var cheatedThisTurn: Boolean = false,
    var cheaterExposed: Boolean = false,
    var currentDrawnTile: Tile? = null

) {
    // Switch to the next player
    fun nextPlayer(): String {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        return players[currentPlayerIndex].id
    }

    // Get the current player
    fun getCurrentPlayer(): String = players[currentPlayerIndex].id

    // Start the game (change status to IN_PROGRESS)
    fun startGame() {
        if (players.size >= 2) {
            status = GamePhase.TILE_PLACEMENT
        } else {
            error("At least 2 players are needed to start the game")
        }
    }

    fun findPlayerById(playerID: String): Player?{
        for(player in players)
        {
            if(player.id == playerID)
                return player

        }
        return null
    }

    // Finish the game (change status to FINISHED)
    fun finishGame() {
        status = GamePhase.FINISHED
    }

    // Add a player to the game
    fun addPlayer(player: String) {
        if (status == GamePhase.WAITING && players.size < 4) {
            players.add(Player(player,0,7,0))
        } else {
            error("Game already started or max players reached")
        }
    }

    // Place a tile on the board
    fun placeTile(tile: Tile, position: Position) {
        if (status != GamePhase.TILE_PLACEMENT) {
            throw IllegalStateException("Game is not in tile placement phase")
        }
        board[position] = tile
        status = GamePhase.MEEPLE_PLACEMENT
    }

    // Draw a tile from the deck
    fun drawTile(): Tile? {
        return if (tileDeck.isNotEmpty()) tileDeck.removeAt(0) else null
    }
}

enum class GamePhase {
    WAITING,
    TILE_PLACEMENT,
    MEEPLE_PLACEMENT,
    SCORING,
    FINISHED
}
