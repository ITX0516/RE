package com.badukai.next.data.game

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: Long): GameEntity?

    @Insert
    suspend fun insert(game: GameEntity): Long

    @Update
    suspend fun update(game: GameEntity)

    @Delete
    suspend fun delete(game: GameEntity)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM games")
    suspend fun count(): Int
}
