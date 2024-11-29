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

package io.koncentric.persistence.r2dbc.postgres

import io.koncentric.persistence.storage.IDomainStorage
import io.koncentric.persistence.storage.ITransactionalDatabaseInterface
import io.r2dbc.postgresql.api.PostgresqlConnection
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitSingle

data class R2DbcPostgresTransactionalDatabaseInterface(val connection: PostgresqlConnection): ITransactionalDatabaseInterface

class R2dbcPostgresDomainStorage(
    private val connectionFactory: ConnectionFactory
    ) : IDomainStorage<R2DbcPostgresTransactionalDatabaseInterface> {

    override suspend fun getTransactionalDatabaseInterface(): R2DbcPostgresTransactionalDatabaseInterface =
        R2DbcPostgresTransactionalDatabaseInterface(connectionFactory.create().awaitSingle() as PostgresqlConnection)

}