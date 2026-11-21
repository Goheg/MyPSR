package com.example.mypsr.data.repository

import android.content.Context
import com.example.mypsr.data.local.dao.SessionDao
import com.example.mypsr.data.local.dao.StepDao
import com.example.mypsr.data.local.entity.SessionEntity
import com.example.mypsr.data.local.entity.SessionWithSteps
import com.example.mypsr.data.local.entity.StepEntity
import com.example.mypsr.model.CapturedStep
import com.example.mypsr.model.RedactionBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class SessionRepositoryImpl(
    private val context: Context,
    private val sessionDao: SessionDao,
    private val stepDao: StepDao
) : SessionRepository {

    override fun getAllSessions(): Flow<List<SessionEntity>> {
        return sessionDao.getAllSessions()
    }

    override suspend fun getSessionById(sessionId: Long): SessionEntity? {
        return withContext(Dispatchers.IO) {
            sessionDao.getSessionById(sessionId)
        }
    }

    override fun getSessionWithSteps(sessionId: Long): Flow<SessionWithSteps?> {
        return sessionDao.getSessionWithSteps(sessionId)
    }

    override suspend fun createSession(title: String): Long {
        return withContext(Dispatchers.IO) {
            val session = SessionEntity(
                title = title,
                createdAt = System.currentTimeMillis()
            )
            sessionDao.insertSession(session)
        }
    }

    override suspend fun updateSession(session: SessionEntity) {
        withContext(Dispatchers.IO) {
            sessionDao.updateSession(session)
        }
    }

    override suspend fun deleteSession(sessionId: Long) {
        withContext(Dispatchers.IO) {
            // Delete associated session directory & files
            val sessionDir = File(context.filesDir, "screenshots/session_$sessionId")
            if (sessionDir.exists()) {
                sessionDir.deleteRecursively()
            }

            // Get session entity to delete HTML report if existing
            val session = sessionDao.getSessionById(sessionId)
            session?.htmlReportPath?.let { path ->
                val htmlFile = File(path)
                if (htmlFile.exists()) {
                    htmlFile.delete()
                }
            }

            sessionDao.deleteSessionById(sessionId)
        }
    }

    override suspend fun insertStep(sessionId: Long, step: CapturedStep): Long {
        return withContext(Dispatchers.IO) {
            val entity = step.toEntity(sessionId)
            val stepId = stepDao.insertStep(entity)
            updateSessionStepCount(sessionId)
            stepId
        }
    }

    override suspend fun insertSteps(sessionId: Long, steps: List<CapturedStep>): List<Long> {
        return withContext(Dispatchers.IO) {
            val entities = steps.map { it.toEntity(sessionId) }
            val ids = stepDao.insertSteps(entities)
            updateSessionStepCount(sessionId)
            ids
        }
    }

    override suspend fun updateStep(step: CapturedStep) {
        withContext(Dispatchers.IO) {
            val entity = step.toEntity(step.sessionId)
            stepDao.updateStep(entity)
        }
    }

    override suspend fun deleteStep(stepId: Long) {
        withContext(Dispatchers.IO) {
            stepDao.deleteStepById(stepId)
        }
    }

    override fun getStepsForSession(sessionId: Long): Flow<List<CapturedStep>> {
        return stepDao.getStepsForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveScreenshot(
        sessionId: Long,
        stepNumber: Int,
        jpegBytes: ByteArray
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "screenshots/session_$sessionId")
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val file = File(dir, "step_${stepNumber}_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    out.write(jpegBytes)
                    out.flush()
                }
                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private suspend fun updateSessionStepCount(sessionId: Long) {
        val currentSession = sessionDao.getSessionById(sessionId)
        if (currentSession != null) {
            val steps = stepDao.getStepsListForSession(sessionId)
            sessionDao.updateSession(currentSession.copy(stepCount = steps.size))
        }
    }

    private fun CapturedStep.toEntity(sessionId: Long): StepEntity {
        return StepEntity(
            id = if (id > 0) id else 0,
            sessionId = sessionId,
            stepNumber = stepNumber,
            description = actionDescription,
            packageName = packageName,
            appName = appName,
            timestamp = timestamp,
            screenshotPath = screenshotPath,
            clickX = clickX,
            clickY = clickY,
            targetBounds = targetBoundsJson,
            isSensitive = isSensitive,
            redactionBoxesJson = serializeRedactionBoxes(redactionBoxes)
        )
    }

    private fun StepEntity.toDomain(): CapturedStep {
        return CapturedStep(
            id = id,
            sessionId = sessionId,
            stepNumber = stepNumber,
            actionDescription = description,
            packageName = packageName,
            appName = appName,
            timestamp = timestamp,
            screenshotPath = screenshotPath,
            clickX = clickX,
            clickY = clickY,
            targetBoundsJson = targetBounds,
            isSensitive = isSensitive,
            redactionBoxes = deserializeRedactionBoxes(redactionBoxesJson)
        )
    }

    private fun serializeRedactionBoxes(boxes: List<RedactionBox>): String? {
        if (boxes.isEmpty()) return null
        val array = JSONArray()
        boxes.forEach { box ->
            val obj = JSONObject().apply {
                put("left", box.left.toDouble())
                put("top", box.top.toDouble())
                put("right", box.right.toDouble())
                put("bottom", box.bottom.toDouble())
                put("isAutoMask", box.isAutoMask)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeRedactionBoxes(json: String?): List<RedactionBox> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<RedactionBox>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RedactionBox(
                        left = obj.getDouble("left").toFloat(),
                        top = obj.getDouble("top").toFloat(),
                        right = obj.getDouble("right").toFloat(),
                        bottom = obj.getDouble("bottom").toFloat(),
                        isAutoMask = obj.optBoolean("isAutoMask", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
