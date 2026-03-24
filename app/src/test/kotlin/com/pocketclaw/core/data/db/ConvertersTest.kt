package com.pocketclaw.core.data.db

import com.pocketclaw.core.data.db.entity.TaskStatus
import com.pocketclaw.core.data.db.entity.TaskType
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun taskTypeToString_returnsEnumName() {
        TaskType.entries.forEach { type ->
            assertEquals(type.name, converters.taskTypeToString(type))
        }
    }

    @Test
    fun stringToTaskType_roundTripsAllValues() {
        TaskType.entries.forEach { type ->
            assertEquals(type, converters.stringToTaskType(type.name))
        }
    }

    @Test
    fun taskStatusToString_returnsEnumName() {
        TaskStatus.entries.forEach { status ->
            assertEquals(status.name, converters.taskStatusToString(status))
        }
    }

    @Test
    fun stringToTaskStatus_roundTripsAllValues() {
        TaskStatus.entries.forEach { status ->
            assertEquals(status, converters.stringToTaskStatus(status.name))
        }
    }
}
