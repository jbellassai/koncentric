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

package io.koncentric.persistence.event

import io.koncentric.persistence.storage.ITransactionalDatabaseInterface
import io.koncentric.persistence.tx.IReadWriteTransaction
import kotlin.reflect.KClass

interface IDomainEventSubscriptionManager<DB: ITransactionalDatabaseInterface> {

    /**
     * Subscribe the [listener] to domain events
     * @param listener the [IDomainPersistenceEventListener]
     */
    fun subscribe(listener: IDomainPersistenceEventListener<*, DB>)

    /**
     * Un-subscribe the [listener] to domain events
     * @param listener the [IDomainPersistenceEventListener]
     */
    fun unsubscribe(listener: IDomainPersistenceEventListener<*, DB>)

    /**
     * Un-subscribe all listeners
     */
    fun unsubscribeAll()

    /**
     * @param listener the [IDomainPersistenceEventListener]
     * @return whether the [listener] is subscribed
     */
    fun isSubscribed(listener: IDomainPersistenceEventListener<*, DB>): Boolean

    /**
     * Executes [block] for each [EventListener] subscribed to events of type [T]
     * @param eventType the [KClass] of the event type
     * @param block the lambda to execute for each listener
     */
    suspend fun <T: IDomainPersistenceEvent> forEachListener(
        eventType: KClass<T>,
        block: suspend (IDomainPersistenceEventListener<T, DB>) -> Unit
    )

    suspend fun <T: IDomainPersistenceEvent> publish(event: T, klass: KClass<T>, tx: IReadWriteTransaction<DB>) {
        forEachListener(klass) {
            it.process(event, tx)
        }
    }
}