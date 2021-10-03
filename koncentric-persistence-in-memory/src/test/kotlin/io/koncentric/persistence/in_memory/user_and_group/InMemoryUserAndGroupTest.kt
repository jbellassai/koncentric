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

package io.koncentric.persistence.in_memory.user_and_group

import io.koncentric.persistence.event.DomainEventListeners
import io.koncentric.persistence.event.IDomainPersistenceEventListener
import io.koncentric.persistence.in_memory.InMemoryStorage
import io.koncentric.persistence.in_memory.InMemoryTransactionManager
import io.koncentric.persistence.tx.IReadWriteTransaction
import io.koncentric.test_domains.users_and_groups.GroupMembershipAdded
import io.koncentric.test_domains.users_and_groups.PersistenceTests
import io.koncentric.test_domains.users_and_groups.UserNameUpdated

private val storage = InMemoryStorage(DatabaseState())
private val subscriptionManager = DomainEventListeners<InMemoryDatabaseInterface>().also {
    it.subscribe(UserNameUpdatedListener)
    it.subscribe(GroupMembershipAddedListener)
}
private val txManager = InMemoryTransactionManager(storage, subscriptionManager)

private object UserNameUpdatedListener :
    IDomainPersistenceEventListener<UserNameUpdated, InMemoryDatabaseInterface> {
    override val eventType = UserNameUpdated::class
    override suspend fun process(event: UserNameUpdated, tx: IReadWriteTransaction<InMemoryDatabaseInterface>) {
        tx.db.update { database ->
            val newUsers = database.users.put(event.user.id, event.user)
            database.modify(users = newUsers)
        }
    }
}

private object GroupMembershipAddedListener :
        IDomainPersistenceEventListener<GroupMembershipAdded, InMemoryDatabaseInterface> {
    override val eventType = GroupMembershipAdded::class
    override suspend fun process(event: GroupMembershipAdded, tx: IReadWriteTransaction<InMemoryDatabaseInterface>) {
        tx.db.update { database ->
            val newMemberships = database.memberships.add(GroupMembership(event.user.id, event.group.id))
            database.modify(memberships = newMemberships)
        }
    }
}

class InMemoryUserAndGroupTest : PersistenceTests(txManager, UserRepository, GroupRepository)