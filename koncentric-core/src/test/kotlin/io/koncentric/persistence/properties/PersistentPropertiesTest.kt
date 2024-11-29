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

package io.koncentric.persistence.properties

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

interface TopAggregate : IMutatePersistently{
    val id: UUID
    suspend fun embeddedEntity(): Entity
    suspend fun embeddedAggregate(): EmbeddedAggregate
}

interface EmbeddedAggregate : IMutatePersistently {
    val id: UUID
    suspend fun list(): List<Entity>
}

interface Entity : IMutatePersistently {
    val id: UUID
    val name: String
}

fun topAggregate(id: UUID = UUID.randomUUID(),
                 embeddedEntity: Entity? = null,
                 embeddedAggregate: EmbeddedAggregate? = null) =
    mockk<TopAggregate>().also {
        every { it.id } returns id
        if(embeddedEntity != null) {
            coEvery { it.embeddedEntity() } returns embeddedEntity
        }
        if(embeddedAggregate != null) {
            coEvery { it.embeddedAggregate() } returns embeddedAggregate
        }
    }

fun topAggregate(id: UUID = UUID.randomUUID(), embeddedEntity: Entity) =
    mockk<TopAggregate>().also {
        every { it.id } returns id
        coEvery { it.embeddedEntity() } returns embeddedEntity
    }

fun embeddedAggregate(id: UUID = UUID.randomUUID(), vararg embeddedEntities: Entity) =
    mockk<EmbeddedAggregate>().also {
        every { it.id } returns id
        coEvery { it.list() } returns listOf(*embeddedEntities)
    }

fun entity(id: UUID = UUID.randomUUID(), name: String = "Entity") =
    mockk<Entity>().also {
        every { it.id } returns id
        every { it.name } returns name
    }

