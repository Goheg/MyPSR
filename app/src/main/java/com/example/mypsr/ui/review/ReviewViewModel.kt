package com.example.mypsr.ui.review

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypsr.data.local.AppDatabase
import com.example.mypsr.data.local.entity.SessionEntity
import com.example.mypsr.data.repository.SessionRepository
import com.example.mypsr.data.repository.SessionRepositoryImpl
import com.example.mypsr.generator.HtmlReportGenerator
import com.example.mypsr.model.CapturedStep
import com.example.mypsr.model.RedactionBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SessionRepository

    private val _session = MutableStateFlow<SessionEntity?>(null)
    val session: StateFlow<SessionEntity?> = _session.asStateFlow()

    private val _steps = MutableStateFlow<List<CapturedStep>>(emptyList())
    val steps: StateFlow<List<CapturedStep>> = _steps.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    init {
        val db = AppDatabase.getInstance(application)
        repository = SessionRepositoryImpl(application, db.sessionDao(), db.stepDao())
    }

    fun loadSessionData(sessionId: Long) {
        viewModelScope.launch {
            _session.value = repository.getSessionById(sessionId)
            repository.getStepsForSession(sessionId).collect { stepList ->
                _steps.value = stepList
            }
        }
    }

    fun updateStepDescription(stepId: Long, newDescription: String) {
        val currentSteps = _steps.value.toMutableList()
        val index = currentSteps.indexOfFirst { it.id == stepId }
        if (index != -1) {
            val updatedStep = currentSteps[index].copy(actionDescription = newDescription)
            currentSteps[index] = updatedStep
            _steps.value = currentSteps
            viewModelScope.launch {
                repository.updateStep(updatedStep)
            }
        }
    }

    fun deleteStep(stepId: Long) {
        val currentSteps = _steps.value.filter { it.id != stepId }
        _steps.value = currentSteps
        viewModelScope.launch {
            repository.deleteStep(stepId)
        }
    }

    fun addRedactionBoxToStep(stepId: Long, redactionBox: RedactionBox) {
        val currentSteps = _steps.value.toMutableList()
        val index = currentSteps.indexOfFirst { it.id == stepId }
        if (index != -1) {
            val step = currentSteps[index]
            val updatedBoxes = step.redactionBoxes.toMutableList().apply { add(redactionBox) }
            val updatedStep = step.copy(redactionBoxes = updatedBoxes)
            currentSteps[index] = updatedStep
            _steps.value = currentSteps
            viewModelScope.launch {
                repository.updateStep(updatedStep)
            }
        }
    }

    fun clearRedactionsForStep(stepId: Long) {
        val currentSteps = _steps.value.toMutableList()
        val index = currentSteps.indexOfFirst { it.id == stepId }
        if (index != -1) {
            val step = currentSteps[index]
            val updatedStep = step.copy(redactionBoxes = emptyList())
            currentSteps[index] = updatedStep
            _steps.value = currentSteps
            viewModelScope.launch {
                repository.updateStep(updatedStep)
            }
        }
    }

    fun generateFinalHtmlReport(context: Context, onComplete: (File) -> Unit) {
        val currentSession = _session.value ?: return
        val currentSteps = _steps.value
        if (currentSteps.isEmpty()) return

        _isGenerating.value = true
        viewModelScope.launch {
            val htmlFile = HtmlReportGenerator.generateHtmlReport(context, currentSession, currentSteps)
            val updatedSession = currentSession.copy(
                htmlReportPath = htmlFile.absolutePath,
                fileSizeBytes = htmlFile.length(),
                isExported = true
            )
            repository.updateSession(updatedSession)
            _isGenerating.value = false
            onComplete(htmlFile)
        }
    }
}
