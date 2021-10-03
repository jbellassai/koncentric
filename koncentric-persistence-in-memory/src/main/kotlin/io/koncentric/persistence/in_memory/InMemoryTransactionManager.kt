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

package io.koncentric.persistence.in_memory

import io.koncentric.persistence.event.IDomainEventSubscriptionManager
import io.koncentric.persistence.event.IDomainPersistenceEvent
import io.koncentric.persistence.tx.IReadOnlyTransaction
import io.koncentric.persistence.tx.IReadWriteTransaction
import io.koncentric.persistence.tx.ITransactionManager
import kotlinx.coroutines.sync.Mutex
import kotlin.reflect.KClass

internal class InMemoryReadTransaction<DB: IInMemoryDatabaseState>(
    override val db: InMemoryTransactionalDatabaseInterface<DB>,
    private val lockedMutex: Mutex
    ) : IReadOnlyTransaction<InMemoryTransactionalDatabaseInterface<DB>> {

    override suspend fun commit() = lockedMutex.unlock()

    override suspend fun rollback() = lockedMutex.unlock()

    override suspend fun release() = if(lockedMutex.isLocked) lockedMutex.unlock() else Unit
}

internal class InMemoryReadWriteTransaction<DB: IInMemoryDatabaseState>(
    override val db: InMemoryTransactionalDatabaseInterface<DB>,
    private val eventSubscriptionManager: IDomainEventSubscriptionManager<InMemoryTransactionalDatabaseInterface<DB>>,
    private val lockedMutex: Mutex
    ) : IReadWriteTransaction<InMemoryTransactionalDatabaseInterface<DB>> {

    override suspend fun commit() {
        try {
            db.commit()
        } finally {
            lockedMutex.unlock()
        }
    }

    override suspend fun rollback() = lockedMutex.unlock()

    override suspend fun release() = if(lockedMutex.isLocked) lockedMutex.unlock() else Unit

    override suspend fun <T: IDomainPersistenceEvent> notifyEvent(event: T, klass: KClass<T>) {
        eventSubscriptionManager.publish(event, klass, this)
    }

}

class InMemoryTransactionManager<DB: IInMemoryDatabaseState>(
    private val storage: InMemoryStorage<DB>,
    private val subscriptionManager: IDomainEventSubscriptionManager<InMemoryTransactionalDatabaseInterface<DB>>,
) : ITransactionManager<InMemoryTransactionalDatabaseInterface<DB>> {

    override suspend fun newReadOnlyTransaction(): IReadOnlyTransaction<InMemoryTransactionalDatabaseInterface<DB>> {
        val mutex = storage.mutex
        mutex.lock()
        val db = storage.getTransactionalDatabaseInterface()
        return InMemoryReadTransaction(db, mutex)
    }

    override suspend fun newReadWriteTransaction(): IReadWriteTransaction<InMemoryTransactionalDatabaseInterface<DB>> {
        val mutex = storage.mutex
        mutex.lock()
        val db = storage.getTransactionalDatabaseInterface()
        return InMemoryReadWriteTransaction(db, eventSubscriptionManager = subscriptionManager, mutex)
    }
}