class PersistentPropertiesTest: StringSpec({

    "Test toPersistentMap() with simple properties" {
        val entity = entity()
        entity.toPersistentMap().apply {
            getOrFail("id") shouldBe entity.id
            getOrFail("name") shouldBe entity.name
        }
    }

    "Test toPersistentMap() with embedded lazy entity function" {
        val entity = entity()
        val topAggregate = topAggregate(embeddedEntity = entity)
        topAggregate.toPersistentMap().apply {
            getOrFail("id") shouldBe topAggregate.id
            getOrFail("embeddedEntity") shouldBeSameFunctionAs topAggregate::embeddedEntity
        }
    }

    "Test toPersistentMap() with embedded lazy embedded aggregate function" {
        val entity = entity()
        val embeddedAggregate = embeddedAggregate(id = UUID.randomUUID(), entity)
        val topAggregate = topAggregate(embeddedAggregate = embeddedAggregate)
        topAggregate.toPersistentMap().apply {
            getOrFail("id") shouldBe topAggregate.id
            getOrFail("embeddedAggregate") shouldBeSameFunctionAs topAggregate::embeddedAggregate
        }
    }

    "Test PersistentProperties with simple properties" {
        val entity = entity()
        val underTest = entity.toPersistentProperties()
        underTest.prop<UUID>("id") shouldBe entity.id
        underTest.prop<String>("name") shouldBe entity.name
        underTest.toLazyDebugMap() shouldBe linkedMapOf(
            "id" to entity.id,
            "name" to entity.name
        )
    }

    "Test PersistentProperties with embedded lazy entity function" {
        val entity = entity()
        val topAggregate = topAggregate(embeddedEntity = entity)
        val underTest = topAggregate.toPersistentProperties()
        underTest.prop<UUID>("id") shouldBe topAggregate.id
        underTest.lazyProp<Entity>("embeddedEntity") shouldBe entity
        underTest.toLazyDebugMap() shouldBe linkedMapOf(
            "id" to topAggregate.id,
            "embeddedAggregate" to NotYetResolved,
            "embeddedEntity" to entity
        )
    }

    "A lazy property function should only be invoked once no matter how many times the property is accessed" {
        val count = AtomicInteger(0)
        val entity = entity()
        val topAggregate = mockk<TopAggregate>().also {
            every { it.id } returns UUID.randomUUID()
            coEvery { it.embeddedEntity() } answers {
                count.incrementAndGet()
                entity
            }
        }
        val underTest = topAggregate.toPersistentProperties().also {
            it.prop<UUID>("id") shouldBe topAggregate.id
            it.toLazyDebugMap() shouldBe linkedMapOf(
                "id" to topAggregate.id,
                "embeddedAggregate" to NotYetResolved,
                "embeddedEntity" to NotYetResolved
            )
        }

        repeat(10) {
            underTest.lazyProp<Entity>("embeddedEntity") shouldBe entity
        }
        count.get() shouldBe 1
        underTest.toLazyDebugMap() shouldBe linkedMapOf(
            "id" to topAggregate.id,
            "embeddedAggregate" to NotYetResolved,
            "embeddedEntity" to entity
        )
    }

    "A lazy property that was reset should have its function called again next time it's accessed" {
        val count = AtomicInteger(0)
        val entity = entity()
        val topAggregate = mockk<TopAggregate>().also {
            every { it.id } returns UUID.randomUUID()
            coEvery { it.embeddedEntity() } answers {
                count.incrementAndGet()
                entity
            }
        }
        val underTest = topAggregate.toPersistentProperties().also {
            it.prop(TopAggregate::id) shouldBe topAggregate.id
            it.toLazyDebugMap() shouldBe linkedMapOf(
                "id" to topAggregate.id,
                "embeddedAggregate" to NotYetResolved,
                "embeddedEntity" to NotYetResolved
            )
        }


        repeat(10) {
            underTest.lazyProp(TopAggregate::embeddedEntity) shouldBe entity
        }
        count.get() shouldBe 1
        underTest.toLazyDebugMap() shouldBe linkedMapOf(
            "id" to topAggregate.id,
            "embeddedAggregate" to NotYetResolved,
            "embeddedEntity" to entity
        )

        underTest.resetLazy(TopAggregate::embeddedEntity)
        underTest.also {
            it.prop(TopAggregate::id) shouldBe topAggregate.id
            it.toLazyDebugMap() shouldBe linkedMapOf(
                "id" to topAggregate.id,
                "embeddedAggregate" to NotYetResolved,
                "embeddedEntity" to NotYetResolved
            )
        }

        repeat(10) {
            underTest.lazyProp(TopAggregate::embeddedEntity) shouldBe entity
        }
        count.get() shouldBe 2
        underTest.toLazyDebugMap() shouldBe linkedMapOf(
            "id" to topAggregate.id,
            "embeddedAggregate" to NotYetResolved,
            "embeddedEntity" to entity
        )
    }

    "A lazy property will have its function called again inside a lazyRefresh {} block" {
        val count = AtomicInteger(0)
        val entity = entity()
        val topAggregate = mockk<TopAggregate>().also {
            every { it.id } returns UUID.randomUUID()
            coEvery { it.embeddedEntity() } answers {
                count.incrementAndGet()
                entity
            }
        }
        val underTest = topAggregate.toPersistentProperties()
        underTest.prop(TopAggregate::id) shouldBe topAggregate.id

        repeat(10) {
            underTest.lazyProp(TopAggregate::embeddedEntity) shouldBe entity
        }
        count.get() shouldBe 1

        lazyRefresh {
            underTest.lazyProp(TopAggregate::embeddedEntity) shouldBe entity
        }
        count.get() shouldBe 2
    }

    "A resolved lazy property value can be updated via the updateResolved() function" {
        val count = AtomicInteger(0)
        val entity = entity()
        val entity2 = entity()
        val topAggregate = mockk<TopAggregate>().also {
            every { it.id } returns UUID.randomUUID()
            coEvery { it.embeddedEntity() } answers {
                count.incrementAndGet()
                entity
            }
        }
        val underTest = topAggregate.toPersistentProperties()
        repeat(10) {
            underTest.lazyProp(TopAggregate::embeddedEntity) shouldBe entity
        }
        count.get() shouldBe 1
        underTest.toLazyDebugMap() shouldBe linkedMapOf(
            "id" to topAggregate.id,
            "embeddedAggregate" to NotYetResolved,
            "embeddedEntity" to entity
        )

        underTest.updateIfResolved(TopAggregate::embeddedEntity) { entity2 }
        underTest.lazyProp(TopAggregate::embeddedEntity) shouldBe entity2
        count.get() shouldBe 1 // should not have triggered the original function again
        underTest.toLazyDebugMap() shouldBe linkedMapOf(
            "id" to topAggregate.id,
            "embeddedAggregate" to NotYetResolved,
            "embeddedEntity" to entity2
        )
    }

    "A non-lazy property value can be updated via the updateResolved() function" {
        val originalId = UUID.randomUUID()
        val newId = UUID.randomUUID()
        val topAggregate = mockk<TopAggregate>().also {
            every { it.id } returns originalId
        }
        val underTest = topAggregate.toPersistentProperties()
        underTest.prop(TopAggregate::id) shouldBe topAggregate.id
        underTest.updateIfResolved("id", UUID::class) { newId }
        underTest.prop(TopAggregate::id) shouldBe newId
        underTest.toLazyDebugMap() shouldBe linkedMapOf(
            "id" to newId,
            "embeddedAggregate" to NotYetResolved,
            "embeddedEntity" to NotYetResolved
        )
    }

    "An unresolved lazy property will not be updated via the updateResolved() function" {
        val count = AtomicInteger(0)
        val entity = entity()
        val entity2 = entity()
        val topAggregate = mockk<TopAggregate>().also {
            every { it.id } returns UUID.randomUUID()
            coEvery { it.embeddedEntity() } answers {
                count.incrementAndGet()
                entity
            }
        }
        val underTest = topAggregate.toPersistentProperties()
        count.get() shouldBe 0
        underTest.toLazyDebugMap() shouldBe linkedMapOf(
            "id" to topAggregate.id,
            "embeddedAggregate" to NotYetResolved,
            "embeddedEntity" to NotYetResolved
        )

        underTest.updateIfResolved(TopAggregate::embeddedEntity) { entity2 }
        count.get() shouldBe 0
        underTest.toLazyDebugMap() shouldBe linkedMapOf(
            "id" to topAggregate.id,
            "embeddedAggregate" to NotYetResolved,
            "embeddedEntity" to NotYetResolved
        )

        underTest.lazyProp(TopAggregate::embeddedEntity) shouldBe entity
        count.get() shouldBe 1
        underTest.toLazyDebugMap() shouldBe linkedMapOf(
            "id" to topAggregate.id,
            "embeddedAggregate" to NotYetResolved,
            "embeddedEntity" to entity
        )
    }

})