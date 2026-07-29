package com.badukai.next.data.game

import kotlinx.coroutines.flow.Flow

class GameRepository(private val dao: GameDao) {
    fun observeAllGames(): Flow<List<GameEntity>> = dao.observeAll()
    suspend fun getGame(id: Long): GameEntity? = dao.getById(id)
    suspend fun saveGame(game: GameEntity): Long = dao.insert(game)
    suspend fun deleteGame(id: Long) = dao.deleteById(id)
    suspend fun getGameCount(): Int = dao.count()
}
