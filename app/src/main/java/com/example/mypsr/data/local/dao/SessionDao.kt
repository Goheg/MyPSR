package com.example.mypsr.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.mypsr.data.local.entity.SessionEntity
import com.example.mypsr.data.local.entity.SessionWithSteps
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: SessionEntity): Long

    @Update
    fun updateSession(session: SessionEntity): Int

    @Delete
    fun deleteSession(session: SessionEntity): Int

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    fun deleteSessionById(sessionId: Long): Int

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun getSessionById(sessionId: Long): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun getSessionWithSteps(sessionId: Long): Flow<SessionWithSteps?>
}
