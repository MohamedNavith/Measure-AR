package com.example.measurear.data

import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeasurementRepository @Inject constructor(
    private val dao: MeasurementDao
) {
    val allMeasurements: Flow<List<MeasurementEntity>> = dao.observeAllMeasurements()

    suspend fun saveMeasurement(entity: MeasurementEntity): Long {
        return dao.insertMeasurement(entity)
    }

    suspend fun deleteMeasurement(entity: MeasurementEntity) {
        entity.thumbnailPath?.let { path ->
            runCatching { File(path).delete() }
        }
        dao.deleteMeasurement(entity)
    }
}
