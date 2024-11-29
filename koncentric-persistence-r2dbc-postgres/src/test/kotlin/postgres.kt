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

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.opentable.db.postgres.embedded.FlywayPreparer
import io.koncentric.persistence.r2dbc.postgres.user_and_group.registerEnums
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.codec.EnumCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SchemaLocations {
    val users_and_groups = "io/koncentric/test_domains/users_and_groups/schema/postgresql"
}

class PostgresTestListener(private val schemaLocation: String) : TestListener {

    private val embeddedPostgres: EmbeddedPostgres by lazy { EmbeddedPostgres.start() }

    fun getPort(): Int = embeddedPostgres.port

    override suspend fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        FlywayPreparer
            .forClasspathLocation(schemaLocation)
            .prepare(embeddedPostgres.postgresDatabase)
    }

    override suspend fun afterSpec(spec: Spec) {
        withContext(Dispatchers.IO) { kotlin.runCatching { embeddedPostgres.close() }}.getOrThrow()
        super.afterSpec(spec)
    }
}

fun connectionFactory(getPort: () -> Int) = PostgresqlConnectionFactory(
    PostgresqlConnectionConfiguration.builder()
        .host("localhost")
        .port(getPort())
        .username("postgres")
        .password("postgres")
        .database("postgres")
        .codecRegistrar(EnumCodec.builder().apply { registerEnums() }.build())
        .build()
)