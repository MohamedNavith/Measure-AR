package com.example.measurear.di

import android.content.Context
import androidx.room.Room
import com.example.measurear.ar.ArMeasureSession
import com.example.measurear.data.AppDatabase
import com.example.measurear.data.MeasurementDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "measure_ar.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideMeasurementDao(database: AppDatabase): MeasurementDao {
        return database.measurementDao()
    }

    @Provides
    @Singleton
    fun provideArMeasureSession(): ArMeasureSession {
        return ArMeasureSession()
    }
}
