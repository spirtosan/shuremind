package com.shuremind.engine

import kotlinx.serialization.Serializable

@Serializable
enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}
