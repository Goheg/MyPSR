package com.example.mypsr.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val stepCount: Int = 0,
    val htmlReportPath: String? = null,
    val fileSizeBytes: Long = 0L,
    val isExported: Boolean = false
)
