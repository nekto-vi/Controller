package com.example.ev.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WeatherCacheDao {

    @Query("SELECT * FROM weather_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getByKey(key: String): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WeatherCacheEntity)
}
