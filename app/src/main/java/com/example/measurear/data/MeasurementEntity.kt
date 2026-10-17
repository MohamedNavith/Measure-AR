package com.example.measurear.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val modeName: String,
    val primaryMeters: Float,
    val areaSquareMeters: Float?,
    val formattedValue: String,
    val pointsSummary: String,
    val pointCount: Int,
    val thumbnailPath: String?
)
