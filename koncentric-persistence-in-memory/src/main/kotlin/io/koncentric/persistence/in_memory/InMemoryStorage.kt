/*
 * Copyright 2021-2024 Koncentric, https://koncentric.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.koncentric.persistence.in_memory

import io.koncentric.persistence.properties.compareAndSetUntilSuccessful
import io.koncentric.persistence.storage.IDomainStorage
import io.koncentric.persistence.storage.ITransactionalDatabaseInterface
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/**
 * Marker interface for a domain-specific in-memory database
 */
interface IInMemoryDatabaseState {
    suspend fun clear(): IInMemoryDatabaseState
}

class InMemoryTransactionalDatabaseInterface<DB: IInMemoryDatabaseState>(
    private val storage: InMemoryStorage<DB>,
    originalDatabase: DB,
) : ITransactionalDatabaseInterface {

    private val currentDatabaseRef: AtomicReference<DB> = AtomicReference(originalDatabase)

    fun currentDatabase(): DB = currentDatabaseRef.get()

    fun update(fn: (DB) -> DB) {
        currentDatabaseRef.compareAndSetUntilSuccessful { fn(currentDatabase()) }
    }

    fun commit() {
        storage.commit(currentDatabase())
    }
}

class InMemoryStorage<DB: IInMemoryDatabaseState>(
    initialDatabase: DB,
) : IDomainStorage<InMemoryTransactionalDatabaseInterface<DB>> {

    private val currentDatabaseRef: AtomicReference<DB> = AtomicReference(initialDatabase)
    internal val mutex = Mutex()

    override suspend fun getTransactionalDatabaseInterface(): InMemoryTransactionalDatabaseInterface<DB> {
        val db = currentDatabaseRef.get()
        return InMemoryTransactionalDatabaseInterface(this, db)
    }

    internal fun commit(newDBState: DB) {
        currentDatabaseRef.compareAndSetUntilSuccessful { newDBState }
    }

    suspend fun reset() {
        mutex.withLock {
            currentDatabaseRef.compareAndSetUntilSuccessful {
                @Suppress("UNCHECKED_CAST")
                it.clear() as DB
            }
        }
    }

    fun currentDatabase(): DB {
        return currentDatabaseRef.get() as DB
    }

}