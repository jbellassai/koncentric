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

package io.koncentric.persistence.r2dbc.postgres.user_and_group

import io.koncentric.persistence.PersistenceException
import io.koncentric.persistence.r2dbc.postgres.R2DbcPostgresTransactionalDatabaseInterface
import io.koncentric.persistence.r2dbc.postgres.user_and_group.UserRepository.getMembersForGroup
import io.koncentric.persistence.storage.ITransactionAware
import io.koncentric.test_domains.users_and_groups.*
import io.r2dbc.spi.Row
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle

private data class R2dbcDataMappingException(override val message: String) : RuntimeException(message)

private inline fun <reified T : Any> Row.getRequiredColumn(name: String): T =
    this.get(name, T::class.java)
        ?: throw R2dbcDataMappingException("No value for column [$name] required for Type [${T::class.qualifiedName}")

private fun Row.toUser(): IUser {
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

    private suspend fun getUserId(id: UserId): Int? {
        val statement = connection().createStatement(
            """
            SELECT id FROM users WHERE external_id = $1
        """.trimIndent()
        )
        return statement
            .bind("$1", id.value)
            .fetchSize(1)
            .execute()
            .awaitFirstOrNull()
            ?.map { row, _ -> row.getRequiredColumn<Int>("id") }
            ?.awaitFirstOrNull()
    }

    private suspend fun getUser(id: Int): IUser? {
        val statement = connection().createStatement(
            """
            SELECT * FROM users WHERE id = $1
        """.trimIndent()
        )
        val result = statement
            .bind("$1", id)
            .fetchSize(1)
            .execute()
            .awaitFirstOrNull()
        return result?.map { row, _ -> row.toUser() }?.awaitFirstOrNull()
    }

    override suspend fun getUser(id: UserId): IUser? {
        val statement = connection().createStatement(
            """
            SELECT * FROM users WHERE external_id = $1
        """.trimIndent()
        )
        val result = statement
            .bind("$1", id.value)
            .fetchSize(1)
            .execute()
            .awaitFirstOrNull()
        return result?.map { row, _ -> row.toUser() }?.awaitFirstOrNull()
    }

    override suspend fun getByEmail(email: String): IUser? {
        val statement = connection().createStatement(
            """
            SELECT * FROM users WHERE email = $1
        """.trimIndent()
        )
        val result = statement
            .bind("$1", email)
            .fetchSize(1)
            .execute()
            .awaitFirstOrNull()
        return result?.map { row, _ -> row.toUser() }?.awaitFirstOrNull()
    }

    override suspend fun create(userSpec: NewUserSpec): IUser {
        if (getByEmail(userSpec.email) != null) throw UserEmailNotUniqueException(userSpec.email)
        val statement = connection().createStatement(
            """
            INSERT INTO users (external_id, email, first_name, last_name, status)
                VALUES ($1, $2, $3, $4, $5)
        """.trimIndent()
        )
        val id = this.nextUserId()
        statement
            .bind("$1", id.value)
            .bind("$2", userSpec.email)
            .bind("$3", userSpec.firstName)
            .bind("$4", userSpec.lastName)
            .bind("$5", EnabledStatus.ENABLED)
            .execute()
            .awaitFirstOrNull()?.rowsUpdated?.awaitSingle()
        return getUser(id) ?: throw PersistenceException("Failed to load created IUser")
    }

    suspend fun updateName(id: UserId, firstName: String, lastName: String) {
        val statement = connection().createStatement(
            """
            UPDATE users SET first_name = $1, last_name = $2 WHERE external_id = $3
        """.trimIndent()
        )
        statement
            .bind("$1", firstName)
            .bind("$2", lastName)
            .bind("$3", id.value)
            .execute()
            .awaitFirstOrNull()?.rowsUpdated?.awaitSingle()
    }

    override suspend fun delete(id: UserId) {
        val statement = connection().createStatement(
            """
            DELETE FROM users WHERE external_id = $1
        """.trimIndent()
        )
        statement
            .bind("$1", id.value)
            .execute()
            .awaitFirstOrNull()
    }

    override suspend fun deleteAll() {
        @Suppress("SqlWithoutWhere")
        val statement = connection().createStatement(
            """
            DELETE FROM users
        """.trimIndent()
        )
        statement
            .execute()
            .awaitFirstOrNull()
    }

    suspend fun addGroupMembership(user: IUser, group: IGroup) {
        val statement = connection().createStatement(
            """
            INSERT INTO groups_users (group_id, user_id, since) 
            VALUES (
                (select id from groups where external_id = $1), 
                (select id from users where external_id = $2), 
                transaction_timestamp()
            )
                ON CONFLICT DO NOTHING
        """.trimIndent()
        )
        val updated = statement
            .bind("$1", group.id.value)
            .bind("$2", user.id.value)
            .execute()
            .awaitFirstOrNull()?.rowsUpdated?.awaitSingle()
        println("UPDATED: $updated")
    }

    suspend fun getMembersForGroup(id: Int): List<IUser> {
        val statement = connection().createStatement(
            """
            SELECT * FROM users u
                INNER JOIN groups_users gu on gu.user_id = u.id
                WHERE gu.group_id = $1
                ORDER BY u.email
        """.trimIndent()
        )
        return statement
            .bind("$1", id)
            .execute()
            .awaitFirst()
            .map { row, _ -> row.toUser() }
            .collectList()
            .awaitSingle()
    }

}

