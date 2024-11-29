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

import io.koncentric.persistence.properties.lazyRefresh
import io.koncentric.persistence.tx.CurrentTransactionUnavailableException
import io.koncentric.persistence.tx.ITransactionManager
import io.koncentric.persistence.tx.withReadOnlyTransaction
import io.koncentric.persistence.tx.withReadWriteTransaction
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

open class PersistenceTests(
    private val txManager: ITransactionManager<*>,
    private val userRepository: AbstractUserRepository<*>,
    private val groupRepository: AbstractGroupRepository<*>,
    private val testListeners: List<TestListener> = emptyList()
) : StringSpec({

    listeners(testListeners)

    val email1 = "jbellassai@example.com"
    val groupName1 = "group1"

    afterTest {
        txManager.withReadWriteTransaction {
            userRepository.deleteAll()
            groupRepository.deleteAll()
        }
    }

    "No user should exist with the email" {
        txManager.withReadOnlyTransaction {
            userRepository.getByEmail(email1) shouldBe null
        }
    }

    "No group should exist with the name" {
        txManager.withReadOnlyTransaction {
            groupRepository.getByName(groupName1) shouldBe null
        }
    }

    "User CRUD" {
        val saved = txManager.withReadWriteTransaction {
            userRepository.getByEmail(email1) shouldBe null
            userRepository.create(NewUserSpec(
                email = email1,
                firstName = "John",
                lastName = "Bellassai"
            ))
        } ?: fail("should not be null")
        saved.apply {
            id shouldNotBe null
            email shouldBe email1
            firstName shouldBe "John"
            lastName shouldBe "Bellassai"
            enabledStatus shouldBe EnabledStatus.ENABLED
        }
        txManager.withReadOnlyTransaction {
            val user = userRepository.getUser(saved.id) ?: fail("should not be null")
            user.apply {
                id shouldBe saved.id
                email shouldBe saved.email
                firstName shouldBe saved.firstName
                lastName shouldBe saved.lastName
                enabledStatus shouldBe saved.enabledStatus
            }
        }
        txManager.withReadWriteTransaction {
            val user = userRepository.getUser(saved.id) ?: fail("should not be null")
            user.mutate {
                updateName(user.firstName.uppercase(), user.lastName.uppercase())
            }
        }
        txManager.withReadOnlyTransaction {
            val user = userRepository.getUser(saved.id) ?: fail("should not be null")
            user.apply {
                id shouldBe saved.id
                email shouldBe email1
                firstName shouldBe saved.firstName.uppercase()
                lastName shouldBe saved.lastName.uppercase()
                enabledStatus shouldBe EnabledStatus.ENABLED
            }
            println(user.toLazyDebugMap())
            user.groups()
            println(user.toLazyDebugMap())
        }
    }

    "Group CRUD" {
        val saved = txManager.withReadWriteTransaction {
            groupRepository.getByName(groupName1) shouldBe null
            groupRepository.create(NewGroupSpec(
                name = groupName1
            ))
        } ?: fail("should not be null")
        saved.apply {
            id shouldNotBe null
            name shouldBe groupName1
        }
        txManager.withReadOnlyTransaction {
            val group = groupRepository.getGroup(saved.id) ?: fail("should not be null")
            group.apply {
                id shouldBe saved.id
                name shouldBe saved.name
            }
        }
        txManager.withReadWriteTransaction {
            val group = groupRepository.getGroup(saved.id) ?: fail("should not be null")
            group.mutate {
                updateName(group.name.uppercase())
            }
        }
        txManager.withReadOnlyTransaction {
            val group = groupRepository.getGroup(saved.id) ?: fail("should not be null")
            group.apply {
                id shouldBe saved.id
                name shouldBe saved.name
            }
            println(group.toLazyDebugMap())
            group.members()
            println(group.toLazyDebugMap())
        }
    }

    "Group Membership" {
        val (userId, groupId) = txManager.withReadWriteTransaction {
            val user = userRepository.create(NewUserSpec(
                email = email1,
                firstName = "John",
                lastName = "Bellassai"
            ))
            val group = groupRepository.create(NewGroupSpec(
                name = groupName1
            ))
            Pair(user.id, group.id)
        } ?: fail("should not be null")

        txManager.withReadWriteTransaction {
            val user = userRepository.getUser(userId) ?: fail("user should exist")
            val group = groupRepository.getGroup(groupId) ?: fail("group should exist")
            user.groups() shouldBe emptyList()
            group.members() shouldBe emptyList()
            val updatedUser = user.mutate {
                addMembershipTo(group)
                this.groups().map { it.id } shouldBe listOf(group.id)
            }
            updatedUser.groups().map { it.id } shouldBe listOf(group.id)
            group.members() shouldBe emptyList() // the update is not yet propagated to the group
            lazyRefresh {
                group.members().map { it.id } shouldBe listOf(user.id)
            }
        }

        txManager.withReadOnlyTransaction {
            val user = userRepository.getUser(userId) ?: fail("user should exist")
            val group = groupRepository.getGroup(groupId) ?: fail("group should exist")
            user.groups().map { it.id } shouldBe listOf(group.id)
            group.members().map { it.id } shouldBe listOf(user.id)
        }
    }

    "Trying to access a lazy property for the first time outside of a transaction should throw CurrentTransactionUnavailableException" {
        val groupId = txManager.withReadWriteTransaction {
            val group = groupRepository.create(NewGroupSpec(
                name = groupName1
            ))
            group.id
        } ?: fail("should not be null")

        val group = txManager.withReadOnlyTransaction {
            groupRepository.getGroup(groupId)
        } ?: fail("should not be null")

        shouldThrow<CurrentTransactionUnavailableException> { group.members() }

        //should not throw since it's accessed in a tx
        txManager.withReadOnlyTransaction { group.members() shouldBe emptyList() }

        // should not throw since the members list is now cached
        group.members() shouldBe emptyList()

        lazyRefresh {
            shouldThrow<CurrentTransactionUnavailableException> { group.members() }
        }

        //should not throw since it's accessed in a tx
        txManager.withReadWriteTransaction { group.members() shouldBe emptyList() }

        // should not throw since the members list is now cached
        group.members() shouldBe emptyList()
    }
})