package com.shuremind.testutil

import com.shuremind.data.repo.TransactionRunner

/** No real Room transaction in JVM unit tests — just runs the block (fakes have no atomicity concerns). */
object NoOpTransactionRunner : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
}