private fun Row.toGroup(): IGroup {
    val internalId: Int = getRequiredColumn("id")
    return IGroup.Builder()
        .withProp("_dbId", Int)
        .withId(GroupId(getRequiredColumn("external_id")))
        .withName(getRequiredColumn("name"))
        .withMembersBy { getMembersForGroup(internalId) }
        .build()
}

object GroupRepository :
    AbstractGroupRepository<R2DbcPostgresTransactionalDatabaseInterface>(),
    ITransactionAware<R2DbcPostgresTransactionalDatabaseInterface> {

    private fun connection() = currentTransaction().db.connection

    suspend fun getGroupId(id: GroupId): Int? {
        val statement = connection().createStatement(
            """
            SELECT id FROM groups WHERE external_id = $1
        """.trimIndent()
        )
        return statement
            .bind("$1", id.value)
            .fetchSize(1)
            .execute()
            .awaitFirstOrNull()
            ?.map { row, _ -> row.getRequiredColumn<Int>("id") }
            ?.awaitFirstOrNull()
    }

    private suspend fun getGroup(id: Int): IGroup? {
        val statement = connection().createStatement(
            """
            SELECT * FROM groups WHERE id = $1
        """.trimIndent()
        )
        val result = statement
            .bind("$1", id)
            .fetchSize(1)
            .execute()
            .awaitFirstOrNull()
        return result?.map { row, _ -> row.toGroup() }?.awaitFirstOrNull()
    }

    override suspend fun getGroup(id: GroupId): IGroup? {
        val statement = connection().createStatement(
            """
            SELECT * FROM groups WHERE external_id = $1
        """.trimIndent()
        )
        val result = statement
            .bind("$1", id.value)
            .fetchSize(1)
            .execute()
            .awaitFirstOrNull()
        return result?.map { row, _ -> row.toGroup() }?.awaitFirstOrNull()
    }

    override suspend fun getByName(name: String): IGroup? {
        val statement = connection().createStatement(
            """
            SELECT * FROM groups WHERE name = $1
        """.trimIndent()
        )
        val result = statement
            .bind("$1", name)
            .fetchSize(1)
            .execute()
            .awaitFirstOrNull()
        return result?.map { row, _ -> row.toGroup() }?.awaitFirstOrNull()
    }

    override suspend fun create(groupSpec: NewGroupSpec): IGroup {
        if (getByName(groupSpec.name) != null) throw GroupNameNotUniqueException(groupSpec.name)
        val statement = connection().createStatement(
            """
            INSERT INTO groups (external_id, name) VALUES ($1, $2)
        """.trimIndent()
        )
        val id = this.nextGroupId()
        statement
            .bind("$1", id.value)
            .bind("$2", groupSpec.name)
            .execute()
            .awaitFirstOrNull()?.rowsUpdated?.awaitSingle()
        return getGroup(id) ?: throw PersistenceException("Failed to load created IGroup")
    }

    suspend fun updateName(id: GroupId, name: String) {
        val statement = connection().createStatement(
            """
            UPDATE groups SET name = $1 WHERE external_id = $2
        """.trimIndent()
        )
        statement
            .bind("$1", name)
            .bind("$2", id.value)
            .execute()
            .awaitFirstOrNull()?.rowsUpdated?.awaitSingle()
    }

    override suspend fun delete(id: GroupId) {
        val statement = connection().createStatement(
            """
            DELETE FROM groups WHERE external_id = $1
        """.trimIndent()
        )
        statement
            .bind("$1", id.value)
            .execute()
            .awaitFirstOrNull()
    }

    override suspend fun deleteAll() {
        @Suppress("SqlWithoutWhere")
        val statement = connection().createStatement(
            """
            DELETE FROM groups
        """.trimIndent()
        )
        statement
            .execute()
            .awaitFirstOrNull()
    }

    suspend fun getGroupsForUser(id: Int): List<IGroup> {
        val statement = connection().createStatement(
            """
            SELECT * FROM groups g
                INNER JOIN groups_users gu on gu.group_id = g.id
                WHERE gu.user_id = $1
                ORDER BY g.name
        """.trimIndent()
        )
        return statement
            .bind("$1", id)
            .execute()
            .awaitFirst()
            .map { row, _ -> row.toGroup() }
            .collectList()
            .awaitSingle()
    }

}