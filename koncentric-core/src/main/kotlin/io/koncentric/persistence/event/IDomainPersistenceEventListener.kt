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

import io.koncentric.persistence.storage.IDomainStorage
import io.koncentric.persistence.storage.ITransactionalDatabaseInterface
import io.koncentric.persistence.tx.IReadWriteTransaction
import kotlin.reflect.KClass

typealias EventListener<S> = IDomainPersistenceEventListener<*, S>

/**
 * Interface for a generic listener/handler for a particular type of [IDomainPersistenceEvent]
 * and for a particular type of [IDomainStorage]
 */
interface IDomainPersistenceEventListener<T: IDomainPersistenceEvent, S: ITransactionalDatabaseInterface> {

    /**
     * The [KClass] representing the type of [IDomainPersistenceEvent] which
     * this listener processes
     */
    val eventType: KClass<T>

    /**
     * Process the [event] in the [IReadWriteTransaction]
     * @param event the [IDomainPersistenceEvent] to process
     * @param tx the [IReadWriteTransaction]
     */
    suspend fun process(event: T, tx: IReadWriteTransaction<S>)
}