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

package io.koncentric.test_domains.users_and_groups

import io.koncentric.persistence.PersistenceException
import io.koncentric.persistence.event.IDomainPersistenceEvent
import io.koncentric.persistence.properties.*
import io.koncentric.persistence.storage.ITransactionalDatabaseInterface
import io.koncentric.persistence.tx.notify
import io.koncentric.persistence.tx.withCurrentReadWriteTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

data class GroupId(val value: UUID)
data class NewGroupSpec(val name: String)
data class GroupNameUpdated(val group: IGroup, val name: String): IDomainPersistenceEvent

/**
 * An exception thrown if a non-unique name is attempted to be saved
 */
data class GroupNameNotUniqueException(val duplicateName: String) : PersistenceException("The group name [$duplicateName] is already in use")

interface IGroup: IMutatePersistently, IDebugLazily {
    val id: GroupId
    val name: String

    suspend fun members(): List<IUser>

    class Builder(from: IGroup? = null) {
        private val props = from?.let { Group.propsFrom(it) } ?: IPersistentProperties.empty()
        fun withId(id: GroupId): Builder = this.apply { props.set(IGroup::id, id) }
        fun withName(name: String): Builder = this.apply { props.set(IGroup::name, name) }
        fun withMembersBy(fn: suspend () -> List<IUser>) = this.apply {
            props.setLazy(IGroup::members, fn)
        }
        fun withProp(name: String, value: Any): Builder = this.apply { props.set(name, value) }
        fun build(): IGroup = Group.from(props)
    }
}

interface IMutableGroup: IGroup {
    /**
     * Update the name of this group
     * @see GroupNameUpdated
     */
    suspend fun updateName(name: String)

    /**
     * Add [user] as a member of this group
     */
    suspend fun addMember(user: IUser)
}

internal class Group private constructor(
    private val props: IPersistentProperties
): IMutableGroup, IDebugLazily by props {

    init {
        props.setDebugPropertyOrder(listOf(
            ::id.name,
            ::name.name,
            ::members.name,
        ))
    }

    override val id: GroupId by persistentDelegate(props)
    override val name: String by persistentDelegate(props)

    override suspend fun updateName(name: String) {
        withCurrentReadWriteTransaction { tx ->
            props.set(::name, name)
            tx.notify(GroupNameUpdated(this, name))
        }
    }

    override suspend fun members(): List<IUser> = props.lazyProp(::members)

    override suspend fun addMember(user: IUser) {
        withCurrentReadWriteTransaction { tx ->
            props.updateIfResolved(::members) { current: List<IUser> ->
                if(current.none { it.id == user.id }) current + user
                else current
            }
            tx.notify(GroupMembershipAdded(user, this))
        }
    }

    companion object {
        internal fun propsFrom(other: IGroup): IPersistentProperties =
            if(other is Group) other.props.copy()
            else other.toPersistentProperties()

        internal fun from(other: IGroup) : Group = Group(propsFrom(other))

        internal fun from(props: IPersistentProperties): Group = Group(props)
    }
}

suspend fun IGroup.mutate(mutate: suspend IMutableGroup.() -> Unit): IMutableGroup {
    return Group.from(this).apply { mutate() }
}

abstract class AbstractGroupRepository<DB: ITransactionalDatabaseInterface> {

    protected suspend fun nextGroupId(): GroupId = withContext(Dispatchers.IO) { GroupId(UUID.randomUUID()) }

    abstract suspend fun getGroup(id: GroupId) : IGroup?

    abstract suspend fun getByName(name: String): IGroup?

    abstract suspend fun create(groupSpec: NewGroupSpec): IGroup

    abstract suspend fun delete(id: GroupId)

    abstract suspend fun deleteAll()
}
