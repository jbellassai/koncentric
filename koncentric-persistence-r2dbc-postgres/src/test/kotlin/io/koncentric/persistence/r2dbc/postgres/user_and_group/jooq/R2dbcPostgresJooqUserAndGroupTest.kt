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

package io.koncentric.persistence.r2dbc.postgres.user_and_group.jooq

import PostgresTestListener
import SchemaLocations
import connectionFactory
import io.koncentric.persistence.event.DomainEventListeners
import io.koncentric.persistence.event.IDomainPersistenceEventListener
import io.koncentric.persistence.r2dbc.postgres.R2DbcPostgresTransactionalDatabaseInterface
import io.koncentric.persistence.r2dbc.postgres.R2dbcPostgresDomainStorage
import io.koncentric.persistence.r2dbc.postgres.R2dbcPostgresTransactionManager
import io.koncentric.persistence.tx.IReadWriteTransaction
import io.koncentric.test_domains.users_and_groups.GroupMembershipAdded
import io.koncentric.test_domains.users_and_groups.PersistenceTests
import io.koncentric.test_domains.users_and_groups.UserNameUpdated

typealias DatabaseType = R2DbcPostgresTransactionalDatabaseInterface

private val postgresTestListener = PostgresTestListener(SchemaLocations.users_and_groups)

private object JooqUserNameUpdatedListener : IDomainPersistenceEventListener<UserNameUpdated, DatabaseType> {
    override val eventType = UserNameUpdated::class
    override suspend fun process(event: UserNameUpdated, tx: IReadWriteTransaction<DatabaseType>) {
        UserRepository.updateName(event.user.id, event.firstName, event.lastName)
    }
}

private object JooqGroupMembershipAddedListener : IDomainPersistenceEventListener<GroupMembershipAdded, DatabaseType> {
    override val eventType = GroupMembershipAdded::class
    override suspend fun process(event: GroupMembershipAdded, tx: IReadWriteTransaction<DatabaseType>) {
        UserRepository.addGroupMembership(event.user, event.group)
    }
}

private val storage = R2dbcPostgresDomainStorage(connectionFactory(postgresTestListener::getPort))
private val subscriptionManager = DomainEventListeners<DatabaseType>().also {
    it.subscribe(JooqUserNameUpdatedListener)
    it.subscribe(JooqGroupMembershipAddedListener)
}
private val txManager = R2dbcPostgresTransactionManager(storage, subscriptionManager)

class R2dbcPostgresJooqUserAndGroupTest : PersistenceTests(
    txManager,
    UserRepository,
    GroupRepository,
    listOf(postgresTestListener)
)