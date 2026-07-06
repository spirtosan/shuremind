package com.shuremind.engine

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    ACTIVE,
    DONE,
    ARCHIVED
}
