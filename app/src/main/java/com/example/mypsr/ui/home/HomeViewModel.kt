package com.example.mypsr.ui.home

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypsr.data.local.AppDatabase
import com.example.mypsr.data.local.entity.SessionEntity
import com.example.mypsr.data.repository.SessionRepository
import com.example.mypsr.data.repository.SessionRepositoryImpl
import com.example.mypsr.generator.HtmlReportGenerator
import com.example.mypsr.service.SessionManager
import com.example.mypsr.util.ShareUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SessionRepository

    val sessions: StateFlow<List<SessionEntity>>
    val recordingState = SessionManager.recordingState
    val currentStepCount = SessionManager.stepCount

    init {
        val db = AppDatabase.getInstance(application)
        repository = SessionRepositoryImpl(application, db.sessionDao(), db.stepDao())
        sessions = repository.getAllSessions().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun startNewSession(resultCode: Int, data: Intent) {
        SessionManager.startSession(getApplication(), resultCode, data)
    }

    fun stopSession() {
        SessionManager.stopSession(getApplication())
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch {
            val session = repository.getSessionById(sessionId)
            if (session != null && newTitle.isNotBlank()) {
                val updated = session.copy(title = newTitle.trim())
                repository.updateSession(updated)

                // Re-generate HTML report if existing so the report header displays the new title
                if (!session.htmlReportPath.isNullOrBlank()) {
                    val steps = repository.getStepsForSession(sessionId).firstOrNull() ?: emptyList()
                    if (steps.isNotEmpty()) {
                        val context = getApplication<Application>()
                        HtmlReportGenerator.generateHtmlReport(context, updated, steps)
                    }
                }
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }

    fun shareSessionReport(context: Context, session: SessionEntity) {
        viewModelScope.launch {
            val reportFile = getOrGenerateReportFile(context, session)
            if (reportFile != null && reportFile.exists()) {
                ShareUtils.shareReportFile(context, reportFile)
            }
        }
    }

    suspend fun getOrGenerateReportFile(context: Context, session: SessionEntity): File? {
        if (!session.htmlReportPath.isNullOrBlank()) {
            val existingFile = File(session.htmlReportPath)
            if (existingFile.exists()) return existingFile
        }

        // Generate report if missing
        val steps = repository.getStepsForSession(session.id).firstOrNull() ?: emptyList()

        if (steps.isEmpty()) return null

        val generatedFile = HtmlReportGenerator.generateHtmlReport(context, session, steps)
        val updatedSession = session.copy(
            htmlReportPath = generatedFile.absolutePath,
            fileSizeBytes = generatedFile.length(),
            isExported = true
        )
        repository.updateSession(updatedSession)
        return generatedFile
    }
}
