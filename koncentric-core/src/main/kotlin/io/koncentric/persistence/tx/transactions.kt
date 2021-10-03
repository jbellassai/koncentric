/*
 * Copyright 2021 Koncentric, https://koncentric.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.koncentric.persistence.tx

import io.koncentric.persistence.PersistenceException
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

/**
 * An exception indicating that there is no current active transaction
 */
class CurrentTransactionUnavailableException : PersistenceException("No current transaction is available")

/**
 * An exception indicating that the active transaction is read-only,
 * but a read-write transaction is required.
 */
class ReadWriteTransactionRequiredException : PersistenceException("A Read-Write transaction is required")

/**
 * An exception indicating that a transaction is already in progress
 */
class ConcurrentTransactionException : PersistenceException("A transaction is already in progress. Try using currentTransaction()")

/**
 * Base class for exceptions which should cause a transaction to automatically retry
 */
open class TransactionRetryException(override val cause: Throwable) : PersistenceException(cause = cause)

/**
 * Holds the current [ITransaction]
 */
internal val threadLocalTx: ThreadLocal<ITransaction<*>> = ThreadLocal()

/**
 * Utility to execute a lambda inside a read-only transaction and automatically,
 * commit or rollback (on exception) and finally release the transaction.
 *
 * @param block the suspendable lambda which receives the new [IReadOnlyTransaction]
 * @return the result [T] from the [block]
 * @throws ConcurrentTransactionException if a transaction is already in progress
 * @throws Throwable if [block] throws it
 */
suspend fun <T> ITransactionManager<*>.withReadOnlyTransaction(block: suspend (IReadOnlyTransaction<*>) -> T?): T? {
    if(threadLocalTx.get() != null) {
        throw ConcurrentTransactionException()
    }
    val tx = this.newReadOnlyTransaction()
    return try {
        withContext(threadLocalTx.asContextElement(tx)) {
            block(tx).also {
                tx.commit()
            }
        }
    } catch (t: Throwable) {
        tx.rollback()
        throw t
    } finally {
        tx.release()
    }
}

/**
 * Utility to execute a lambda inside a read-write transaction and automatically,
 * commit or rollback (on exception) and finally release the transaction.
 *
 * This function also transparently implements some number of retries if
 * [TransactionRetryException] is thrown from the [block].
 *
 * @param retries the number of retries which should be attempted. Must be `>= 0` and defaults to `5`
 * @param block the suspendable lambda which receives the new [IReadOnlyTransaction]
 * @return the result [T] from the [block]
 * @throws ConcurrentTransactionException if a transaction is already in progress
 * @throws Throwable if [block] throws it
 */
suspend fun <T> ITransactionManager<*>.withReadWriteTransaction(retries: Int = 5, block: suspend (IReadWriteTransaction<*>) -> T?): T? {
    require(retries >= 0)
    if(threadLocalTx.get() != null) {
        throw ConcurrentTransactionException()
    }
    return withTransactionRetry(retries) {
        val tx = this.newReadWriteTransaction()
        return@withTransactionRetry try {
            withContext(threadLocalTx.asContextElement(tx)) {
                block(tx).also {
                    tx.commit()
                }
            }
        } catch (t: Throwable) {
            tx.rollback()
            throw t
        } finally {
            tx.release()
        }
    }
}

/**
 * Utility to automatically retry the [block] if [TransactionRetryException]
 * is thrown from it.
 *
 * @param retries the maximum number of retries. Must be `>= 0`.
 * @param block the suspendable lambda to execute
 * @return the result [T] from the [block]
 * @throws Throwable if thrown from the [block]
 */
suspend fun <T> withTransactionRetry(retries: Int, block: suspend () -> T?): T? {
    require(retries >= 0) { "retries [$retries] must be >= 0"}
    var retriesLeft = retries
    while(true) {
        try {
            return block()
        } catch (e: TransactionRetryException) {
            if(retriesLeft == 0) throw e.cause
            else retriesLeft--
        }
    }
}
/**
 * @return the current [ITransaction]
 * @throws CurrentTransactionUnavailableException if no transaction is available
 */
@Suppress("UNCHECKED_CAST")
fun currentTransaction(): ITransaction<*> =
    (threadLocalTx.get()) ?: throw CurrentTransactionUnavailableException()

/**
 * Executes the [block] with the current [IReadWriteTransaction].
 * @throws CurrentTransactionUnavailableException if no transaction is available
 * @throws ReadWriteTransactionRequiredException if the current transaction is not an [IReadWriteTransaction]
 */
inline fun withCurrentReadWriteTransaction(block: (IReadWriteTransaction<*>) -> Unit) {
    (currentTransaction() as? IReadWriteTransaction<*>)?.let { block(it) }
        ?: throw ReadWriteTransactionRequiredException()
}