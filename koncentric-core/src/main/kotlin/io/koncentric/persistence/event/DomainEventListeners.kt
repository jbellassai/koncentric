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

package io.koncentric.persistence.event

import io.koncentric.persistence.properties.compareAndSetUntilSuccessful
import io.koncentric.persistence.storage.ITransactionalDatabaseInterface
import io.vavr.collection.LinkedHashMultimap
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass
import kotlin.reflect.full.superclasses

private typealias ListenersMap<S> = LinkedHashMultimap<KClass<out IDomainPersistenceEvent>, EventListener<S>>

private fun <DB: ITransactionalDatabaseInterface> emptySubscribers(): ListenersMap<DB> =
    LinkedHashMultimap.withSeq<EventListener<DB>>().empty()

/**
 * Standard implementation of [IDomainEventSubscriptionManager] which manages
 * subscriptions via a persistent Multimap.
 */
class DomainEventListeners<DB: ITransactionalDatabaseInterface> : IDomainEventSubscriptionManager<DB> {
    private val subscribedListenersRef = AtomicReference<ListenersMap<DB>>(emptySubscribers())

    override fun subscribe(listener: IDomainPersistenceEventListener<*, DB>) {
        val eventType = listener.eventType
        subscribedListenersRef.compareAndSetUntilSuccessful { current ->
            current.put(eventType, listener)
        }
    }

    override fun unsubscribe(listener: IDomainPersistenceEventListener<*, DB>) {
        subscribedListenersRef.compareAndSetUntilSuccessful { current ->
            current.filterNotValues { it === listener }
        }
    }

    override fun unsubscribeAll() {
        subscribedListenersRef.compareAndSetUntilSuccessful {
            emptySubscribers()
        }
    }

    override fun isSubscribed(listener: IDomainPersistenceEventListener<*, DB>): Boolean {
        return !subscribedListenersRef.get().find { it._2 === listener }.isEmpty
    }

    override suspend fun <T: IDomainPersistenceEvent> forEachListener(
        eventType: KClass<T>,
        block: suspend (IDomainPersistenceEventListener<T, DB>) -> Unit) {
        subscribedListenersRef.get().iterator().forEach {
            val listenerEventType = it._1
            if(eventType == listenerEventType || eventType in listenerEventType.superclasses) {
                val listener = it._2
                @Suppress("UNCHECKED_CAST")
                block(listener as IDomainPersistenceEventListener<T, DB>)
            }
        }
    }
}