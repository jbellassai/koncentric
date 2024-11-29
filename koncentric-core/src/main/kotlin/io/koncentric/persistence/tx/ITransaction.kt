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

package io.koncentric.persistence.tx

import io.koncentric.persistence.event.IDomainPersistenceEvent
import io.koncentric.persistence.storage.IDomainStorage
import io.koncentric.persistence.storage.ITransactionalDatabaseInterface
import kotlin.reflect.KClass

/**
 * A generic interface for a transaction associated with some
 * type of [IDomainStorage]
 */
sealed interface ITransaction<DBI: ITransactionalDatabaseInterface> {
    /**
     * The [ITransactionalDatabaseInterface] associated with the transaction
     */
    val db: DBI

    /**
     * Commit the transaction
     */
    suspend fun commit()

    /**
     * Roll back the transaction
     */
    suspend fun rollback()

    /**
     * Release the transaction
     */
    suspend fun release()
}

/**
 * A read-only transaction associated with some type of [ITransactionalDatabaseInterface]
 */
interface IReadOnlyTransaction<DBI: ITransactionalDatabaseInterface> : ITransaction<DBI>

/**
 * A read-write transaction associated with some type of [ITransactionalDatabaseInterface]
 */
interface IReadWriteTransaction<DBI: ITransactionalDatabaseInterface> : ITransaction<DBI> {

    /**
     * Notify the transaction that a particular domain persistence event has occurred
     */
    suspend fun <T: IDomainPersistenceEvent> notifyEvent(event: T, klass: KClass<T>)
}

suspend inline fun <reified T: IDomainPersistenceEvent> IReadWriteTransaction<*>.notify(event: T) =
    this.notifyEvent(event, T::class)