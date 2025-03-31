package com.carcassonne.backend

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long>

@Repository
interface GameRepository : JpaRepository<Game, Long>

@Repository
interface GamePlayerRepository : JpaRepository<GamePlayer, Long>

@Repository
interface TileRepository : JpaRepository<Tile, Long>

@Repository
interface MeepleRepository : JpaRepository<Meeple, Long>

@Repository
interface MoveRepository : JpaRepository<Move, Long>
