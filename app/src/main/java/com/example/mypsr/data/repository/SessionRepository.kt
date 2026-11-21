package com.example.mypsr.data.repository

import com.example.mypsr.data.local.entity.SessionEntity
import com.example.mypsr.data.local.entity.SessionWithSteps
import com.example.mypsr.model.CapturedStep
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun getAllSessions(): Flow<List<SessionEntity>>
    suspend fun getSessionById(sessionId: Long): SessionEntity?
    fun getSessionWithSteps(sessionId: Long): Flow<SessionWithSteps?>
    suspend fun createSession(title: String): Long
    suspend fun updateSession(session: SessionEntity)
    suspend fun deleteSession(sessionId: Long)

    suspend fun insertStep(sessionId: Long, step: CapturedStep): Long
    suspend fun insertSteps(sessionId: Long, steps: List<CapturedStep>): List<Long>
    suspend fun updateStep(step: CapturedStep)
    suspend fun deleteStep(stepId: Long)
    fun getStepsForSession(sessionId: Long): Flow<List<CapturedStep>>
    suspend fun saveScreenshot(sessionId: Long, stepNumber: Int, jpegBytes: ByteArray): String?
}
