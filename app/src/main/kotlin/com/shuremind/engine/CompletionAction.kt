package com.shuremind.engine

import kotlinx.serialization.Serializable

@Serializable
enum class CompletionAction {
    DONE,
    SKIPPED
}
