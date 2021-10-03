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

data class UserId(val value: UUID)
enum class EnabledStatus { ENABLED, DISABLED }

data class NewUserSpec(
    val email: String,
    val firstName: String,
    val lastName: String
)

interface IUser : IMutatePersistently, IDebugLazily {
    val id: UserId
    val email: String
    val firstName: String
    val lastName: String
    val enabledStatus: EnabledStatus

    suspend fun groups(): List<IGroup>

    class Builder(from: IUser? = null) {
        private val props = from?.let { User.propsFrom(it) } ?: IPersistentProperties.empty()

        fun withId(id: UserId): Builder = this.apply {
            props.set(IUser::id, id)
        }

        fun withEmail(email: String): Builder = this.apply {
            props.set(IUser::email, email)
        }

        fun withFirstName(firstName: String): Builder = this.apply {
            props.set(IUser::firstName, firstName)
        }

        fun withLastName(lastName: String): Builder = this.apply {
            props.set(IUser::lastName, lastName)
        }

        fun withEnabledStatus(enabledStatus: EnabledStatus) = this.apply {
            props.set(IUser::enabledStatus, enabledStatus)
        }

        fun withGroupsBy(fn: suspend () -> List<IGroup>) = this.apply {
            props.setLazy(IUser::groups, fn)
        }

        fun build(): IUser = User.from(props)
    }
}

suspend fun IUser.mutate(mutate: suspend IMutableUser.() -> Unit): IMutableUser {
    return User.from(this).apply { mutate() }
}

/**
 * An event published when a user's name changes
 */
data class UserNameUpdated(val user: IUser, val firstName: String, val lastName: String): IDomainPersistenceEvent

/**
 * An event published when a user is disabled
 */
data class UserDisabled(val user: IUser): IDomainPersistenceEvent

/**
 * An event published when a user is enabled
 */
data class UserEnabled(val user: IUser): IDomainPersistenceEvent

/**
 * An event published when a user is added as a member of a group
 */
data class GroupMembershipAdded(val user: IUser, val group: IGroup): IDomainPersistenceEvent

/**
 * An exception thrown if a non-unique email is attempted to be saved
 */
data class UserEmailNotUniqueException(val duplicateEmail: String) : PersistenceException("The email address [$duplicateEmail] is already in use")

interface IMutableUser : IUser {

    /**
     * Update the name of this user.
     * @see UserNameUpdated
     */
    suspend fun updateName(firstName: String, lastName: String)

    /**
     * Disables this user
     * @see UserDisabled
     */
    suspend fun disable()

    /**
     * Enables this user
     * @see UserEnabled
     */
    suspend fun enable()

    /**
     * Add this user as a member of [group]
     */
    suspend fun addMembershipTo(group: IGroup)
}

internal class User private constructor(
    private val props: IPersistentProperties
) : IMutableUser, IDebugLazily by props {

    init {
        props.setDebugPropertyOrder(listOf(
            ::id.name,
            ::email.name,
            ::firstName.name,
            ::lastName.name,
            ::enabledStatus.name,
            ::groups.name
        ))
    }

    override val id: UserId by persistentDelegate(props)
    override val email: String by persistentDelegate(props)
    override val firstName: String by persistentDelegate(props)
    override val lastName: String by persistentDelegate(props)
    override val enabledStatus: EnabledStatus by persistentDelegate(props)

    override suspend fun updateName(firstName: String, lastName: String) {
        withCurrentReadWriteTransaction { tx ->
            props.set(::firstName, firstName)
            props.set(::lastName, lastName)
            tx.notify(UserNameUpdated(this, firstName, lastName))
        }
    }

    override suspend fun disable() {
        withCurrentReadWriteTransaction { tx ->
            props.set(::enabledStatus, EnabledStatus.DISABLED)
            tx.notify(UserDisabled(this))
        }
    }

    override suspend fun enable() {
        withCurrentReadWriteTransaction { tx ->
            props.set(::enabledStatus, EnabledStatus.ENABLED)
            tx.notify(UserEnabled(this))
        }
    }

    override suspend fun groups(): List<IGroup> = props.lazyProp(::groups)

    override suspend fun addMembershipTo(group: IGroup) {
        withCurrentReadWriteTransaction { tx ->
            tx.notify(GroupMembershipAdded(this, group))
            props.resetLazy(::groups)
        }
    }

    companion object {

        internal fun propsFrom(other: IUser) : IPersistentProperties =
            if(other is User) other.props.copy()
            else other.toPersistentProperties()

        internal fun from(other: IUser) : User = User(propsFrom(other))

        internal fun from(props: IPersistentProperties): User = User(props)
    }
}

abstract class AbstractUserRepository<DB: ITransactionalDatabaseInterface> {

    protected suspend fun nextUserId(): UserId = withContext(Dispatchers.IO) { UserId(UUID.randomUUID()) }

    abstract suspend fun getUser(id: UserId) : IUser?

    abstract suspend fun getByEmail(email: String): IUser?

    abstract suspend fun create(userSpec: NewUserSpec): IUser

    abstract suspend fun delete(id: UserId)

    abstract suspend fun deleteAll()
}