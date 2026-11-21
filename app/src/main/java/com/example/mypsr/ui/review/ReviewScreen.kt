package com.example.mypsr.ui.review

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mypsr.model.CapturedStep
import com.example.mypsr.model.RedactionBox
import com.example.mypsr.ui.theme.CyberCyan
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    sessionId: Long,
    viewModel: ReviewViewModel,
    onNavigateBack: () -> Unit,
    onReportGenerated: (Long) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(sessionId) {
        viewModel.loadSessionData(sessionId)
    }

    val session by viewModel.session.collectAsState()
    val steps by viewModel.steps.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = session?.title ?: "Review Steps", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            color = CyberCyan
                        )
                    } else {
                        Button(
                            onClick = {
                                viewModel.generateFinalHtmlReport(context) {
                                    onReportGenerated(sessionId)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Export Report", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        if (steps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No steps captured for this session.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(steps, key = { _, step -> step.id }) { index, step ->
                    ReviewStepCard(
                        stepNumber = index + 1,
                        step = step,
                        onDescriptionChanged = { newDesc ->
                            viewModel.updateStepDescription(step.id, newDesc)
                        },
                        onDeleteStep = {
                            viewModel.deleteStep(step.id)
                        },
                        onAddRedactionBox = { box ->
                            viewModel.addRedactionBoxToStep(step.id, box)
                        },
                        onClearRedactions = {
                            viewModel.clearRedactionsForStep(step.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewStepCard(
    stepNumber: Int,
    step: CapturedStep,
    onDescriptionChanged: (String) -> Unit,
    onDeleteStep: () -> Unit,
    onAddRedactionBox: (RedactionBox) -> Unit,
    onClearRedactions: () -> Unit
) {
    var textValue by remember(step.id) { mutableStateOf(step.actionDescription) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Step $stepNumber",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = step.appName,
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray),
                        modifier = Modifier
                            .background(Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                IconButton(onClick = onDeleteStep) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Step", tint = Color.Red)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    onDescriptionChanged(it)
                },
                label = { Text("Step Description") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Screenshot display & Touch-drag Redaction Canvas
            if (!step.screenshotPath.isNullOrBlank()) {
                val file = File(step.screenshotPath)
                if (file.exists()) {
                    val bitmap = remember(step.screenshotPath) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }

                    if (bitmap != null) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Drag on image to mask sensitive area:",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                                )
                                if (step.redactionBoxes.isNotEmpty()) {
                                    IconButton(onClick = onClearRedactions) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear Redactions", tint = Color.Red)
                                    }
                                }
                            }

                            RedactionCanvasImage(
                                bitmap = bitmap,
                                redactionBoxes = step.redactionBoxes,
                                onAddRedactionBox = onAddRedactionBox
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RedactionCanvasImage(
    bitmap: android.graphics.Bitmap,
    redactionBoxes: List<RedactionBox>,
    onAddRedactionBox: (RedactionBox) -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .onGloballyPositioned { containerSize = it.size }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStart = offset
                        dragCurrent = offset
                    },
                    onDrag = { change, _ ->
                        dragCurrent = change.position
                    },
                    onDragEnd = {
                        val start = dragStart
                        val end = dragCurrent
                        if (start != null && end != null && containerSize.width > 0 && containerSize.height > 0) {
                            val left = minOf(start.x, end.x) / containerSize.width
                            val top = minOf(start.y, end.y) / containerSize.height
                            val right = maxOf(start.x, end.x) / containerSize.width
                            val bottom = maxOf(start.y, end.y) / containerSize.height

                            if (right - left > 0.02f && bottom - top > 0.02f) {
                                onAddRedactionBox(
                                    RedactionBox(left = left, top = top, right = right, bottom = bottom)
                                )
                            }
                        }
                        dragStart = null
                        dragCurrent = null
                    }
                )
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Step Screenshot",
            modifier = Modifier.fillMaxWidth()
        )

        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            // Render existing redaction boxes
            redactionBoxes.forEach { box ->
                val rectLeft = box.left * w
                val rectTop = box.top * h
                val rectWidth = (box.right - box.left) * w
                val rectHeight = (box.bottom - box.top) * h

                drawRect(
                    color = Color.Black,
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectWidth, rectHeight)
                )
            }

            // Render active dragging rectangle
            val start = dragStart
            val end = dragCurrent
            if (start != null && end != null) {
                val rectLeft = minOf(start.x, end.x)
                val rectTop = minOf(start.y, end.y)
                val rectWidth = kotlin.math.abs(end.x - start.x)
                val rectHeight = kotlin.math.abs(end.y - start.y)

                drawRect(
                    color = Color.Red.copy(alpha = 0.5f),
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectWidth, rectHeight)
                )
            }
        }
    }
}
