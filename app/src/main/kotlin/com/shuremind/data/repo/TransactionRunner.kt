package com.shuremind.data.repo

import androidx.room.withTransaction
import com.shuremind.data.AppDatabase

/**
 * Thin seam around [androidx.room.withTransaction] so Room-transactional repositories (like
 * [ImportRepository]) can be exercised in JVM unit tests against fake DAOs, without a real
 * [AppDatabase] instance. Prod wiring uses [RoomTransactionRunner]; tests use a no-op passthrough.
 */
interface TransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

internal class RoomTransactionRunner(private val database: AppDatabase) : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = database.withTransaction { block() }
}
