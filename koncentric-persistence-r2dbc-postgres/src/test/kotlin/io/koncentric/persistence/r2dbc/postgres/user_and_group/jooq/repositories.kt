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

import io.koncentric.persistence.PersistenceException
import io.koncentric.persistence.jooq.user_and_group.generated.tables.references.GROUPS
import io.koncentric.persistence.jooq.user_and_group.generated.tables.references.GROUPS_USERS
import io.koncentric.persistence.jooq.user_and_group.generated.tables.references.USERS
import io.koncentric.persistence.r2dbc.postgres.R2DbcPostgresTransactionalDatabaseInterface
import io.koncentric.persistence.storage.ITransactionAware
import io.koncentric.test_domains.users_and_groups.*
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SQLDialect
import org.jooq.SelectJoinStep
import org.jooq.impl.DSL.*
import org.jooq.impl.SQLDataType.TIMESTAMP
import reactor.core.publisher.Flux

private fun transactionTimestamp() = field(unquotedName("transaction_timestamp()"), TIMESTAMP.notNull())

private data class JooqDataMappingException(override val message: String) : RuntimeException(message)

private inline fun <reified T : Any> Record.getRequiredColumn(name: String): T =
    this.get(name, T::class.java)
        ?: throw JooqDataMappingException("No value for column [$name] required for Type [${T::class.qualifiedName}")


private fun Record.toUser(): IUser {
    val internalId: Int = getRequiredColumn("id")
    return IUser.Builder()
        .withId(UserId(getRequiredColumn("external_id")))
        .withEmail(getRequiredColumn("email"))
        .withFirstName(getRequiredColumn("first_name"))
        .withLastName(getRequiredColumn("last_name"))
        .withEnabledStatus(getRequiredColumn("status"))
        .withGroupsBy { GroupRepository.getGroupsForUser(internalId) }
        .build()
}

object UserRepository :
    AbstractUserRepository<R2DbcPostgresTransactionalDatabaseInterface>(),
    ITransactionAware<R2DbcPostgresTransactionalDatabaseInterface> {

    private fun connection() = currentTransaction().db.connection
    private suspend fun <T : Any?> withCtx(block: suspend DSLContext.() -> T): T =
        using(connection(), SQLDialect.POSTGRES).let { it.block() }

    private fun DSLContext.selectUsers(): SelectJoinStep<Record> =
        select().from(USERS)

//        this.select(USERS.fields().map {
//            // workaround for https://github.com/jOOQ/jOOQ/issues/12231
//            if (it == USERS.STATUS) it.cast(VARCHAR).`as`("status")
//            else it
//        }).from(USERS)

    private suspend fun getUserId(id: UserId): Int? {
        return withCtx {
            select(USERS.ID)
                .from(USERS)
                .where(USERS.EXTERNAL_ID.eq(id.value))
                .awaitFirstOrNull()
                ?.get(USERS.ID)
        }
    }

    private suspend fun getUser(id: Int): IUser? {
        val result = withCtx {
            selectUsers()
                .where(USERS.ID.eq(id))
                .limit(1)
                .awaitFirstOrNull()
        }
        return result?.map { it.toUser() }
    }

    override suspend fun getUser(id: UserId): IUser? {
        val result = withCtx {
            selectUsers()
                .where(USERS.EXTERNAL_ID.eq(id.value))
                .limit(1)
                .awaitFirstOrNull()
        }
        return result?.map { it.toUser() }
    }

    override suspend fun getByEmail(email: String): IUser? {
        val result = withCtx {
            selectUsers()
                .where(USERS.EMAIL.eq(email))
                .limit(1)
                .awaitFirstOrNull()
        }
        return result?.map { it.toUser() }
    }

    override suspend fun create(userSpec: NewUserSpec): IUser {
        if (getByEmail(userSpec.email) != null) throw UserEmailNotUniqueException(userSpec.email)
        val id = this.nextUserId()
        withCtx {
            insertInto(
                USERS,
                USERS.EXTERNAL_ID, USERS.EMAIL, USERS.FIRST_NAME, USERS.LAST_NAME, USERS.STATUS
            )
                .values(
                    id.value,
                    userSpec.email,
                    userSpec.firstName,
                    userSpec.lastName,
                    EnabledStatus.ENABLED
                )
                .awaitFirstOrNull()
        }
        return getUser(id) ?: throw PersistenceException("Failed to load created IUser")
    }

    suspend fun updateName(id: UserId, firstName: String, lastName: String) {
        withCtx {
            update(USERS)
                .set(
                    mapOf(
                        USERS.FIRST_NAME to firstName,
                        USERS.LAST_NAME to lastName
                    )
                )
                .where(USERS.EXTERNAL_ID.eq(id.value))
                .awaitSingle()
        }
    }

    override suspend fun delete(id: UserId) {
        withCtx {
            delete(USERS)
                .where(USERS.EXTERNAL_ID.eq(id.value))
                .awaitSingle()
        }
    }

    override suspend fun deleteAll() {
        withCtx {
            delete(USERS).awaitSingle()
        }
    }

    suspend fun addGroupMembership(user: IUser, group: IGroup) {
        withCtx {
            insertInto(GROUPS_USERS)
                .values(
                    select(GROUPS.ID).from(GROUPS).where(GROUPS.EXTERNAL_ID.eq(group.id.value)),
                    select(USERS.ID).from(USERS).where(USERS.EXTERNAL_ID.eq(user.id.value)),
                    transactionTimestamp(),
                )
                .awaitFirst()
        }
    }

    suspend fun getMembersForGroup(id: Int): List<IUser> {
        return withCtx {
            Flux.from(
                selectUsers()
                    .innerJoin(GROUPS_USERS).on(GROUPS_USERS.USER_ID.eq(USERS.ID))
                    .where(GROUPS_USERS.GROUP_ID.eq(id))
                    .orderBy(USERS.EMAIL)
            )
                .map { it.toUser() }
                .collectList()
                .awaitSingle()
        }
    }

}

