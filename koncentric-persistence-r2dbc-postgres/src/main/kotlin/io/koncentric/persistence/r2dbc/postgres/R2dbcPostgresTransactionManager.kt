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

package io.koncentric.persistence.r2dbc.postgres

import io.koncentric.persistence.event.IDomainEventSubscriptionManager
import io.koncentric.persistence.event.IDomainPersistenceEvent
import io.koncentric.persistence.tx.IReadOnlyTransaction
import io.koncentric.persistence.tx.IReadWriteTransaction
import io.koncentric.persistence.tx.ITransactionManager
import io.r2dbc.postgresql.api.PostgresTransactionDefinition
import io.r2dbc.spi.IsolationLevel.SERIALIZABLE
import io.r2dbc.spi.TransactionDefinition
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlin.reflect.KClass

internal abstract class AbstractPostgresTransaction(open val db: R2DbcPostgresTransactionalDatabaseInterface) {

    fun currentConnection() = db.connection

    open suspend fun commit() {
        currentConnection().commitTransaction().awaitSingleOrNull()
    }
    open suspend fun rollback() {
        currentConnection().rollbackTransaction().awaitSingleOrNull()
    }
    open suspend fun release() {
        currentConnection().close().awaitSingleOrNull()
    }
}

internal class R2dbcPostgresReadOnlyTransaction(
    override val db: R2DbcPostgresTransactionalDatabaseInterface
    ) : AbstractPostgresTransaction(db), IReadOnlyTransaction<R2DbcPostgresTransactionalDatabaseInterface> {

    override suspend fun commit() = super.commit()
    override suspend fun rollback() = super.rollback()
    override suspend fun release() = super.release()
}

internal class R2dbcPostgresReadWriteTransaction(
    override val db: R2DbcPostgresTransactionalDatabaseInterface,
    private val eventSubscriptionManager: IDomainEventSubscriptionManager<R2DbcPostgresTransactionalDatabaseInterface>
) : AbstractPostgresTransaction(db), IReadWriteTransaction<R2DbcPostgresTransactionalDatabaseInterface> {

    override suspend fun commit() = super.commit()
    override suspend fun rollback() = super.rollback()
    override suspend fun release() = super.release()

    override suspend fun <T : IDomainPersistenceEvent> notifyEvent(event: T, klass: KClass<T>) {
        eventSubscriptionManager.publish(event, klass, this)
    }

}

class R2dbcPostgresTransactionManager(
    private val storage: R2dbcPostgresDomainStorage,
    private val subscriptionManager: IDomainEventSubscriptionManager<R2DbcPostgresTransactionalDatabaseInterface>
) : ITransactionManager<R2DbcPostgresTransactionalDatabaseInterface>  {

    private fun txDefinition(): TransactionDefinition =
        PostgresTransactionDefinition.from(SERIALIZABLE)
            .readOnly()
            .notDeferrable()

    override suspend fun newReadOnlyTransaction(): IReadOnlyTransaction<R2DbcPostgresTransactionalDatabaseInterface> {
        val dbi = storage.getTransactionalDatabaseInterface()
        val connection = dbi.connection
        try {

            connection.beginTransaction(txDefinition()).awaitSingleOrNull()
            return R2dbcPostgresReadOnlyTransaction(dbi)
        } catch(t: Throwable) {
            connection.close().awaitSingleOrNull()
            throw t
        }
    }

    override suspend fun newReadWriteTransaction(): IReadWriteTransaction<R2DbcPostgresTransactionalDatabaseInterface> {
        val dbi = storage.getTransactionalDatabaseInterface()
        val connection = dbi.connection
        try {
            connection.beginTransaction().awaitSingleOrNull()
            connection.transactionIsolationLevel = SERIALIZABLE
            return R2dbcPostgresReadWriteTransaction(dbi, subscriptionManager)
        } catch(t: Throwable) {
            connection.close().awaitSingleOrNull()
            throw t
        }
    }

}