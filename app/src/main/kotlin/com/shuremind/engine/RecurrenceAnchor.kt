package com.shuremind.engine

import kotlinx.serialization.Serializable

@Serializable
enum class RecurrenceAnchor {
    CALENDAR,
    COMPLETION
}
