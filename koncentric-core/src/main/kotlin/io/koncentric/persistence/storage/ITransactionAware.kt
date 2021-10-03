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

package io.koncentric.persistence.storage

import io.koncentric.persistence.tx.*

interface ITransactionAware<DB: ITransactionalDatabaseInterface> {
    /**
     * @return the current [ITransaction]
     * @throws CurrentTransactionUnavailableException if no transaction is available
     */
    @Suppress("UNCHECKED_CAST")
    fun currentTransaction(): ITransaction<DB> =
        (threadLocalTx.get() as? ITransaction<DB>) ?: throw CurrentTransactionUnavailableException()

}

/**
 * Executes the [block] with the current [ITransaction].
 * @throws CurrentTransactionUnavailableException if no transaction is available
 */
inline fun <DB: ITransactionalDatabaseInterface, T: Any?> ITransactionAware<DB>.withCurrentTransaction(block: (ITransaction<DB>) -> T): T {
    return block(currentTransaction())
}

/**
 * Executes the [block] with the current [IReadWriteTransaction].
 * @throws CurrentTransactionUnavailableException if no transaction is available
 * @throws ReadWriteTransactionRequiredException if the current transaction is not an [IReadWriteTransaction]
 */
inline fun <reified DB: ITransactionalDatabaseInterface, T: Any?> ITransactionAware<DB>.withCurrentReadWriteTransaction(block: (IReadWriteTransaction<DB>) -> T): T {
    (currentTransaction() as? IReadWriteTransaction<DB>)?.let { return block(it) }
        ?: throw ReadWriteTransactionRequiredException()
}
