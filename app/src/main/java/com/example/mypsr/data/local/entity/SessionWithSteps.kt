package com.example.mypsr.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class SessionWithSteps(
    @Embedded val session: SessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val steps: List<StepEntity>
)