private fun Record.toGroup(): IGroup {
    val internalId: Int = getRequiredColumn("id")
    return IGroup.Builder()
        .withProp("_dbId", Int)
        .withId(GroupId(getRequiredColumn("external_id")))
        .withName(getRequiredColumn("name"))
        .withMembersBy { UserRepository.getMembersForGroup(internalId) }
        .build()
}

object GroupRepository :
    AbstractGroupRepository<R2DbcPostgresTransactionalDatabaseInterface>(),
    ITransactionAware<R2DbcPostgresTransactionalDatabaseInterface> {

    private fun connection() = currentTransaction().db.connection
    private suspend fun <T : Any?> withCtx(block: suspend DSLContext.() -> T): T =
        using(connection(), SQLDialect.POSTGRES).let { it.block() }

    suspend fun getGroupId(id: GroupId): Int? {
        return withCtx {
            select(GROUPS.ID)
                .from(GROUPS)
                .where(GROUPS.EXTERNAL_ID.eq(id.value))
                .awaitFirstOrNull()
                ?.get(GROUPS.ID)
        }
    }

    private suspend fun getGroup(id: Int): IGroup? {
        val result = withCtx {
            selectFrom(GROUPS)
                .where(GROUPS.ID.eq(id))
                .limit(1)
                .awaitFirstOrNull()
        }
        return result?.map { it.toGroup() }
    }

    override suspend fun getGroup(id: GroupId): IGroup? {
        val result = withCtx {
            selectFrom(GROUPS)
                .where(GROUPS.EXTERNAL_ID.eq(id.value))
                .limit(1)
                .awaitFirstOrNull()
        }
        return result?.map { it.toGroup() }
    }

    override suspend fun getByName(name: String): IGroup? {
        val result = withCtx {
            selectFrom(GROUPS)
                .where(GROUPS.NAME.eq(name))
                .limit(1)
                .awaitFirstOrNull()
        }
        return result?.map { it.toGroup() }
    }

    override suspend fun create(groupSpec: NewGroupSpec): IGroup {
        if (getByName(groupSpec.name) != null) throw GroupNameNotUniqueException(groupSpec.name)
        val id = this.nextGroupId()
        withCtx {
            insertInto(
                GROUPS,
                GROUPS.EXTERNAL_ID, GROUPS.NAME
            )
                .values(
                    id.value,
                    groupSpec.name
                )
                .awaitFirstOrNull()
        }
        return getGroup(id) ?: throw PersistenceException("Failed to load created IGroup")
    }

    suspend fun updateName(id: GroupId, name: String) {
        withCtx {
            update(GROUPS)
                .set(
                    mapOf(
                        GROUPS.NAME to name,
                    )
                )
                .where(GROUPS.EXTERNAL_ID.eq(id.value))
                .awaitSingle()
        }
    }

    override suspend fun delete(id: GroupId) {
        withCtx {
            delete(GROUPS)
                .where(GROUPS.EXTERNAL_ID.eq(id.value))
                .awaitSingle()
        }
    }

    override suspend fun deleteAll() {
        withCtx {
            delete(GROUPS).awaitSingle()
        }
    }

    suspend fun getGroupsForUser(id: Int): List<IGroup> {
        return withCtx {
            Flux.from(
                select()//select(GROUPS.ID, GROUPS.EXTERNAL_ID, GROUPS.NAME)
                    .from(GROUPS)
                    .join(GROUPS_USERS).on(GROUPS_USERS.GROUP_ID.eq(GROUPS.ID))
                    .where(GROUPS_USERS.USER_ID.eq(id))
                    .orderBy(GROUPS.NAME)
            )
                .map {
                    it.toGroup()
                }
                .collectList()
                .awaitSingle()
        }
    }

}