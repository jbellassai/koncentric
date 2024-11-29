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

package io.koncentric.persistence.in_memory.user_and_group

import io.koncentric.persistence.in_memory.IInMemoryDatabaseState
import io.koncentric.persistence.in_memory.InMemoryTransactionalDatabaseInterface
import io.koncentric.persistence.storage.ITransactionAware
import io.koncentric.persistence.storage.withCurrentReadWriteTransaction
import io.koncentric.persistence.storage.withCurrentTransaction
import io.koncentric.test_domains.users_and_groups.*
import io.vavr.collection.HashMap
import io.vavr.collection.HashSet

internal typealias InMemoryDatabaseInterface = InMemoryTransactionalDatabaseInterface<DatabaseState>

internal data class GroupMembership(val user: UserId, val group: GroupId)

internal class DatabaseState(
    val users: HashMap<UserId, IUser> = HashMap.empty(),
    val groups: HashMap<GroupId, IGroup> = HashMap.empty(),
    val memberships: HashSet<GroupMembership> = HashSet.empty()
) : IInMemoryDatabaseState {

    override suspend fun clear(): DatabaseState {
        return DatabaseState()
    }

    fun modify(
        users: HashMap<UserId, IUser> = this.users,
        groups: HashMap<GroupId, IGroup> = this.groups,
        memberships: HashSet<GroupMembership> = this.memberships
    ): DatabaseState {
        return DatabaseState(users = users, groups = groups, memberships = memberships)
    }
}

internal object UserRepository
    : AbstractUserRepository<InMemoryDatabaseInterface>(),
    ITransactionAware<InMemoryDatabaseInterface> {

    override suspend fun getUser(id: UserId): IUser? {
        return withCurrentTransaction { tx ->
            tx.db.currentDatabase().users[id].orNull?.fromDatabase()
        }
    }

    override suspend fun getByEmail(email: String): IUser? {
        return withCurrentTransaction { tx ->
            tx.db.currentDatabase().users
                .find { it._2.email.equals(email, ignoreCase = true) }
                .getOrElse { null }?._2?.fromDatabase()
        }
    }

    private fun IUser.fromDatabase(): IUser =
        IUser.Builder(this)
            .withGroupsBy { userGroups(id) }
            .build()

    private suspend fun userGroups(userId: UserId): List<IGroup> {
        return withCurrentTransaction { tx ->
            val groupIds = tx.db.currentDatabase().memberships
                .toStream()
                .filter { it.user == userId }
                .map { it.group }
                .toList()
            groupIds.mapNotNull { GroupRepository.getGroup(it) }
        }
    }

    override suspend fun create(userSpec: NewUserSpec): IUser {
        return withCurrentReadWriteTransaction { tx ->
            if(getByEmail(userSpec.email) != null) throw UserEmailNotUniqueException(userSpec.email)
            val id = this.nextUserId()
            val user = IUser.Builder()
                .withId(id)
                .withEmail(userSpec.email)
                .withFirstName(userSpec.firstName)
                .withLastName(userSpec.lastName)
                .withEnabledStatus(EnabledStatus.ENABLED)
                .withGroupsBy { userGroups(id) }
                .build()

            tx.db.update { database ->
                val newUsers = database.users.put(id, user)
                database.modify(users = newUsers)
            }
            user
        }
    }

    override suspend fun delete(id: UserId) {
        return withCurrentReadWriteTransaction { tx ->
            tx.db.update { database ->
                val newUsers = database.users.remove(id)
                val newMemberships = database.memberships.filterNot { it.user == id }
                database.modify(users = newUsers, memberships = newMemberships)
            }
        }
    }

    override suspend fun deleteAll() {
        withCurrentReadWriteTransaction { tx ->
            tx.db.update { database ->
                val newUsers = database.users.filterKeys { false }
                val newMemberships = database.memberships.filter { false }
                database.modify(users = newUsers, memberships = newMemberships)
            }
        }
    }
}

internal object GroupRepository
    : AbstractGroupRepository<InMemoryDatabaseInterface>(),
    ITransactionAware<InMemoryDatabaseInterface> {

    override suspend fun getGroup(id: GroupId): IGroup? {
        return withCurrentTransaction { tx ->
            tx.db.currentDatabase().groups[id].orNull?.fromDatabase()
        }
    }

    override suspend fun getByName(name: String): IGroup? {
        return withCurrentTransaction { tx ->
            tx.db.currentDatabase().groups
                .find { it._2.name.equals(name, ignoreCase = true) }
                .getOrElse { null }?._2?.fromDatabase()
        }
    }

    private suspend fun groupMembers(groupId: GroupId): List<IUser> {
        return withCurrentTransaction { tx ->
            val userIds = tx.db.currentDatabase().memberships
                .toStream()
                .filter { it.group == groupId }
                .map { it.user }
                .toList()
            userIds.mapNotNull { UserRepository.getUser(it) }
        }
    }

    private fun IGroup.fromDatabase(): IGroup =
        IGroup.Builder(this)
            .withMembersBy { groupMembers(id) }
            .build()

    override suspend fun create(groupSpec: NewGroupSpec): IGroup {
        return withCurrentReadWriteTransaction { tx ->
            if(getByName(groupSpec.name) != null) throw GroupNameNotUniqueException(groupSpec.name)
            val id = this.nextGroupId()
            val group = IGroup.Builder()
                .withId(id)
                .withName(groupSpec.name)
                .withMembersBy { groupMembers(id) }
                .build()

            tx.db.update { database ->
                val newGroups = database.groups.put(id, group)
                database.modify(groups = newGroups)
            }
            group
        }
    }

    override suspend fun delete(id: GroupId) {
        return withCurrentReadWriteTransaction { tx ->
            tx.db.update { database ->
                val newGroups = database.groups.remove(id)
                val newMemberships = database.memberships.filterNot { it.group == id }
                database.modify(groups = newGroups, memberships = newMemberships)
            }
        }
    }

    override suspend fun deleteAll() {
        withCurrentReadWriteTransaction { tx ->
            tx.db.update { database ->
                val newGroups = database.groups.filterKeys { false }
                val newMemberships = database.memberships.filter { false }
                database.modify(groups = newGroups, memberships = newMemberships)
            }
        }
    }

}