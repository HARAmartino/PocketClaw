package com.pocketclaw.core.data.db

import androidx.room.TypeConverter
import com.pocketclaw.core.data.db.entity.TaskStatus
import com.pocketclaw.core.data.db.entity.TaskType

/**
 * Room TypeConverters for custom enum types stored in the database.
 * Enums are persisted as their [Enum.name] string so that queries like
 * `WHERE status = 'EXECUTING'` work correctly.
 */
class Converters {

    @TypeConverter
    fun taskTypeToString(value: TaskType): String = value.name

    @TypeConverter
    fun stringToTaskType(value: String): TaskType = TaskType.valueOf(value)

    @TypeConverter
    fun taskStatusToString(value: TaskStatus): String = value.name

    @TypeConverter
    fun stringToTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)
}
