package com.socialcommentcollector.app.data

import androidx.room.TypeConverter
import com.socialcommentcollector.app.model.CollectionStatus
import com.socialcommentcollector.app.model.Platform

class DatabaseConverters {
    @TypeConverter
    fun fromStatus(value: CollectionStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): CollectionStatus = CollectionStatus.valueOf(value)

    @TypeConverter
    fun fromPlatform(value: Platform): String = value.name

    @TypeConverter
    fun toPlatform(value: String): Platform = Platform.valueOf(value)
}
