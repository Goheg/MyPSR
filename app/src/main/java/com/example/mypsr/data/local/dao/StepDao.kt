package com.example.mypsr.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mypsr.data.local.entity.StepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStep(step: StepEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSteps(steps: List<StepEntity>): List<Long>

    @Update
    fun updateStep(step: StepEntity): Int

    @Delete
    fun deleteStep(step: StepEntity): Int

    @Query("DELETE FROM steps WHERE id = :stepId")
    fun deleteStepById(stepId: Long): Int

    @Query("DELETE FROM steps WHERE sessionId = :sessionId")
    fun deleteStepsForSession(sessionId: Long): Int

    @Query("SELECT * FROM steps WHERE sessionId = :sessionId ORDER BY stepNumber ASC")
    fun getStepsForSession(sessionId: Long): Flow<List<StepEntity>>

    @Query("SELECT * FROM steps WHERE sessionId = :sessionId ORDER BY stepNumber ASC")
    fun getStepsListForSession(sessionId: Long): List<StepEntity>
}
