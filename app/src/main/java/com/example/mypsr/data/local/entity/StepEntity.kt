package com.example.mypsr.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class StepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val stepNumber: Int,
    val description: String,
    val packageName: String,
    val appName: String,
    val timestamp: Long,
    val screenshotPath: String? = null,
    val clickX: Int? = null,
    val clickY: Int? = null,
    val targetBounds: String? = null,
    val isSensitive: Boolean = false,
    val redactionBoxesJson: String? = null
)
