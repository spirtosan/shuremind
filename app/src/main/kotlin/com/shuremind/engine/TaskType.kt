package com.shuremind.engine

import kotlinx.serialization.Serializable

@Serializable
enum class TaskType {
    EVENT,
    ANNIVERSARY,
    DEADLINE,
    WINDOW,
    NAG,
    RECURRING,
    CONSUMABLE,
    SOMEDAY
}
