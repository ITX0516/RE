package com.badukai.next.data.game

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val boardSize: Int,
    val playerColor: String,        // BLACK / WHITE
    val komi: Float,
    val result: String,             // "B+3.5" / "W+R" / "B+T" 等
    val moveCount: Int,
    val sgf: String,                // 完整 SGF 棋谱文本
    val createdAt: Long,            // 时间戳
    val modelName: String           // HUMAN / SUPERHUMAN / GODLIKE
)
